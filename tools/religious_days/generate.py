#!/usr/bin/env python3
"""Fetch Diyanet's annual religious-day tables and generate the Kotlin data file."""

from __future__ import annotations

from html.parser import HTMLParser
import re
import sys
from datetime import date, timedelta
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
HIJRI_MONTHS = {
    "MUHARREM": 1, "MUHARRAM": 1,
    "SAFER": 2,
    "REBIÜLEVVEL": 3, "REBİÜLEVVEL": 3, "R.EVVEL": 3, "R.VVEL": 3,
    "REBIÜLAHIR": 4, "REBİÜLAHİR": 4, "R.AHIR": 4, "R.AHİR": 4,
    "CEMAZİYELEVVEL": 5, "CEMAZIYELEVVEL": 5, "C.EVVEL": 5,
    "CEMAZİYELAHİR": 6, "CEMAZIYELAHIR": 6, "C.AHİR": 6,
    "RECEB": 7, "RECEP": 7,
    "ŞABAN": 8, "SABAN": 8,
    "RAMAZAN": 9,
    "ŞEVVAL": 10, "SEVVAL": 10,
    "ZİLKADE": 11, "ZILKADE": 11,
    "ZİLHİCCE": 12, "ZILHICCE": 12,
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


def parse_label_days(value: str) -> list[int]:
    compact = re.sub(r"(?<=\d)\s+(?=\d)", "", value)
    pieces = compact.split("/")
    if not pieces or any(not piece.isdigit() for piece in pieces):
        raise ValueError(f"Could not parse day label: {value!r}")
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
    if "AREFE" in upper:
        return "AREFE"
    if "KANDİL" in upper or "KANDIL" in upper or "GECESİ" in upper or "GECESI" in upper:
        return "KANDIL"
    if "BAYRAM" in upper:
        return "BAYRAM"
    return "SPECIAL_DAY"


def parse_hijri_pair(day_label: str, month_label: str, year_label: str) -> tuple[tuple[int, int, int], ...]:
    days = parse_label_days(day_label)
    month_names = [part.strip() for part in month_label.split("/")]
    months = []
    for month in month_names:
        key = month.upper()
        if key not in HIJRI_MONTHS:
            raise ValueError(f"Could not parse Hijri month: {month!r}")
        months.append(HIJRI_MONTHS[key])
    if len(months) == 1 and len(days) == 2:
        months *= 2
    if len(days) != len(months):
        raise ValueError(f"Slash Hijri day/month pair does not match: {day_label!r} {month_label!r}")
    year_match = re.search(r"\d{4}", year_label)
    if not year_match:
        raise ValueError(f"Could not parse Hijri year: {year_label!r}")
    year = int(year_match.group())
    return tuple((day, month, year) for day, month in zip(days, months))


def validate_slash_pair(
    gregorian_label: str,
    gregorian_dates: list[date],
    hijri_label: str,
    is_night: bool,
) -> None:
    gregorian_slash = "/" in gregorian_label
    hijri_day, hijri_month, hijri_year = hijri_label.split()
    hijri_pair = parse_hijri_pair(hijri_day, hijri_month, hijri_year)
    hijri_slash = "/" in hijri_day or "/" in hijri_month
    if not (gregorian_slash or hijri_slash):
        return
    if not gregorian_slash or not hijri_slash:
        raise ValueError(f"Slash date must appear in both official labels: {gregorian_label!r} / {hijri_label!r}")
    if len(gregorian_dates) != 2 or gregorian_dates[1] != gregorian_dates[0] + timedelta(days=1):
        raise ValueError(f"Slash Gregorian dates are not consecutive: {gregorian_label!r}")
    if len(hijri_pair) != 2:
        raise ValueError(f"Slash Hijri dates must contain exactly two days: {hijri_label!r}")
    (day1, month1, year1), (day2, month2, year2) = hijri_pair
    same_month_next_day = year1 == year2 and month1 == month2 and day1 in range(1, 30) and day2 == day1 + 1
    next_month_first_day = (
        day2 == 1 and month2 == (month1 % 12) + 1 and
        year2 == year1 + (1 if month1 == 12 else 0) and day1 in {29, 30}
    )
    if not (same_month_next_day or next_month_first_day):
        raise ValueError(f"Slash Hijri dates are not consecutive: {hijri_label!r}")
    if not is_night:
        raise ValueError(f"Slash religious-day record must be an evening observance: {gregorian_label!r}")


def display_name(name: str, date_value: date, all_events: list[dict[str, object]]) -> str:
    upper = name.upper()
    aliases = {
        "REGAİB KANDİLİ": "Regaip Kandili", "REGAİP KANDİLİ": "Regaip Kandili",
        "MİRAC KANDİLİ": "Miraç Kandili", "MİRAÇ KANDİLİ": "Miraç Kandili",
        "MEVLİD KANDİLİ": "Mevlid Kandili", "MEVLİT KANDİLİ": "Mevlid Kandili",
        "BERAT KANDİLİ": "Berat Kandili", "KADİR GECESİ": "Kadir Gecesi",
        "ÜÇ AYLARIN BAŞLANGICI": "Üç Ayların Başlangıcı",
        "RAMAZAN BAŞLANGICI": "Ramazan Başlangıcı",
        "HİCRİ YILBAŞI": "Hicri Yılbaşı", "HİCRÎ YILBAŞI": "Hicri Yılbaşı",
        "AŞURE GÜNÜ": "Aşure Günü",
    }
    if upper in aliases:
        return aliases[upper]

    if upper in {"RAMAZAN BAY. AREFESİ", "RAMAZAN BAYRAMI AREFESİ"}:
        return "Ramazan Bayramı Arefesi"
    if upper in {"KURBAN BAY. AREFESİ", "KURBAN BAYRAMI AREFESİ"}:
        return "Kurban Bayramı Arefesi"
    if upper == "AREFE":
        following = [event for event in all_events if event["date"] == date_value + timedelta(days=1)]
        if any(str(event["name"]).upper().startswith("RAMAZAN BAYRAMI") for event in following):
            return "Ramazan Bayramı Arefesi"
        if any(str(event["name"]).upper().startswith("KURBAN BAYRAMI") for event in following):
            return "Kurban Bayramı Arefesi"
        raise ValueError(f"Could not determine which bayram follows AREFE on {date_value}")

    bayram = re.fullmatch(r"(RAMAZAN|KURBAN) BAYRAMI(?: \((\d)\. GÜN\)| (\d)\. GÜN)", upper)
    if bayram:
        prefix = "Ramazan" if bayram.group(1) == "RAMAZAN" else "Kurban"
        day = bayram.group(2) or bayram.group(3)
        return f"{prefix} Bayramı ({day}. gün)"
    raise ValueError(f"No visible-name mapping for official Diyanet name {name!r} on {date_value}")


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
        gregorian_days = parse_label_days(gregorian_day)
        months, gregorian_year = parse_month_year(gregorian_month_year)
        date_values = []
        for index, day in enumerate(gregorian_days):
            month = months[min(index, len(months) - 1)]
            date_values.append(date(gregorian_year, month, day))
        hijri_label = " ".join((hijri_day, hijri_month, hijri_year))
        source_gregorian_label = " ".join((gregorian_day, gregorian_month_year))
        is_night = type_for(name) == "KANDIL"
        validate_slash_pair(source_gregorian_label, date_values, hijri_label, is_night)
        events.append({
            "date": date_values[0],
            "name": name,
            "type": type_for(name),
            "hijri": hijri_label,
            "gregorian": source_gregorian_label,
            "is_night": is_night,
        })
    if not events:
        raise ValueError(f"No religious-day rows parsed for {year} from {url}")
    for event in events:
        event["display_name"] = display_name(
            str(event["name"]), event["date"], events
        )
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
        "    /** First date in Diyanet's label; slash pairs describe one evening observance. */",
        "    val date: LocalDate,",
        "    /** Consistent Turkish UI label generated from the source name and its context. */",
        "    val displayName: String,",
        "    val sourceGregorianDateLabel: String,",
        "    val sourceHijriDateLabel: String?,",
        "    val isNight: Boolean",
        ")",
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
            primary = event["date"].isoformat()
            lines.extend([
                "        DiyanetReligiousDay(",
                f"            type = DiyanetReligiousDayType.{event['type']},",
                f"            name = {kotlin_string(str(event['name']))},",
                f"            date = LocalDate.parse({kotlin_string(str(primary))}),",
                f"            displayName = {kotlin_string(str(event['display_name']))},",
                f"            sourceGregorianDateLabel = {kotlin_string(str(event['gregorian']))},",
                f"            sourceHijriDateLabel = {kotlin_string(str(event['hijri']))},",
                f"            isNight = {str(bool(event['is_night'])).lower()},",
                "        ),",
            ])
    lines.extend([
        "    ).sortedBy(DiyanetReligiousDay::date)",
        "",
        "    fun forYear(year: Int): List<DiyanetReligiousDay> =",
        "        entries.filter { it.date.year == year }",
        "",
        "    fun on(date: LocalDate): List<DiyanetReligiousDay> =",
        "        entries.filter { it.date == date }",
        "",
        "    fun inMonth(year: Int, month: Int): List<DiyanetReligiousDay> =",
        "        entries.filter { it.date.year == year && it.date.monthValue == month }",
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
