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

import dev.ryanhcode.sable.api.physics.mass.MassData
import java.util.UUID

internal data class PhysicsInertiaTensor(
    val xx: Double,
    val xy: Double,
    val xz: Double,
    val yx: Double,
    val yy: Double,
    val yz: Double,
    val zx: Double,
    val zy: Double,
    val zz: Double,
)

internal data class PhysicsMassProperties(
    val constructionId: UUID,
    val dimension: String,
    val gameTick: Long,
    val mass: Double,
    val centerOfMass: PhysicsVector,
    val inertiaTensor: PhysicsInertiaTensor,
) {
    companion object {
        fun copyOf(
            constructionId: UUID,
            dimension: String,
            gameTick: Long,
            data: MassData,
        ): PhysicsMassProperties? {
            val mass = data.mass
            val center = data.centerOfMass ?: return null
            val tensor = data.inertiaTensor
            if (!mass.isFinite() || mass <= 0.0 || !center.isFinite || !tensor.isFinite) return null
            return PhysicsMassProperties(
                constructionId,
                dimension,
                gameTick,
                mass,
                PhysicsVector(center.x(), center.y(), center.z()),
                PhysicsInertiaTensor(
                    tensor.m00(),
                    tensor.m10(),
                    tensor.m20(),
                    tensor.m01(),
                    tensor.m11(),
                    tensor.m21(),
                    tensor.m02(),
                    tensor.m12(),
                    tensor.m22(),
                ),
            )
        }
    }
}
