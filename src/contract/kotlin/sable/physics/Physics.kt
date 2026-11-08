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

package sable.physics

import ru.lazyhat.compukters.api.contracts.GuestCall
import ru.lazyhat.compukters.api.contracts.GuestService
import kotlin.math.Matrix3d
import kotlin.math.Rotation3d
import kotlin.math.Vector3d

/** An immutable observation copied at the request's server tick. */
value class PhysicsSnapshot(
    val constructionId: String,
    val dimension: String,
    val gameTick: Long,
    val paused: Boolean,
    val position: Vector3d,
    val orientation: Rotation3d,
    val scale: Vector3d,
    val rotationPoint: Vector3d,
    val linearVelocity: Vector3d,
    val angularVelocity: Vector3d,
)

/**
 * Last published upstream mass data, copied at the request's server tick.
 * Mass uses Sable's kpg unit; the center uses plot coordinates.
 * Inertia is a row-major Matrix3d in plot axes about the center of mass, in kpg * m².
 */
value class PhysicsMassProperties(
    val constructionId: String,
    val dimension: String,
    val gameTick: Long,
    val mass: Double,
    val centerOfMass: Vector3d,
    val inertiaTensor: Matrix3d,
)

@GuestService(bindings = "PhysicsBindings")
interface Physics {
    /** Observes the computer's current construction; throws IllegalStateException when unavailable. */
    @GuestCall(binding = "snapshot")
    fun snapshot(): PhysicsSnapshot

    /** Observes merged mass properties; throws IllegalStateException when unavailable or invalid. */
    @GuestCall(binding = "massProperties")
    fun massProperties(): PhysicsMassProperties
}
