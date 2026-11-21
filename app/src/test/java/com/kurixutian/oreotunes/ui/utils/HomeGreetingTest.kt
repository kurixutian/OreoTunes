package com.kurixutian.oreotunes.ui.utils

import com.kurixutian.oreotunes.R
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGreetingTest {

    @Test
    fun homeGreetingResource_returnsGreetingForEachTimeOfDayRange() {
        assertEquals(R.string.home_greeting_morning, homeGreetingResource(0))
        assertEquals(R.string.home_greeting_morning, homeGreetingResource(11))
        assertEquals(R.string.home_greeting_afternoon, homeGreetingResource(12))
        assertEquals(R.string.home_greeting_afternoon, homeGreetingResource(16))
        assertEquals(R.string.home_greeting_evening, homeGreetingResource(17))
        assertEquals(R.string.home_greeting_evening, homeGreetingResource(23))
    }
}
