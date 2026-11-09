package com.cocoexperiments.tumbler.domain

object PinPolicy {
    const val LENGTH = 4
}

sealed interface TapResult {
    /** Correct group; [step] digits have been matched so far. */
    data class Advanced(val step: Int) : TapResult

    /** Last digit matched: the PIN is complete. */
    data object Completed : TapResult

    /** Wrong group; the sequence has been reset to step 0. */
    data object Rejected : TapResult
}

/**
 * Pure state machine for one unlock attempt. [step] is the index (0 until pin.size) of the
 * digit currently being matched.
 */
class PinSession(private val pin: List<Int>) {
    init {
        require(pin.isNotEmpty()) { "PIN must not be empty" }
    }

    var step: Int = 0
        private set

    fun onGroupSelected(group: KeypadGroup): TapResult {
        if (pin[step] in group) {
            step++
            if (step == pin.size) return TapResult.Completed
            return TapResult.Advanced(step)
        }
        step = 0
        return TapResult.Rejected
    }

    fun reset() {
        step = 0
    }
}

/** Escalating lockout after repeated wrong taps (brute-force protection). */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private const val BASE_MILLIS = 30_000L
    private const val MAX_MILLIS = 15 * 60_000L

    /** Lockout to apply after [consecutiveFailures] wrong taps: 0 for the first 4, then 30s, 60s, ... capped at 15 min. */
    fun lockoutMillis(consecutiveFailures: Int): Long {
        if (consecutiveFailures < FREE_ATTEMPTS) return 0L
        val tier = (consecutiveFailures - FREE_ATTEMPTS).coerceAtMost(5)
        return minOf(BASE_MILLIS shl tier, MAX_MILLIS)
    }
}
