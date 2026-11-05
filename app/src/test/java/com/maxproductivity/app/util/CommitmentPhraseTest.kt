package com.maxproductivity.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentPhraseTest {

    @Test
    fun matches_ignoresCaseAndPunctuation() {
        assertTrue(CommitmentPhrase.matches("I will not waste my time and potential."))
        assertTrue(CommitmentPhrase.matches("i will not waste my time and potential"))
        assertTrue(CommitmentPhrase.matches("I WILL NOT WASTE MY TIME AND POTENTIAL!"))
        assertTrue(CommitmentPhrase.matches("  I will not waste my time and potential  "))
        assertTrue(CommitmentPhrase.matches("I will not waste my time, and potential."))
        assertTrue(CommitmentPhrase.matches("I will not waste my time,and potential!"))
        assertTrue(CommitmentPhrase.matches("I-will-not-waste-my-time-and-potential"))
    }

    @Test
    fun matches_rejectsDifferentWording() {
        assertFalse(CommitmentPhrase.matches(""))
        assertFalse(CommitmentPhrase.matches("I will not waste time"))
        assertFalse(CommitmentPhrase.matches("I will waste my time and potential."))
    }

    @Test
    fun normalize_collapsesWhitespaceAndDropsPunctuation() {
        assertEquals(
            "i will not waste my time and potential",
            CommitmentPhrase.normalize("I will not waste my time and potential."),
        )
        assertEquals(
            "i will not waste my time and potential",
            CommitmentPhrase.normalize("I   will not   waste my time and potential!!!"),
        )
    }
}
