#!/usr/bin/env python3
"""Create the compact ADM2 coordinate CSV from the downloaded HDX archive."""

import argparse
import csv
import io
from pathlib import Path
import unicodedata
import zipfile

import shapefile
from pyproj import CRS, Transformer
from shapely import transform
from shapely.geometry import shape as shape_from_geojson


DEFAULT_OUTPUT = Path(__file__).with_name("district_coordinates.csv")
LAYER = "tur_admin2"
EXPECTED_FEATURE_COUNT = 973
EQUAL_AREA_CRS = CRS.from_proj4(
    "+proj=laea +lat_0=39 +lon_0=35 +datum=WGS84 +units=m +no_defs +type=crs"
)


def nfc(value):
    return unicodedata.normalize("NFC", value.strip())


def extract_rows(archive_path):
    with zipfile.ZipFile(archive_path) as archive:
        files = {Path(name).name.lower(): name for name in archive.namelist()}
        required_files = {f"{LAYER}.{extension}" for extension in ("shp", "shx", "dbf", "prj")}
        missing = required_files - files.keys()
        if missing:
            raise ValueError(f"HDX archive is missing {sorted(missing)}")

        source_crs = CRS.from_wkt(archive.read(files[f"{LAYER}.prj"]).decode("utf-8"))
        if not source_crs.is_geographic:
            raise ValueError(f"Expected geographic source coordinates, got {source_crs.name}")
        to_equal_area = Transformer.from_crs(source_crs, EQUAL_AREA_CRS, always_xy=True)
        to_wgs84 = Transformer.from_crs(EQUAL_AREA_CRS, CRS.from_epsg(4326), always_xy=True)

        reader = shapefile.Reader(
            shp=io.BytesIO(archive.read(files[f"{LAYER}.shp"])),
            shx=io.BytesIO(archive.read(files[f"{LAYER}.shx"])),
            dbf=io.BytesIO(archive.read(files[f"{LAYER}.dbf"])),
            encoding="utf-8",
        )
        fields = [field[0] for field in reader.fields[1:]]
        rows = []
        for feature in reader.iterShapeRecords():
            attributes = dict(zip(fields, feature.record))
            geometry = shape_from_geojson(feature.shape.__geo_interface__)
            if geometry.is_empty:
                raise ValueError(f"Empty geometry for {attributes.get('adm2_pcode')}")
            projected = transform(geometry, to_equal_area.transform, interleaved=False)
            point = projected.centroid
            if not projected.covers(point):
                point = projected.representative_point()
            longitude, latitude = to_wgs84.transform(point.x, point.y)
            rows.append(
                {
                    "adm1_tr": nfc(attributes["adm1_name1"]),
                    "adm2_tr": nfc(attributes["adm2_name1"]),
                    "adm2_pcode": attributes["adm2_pcode"].strip(),
                    "lat": f"{latitude:.5f}",
                    "lon": f"{longitude:.5f}",
                }
            )

    if len(rows) != EXPECTED_FEATURE_COUNT:
        raise ValueError(f"Expected {EXPECTED_FEATURE_COUNT} ADM2 polygons, got {len(rows)}")
    if len({row["adm2_pcode"] for row in rows}) != len(rows):
        raise ValueError("ADM2 p-codes are not unique")
    return sorted(rows, key=lambda row: row["adm2_pcode"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("shapefile_zip", type=Path, help="Downloaded tur_admin_boundaries.shp.zip")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    rows = extract_rows(args.shapefile_zip)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(
            destination,
            fieldnames=("adm1_tr", "adm2_tr", "adm2_pcode", "lat", "lon"),
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} ADM2 centroids to {args.output}")


if __name__ == "__main__":
    main()
