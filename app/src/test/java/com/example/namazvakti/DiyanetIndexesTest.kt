package com.example.namazvakti

import com.example.namazvakti.domain.model.DiyanetDistricts
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiyanetIndexesTest {
    @Test
    fun districtIndexesMatchTheSourceLists() {
        val districts = DiyanetDistricts.districts

        assertEquals("District IDs must be unique", districts.size, DiyanetDistricts.byId.size)
        assertEquals(districts.associateBy { it.id }, DiyanetDistricts.byId)
        assertEquals(districts.groupBy { it.provinceId }, DiyanetDistricts.byProvinceId)
        assertTrue(DiyanetDistricts.byProvinceId.values.flatten().all { it in districts })
    }

    @Test
    fun religiousDayDateIndexMatchesTheSourceEntries() {
        val entriesByDate = DiyanetReligiousDays.entries.groupBy { it.date }

        assertEquals(entriesByDate, DiyanetReligiousDays.byDate)
        entriesByDate.forEach { (date, events) ->
            assertEquals(events, DiyanetReligiousDays.on(date))
        }
        assertTrue(DiyanetReligiousDays.on(DiyanetReligiousDays.entries.first().date.minusDays(1)).isEmpty())
    }
}
