/*
 * The Compukters Developers
 *
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.lazyhat.compukters.integration.sable

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import ru.lazyhat.compukters.impl.registry.CompuktersRegistry
import ru.lazyhat.compukters.minecraft.computer.ComputerBlockEntity
import ru.lazyhat.compukters.minecraft.computer.ComputerPeripheralIdentity
import ru.lazyhat.compukters.minecraft.display.DisplayBlockEntity
import ru.lazyhat.compukters.minecraft.peripheral.ComputerPeripheralNames
import net.minecraft.util.Unit as MinecraftUnit

@PrefixGameTestTemplate(false)
object SableObservationGameTests {
    @JvmStatic
    @GameTest(template = "bastion/mobs/empty", templateNamespace = "minecraft", timeoutTicks = 100_000)
    fun computerAssemblyAndReturn(helper: GameTestHelper) {
        val level = helper.level
        val relative = BlockPos(2, 3, 2)
        val anchor = helper.absolutePos(relative)
        helper.setBlock(relative, CompuktersRegistry.COMPUTER.get())
        helper.setBlock(relative.below(), Blocks.STONE)
        val original = helper.getBlockEntity(relative) as ComputerBlockEntity
        val displayPosition = anchor.north(4)
        // Sable splits disconnected clusters on tick; keep the fixture as one physical construction.
        val supports = (0..4).map { anchor.below().north(it) }
        supports.forEach { level.setBlockAndUpdate(it, Blocks.STONE.defaultBlockState()) }
        level.setBlockAndUpdate(displayPosition, CompuktersRegistry.DISPLAY.get().defaultBlockState())
        SablePeripheralNetworkFixtures.bind(helper, listOf(anchor, displayPosition))
        ComputerPeripheralNames.setName(level, ComputerPeripheralIdentity("compukters-display", displayPosition, "text"), "sable-screen")
        val displayId = requireNotNull((level.getBlockEntity(displayPosition) as DisplayBlockEntity).screenIdentity())
        val identity = original.computerId()
        val container = requireNotNull(SubLevelContainer.getContainer(level))
        val physics = container.physicsSystem()
        val wasPaused = physics.paused
        // Lifecycle evidence must not depend on how far the body falls while VM workers settle.
        physics.setPaused(true)
        var body: ServerSubLevel? = null
        var retained: PhysicsSnapshot? = null
        var originalMachine: Long? = null
        var currentPosition = anchor
        val guest = SableGuestComputerScenario(helper) { currentPosition }
        val sequence = helper.startSequence()
        guest.prepare(sequence, SNAPSHOT_PROGRAM)
        guest.run(sequence, "world")
        guest.awaitMarker(sequence, "sable-unavailable-world")
        sequence
            .thenWaitUntil {
                helper.assertTrue(original.terminalMachineId != null, "world computer has not attached its runtime")
                originalMachine = original.terminalMachineId
            }.thenExecute {
                helper.assertTrue(SablePhysicsSnapshots.read(level, anchor) == null, "world computer unexpectedly has a physics snapshot")
                helper.assertTrue(
                    level.getBlockEntity(displayPosition) is DisplayBlockEntity,
                    "source display disappeared before assembly: ${level.getBlockState(displayPosition)}",
                )
                body =
                    SubLevelAssemblyHelper.assembleBlocks(
                        level,
                        anchor,
                        listOf(anchor, displayPosition) + supports,
                        BoundingBox3i(anchor.x, anchor.y - 1, anchor.z - 4, anchor.x + 1, anchor.y + 1, anchor.z + 1),
                    )
                container.addForceLoadTicket(requireNotNull(body), SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
                helper.assertTrue(original.isRemoved, "assembly did not retire the source block entity")
                val assembledPosition = requireNotNull(body).plot.centerBlock
                currentPosition = assembledPosition
                helper.assertTrue(level.getBlockEntity(assembledPosition) is ComputerBlockEntity, "computer was not copied during assembly")
                helper.assertTrue(
                    level.getBlockEntity(assembledPosition.north(4)) is DisplayBlockEntity,
                    "display missing immediately after assembly: ${level.getBlockState(assembledPosition.north(4))}",
                )
            }.thenWaitUntil {
                val position = requireNotNull(body).plot.centerBlock
                val moved = level.getBlockEntity(position) as? ComputerBlockEntity
                helper.assertTrue(
                    moved != null,
                    "assembled computer block entity is missing at $position; " +
                        "state=${level.getBlockState(position)}; buildHeight=${level.minBuildHeight}..${level.maxBuildHeight}",
                )
                helper.assertTrue(moved!!.computerId() == identity, "assembly changed ComputerId")
                val movedDisplay = level.getBlockEntity(position.north(4)) as? DisplayBlockEntity
                helper.assertTrue(
                    movedDisplay != null,
                    "assembled display missing at ${position.north(
                        4,
                    )}: state=${level.getBlockState(position.north(4))}, loaded=${level.hasChunkAt(position.north(4))}",
                )
                requireNotNull(movedDisplay)
                helper.assertTrue(movedDisplay.screenIdentity() == displayId, "assembly changed screen identity")
                helper.assertTrue(movedDisplay.displayColor(0, 0) == 0x123456, "assembly lost published screen pixels")
                helper.assertTrue(moved.terminalMachineId != null, "moved computer did not acquire its existing filesystem/runtime")
                helper.assertTrue(moved.terminalMachineId != originalMachine, "assembly retained the source runtime epoch")
                val snapshot = SablePhysicsSnapshots.read(level, position)
                helper.assertTrue(snapshot != null, "on-demand snapshot is unavailable")
                helper.assertTrue(snapshot!!.constructionId == requireNotNull(body).uniqueId, "snapshot belongs to another construction")
                helper.assertTrue(snapshot.linearVelocity.y.isFinite(), "invalid solver velocity")
                retained = snapshot
            }
        guest.run(sequence, "inside")
        guest.awaitMarker(sequence, "sable-snapshot-ok")
        sequence
            .thenExecute {
                val position = requireNotNull(body).plot.centerBlock
                SubLevelAssemblyHelper.moveBlocks(
                    level,
                    SubLevelAssemblyHelper.AssemblyTransform(position, anchor, 0, Rotation.NONE, level),
                    listOf(position, position.north(4)),
                )
                currentPosition = anchor
            }.thenWaitUntil {
                val restored = level.getBlockEntity(anchor) as? ComputerBlockEntity
                helper.assertTrue(restored != null, "returned computer block entity is missing")
                helper.assertTrue(restored!!.computerId() == identity, "return move changed ComputerId")
                val returnedDisplay = level.getBlockEntity(displayPosition) as DisplayBlockEntity
                helper.assertTrue(
                    returnedDisplay.screenIdentity() == displayId && returnedDisplay.displayColor(0, 0) == 0x123456,
                    "return lost screen identity or pixels",
                )
                helper.assertTrue(restored.terminalMachineId != null, "returned computer did not attach its runtime")
                helper.assertTrue(SablePhysicsSnapshots.read(level, anchor) == null, "returned computer is still bound to a construction")
                helper.assertTrue(requireNotNull(retained).constructionId == requireNotNull(body).uniqueId, "retained snapshot changed")
            }
        guest.run(sequence, "returned")
        guest.awaitMarker(sequence, "sable-unavailable-returned")
        sequence
            .thenExecute {
                container.removeForceLoadTicket(requireNotNull(body), SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
                container.removeSubLevel(requireNotNull(body), SubLevelRemovalReason.REMOVED)
                physics.setPaused(wasPaused)
            }.thenSucceed()
        helper.testInfo.addListener(
            object : GameTestListener {
                private fun cleanup() {
                    physics.setPaused(wasPaused)
                    body?.let {
                        container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
                        if (!it.isRemoved) container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
                    }
                }

                override fun testStructureLoaded(info: GameTestInfo) = Unit

                override fun testPassed(
                    info: GameTestInfo,
                    runner: GameTestRunner,
                ) = cleanup()

                override fun testFailed(
                    info: GameTestInfo,
                    runner: GameTestRunner,
                ) = cleanup()

                override fun testAddedForRerun(
                    info: GameTestInfo,
                    rerun: GameTestInfo,
                    runner: GameTestRunner,
                ) = cleanup()
            },
        )
    }

    private val SNAPSHOT_PROGRAM =
        """
        import sable.physics.Physics
        import compukter.display.GraphicalDisplay
        import compukter.filesystem.FileSystem
        fun main(args: Array<String>) {
            check(FileSystem.readText("/home/survivor.txt") == "sable-files-survived")
            val display = GraphicalDisplay.namedOrNull("sable-screen") ?: error("bound display unavailable")
            display.pixel(0, 0, 0x123456)
            try {
                val snapshot = Physics.snapshot()
                check(snapshot.constructionId.length == 36)
                check(snapshot.dimension == "minecraft:overworld")
                check(snapshot.gameTick >= 0L)
                check(snapshot.paused)
                check(snapshot.position.x > -30000000.0 && snapshot.position.x < 30000000.0)
                check(snapshot.linearVelocity.y == snapshot.linearVelocity.y)
                check(snapshot.angularVelocity.y == snapshot.angularVelocity.y)
                check(snapshot.orientation.w == snapshot.orientation.w)
                check(snapshot.scale.x > 0.0)
                println("sable-snapshot-ok")
            } catch (error: IllegalStateException) {
                println("sable-unavailable-" + args[0])
            }
        }
        """.trimIndent()
}

@EventBusSubscriber(modid = CompuktersSableMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object SableGameTestRegistration {
    @JvmStatic
    @SubscribeEvent
    fun register(event: RegisterGameTestsEvent) {
        event.register(SableObservationGameTests::class.java)
        event.register(SableHibernationGameTests::class.java)
        event.register(SablePeripheralNetworkGameTests::class.java)
    }
}
