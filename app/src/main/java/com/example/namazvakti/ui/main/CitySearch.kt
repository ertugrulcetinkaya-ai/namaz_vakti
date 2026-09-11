package com.example.namazvakti.ui.main

import java.util.Locale

fun String.citySearchKey(): String = lowercase(Locale.forLanguageTag("tr-TR"))
    .replace("ı", "i")
    .replace("ğ", "g")
    .replace("ü", "u")
    .replace("ş", "s")
    .replace("ö", "o")
    .replace("ç", "c")
