# Diyanet religious days data

`app/src/main/java/com/example/namazvakti/domain/model/DiyanetReligiousDays.kt` is generated from the official Diyanet annual **Dini Günler** pages. The generator is a developer tool and is not part of the Android build.

## Annual update

1. Update `SOURCE_IDS` in `generate.py` with the year's official `icerik` id.
2. Run `python3 tools/religious_days/generate.py` from the repository root. The script downloads and parses the official pages and rewrites the Kotlin source.
3. Review the diff against each linked Diyanet table, especially event names and dates written with `/`. A slash pair is one evening observance dated on its first Gregorian day; the generator verifies that both Gregorian and Hijri pairs are consecutive and that the event is marked as a night. The exact source labels and raw Diyanet name remain in the generated data.
4. If a new or changed source name has no explicit Turkish display-name mapping, generation fails. Add the reviewed mapping in `generate.py` before accepting the new data.
5. Run the religious-day consistency tests and project verification commands before committing.

Current source pages:

- [2025](https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=152)
- [2026](https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=153)
- [2027](https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=154)
- [2028](https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=185) through [2035](https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=192)

The generator includes rows with a Diyanet religious-day name only. It does not add events that are absent from the source table.

## Verified calendar relationships (2025–2035)

The generated pages were checked against the checked-in `DiyanetHijriCalendar` and `DiyanetRamadanCalendar`:

- All 12 listed Ramadan starts, Qadr nights, and first Ramadan Bayramı days match the Ramadan ranges; their Hijri dates also match the official month-start calendar.
- Hicrî Yılbaşı is 1 Muharrem, Aşure is 10 Muharrem, and the first Kurban Bayramı day is 10 Zilhicce in every published year.
- Mevlid Kandili is listed on 11 Rebiülevvel, Miraç Kandili on 26 Recep, and Berat Kandili on 14 Şaban in every occurrence.
- Regaip Kandili is listed on the Thursday date for the first Friday night in Recep. Its Hijri day number varies because the Gregorian evening can begin the following Hijri day; slash pairs are retained verbatim as source labels but only the first Gregorian date is an event date.

For `isNight`, kandils and Kadir Gecesi are evening-starting observances: the first Diyanet-listed Gregorian date is presented as “Bu akşam …”. Other source rows are daytime observances. All observances on one date are combined in the home announcement and in a single reminder; the large widget gives night observances priority when its one-line label is constrained.
