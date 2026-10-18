package com.uncosens.rocketchess.analysis

import android.app.ActivityManager
import android.content.Context

/**
 * How many engines a full analysis runs at once, each on one thread with its own positions.
 * One Stockfish 19 process holds about [ENGINE_MB] for its network plus its hash (measured on a Galaxy S23+).
 */
object EngineBudget {
    const val ENGINE_MB = 250
    /** Memory left free for the app and the system after the engines start. */
    const val RESERVE_MB = 512
    const val MIN_HASH_MB = 16

    /**
     * The user's core count ([cores]), lowered for memory: 4 needs 7 GB or more in all
     * (an 8 GB device), 2 needs 5 GB, else 1; and the engines must fit in free memory.
     */
    fun engines(cores: Int, totalMb: Long, availMb: Long, lowRam: Boolean, hashMb: Int): Int {
        if (lowRam) return 1
        val byTotal = when {
            totalMb >= 7_000 -> 4
            totalMb >= 5_000 -> 2
            else -> 1
        }
        val byFree = ((availMb - RESERVE_MB - hashMb) / ENGINE_MB).toInt()
        return minOf(cores, byTotal, byFree).coerceAtLeast(1)
    }

    /** Each engine's share of the hash in [opts], at least [MIN_HASH_MB]. */
    fun hashShare(opts: EngineOptions, engines: Int) = maxOf(MIN_HASH_MB, opts.hashMb / engines)

    /** [engines] for this device now. */
    fun forDevice(c: Context, opts: EngineOptions): Int {
        val am = c.getSystemService(ActivityManager::class.java)
        val m = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return engines(opts.threads, m.totalMem / MB, m.availMem / MB, am.isLowRamDevice, opts.hashMb)
    }

    private const val MB = 1024L * 1024L
}
