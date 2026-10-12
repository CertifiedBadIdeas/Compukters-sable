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

import kotlin.math.Matrix3d
import kotlin.math.Quaterniond
import kotlin.math.Rotation3d
import kotlin.math.Vector3d

/** An immutable observation copied at the request's server tick. */
public value class PhysicsSnapshot(
    public val constructionId: String,
    public val dimension: String,
    public val gameTick: Long,
    public val paused: Boolean,
    public val position: Vector3d,
    public val orientation: Rotation3d,
    public val scale: Vector3d,
    public val rotationPoint: Vector3d,
    public val linearVelocity: Vector3d,
    public val angularVelocity: Vector3d,
)

/**
 * Last published upstream mass data, copied at the request's server tick.
 * Mass uses Sable's kpg unit; the center uses plot coordinates.
 * Inertia is a row-major Matrix3d in plot axes about the center of mass, in kpg * m².
 */
public value class PhysicsMassProperties(
    public val constructionId: String,
    public val dimension: String,
    public val gameTick: Long,
    public val mass: Double,
    public val centerOfMass: Vector3d,
    public val inertiaTensor: Matrix3d,
)

public object Physics {
    /** Observes the computer's current construction; throws IllegalStateException when unavailable. */
    public fun snapshot(): PhysicsSnapshot {
        val reply = PhysicsBindings.snapshot()
        return PhysicsSnapshot(
            reply.constructionId, reply.dimension, reply.gameTick, reply.paused,
            reply.position, Rotation3d.fromQuaternion(reply.orientation), reply.scale,
            reply.rotationPoint, reply.linearVelocity, reply.angularVelocity,
        )
    }

    /** Observes merged mass properties; throws IllegalStateException when unavailable or invalid. */
    public fun massProperties(): PhysicsMassProperties = PhysicsBindings.massProperties()
}

/** Raw quaternion crosses as unconstrained components; the public adapter validates the rotation. */
internal value class PhysicsSnapshotReply(
    public val constructionId: String,
    public val dimension: String,
    public val gameTick: Long,
    public val paused: Boolean,
    public val position: Vector3d,
    public val orientation: Quaterniond,
    public val scale: Vector3d,
    public val rotationPoint: Vector3d,
    public val linearVelocity: Vector3d,
    public val angularVelocity: Vector3d,
)

private object PhysicsBindings {
    external fun snapshot(): PhysicsSnapshotReply

    external fun massProperties(): PhysicsMassProperties
}
