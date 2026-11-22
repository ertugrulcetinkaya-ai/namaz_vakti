package com.example.namazvakti

import com.example.namazvakti.ui.main.AppBackStack
import com.example.namazvakti.ui.main.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppBackStackTest {
    @Test
    fun backFromImsakiyeReturnsToMonthly() {
        val stack = AppBackStack().push(Screen.Monthly).push(Screen.Imsakiye)

        assertEquals(Screen.Monthly, stack.pop().current)
    }

    @Test
    fun backFromReligiousDaysReturnsToMonthly() {
        val stack = AppBackStack().push(Screen.Monthly).push(Screen.ReligiousDays)

        assertEquals(Screen.Monthly, stack.pop().current)
    }

    @Test
    fun backFromDistrictsReturnsToProvinces() {
        val cities = Screen.Cities(step = 1, provinceCity = null)
        val stack = AppBackStack().push(cities).push(Screen.Cities(step = 2, provinceCity = "Istanbul"))

        assertEquals(cities, stack.pop().current)
    }

    @Test
    fun backFromHomeEmptiesTheStack() {
        assertNull(AppBackStack().pop().current)
    }

    @Test
    fun replaceTopKeepsThePreviousScreen() {
        val stack = AppBackStack().push(Screen.Monthly).push(Screen.Imsakiye)
        val replaced = stack.replaceTop(Screen.ReligiousDays)

        assertEquals(Screen.ReligiousDays, replaced.current)
        assertEquals(Screen.Monthly, replaced.pop().current)
    }
}
