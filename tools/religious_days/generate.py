#!/usr/bin/env python3
"""Fetch Diyanet's annual religious-day tables and generate the Kotlin data file."""

from __future__ import annotations

from html.parser import HTMLParser
import re
import sys
from datetime import date
from pathlib import Path
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "app/src/main/java/com/example/namazvakti/domain/model/DiyanetReligiousDays.kt"
SOURCE_IDS = {2025: 152, 2026: 153, 2027: 154, **{year: 185 + year - 2028 for year in range(2028, 2036)}}
BASE_URL = "https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik={}"
MONTHS = {
    "OCAK": 1, "SUBAT": 2, "MART": 3, "NISAN": 4, "MAYIS": 5,
    "HAZIRAN": 6, "TEMMUZ": 7, "AGUSTOS": 8, "EYLUL": 9,
    "EKIM": 10, "KASIM": 11, "ARALIK": 12,
}


class TableRows(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.rows: list[list[str]] = []
        self.row: list[str] | None = None
        self.cell: list[str] | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "tr":
            self.row = []
        elif tag in {"td", "th"} and self.row is not None:
            self.cell = []

    def handle_data(self, data: str) -> None:
        if self.cell is not None:
            self.cell.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag in {"td", "th"} and self.cell is not None and self.row is not None:
            self.row.append(" ".join("".join(self.cell).split()))
            self.cell = None
        elif tag == "tr" and self.row is not None:
            self.rows.append(self.row)
            self.row = None


def ascii_month(value: str) -> str:
    return value.upper().translate(str.maketrans({"İ": "I", "Ş": "S", "Ğ": "G", "Ü": "U", "Ö": "O", "Ç": "C"}))


def parse_day_candidates(value: str) -> list[int]:
    compact = re.sub(r"(?<=\d)\s+(?=\d)", "", value)
    pieces = compact.split("/")
    if not pieces or any(not piece.isdigit() for piece in pieces):
        raise ValueError(f"Could not parse day candidates: {value!r}")
    return [int(piece) for piece in pieces]


def parse_month_year(value: str) -> tuple[list[int], int]:
    year_match = re.search(r"(20\d{2})", value)
    if not year_match:
        raise ValueError(f"Could not parse Gregorian year: {value!r}")
    month_text = value[:year_match.start()].strip(" -")
    month_numbers = []
    for month in month_text.split("/"):
        key = re.sub(r"[^A-Z]", "", ascii_month(month))
        if key not in MONTHS:
            raise ValueError(f"Could not parse Gregorian month: {month!r} in {value!r}")
        month_numbers.append(MONTHS[key])
    return month_numbers, int(year_match.group(1))


def type_for(name: str) -> str:
    upper = name.upper()
    if "KANDİL" in upper or "KANDIL" in upper or "GECESİ" in upper or "GECESI" in upper:
        return "KANDIL"
    if "BAYRAM" in upper:
        return "BAYRAM"
    if "AREFE" in upper:
        return "AREFE"
    return "SPECIAL_DAY"


def parse_source(year: int, source_id: int) -> list[dict[str, object]]:
    url = BASE_URL.format(source_id)
    request = Request(url, headers={"User-Agent": "Mozilla/5.0 (compatible; ReligiousDaysDataGenerator/1.0)"})
    with urlopen(request, timeout=30) as response:
        html = response.read().decode("utf-8")
    parser = TableRows()
    parser.feed(html)

    events = []
    for cells in parser.rows:
        if len(cells) != 7:
            continue
        hijri_day, hijri_month, hijri_year, gregorian_day, gregorian_month_year, _weekday, name = cells
        if not re.search(r"\d", gregorian_day) or not re.search(r"20\d{2}", gregorian_month_year):
            continue
        name = " ".join(name.split())
        if not name or set(name) <= {".", "-", "…"}:
            continue
        days = parse_day_candidates(gregorian_day)
        months, gregorian_year = parse_month_year(gregorian_month_year)
        dates = []
        for index, day in enumerate(days):
            month = months[min(index, len(months) - 1)]
            dates.append(date(gregorian_year, month, day).isoformat())
        hijri_label = " ".join((hijri_day, hijri_month, hijri_year))
        source_gregorian_label = " ".join((gregorian_day, gregorian_month_year))
        events.append({
            "dates": dates,
            "name": name,
            "type": type_for(name),
            "hijri": hijri_label,
            "gregorian": source_gregorian_label,
            "is_night": type_for(name) == "KANDIL",
        })
    if not events:
        raise ValueError(f"No religious-day rows parsed for {year} from {url}")
    return events


def kotlin_string(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'


def generate() -> str:
    lines = [
        "// Otomatik üretildi; elle düzenlemeyin. Kaynak: tools/religious_days/generate.py.",
        "package com.example.namazvakti.domain.model",
        "",
        "import java.time.LocalDate",
        "",
        "enum class DiyanetReligiousDayType { KANDIL, BAYRAM, AREFE, SPECIAL_DAY }",
        "",
        "data class DiyanetReligiousDay(",
        "    val type: DiyanetReligiousDayType,",
        "    /** Exact Turkish name in Diyanet's source table. */",
        "    val name: String,",
        "    /** First date as listed; remaining candidates preserve slash-separated Diyanet dates. */",
        "    val date: LocalDate,",
        "    val alternateDates: List<LocalDate>,",
        "    val sourceGregorianDateLabel: String,",
        "    val sourceHijriDateLabel: String?,",
        "    val isNight: Boolean",
        ") {",
        "    val dates: List<LocalDate> get() = listOf(date) + alternateDates",
        "    val displayName: String get() = when {",
        "        name.contains(\"REGAİB\") -> \"Regaip Kandili\"",
        "        name.contains(\"MİRAC\") -> \"Miraç Kandili\"",
        "        name.contains(\"MEVLİD\") -> \"Mevlid Kandili\"",
        "        name.contains(\"MEVLİT\") -> \"Mevlit Kandili\"",
        "        name.contains(\"BERAT\") -> \"Berat Kandili\"",
        "        name.contains(\"KADİR\") -> \"Kadir Gecesi\"",
        "        name.contains(\"RAMAZAN BAYRAMI\") -> bayramDisplayName(\"Ramazan Bayramı\")",
        "        name.contains(\"KURBAN BAYRAMI\") -> bayramDisplayName(\"Kurban Bayramı\")",
        "        name.contains(\"RAMAZAN BAY.\") -> \"Ramazan Bayramı Arefesi\"",
        "        name.contains(\"KURBAN BAY.\") -> \"Kurban Bayramı Arefesi\"",
        "        name == \"AREFE\" -> \"Arefe\"",
        "        name.contains(\"ÜÇ AYLARIN BAŞLANGICI\") -> \"Üç Ayların Başlangıcı\"",
        "        name.contains(\"HİCRİ YILBAŞI\") -> \"Hicrî Yılbaşı\"",
        "        name.contains(\"AŞURE GÜNÜ\") -> \"Aşure Günü\"",
        "        else -> name.lowercase(java.util.Locale.forLanguageTag(\"tr-TR\")).replaceFirstChar { it.titlecase(java.util.Locale.forLanguageTag(\"tr-TR\")) }",
        "    }",
        "    private fun bayramDisplayName(prefix: String): String {",
        "        val day = name.dropWhile { !it.isDigit() }.takeWhile(Char::isDigit).toIntOrNull()",
        "        return if (day == null) prefix else \"$prefix ($day. gün)\"",
        "    }",
        "}",
        "",
        "object DiyanetReligiousDays {",
        "    const val FIRST_YEAR = 2025",
        "    const val LAST_YEAR = 2035",
        "",
        "    val entries: List<DiyanetReligiousDay> = listOf(",
    ]
    for year, source_id in SOURCE_IDS.items():
        url = BASE_URL.format(source_id)
        lines.append(f"        // {year} source: {url}")
        for event in parse_source(year, source_id):
            dates = event["dates"]
            primary = dates[0]
            alternates = dates[1:]
            lines.extend([
                "        DiyanetReligiousDay(",
                f"            type = DiyanetReligiousDayType.{event['type']},",
                f"            name = {kotlin_string(str(event['name']))},",
                f"            date = LocalDate.parse({kotlin_string(str(primary))}),",
                "            alternateDates = listOf(" + ", ".join(f"LocalDate.parse({kotlin_string(str(value))})" for value in alternates) + "),",
                f"            sourceGregorianDateLabel = {kotlin_string(str(event['gregorian']))},",
                f"            sourceHijriDateLabel = {kotlin_string(str(event['hijri']))},",
                f"            isNight = {str(bool(event['is_night'])).lower()},",
                "        ),",
            ])
    lines.extend([
        "    ).sortedBy(DiyanetReligiousDay::date)",
        "",
        "    fun forYear(year: Int): List<DiyanetReligiousDay> =",
        "        entries.filter { event -> event.dates.any { it.year == year } }",
        "",
        "    fun on(date: LocalDate): List<DiyanetReligiousDay> =",
        "        entries.filter { date in it.dates }",
        "",
        "    fun inMonth(year: Int, month: Int): List<DiyanetReligiousDay> =",
        "        entries.filter { event -> event.dates.any { it.year == year && it.monthValue == month } }",
        "}",
        "",
    ])
    return "\n".join(lines)


if __name__ == "__main__":
    try:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(generate(), encoding="utf-8")
    except Exception as error:
        print(f"Generation failed: {error}", file=sys.stderr)
        raise
    print(f"Generated {OUTPUT}")
