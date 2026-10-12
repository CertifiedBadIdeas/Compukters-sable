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
import org.joml.Matrix3d
import org.joml.Matrix3dc
import org.joml.Vector3d
import org.joml.Vector3dc
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PhysicsMassPropertiesTest {
    private val identity = UUID.fromString("a55e080d-4761-443c-af0e-ed7af89b1a84")

    @Test
    fun `tensor components use row then column axes`() {
        val snapshot = assertNotNull(copy(Fixture()))
        assertEquals(identity, snapshot.constructionId)
        assertEquals("minecraft:overworld", snapshot.dimension)
        assertEquals(42L, snapshot.gameTick)
        assertEquals(25.0, snapshot.mass)
        assertEquals(PhysicsVector(10.0, 20.0, 30.0), snapshot.centerOfMass)
        assertEquals(PhysicsInertiaTensor(1.0, 4.0, 7.0, 2.0, 5.0, 8.0, 3.0, 6.0, 9.0), snapshot.inertiaTensor)
    }

    @Test
    fun `retained observation owns its scalar values`() {
        val fixture = Fixture()
        val snapshot = assertNotNull(copy(fixture))
        fixture.totalMass = 50.0
        fixture.center!!.set(100.0, 200.0, 300.0)
        fixture.tensor.zero()
        assertEquals(25.0, snapshot.mass)
        assertEquals(PhysicsVector(10.0, 20.0, 30.0), snapshot.centerOfMass)
        assertEquals(7.0, snapshot.inertiaTensor.xz)
        assertEquals(50.0, assertNotNull(copy(fixture)).mass)
    }

    @Test
    fun `uninitialized and invalid mass is unavailable`() {
        for (mass in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(copy(Fixture().apply { totalMass = mass }), "mass=$mass")
        }
        assertNull(copy(Fixture().apply { center = null }))
    }

    @Test
    fun `nonfinite center and tensor are unavailable`() {
        assertNull(copy(Fixture().apply { center!!.y = Double.NaN }))
        assertNull(copy(Fixture().apply { tensor.m20(Double.POSITIVE_INFINITY) }))
    }

    private fun copy(fixture: Fixture): PhysicsMassProperties? = PhysicsMassProperties.copyOf(identity, "minecraft:overworld", 42L, fixture)

    private class Fixture : MassData {
        var totalMass = 25.0
        var center: Vector3d? = Vector3d(10.0, 20.0, 30.0)
        val tensor = Matrix3d(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)

        override fun getMass(): Double = totalMass

        override fun getInverseMass(): Double = 1.0 / totalMass

        override fun getCenterOfMass(): Vector3dc? = center

        override fun getInertiaTensor(): Matrix3dc = tensor

        override fun getInverseInertiaTensor(): Matrix3dc = Matrix3d()
    }
}
