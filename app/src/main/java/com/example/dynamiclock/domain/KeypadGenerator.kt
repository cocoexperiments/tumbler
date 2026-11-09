package com.example.dynamiclock.domain

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** One on-screen button: exactly [KeypadGenerator.GROUP_SIZE] distinct digits. */
data class KeypadGroup(val digits: List<Int>) {
    init {
        require(digits.size == KeypadGenerator.GROUP_SIZE) { "A group holds ${KeypadGenerator.GROUP_SIZE} digits" }
        require(digits.toSet().size == digits.size) { "Digits within a group must be distinct" }
        require(digits.all { it in 0..9 }) { "Digits must be 0-9" }
    }

    operator fun contains(digit: Int): Boolean = digit in digits
}

/**
 * Builds the 3x3 board of digit groups.
 *
 * Guarantees for every board returned by [generate]:
 *  - 9 groups, each with 3 distinct digits (27 slots in total);
 *  - every digit 0-9 appears in at least [MIN_OCCURRENCES] groups, so any PIN digit is always
 *    reachable through at least two different buttons;
 *  - group order and digit order inside each group are random.
 *
 * Construction: 0-9 twice (20 slots) + 7 distinct extra digits (so each digit appears 2 or 3 times).
 * The pool is shuffled and cut into groups of 3 (rejection sampling keeps the distribution close to
 * uniform). If 64 shuffles all produce a duplicate inside some group, a collision-free dealing
 * fallback is used, which always succeeds.
 */
class KeypadGenerator(
    private val random: Random = SecureRandom().asKotlinRandom(),
) {

    fun generate(): List<KeypadGroup> {
        repeat(MAX_RANDOM_ATTEMPTS) {
            val groups = buildPool().shuffled(random).chunked(GROUP_SIZE)
            if (groups.all { it.toSet().size == GROUP_SIZE }) {
                return groups.map(::KeypadGroup)
            }
        }
        return dealWithoutCollisions(buildPool())
    }

    private fun buildPool(): List<Int> {
        val pool = ArrayList<Int>(TOTAL_SLOTS)
        repeat(MIN_OCCURRENCES) { pool.addAll(DIGITS) }
        pool.addAll(DIGITS.shuffled(random).take(TOTAL_SLOTS - pool.size))
        return pool
    }

    /**
     * Lines equal digits up in consecutive runs, then deals them round-robin into 9 buckets.
     * A run is at most 9 long, so its copies always land in different buckets, and 27 items
     * over 9 buckets gives exactly 3 per bucket.
     */
    internal fun dealWithoutCollisions(pool: List<Int>): List<KeypadGroup> {
        val ordered = pool.groupBy { it }.values.shuffled(random).flatten()
        val buckets = List(GROUP_COUNT) { mutableListOf<Int>() }
        ordered.forEachIndexed { i, digit -> buckets[i % GROUP_COUNT].add(digit) }
        return buckets.map { KeypadGroup(it.shuffled(random)) }.shuffled(random)
    }

    companion object {
        const val GROUP_COUNT = 9
        const val GROUP_SIZE = 3
        const val MIN_OCCURRENCES = 2
        private const val TOTAL_SLOTS = GROUP_COUNT * GROUP_SIZE
        private const val MAX_RANDOM_ATTEMPTS = 64
        private val DIGITS = (0..9).toList()

        /** True when [groups] satisfies every guarantee documented on the class. */
        fun isValid(groups: List<KeypadGroup>): Boolean =
            groups.size == GROUP_COUNT &&
                DIGITS.all { digit -> groups.count { digit in it } >= MIN_OCCURRENCES }
    }
}
