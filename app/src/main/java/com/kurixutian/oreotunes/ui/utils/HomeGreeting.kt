package com.kurixutian.oreotunes.ui.utils

import com.kurixutian.oreotunes.R

fun homeGreetingResource(hourOfDay: Int): Int = when (hourOfDay) {
    in 0..11 -> R.string.home_greeting_morning
    in 12..16 -> R.string.home_greeting_afternoon
    in 17..23 -> R.string.home_greeting_evening
    else -> error("hourOfDay must be in 0..23")
}
