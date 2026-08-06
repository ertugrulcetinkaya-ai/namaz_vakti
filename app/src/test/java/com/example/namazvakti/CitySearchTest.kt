package com.example.namazvakti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitySearchTest {
    @Test
    fun normalizesTurkishCharacters() {
        assertEquals("istanbul", "İSTANBUL".citySearchKey())
        assertEquals("agri", "AĞRI".citySearchKey())
        assertEquals("sanliurfa", "ŞANLIURFA".citySearchKey())
    }

    @Test
    fun asciiQueryMatchesTurkishCity() {
        assertTrue("İZMİR".citySearchKey().contains("izmir".citySearchKey()))
        assertTrue("AĞRI".citySearchKey().contains("agri".citySearchKey()))
    }
}
