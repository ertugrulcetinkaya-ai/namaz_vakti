# Diyanet district locations

`generate.py` combines the checked-in Diyanet snapshot with `district_coordinates.csv` to regenerate `DiyanetDistricts.kt`. The coordinate input has only ADM1 name, ADM2 name, ADM2 p-code, latitude, and longitude. The raw boundary archive is not stored in this repository.

## Boundary source

- Dataset: [Türkiye - Subnational Administrative Boundaries (COD-AB)](https://data.humdata.org/dataset/cod-ab-tur), published by OCHA/HDX from data sourced from the General Command of Mapping (Harita Genel Komutanlığı).
- Dataset version: `01`; the HDX record says 973 ADM2 districts and was reviewed for accuracy on 16 January 2025. The Shapefile resource used here was last modified on 26 January 2026.
- License shown by the HDX dataset record: [CC BY-IGO](https://data.humdata.org/dataset/cod-ab-tur).
- Download URL: <https://data.humdata.org/dataset/d74086a0-f398-4474-9e12-1b9a70907bd0/resource/d2315bc0-033e-4912-aab1-06afe3495694/download/tur_admin_boundaries.shp.zip>
- SHA-256 of the downloaded archive: `c2191753bd8b9de74231b341e22234020d083943ff6b3fbd84bec4437876c894`
- Retrieved on 22 November 2026.

The source Shapefile uses geographic WGS 84 coordinates. `extract_coordinates.py` projects ADM2 polygons to a WGS 84 Lambert azimuthal equal-area projection centered on Türkiye, calculates each polygon centroid, and uses Shapely's `representative_point` when the centroid is outside the polygon. It converts the point back to latitude/longitude and writes five decimal places. `NOTICE.md` contains the attribution.

## Regenerate

Download and verify the source archive, then create the compact CSV and Kotlin table:

```bash
python3 -m pip install -r tools/diyanet_locations/requirements.txt
curl --fail --location \
  --output /tmp/tur_admin_boundaries.shp.zip \
  'https://data.humdata.org/dataset/d74086a0-f398-4474-9e12-1b9a70907bd0/resource/d2315bc0-033e-4912-aab1-06afe3495694/download/tur_admin_boundaries.shp.zip'
echo 'c2191753bd8b9de74231b341e22234020d083943ff6b3fbd84bec4437876c894  /tmp/tur_admin_boundaries.shp.zip' | shasum -a 256 -c
python3 tools/diyanet_locations/extract_coordinates.py /tmp/tur_admin_boundaries.shp.zip
python3 tools/diyanet_locations/generate.py
```

The generator normalizes Turkish dotted/dotless `I` and diacritics, joins by province and district name, and uses `overrides.csv` for 22 documented Diyanet naming variants. It never assigns coordinates to a province's central district. `unmatched.txt` reports any noncentral district without one unambiguous ADM2 match. Current coverage is 788/788 noncentral districts (100.0%): 766 normalized name matches and 22 overrides.

Run generator tests with:

```bash
python3 -m pytest tools/diyanet_locations/test_generate.py
```
