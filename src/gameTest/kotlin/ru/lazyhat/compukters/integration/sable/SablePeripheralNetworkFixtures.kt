/*
 * The Compukters Developers
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 * Licensed under the Apache License, Version 2.0.
 */

package ru.lazyhat.compukters.integration.sable

import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayer
import ru.lazyhat.compukters.impl.registry.CompuktersRegistry
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralBindingResult
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralConfiguratorItem
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralConfiguratorServer
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralWorldPositions

internal object SablePeripheralNetworkFixtures {
    // The vanilla logged-in GameTest mock has no negotiated Sable channels and fails Sable's login sync.
    // These tests exercise server-side item interactions; a NeoForge fake player does not simulate a login.
    fun player(helper: GameTestHelper): FakePlayer = FakePlayer(helper.level, GameProfile(java.util.UUID.randomUUID(), "network-test"))

    fun bind(
        helper: GameTestHelper,
        positions: List<BlockPos>,
    ): java.util.UUID {
        val player = player(helper)
        val stack = ItemStack(CompuktersRegistry.PERIPHERAL_CONFIGURATOR_ITEM.get())
        val item = stack.item as PeripheralConfiguratorItem
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        item.use(helper.level, player, InteractionHand.MAIN_HAND)
        for (position in positions) {
            player.setPos(requireNotNull(PeripheralWorldPositions.project(helper.level, Vec3.atCenterOf(position))))
            val result = PeripheralConfiguratorServer.bind(player, InteractionHand.MAIN_HAND, position, Direction.NORTH)
            helper.assertTrue(
                result in listOf(PeripheralBindingResult.CREATED, PeripheralBindingResult.SELECTED, PeripheralBindingResult.BOUND),
                "binding failed: $result",
            )
        }
        return requireNotNull(item.selectedNetwork(stack))
    }
}
