package com.example.namazvakti.domain.model

internal object HijriMonthNames {
    val turkishNames = listOf(
        "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir",
        "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce"
    )

    fun turkishName(number: Int?): String? = number?.let { turkishNames.getOrNull(it - 1) }

    fun numberFromTurkishName(name: String): Int = turkishNames.indexOf(name) + 1
}
