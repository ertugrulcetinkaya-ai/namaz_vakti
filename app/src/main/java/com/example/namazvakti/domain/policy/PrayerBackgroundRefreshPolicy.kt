package com.example.namazvakti.domain.policy

fun needsBackgroundRefresh(hasWidgets: Boolean, notificationsEnabled: Boolean): Boolean =
    hasWidgets || notificationsEnabled
