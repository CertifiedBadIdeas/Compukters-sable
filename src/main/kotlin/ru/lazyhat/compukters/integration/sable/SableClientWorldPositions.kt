/*
 * The Compukters Developers
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 * Licensed under the Apache License, Version 2.0.
 */

package ru.lazyhat.compukters.integration.sable

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import org.joml.Vector3d
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPeripheralWorldIntegration

/** Match Sable's block rendering without exposing client classes to dedicated-server setup. */
@EventBusSubscriber(modid = CompuktersSableMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object SableClientWorldPositions {
    @JvmStatic
    @SubscribeEvent
    fun setup(event: FMLClientSetupEvent) {
        event.enqueueWork { CompuktersPeripheralWorldIntegration.installRenderProjection(::project) }
    }

    private fun project(
        level: Level,
        point: Vec3,
        partialTick: Float,
    ): Vec3? {
        if (!Sable.HELPER.isInPlotGrid(level, kotlin.math.floor(point.x).toInt() shr 4, kotlin.math.floor(point.z).toInt() shr 4)) {
            return point
        }
        val body = Sable.HELPER.getContaining(level, point) as? ClientSubLevel ?: return null
        if (body.isRemoved) return null
        val transformed = body.renderPose(partialTick).transformPosition(Vector3d(point.x, point.y, point.z))
        return Vec3(transformed.x, transformed.y, transformed.z)
    }
}
