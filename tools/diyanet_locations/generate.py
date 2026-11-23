#!/usr/bin/env python3
"""Generate the Diyanet district table from checked-in source data."""

import csv
import json
from pathlib import Path
import unicodedata


ROOT = Path(__file__).resolve().parents[2]
SNAPSHOT = ROOT / "tools/diyanet_snapshot"
COORDINATES = Path(__file__).with_name("district_coordinates.csv")
OVERRIDES = Path(__file__).with_name("overrides.csv")
UNMATCHED = Path(__file__).with_name("unmatched.txt")
OUTPUT = ROOT / "app/src/main/java/com/example/namazvakti/domain/model/DiyanetDistricts.kt"


def normalize(value):
    """Fold Turkish dotted/dotless I and diacritics for name matching."""
    value = unicodedata.normalize("NFKD", value.casefold().replace("ı", "i"))
    return " ".join(
        "".join(char for char in value if not unicodedata.category(char).startswith("M")).split()
    )


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def quote(value):
    return json.dumps(value, ensure_ascii=False)


def load_coordinate_rows(path):
    by_name = {}
    by_pcode = {}
    with path.open(encoding="utf-8", newline="") as source:
        for row in csv.DictReader(source):
            row["latitude"] = float(row["lat"])
            row["longitude"] = float(row["lon"])
            key = (normalize(row["adm1_tr"]), normalize(row["adm2_tr"]))
            by_name.setdefault(key, []).append(row)
            if row["adm2_pcode"] in by_pcode:
                raise ValueError(f"Duplicate ADM2 p-code: {row['adm2_pcode']}")
            by_pcode[row["adm2_pcode"]] = row
    return by_name, by_pcode


def load_overrides(path):
    overrides = {}
    if not path.exists():
        return overrides
    with path.open(encoding="utf-8", newline="") as source:
        for row in csv.DictReader(source):
            key = (normalize(row["diyanet_province"]), normalize(row["diyanet_district"]))
            if key in overrides:
                raise ValueError(f"Duplicate Diyanet override: {row}")
            overrides[key] = row["adm2_pcode"]
    return overrides


def build_district_table(
    snapshot_dir=SNAPSHOT,
    coordinates_path=COORDINATES,
    overrides_path=OVERRIDES,
):
    """Return provinces, enriched Diyanet districts, and unmatched districts."""
    provinces = load(snapshot_dir / "sehirler_2.json")
    coordinate_rows, coordinates_by_pcode = load_coordinate_rows(coordinates_path)
    overrides = load_overrides(overrides_path)
    used_overrides = set()
    output_provinces = []
    output_districts = []
    unmatched = []

    for province in provinces:
        province_id = province["SehirID"]
        province_name = province["SehirAdi"]
        entries = load(snapshot_dir / "ilceler" / f"{province_id}.json")
        central = [entry for entry in entries if normalize(entry["IlceAdi"]) == normalize(province_name)]
        if len(central) != 1:
            raise ValueError(f"{province_name}: expected one central district, got {len(central)}")
        central_id = central[0]["IlceID"]
        output_provinces.append((province_id, province_name, central_id))

        for district in entries:
            district_id = district["IlceID"]
            district_name = district["IlceAdi"]
            latitude = longitude = None

            # The province's central district keeps the existing province-center coordinates.
            if district_id != central_id:
                key = (normalize(province_name), normalize(district_name))
                override_pcode = overrides.get(key)
                if override_pcode is not None:
                    used_overrides.add(key)
                    coordinate = coordinates_by_pcode.get(override_pcode)
                    if coordinate is None:
                        raise ValueError(f"Unknown ADM2 p-code in override: {override_pcode}")
                    if normalize(coordinate["adm1_tr"]) != normalize(province_name):
                        raise ValueError(f"Override province does not match {province_name}: {override_pcode}")
                    candidates = [coordinate]
                else:
                    candidates = coordinate_rows.get(key, [])

                if len(candidates) == 1:
                    latitude = candidates[0]["latitude"]
                    longitude = candidates[0]["longitude"]
                else:
                    unmatched.append((province_name, district_name, district_id))

            output_districts.append(
                (province_id, province_name, district_id, district_name, latitude, longitude)
            )

    unused_overrides = set(overrides) - used_overrides
    if unused_overrides:
        raise ValueError(f"Overrides did not match a noncentral Diyanet district: {sorted(unused_overrides)}")
    return output_provinces, output_districts, unmatched


def write_unmatched(unmatched, noncentral_count):
    matched_count = noncentral_count - len(unmatched)
    rate = 100 * matched_count / noncentral_count if noncentral_count else 100.0
    lines = [
        f"Noncentral districts matched: {matched_count}/{noncentral_count} ({rate:.1f}%)",
        "Unmatched districts (province | district | Diyanet ID):",
    ]
    lines.extend(f"{province} | {district} | {district_id}" for province, district, district_id in unmatched)
    UNMATCHED.write_text("\n".join(lines) + "\n", encoding="utf-8")


def generate(output=OUTPUT):
    provinces, districts, unmatched = build_district_table()
    if len(provinces) != 81 or len(districts) != 869:
        raise ValueError(f"Unexpected Diyanet table sizes: {len(provinces)} provinces, {len(districts)} districts")

    lines = [
        "package com.example.namazvakti.domain.model",
        "",
        "// Generated by tools/diyanet_locations/generate.py; do not edit.",
        "object DiyanetDistricts {",
        "    data class District(",
        "        val provinceId: String, val provinceName: String, val id: String, val name: String,",
        "        val latitude: Double?, val longitude: Double?",
        "    )",
        "    data class Province(val id: String, val name: String, val centralDistrictId: String)",
        "",
        "    val provinces = listOf(",
    ]
    lines.extend(
        f"        Province({quote(province_id)}, {quote(name)}, {quote(central_id)}),"
        for province_id, name, central_id in provinces
    )
    lines += ["    )", "", "    val districts = listOf("]
    for province_id, province_name, district_id, district_name, latitude, longitude in districts:
        lat_literal = "null" if latitude is None else f"{latitude:.5f}"
        lon_literal = "null" if longitude is None else f"{longitude:.5f}"
        lines.append(
            "        District("
            f"{quote(province_id)}, {quote(province_name)}, {quote(district_id)}, {quote(district_name)}, "
            f"{lat_literal}, {lon_literal}),"
        )
    lines += [
        "    )",
        "",
        "    val byId: Map<String, District> by lazy { districts.associateBy { it.id } }",
        "    val byProvinceId: Map<String, List<District>> by lazy { districts.groupBy { it.provinceId } }",
        "",
        "    fun forCity(city: String, country: String): Province? {",
        "        val option = PrayerLocationConfig.cityOptions.firstOrNull { it.city == city && it.country == country }",
        "            ?: return null",
        "        return provinces.firstOrNull { it.name == option.displayCity }",
        "    }",
        "}",
        "",
    ]
    output.write_text("\n".join(lines), encoding="utf-8")

    noncentral_count = len(districts) - len(provinces)
    write_unmatched(unmatched, noncentral_count)
    return len(districts) - len(unmatched) - len(provinces), noncentral_count, unmatched


if __name__ == "__main__":
    matched, noncentral, unmatched = generate()
    print(f"Matched {matched}/{noncentral} noncentral districts; {len(unmatched)} unmatched.")
