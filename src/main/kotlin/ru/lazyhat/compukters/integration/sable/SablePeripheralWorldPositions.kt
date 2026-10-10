/*
 * The Compukters Developers
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 * Licensed under the Apache License, Version 2.0.
 */

package ru.lazyhat.compukters.integration.sable

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3d
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPeripheralWorldIntegration

/** Query the live pose for each access; reserved plot coordinates are never a world-space fallback. */
internal object SablePeripheralWorldPositions {
    fun register() {
        CompuktersPeripheralWorldIntegration.install(::project, ::nearby)
    }

    private fun project(
        level: Level,
        point: Vec3,
    ): Vec3? {
        if (!Sable.HELPER.isInPlotGrid(level, kotlin.math.floor(point.x).toInt() shr 4, kotlin.math.floor(point.z).toInt() shr 4)) {
            return point
        }
        val body = Sable.HELPER.getContaining(level, point) ?: return null
        if (body.isRemoved) return null
        val transformed = body.logicalPose().transformPosition(Vector3d(point.x, point.y, point.z))
        return Vec3(transformed.x, transformed.y, transformed.z)
    }

    private fun nearby(
        level: ServerLevel,
        center: Vec3,
        radius: Double,
    ): Sequence<BlockEntity> {
        val bounds =
            BoundingBox3d(
                center.x - radius,
                center.y - radius,
                center.z - radius,
                center.x + radius,
                center.y + radius,
                center.z + radius,
            )
        val container = SubLevelContainer.getContainer(level) ?: return emptySequence()
        // Cached global bounds can lag assembly or pose changes. Project loaded chunk extents
        // with the live pose; the base applies exact per-device distance and its scan budget.
        return container.allSubLevels
            .asSequence()
            .filterNot { it.isRemoved }
            .flatMap { body ->
                body.plot.loadedChunks.asSequence().filter { holder ->
                    val chunk = holder.chunk.pos
                    BoundingBox3d(
                        chunk.minBlockX.toDouble(),
                        level.minBuildHeight.toDouble(),
                        chunk.minBlockZ.toDouble(),
                        chunk.minBlockX + 16.0,
                        level.maxBuildHeight.toDouble(),
                        chunk.minBlockZ + 16.0,
                    ).transform(body.logicalPose()).intersects(bounds)
                }
            }.flatMap {
                it.chunk.blockEntities.values
                    .asSequence()
            }
    }
}
