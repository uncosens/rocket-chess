package com.uncosens.rocketchess.data

import android.content.Context

/** The last name searched on each site, to fill the search box. */
object KnownUsers {
    private fun prefs(c: Context) = c.getSharedPreferences("users", Context.MODE_PRIVATE)

    fun add(c: Context, source: String, name: String) {
        prefs(c).edit().putString("last_$source", name.trim()).apply()
    }

    fun last(c: Context, source: String): String = prefs(c).getString("last_$source", null).orEmpty()

    /** Names older versions kept for all games. Moved onto each game once, then forgotten. */
    fun oldNames(c: Context): Set<String> = prefs(c).getStringSet("names", null).orEmpty().toSet()

    fun forgetOldNames(c: Context) = prefs(c).edit().remove("names").apply()
}

/** True if [owner] played White, false for Black, null if [owner] is null or not a player. White wins a tie. */
fun userSide(white: String, black: String, owner: String?): Boolean? = when {
    owner == null -> null
    white.equals(owner, ignoreCase = true) -> true
    black.equals(owner, ignoreCase = true) -> false
    else -> null
}
