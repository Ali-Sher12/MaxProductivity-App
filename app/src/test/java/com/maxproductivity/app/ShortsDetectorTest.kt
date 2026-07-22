package com.maxproductivity.app

import org.junit.Assert.assertFalse
import org.junit.Test

class ShortsDetectorTest {

    @Test
    fun isShortsScreen_returnsFalseForNullRoot() {
        assertFalse(ShortsDetector.isShortsScreen(null))
    }
}
