from generate import build_district_table, normalize


def test_normalize_handles_turkish_i_and_circumflex():
    assert normalize("İlçe") == normalize("ILCE") == normalize("ılçe") == "ilce"
    assert normalize("KÂĞITHANE") == normalize("Kağıthane") == "kagithane"


def test_central_districts_keep_null_coordinates():
    provinces, districts, unmatched = build_district_table()
    central_ids = {(province[0], province[2]) for province in provinces}
    central = [entry for entry in districts if (entry[0], entry[2]) in central_ids]

    assert len(central) == 81
    assert all(entry[4:] == (None, None) for entry in central)
    assert unmatched == []


def test_known_noncentral_districts_have_plausible_coordinates():
    _, districts, _ = build_district_table()
    expected = {
        ("İSTANBUL", "KARTAL"): (40.7, 41.1, 28.9, 29.5),
        ("İSTANBUL", "MALTEPE"): (40.7, 41.2, 28.9, 29.5),
        ("ANTALYA", "ALANYA"): (36.2, 37.0, 31.7, 32.5),
    }

    for (province_name, district_name), (min_lat, max_lat, min_lon, max_lon) in expected.items():
        district = next(
            entry for entry in districts
            if entry[1] == province_name and entry[3] == district_name
        )
        latitude, longitude = district[4:]
        assert latitude is not None and min_lat <= latitude <= max_lat
        assert longitude is not None and min_lon <= longitude <= max_lon
