package com.example.namazvakti

import com.example.namazvakti.app.*
import com.example.namazvakti.data.local.*
import com.example.namazvakti.data.remote.*
import com.example.namazvakti.data.repository.*
import com.example.namazvakti.domain.model.*
import com.example.namazvakti.domain.policy.*
import com.example.namazvakti.domain.port.*
import com.example.namazvakti.ui.main.*
import com.example.namazvakti.widget.*
import com.example.namazvakti.widget.alarm.*
import com.example.namazvakti.widget.renderer.*
import com.example.namazvakti.widget.worker.*
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

    @Test
    fun unknownPersistedCityKeepsItsOwnDisplayName() {
        val option = PrayerLocationConfig.optionForCityAndCountry("Baku", "Azerbaijan")

        assertEquals("Baku", option.city)
        assertEquals("BAKU", option.displayCity)
    }
}
