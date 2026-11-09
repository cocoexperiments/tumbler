package com.cocoexperiments.tumbler.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeypadGeneratorTest {

    @Test
    fun everyBoardSatisfiesGuarantees() {
        val generator = KeypadGenerator(Random(1234))
        repeat(20_000) {
            val groups = generator.generate()
            assertEquals(9, groups.size)
            groups.forEach { assertEquals(3, it.digits.toSet().size) }
            assertTrue("Board missing a digit twice: $groups", KeypadGenerator.isValid(groups))
        }
    }

    @Test
    fun fallbackDealingAlwaysValid() {
        val generator = KeypadGenerator(Random(99))
        val pool = (0..9).toList().let { it + it } + listOf(0, 1, 2, 3, 4, 5, 6)
        repeat(5_000) {
            assertTrue(KeypadGenerator.isValid(generator.dealWithoutCollisions(pool)))
        }
    }

    @Test
    fun boardsReshuffle() {
        val generator = KeypadGenerator(Random(7))
        val boards = List(50) { generator.generate() }
        assertTrue(boards.distinct().size > 40)
    }

    @Test
    fun anyPinDigitIsAlwaysReachable() {
        val generator = KeypadGenerator(Random(5))
        repeat(2_000) {
            val groups = generator.generate()
            for (digit in 0..9) assertTrue(groups.count { digit in it } >= 2)
        }
    }
}
