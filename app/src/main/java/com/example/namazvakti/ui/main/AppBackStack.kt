package com.example.namazvakti.ui.main

import android.os.Parcelable
import androidx.compose.runtime.saveable.Saver
import kotlinx.parcelize.Parcelize

sealed interface Screen : Parcelable {
    @Parcelize
    data object Home : Screen

    @Parcelize
    data class Cities(val step: Int, val provinceCity: String?) : Screen

    @Parcelize
    data object Monthly : Screen

    @Parcelize
    data object Imsakiye : Screen

    @Parcelize
    data object ReligiousDays : Screen

    @Parcelize
    data object About : Screen
}

internal class AppBackStack(private val screens: List<Screen> = listOf(Screen.Home)) {
    val current: Screen?
        get() = screens.lastOrNull()

    fun push(screen: Screen): AppBackStack = AppBackStack(screens + screen)

    fun pop(): AppBackStack = AppBackStack(screens.dropLast(1))

    fun replaceTop(screen: Screen): AppBackStack = pop().push(screen)

    companion object {
        val Saver = Saver<AppBackStack, ArrayList<Screen>>(
            save = { ArrayList(it.screens) },
            restore = ::AppBackStack
        )
    }
}
