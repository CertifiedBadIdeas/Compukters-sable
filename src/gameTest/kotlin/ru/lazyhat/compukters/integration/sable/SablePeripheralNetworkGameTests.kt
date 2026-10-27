/*
 * The Compukters Developers
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 * Licensed under the Apache License, Version 2.0.
 */

package ru.lazyhat.compukters.integration.sable

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import ru.lazyhat.compukters.impl.registry.CompuktersRegistry
import ru.lazyhat.compukters.minecraft.computer.ComputerPeripheralIdentity
import ru.lazyhat.compukters.minecraft.display.AttachedDisplaySurfaces
import ru.lazyhat.compukters.minecraft.display.DisplayAssembly
import ru.lazyhat.compukters.minecraft.display.DisplayBlockEntity
import ru.lazyhat.compukters.minecraft.peripheral.ComputerPeripheralLookup
import ru.lazyhat.compukters.minecraft.peripheral.ComputerPeripheralLookupStatus
import ru.lazyhat.compukters.minecraft.peripheral.ComputerPeripheralNames
import ru.lazyhat.compukters.minecraft.peripheral.ConfiguratorOverlayServer
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralBindingResult
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralConfiguratorContext
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralConfiguratorItem
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralConfiguratorMode
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralConfiguratorServer
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralWorldPositions
import net.minecraft.util.Unit as MinecraftUnit

@PrefixGameTestTemplate(false)
object SablePeripheralNetworkGameTests {
    @JvmStatic
    @GameTest(batch = "surface-removal", template = "bastion/mobs/empty", templateNamespace = "minecraft")
    fun permanentRemovalRetiresSurfacesButUnloadPreservesThem(helper: GameTestHelper) {
        val level = helper.level
        val shore = helper.absolutePos(BlockPos(2, 3, 2))
        level.setBlockAndUpdate(shore, CompuktersRegistry.COMPUTER.get().defaultBlockState())
        val container = requireNotNull(SubLevelContainer.getContainer(level))
        val physics = container.physicsSystem()
        val paused = physics.paused
        physics.setPaused(true)
        try {
            for ((index, reason) in listOf(SubLevelRemovalReason.REMOVED, SubLevelRemovalReason.UNLOADED).withIndex()) {
                val first = shore.offset(4, 0, index * 4)
                val second = first.west()
                listOf(first, second).forEach { level.setBlockAndUpdate(it, Blocks.OAK_PLANKS.defaultBlockState()) }
                AttachedDisplaySurfaces.create(level, first, second, Direction.NORTH)
                SablePeripheralNetworkFixtures.bind(helper, listOf(shore, first))
                val name = "surface-$index"
                ComputerPeripheralNames.setName(level, ComputerPeripheralIdentity("compukters-display", first, "text"), name)
                val body =
                    SubLevelAssemblyHelper.assembleBlocks(
                        level,
                        first,
                        listOf(first, second),
                        BoundingBox3i(first.x - 1, first.y, first.z, first.x + 1, first.y + 1, first.z + 1),
                    )
                body.updateMergedMassData(1.0f)

                fun present() =
                    PeripheralConfiguratorServer
                        .inspect(
                            level,
                            PeripheralConfiguratorContext(shore, Direction.NORTH),
                            PeripheralConfiguratorMode.INSPECT_NETWORK,
                        ).entries
                        .any { it.name == name }
                helper.assertTrue(present(), "native assembly lost its attached display membership")
                container.removeSubLevel(body, reason)
                container.processSubLevelRemovals()
                helper.assertTrue(present() == (reason == SubLevelRemovalReason.UNLOADED), "wrong saved display lifetime for $reason")
            }
            helper.succeed()
        } finally {
            physics.setPaused(paused)
        }
    }

    @JvmStatic
    @GameTest(batch = "peripheral-partial", template = "bastion/mobs/empty", templateNamespace = "minecraft")
    fun partialScreenTransfer(helper: GameTestHelper) {
        val level = helper.level
        val computer = helper.absolutePos(BlockPos(2, 3, 2))
        val first = computer.east(4)
        val second = first.west()
        level.setBlockAndUpdate(computer, CompuktersRegistry.COMPUTER.get().defaultBlockState())
        for (position in listOf(first, second)) level.setBlockAndUpdate(position, CompuktersRegistry.DISPLAY.get().defaultBlockState())
        val network = SablePeripheralNetworkFixtures.bind(helper, listOf(computer, first, second))
        val player = SablePeripheralNetworkFixtures.player(helper)
        val stack = ItemStack(CompuktersRegistry.PERIPHERAL_CONFIGURATOR_ITEM.get())
        val selections = mutableMapOf<String, String>()
        for (position in listOf(first, second)) {
            player.setPos(Vec3.atCenterOf(position))
            DisplayAssembly.click(player, stack, position, { selections[it].orEmpty() }, { key, value ->
                if (value == null) selections.remove(key) else selections[key] = value
            })
        }
        ComputerPeripheralNames.setName(level, ComputerPeripheralIdentity("compukters-display", first, "text"), "partial-screen")
        val old = (level.getBlockEntity(first) as DisplayBlockEntity).screenIdentity()
        val target = first.south(4)
        SubLevelAssemblyHelper.moveBlocks(
            level,
            SubLevelAssemblyHelper.AssemblyTransform(first, target, 0, Rotation.NONE, level),
            listOf(first),
        )
        val moved = level.getBlockEntity(target) as DisplayBlockEntity
        val retained = level.getBlockEntity(second) as DisplayBlockEntity
        helper.assertTrue(
            moved.screenIdentity() != old && retained.screenIdentity() != old &&
                moved.screenIdentity() != retained.screenIdentity(),
            "partial screen was not split",
        )
        val inspection =
            PeripheralConfiguratorServer.inspect(
                level,
                PeripheralConfiguratorContext(computer, Direction.NORTH),
                PeripheralConfiguratorMode.INSPECT_NETWORK,
            )
        helper.assertTrue(
            inspection.entries.size == 3 && inspection.entries.count { it.name == "partial-screen" } == 1,
            "partial transfer lost membership or duplicated the name",
        )
        val selected = SablePeripheralNetworkFixtures.bind(helper, listOf(target))
        helper.assertTrue(selected == network, "partial transfer changed network UUID")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(batch = "peripheral-networks", template = "bastion/mobs/empty", templateNamespace = "minecraft")
    fun rangeAssemblyAndReturn(helper: GameTestHelper) {
        val level = helper.level
        val shore = helper.absolutePos(BlockPos(2, 3, 2))
        val onboard = shore.east(6)
        val first = onboard.north(3)
        val second = first.west()
        val other = shore.south(8)
        for (position in listOf(
            shore,
            onboard,
            other,
        )) {
            level.setBlockAndUpdate(position, CompuktersRegistry.COMPUTER.get().defaultBlockState())
        }
        for (position in listOf(first, second)) level.setBlockAndUpdate(position, CompuktersRegistry.DISPLAY.get().defaultBlockState())
        val player = SablePeripheralNetworkFixtures.player(helper)
        val stack = ItemStack(CompuktersRegistry.PERIPHERAL_CONFIGURATOR_ITEM.get())
        val item = stack.item as PeripheralConfiguratorItem
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        item.selectMode(stack, 1)
        for (position in listOf(shore, onboard, other, first, second)) {
            player.setPos(Vec3.atCenterOf(position))
            val result = PeripheralConfiguratorServer.bind(player, InteractionHand.MAIN_HAND, position, Direction.NORTH)
            helper.assertTrue(
                result in listOf(PeripheralBindingResult.CREATED, PeripheralBindingResult.SELECTED, PeripheralBindingResult.BOUND),
                "binding failed: $result",
            )
        }
        val selections = mutableMapOf<String, String>()
        for (position in listOf(first, second)) {
            player.setPos(Vec3.atCenterOf(position))
            DisplayAssembly.click(player, stack, position, { selections[it].orEmpty() }, { key, value ->
                if (value == null) selections.remove(key) else selections[key] = value
            })
        }
        val id = requireNotNull((level.getBlockEntity(first) as DisplayBlockEntity).screenIdentity())
        val network = requireNotNull(item.selectedNetwork(stack))
        ComputerPeripheralNames.setName(level, ComputerPeripheralIdentity("compukters-display", first, "text"), "ship-screen")
        val container = requireNotNull(SubLevelContainer.getContainer(level))
        val physics = container.physicsSystem()
        val paused = physics.paused
        val bodies = mutableListOf<ServerSubLevel>()
        physics.setPaused(true)

        fun assemble(
            anchor: BlockPos,
            positions: List<BlockPos>,
        ): ServerSubLevel {
            val body =
                SubLevelAssemblyHelper.assembleBlocks(
                    level,
                    anchor,
                    positions,
                    BoundingBox3i(anchor.x - 8, anchor.y - 1, anchor.z - 8, anchor.x + 8, anchor.y + 2, anchor.z + 8),
                )
            bodies += body
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
            return body
        }

        fun found(computer: BlockPos) =
            ComputerPeripheralLookup.find(level, computer, "compukters-display", "ship-screen").status ==
                ComputerPeripheralLookupStatus.FOUND
        try {
            val ship = assemble(onboard, listOf(onboard, first, second))
            val movedComputer = ship.plot.centerBlock
            val movedFirst = movedComputer.offset(first.subtract(onboard))
            val movedSecond = movedComputer.offset(second.subtract(onboard))
            helper.assertTrue(
                (level.getBlockEntity(movedFirst) as DisplayBlockEntity).screenIdentity() == id,
                "assembly changed canvas UUID",
            )
            helper.assertTrue(
                (level.getBlockEntity(movedSecond) as DisplayBlockEntity).screenIdentity() == id,
                "assembly split a whole screen",
            )
            helper.assertTrue(found(shore) && found(movedComputer), "shore or onboard computer cannot reach assembled screen")
            helper.assertTrue(
                ConfiguratorOverlayServer.collect(level, shore).devices.any { it.position == movedFirst },
                "nearby ship missing from HUD",
            )
            ship.logicalPose().orientation().rotateY(Math.PI / 2)
            ship.logicalPose().scale().set(2.0)
            helper.assertTrue(found(shore), "rotated and scaled nearby screen is unreachable")
            ship.logicalPose().position().add(100.0, 0.0, 0.0)
            helper.assertTrue(!found(shore) && found(movedComputer), "world distance does not follow live pose")
            val otherShip = assemble(other, listOf(other))
            helper.assertTrue(!found(otherShip.plot.centerBlock), "distant ship reached the screen")
            otherShip.logicalPose().position().add(100.0, 0.0, 0.0)
            helper.assertTrue(found(otherShip.plot.centerBlock), "nearby ship-to-ship access failed")
            val current = requireNotNull(PeripheralWorldPositions.project(level, Vec3.atCenterOf(movedFirst)))
            val desired = Vec3.atCenterOf(shore).add(64.0, 0.0, 0.0)
            val delta = desired.subtract(current)
            ship.logicalPose().position().add(delta.x, delta.y, delta.z)
            helper.assertTrue(
                PeripheralWorldPositions.within(level, Vec3.atCenterOf(shore), Vec3.atCenterOf(movedFirst), 64.0),
                "64-block boundary rejected",
            )
            ship.logicalPose().position().add(0.01, 0.0, 0.0)
            helper.assertTrue(
                !PeripheralWorldPositions.within(level, Vec3.atCenterOf(shore), Vec3.atCenterOf(movedFirst), 64.0),
                "outside radius accepted",
            )
            player.setPos(requireNotNull(PeripheralWorldPositions.project(level, Vec3.atCenterOf(movedComputer))))
            helper.assertTrue(
                PeripheralConfiguratorServer.bind(player, InteractionHand.MAIN_HAND, movedComputer, Direction.NORTH) ==
                    PeripheralBindingResult.BOUND,
                "world-space configurator click failed",
            )
            helper.assertTrue(item.selectedNetwork(stack) == network, "computer network UUID changed")
            SubLevelAssemblyHelper.moveBlocks(
                level,
                SubLevelAssemblyHelper.AssemblyTransform(movedComputer, onboard, 0, Rotation.NONE, level),
                listOf(movedComputer, movedFirst, movedSecond),
            )
            helper.assertTrue(found(shore) && found(onboard), "return lost network bindings or names")
            helper.assertTrue((level.getBlockEntity(first) as DisplayBlockEntity).screenIdentity() == id, "return changed canvas UUID")
            val snapshot =
                PeripheralConfiguratorServer.inspect(
                    level,
                    PeripheralConfiguratorContext(onboard, Direction.NORTH),
                    PeripheralConfiguratorMode.INSPECT_NETWORK,
                )
            helper.assertTrue(snapshot.entries.size == 4, "transfer duplicated network members: ${snapshot.entries}")
            container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED)
            container.processSubLevelRemovals()
            helper.assertTrue(
                PeripheralWorldPositions.project(level, Vec3.atCenterOf(movedFirst)) == null,
                "removed plot used as ordinary world coordinate",
            )
            helper.succeed()
        } finally {
            for (body in bodies) {
                container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
                if (!body.isRemoved) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
            }
            physics.setPaused(paused)
        }
    }
}
