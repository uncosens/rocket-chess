package com.uncosens.rocketchess.analysis

import android.content.Context
import kotlin.math.abs

/** Engine options the user can change. Defaults come from AnalysisPolicy. */
data class EngineOptions(
    val threads: Int = AnalysisPolicy.THREADS,
    val hashMb: Int = AnalysisPolicy.HASH_MB,
    val depth: Int = AnalysisPolicy.DEPTH,
) {
    /** Moves each value into the allowed choices for a device with [cores] CPU cores. */
    fun clamped(cores: Int) = EngineOptions(
        threads = threads.coerceIn(1, maxThreads(cores)),
        hashMb = HASH_CHOICES.minBy { abs(it - hashMb) },
        depth = depth.coerceIn(DEPTH_RANGE),
    )

    companion object {
        val HASH_CHOICES = listOf(16, 32, 64, 128)
        val DEPTH_RANGE = 10..20
        fun maxThreads(cores: Int) = cores.coerceIn(1, 4)

        /** Reads saved values with [get] (key, default) and clamps them. */
        fun read(cores: Int, get: (String, Int) -> Int) = EngineOptions(
            threads = get(KEY_THREADS, AnalysisPolicy.THREADS),
            hashMb = get(KEY_HASH, AnalysisPolicy.HASH_MB),
            depth = get(KEY_DEPTH, AnalysisPolicy.DEPTH),
        ).clamped(cores)

        internal const val KEY_THREADS = "threads"
        internal const val KEY_HASH = "hash_mb"
        internal const val KEY_DEPTH = "depth"
    }
}

/** Keeps engine options in SharedPreferences. */
object EngineOptionsStore {
    val cores get() = Runtime.getRuntime().availableProcessors()

    private fun prefs(c: Context) = c.getSharedPreferences("engine", Context.MODE_PRIVATE)

    fun load(c: Context): EngineOptions = prefs(c).let { p -> EngineOptions.read(cores) { k, d -> p.getInt(k, d) } }

    fun save(c: Context, o: EngineOptions) {
        prefs(c).edit()
            .putInt(EngineOptions.KEY_THREADS, o.threads)
            .putInt(EngineOptions.KEY_HASH, o.hashMb)
            .putInt(EngineOptions.KEY_DEPTH, o.depth)
            .apply()
    }
}
