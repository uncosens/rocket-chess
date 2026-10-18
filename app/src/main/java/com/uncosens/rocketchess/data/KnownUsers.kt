package com.uncosens.rocketchess.data

import android.content.Context

/** User names searched online. In a game, a known name's side is the user's side. */
object KnownUsers {
    private fun prefs(c: Context) = c.getSharedPreferences("users", Context.MODE_PRIVATE)

    fun names(c: Context): Set<String> = prefs(c).getStringSet("names", null).orEmpty().toSet()

    fun add(c: Context, source: String, name: String) {
        prefs(c).edit().putStringSet("names", names(c) + name.trim().lowercase()).putString("last_$source", name.trim()).apply()
    }

    /** The last name searched on [source], to fill the search box. */
    fun last(c: Context, source: String): String = prefs(c).getString("last_$source", null).orEmpty()
}

/** True if the user played White, false for Black, null if neither name is known. White wins a tie. */
fun userSide(white: String, black: String, names: Set<String>): Boolean? = when {
    white.lowercase() in names -> true
    black.lowercase() in names -> false
    else -> null
}
