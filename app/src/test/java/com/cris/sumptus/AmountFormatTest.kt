package com.cris.sumptus

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFormatTest {
    @Test
    fun acceptsSpanishAndDotDecimalFormats() {
        assertEquals(120_050L, parseAmountToCents("1.200,50"))
        assertEquals(120_050L, parseAmountToCents("1200.50"))
        assertEquals(-250L, parseAmountToCents("-2,50"))
    }
}
