package com.istanbul.weatherintel.ui

import java.util.Locale

/** Shared number formatting used across screens. */
fun format(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(Locale.US, "%.1f", v)

