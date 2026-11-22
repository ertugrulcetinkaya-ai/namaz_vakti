#!/usr/bin/env python3
"""Download and validate Diyanet prayer calendars for the repository mirror."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import date, datetime, timezone
from email.utils import parsedate_to_datetime
import hashlib
import json
import math
import re
import shutil
import socket
import sys
import tempfile
import time
from pathlib import Path
from typing import Callable, Iterable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo


ROOT = Path(__file__).resolve().parents[2]
SOURCE = "namazvakti.mtopal.dev"
API_BASE_URL = "https://namazvakti.mtopal.dev/vakitler/"
REPOSITORY_URL = "https://github.com/ertugrulcetinkaya-ai/namaz_vakti"
USER_AGENT = f"NamazVakti Diyanet mirror (+{REPOSITORY_URL})"
REQUEST_TIMEOUT_SECONDS = 20
REQUEST_INTERVAL_SECONDS = 7
RETRY_COUNT = 3
HTTP_429_RETRY_COUNT = 2
HTTP_429_FALLBACK_DELAY_SECONDS = 15 * 60
HTTP_429_MAX_RETRY_AFTER_SECONDS = 30 * 60
MAX_INVALID_PERCENT = 2
HIJRI_MONTHS = (
    "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel",
    "Cemaziyelahir", "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce",
)
TIME_PATTERN = re.compile(r"^[0-9]{2}:[0-9]{2}$")
DATE_PATTERN = re.compile(r"^[0-9]{2}\.[0-9]{2}\.[0-9]{4}$")
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
DISTRICT_ID_PATTERN = re.compile(r"^[0-9]+$")


class InvalidCalendarError(ValueError):
    """The payload does not meet the Diyanet calendar contract."""


class RateLimitExceeded(RuntimeError):
    """The upstream server kept returning HTTP 429 after the allowed retries."""


@dataclass(frozen=True)
class CalendarInfo:
    first_date: date
    last_date: date
    dates: tuple[date, ...]


@dataclass(frozen=True)
class DownloadedCalendar:
    district_id: str
    raw_body: bytes
    info: CalendarInfo


class RequestPacer:
    """Keep request starts at least seven seconds apart, including across districts."""

    def __init__(
        self,
        *,
        sleep: Callable[[float], None] = time.sleep,
        monotonic: Callable[[], float] = time.monotonic,
    ) -> None:
        self.sleep = sleep
        self.monotonic = monotonic
        self.last_request_started: float | None = None

    def before_request(self, minimum_gap_seconds: float) -> None:
        if self.last_request_started is not None:
            elapsed = self.monotonic() - self.last_request_started
            remaining = minimum_gap_seconds - elapsed
            if remaining > 0:
                self.sleep(remaining)
        self.last_request_started = self.monotonic()

    def wait_after_response(self, delay_seconds: float) -> None:
        self.sleep(delay_seconds)
        # The requested delay has elapsed; the next request must not be paced twice.
        self.last_request_started = None


def district_ids_from_locations() -> list[str]:
    """Read IDs from the generated location table's source data, never Kotlin."""
    if str(ROOT) not in sys.path:
        sys.path.insert(0, str(ROOT))
    from tools.diyanet_locations.generate import build_district_table

    _, districts, _ = build_district_table()
    ids = [str(row[2]) for row in districts]
    if len(ids) != 869 or len(ids) != len(set(ids)):
        raise ValueError(f"Expected 869 unique Diyanet district IDs, got {len(ids)}")
    return ids


def chunk_district_ids(ids: list[str], chunk_count: int = 9) -> list[list[str]]:
    """Split IDs in stable source order into near-equal chunks."""
    if chunk_count < 1:
        raise ValueError("chunk_count must be positive")
    quotient, remainder = divmod(len(ids), chunk_count)
    chunks: list[list[str]] = []
    offset = 0
    for index in range(chunk_count):
        size = quotient + (1 if index < remainder else 0)
        chunks.append(ids[offset : offset + size])
        offset += size
    return chunks


def _parse_row(row: object) -> date:
    if not isinstance(row, dict):
        raise InvalidCalendarError("row is not an object")

    date_text = row.get("MiladiTarihKisa")
    if not isinstance(date_text, str) or not DATE_PATTERN.fullmatch(date_text):
        raise InvalidCalendarError("MiladiTarihKisa must use dd.MM.yyyy")
    try:
        row_date = datetime.strptime(date_text, "%d.%m.%Y").date()
    except ValueError as error:
        raise InvalidCalendarError(f"invalid MiladiTarihKisa: {date_text}") from error

    for name in ("Imsak", "Gunes", "Ogle", "Ikindi", "Aksam", "Yatsi"):
        value = row.get(name)
        if not isinstance(value, str) or not TIME_PATTERN.fullmatch(value):
            raise InvalidCalendarError(f"{name} must use HH:mm")
        try:
            datetime.strptime(value, "%H:%M")
        except ValueError as error:
            raise InvalidCalendarError(f"invalid {name}: {value}") from error

    hijri_text = row.get("HicriTarihKisa")
    if not isinstance(hijri_text, str):
        raise InvalidCalendarError("HicriTarihKisa must be a string")
    hijri_fields = hijri_text.split(" ")
    if len(hijri_fields) != 3:
        raise InvalidCalendarError("HicriTarihKisa must contain day, Turkish month, and year")
    day_text, month_text, year_text = hijri_fields
    try:
        hijri_day = int(day_text)
        hijri_year = int(year_text)
    except ValueError as error:
        raise InvalidCalendarError("HicriTarihKisa day and year must be integers") from error
    if (
        not 1 <= hijri_day <= 30
        or month_text not in HIJRI_MONTHS
        or not 1 <= hijri_year <= 9999
    ):
        raise InvalidCalendarError("HicriTarihKisa is outside the accepted range")
    return row_date


def validate_calendar(raw_body: bytes | str, today: date) -> CalendarInfo:
    """Validate strictly using DiyanetPrayerTimesApi's field and sequence rules."""
    try:
        payload = json.loads(raw_body.decode("utf-8") if isinstance(raw_body, bytes) else raw_body)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise InvalidCalendarError("response is not UTF-8 JSON") from error
    if not isinstance(payload, list) or not payload:
        raise InvalidCalendarError("response must be a non-empty JSON array")

    dates: list[date] = []
    for row in payload:
        dates.append(_parse_row(row))

    ordered_dates = sorted(set(dates))
    if len(ordered_dates) != len(payload):
        raise InvalidCalendarError("response contains invalid or duplicate rows")
    if not ordered_dates or ordered_dates[0] != today:
        raise InvalidCalendarError(f"response must start today ({today})")
    if any(next_day != previous_day.fromordinal(previous_day.toordinal() + 1)
           for previous_day, next_day in zip(ordered_dates, ordered_dates[1:])):
        raise InvalidCalendarError("response dates must be consecutive")
    return CalendarInfo(ordered_dates[0], ordered_dates[-1], tuple(ordered_dates))


def _is_timeout(error: BaseException) -> bool:
    if isinstance(error, (TimeoutError, socket.timeout)):
        return True
    return isinstance(error, URLError) and isinstance(error.reason, (TimeoutError, socket.timeout))


def _retry_after_seconds(header: str | None, now: datetime | None = None) -> float:
    if not header:
        return float(HTTP_429_FALLBACK_DELAY_SECONDS)
    try:
        seconds = float(header.strip())
        return max(0.0, seconds) if math.isfinite(seconds) else float(HTTP_429_FALLBACK_DELAY_SECONDS)
    except ValueError:
        try:
            retry_at = parsedate_to_datetime(header)
            if retry_at.tzinfo is None:
                retry_at = retry_at.replace(tzinfo=timezone.utc)
            current = now or datetime.now(timezone.utc)
            return max(0.0, (retry_at - current).total_seconds())
        except (TypeError, ValueError, OverflowError):
            return float(HTTP_429_FALLBACK_DELAY_SECONDS)


def fetch_calendar(
    district_id: str,
    today: date,
    *,
    opener: Callable[..., object] = urlopen,
    sleep: Callable[[float], None] = time.sleep,
    monotonic: Callable[[], float] = time.monotonic,
    pacer: RequestPacer | None = None,
    log: Callable[[str], None] = print,
) -> DownloadedCalendar:
    request = Request(
        f"{API_BASE_URL}{district_id}",
        headers={"User-Agent": USER_AGENT, "Accept": "application/json", "Accept-Encoding": "identity"},
        method="GET",
    )
    request_pacer = pacer or RequestPacer(sleep=sleep, monotonic=monotonic)
    retry_index = 0
    rate_limit_retries = 0

    while True:
        delay = max(REQUEST_INTERVAL_SECONDS, REQUEST_INTERVAL_SECONDS * (2 ** max(0, retry_index - 1)))
        request_pacer.before_request(delay)

        try:
            response = opener(request, timeout=REQUEST_TIMEOUT_SECONDS)
            with response:
                status = getattr(response, "status", 200)
                if status == 429:
                    retry_after = _retry_after_seconds(response.headers.get("Retry-After"))
                    if retry_after > HTTP_429_MAX_RETRY_AFTER_SECONDS:
                        error = RateLimitExceeded(
                            f"HTTP 429 Retry-After exceeds {HTTP_429_MAX_RETRY_AFTER_SECONDS}s"
                        )
                        error.retry_after = retry_after  # type: ignore[attr-defined]
                        raise error
                    error = RateLimitExceeded("HTTP 429")
                    error.retry_after = retry_after  # type: ignore[attr-defined]
                    raise error
                if status >= 500:
                    raise HTTPError(request.full_url, status, "upstream server error", response.headers, None)
                if not 200 <= status < 300:
                    raise HTTPError(request.full_url, status, "upstream request failed", response.headers, None)
                raw_body = response.read()
            info = validate_calendar(raw_body, today)
            return DownloadedCalendar(district_id, raw_body, info)
        except HTTPError as error:
            if error.code == 429:
                if rate_limit_retries >= HTTP_429_RETRY_COUNT:
                    raise RateLimitExceeded(f"HTTP 429 after {HTTP_429_RETRY_COUNT} retries") from error
                rate_limit_retries += 1
                retry_after = _retry_after_seconds(error.headers.get("Retry-After") if error.headers else None)
                if retry_after > HTTP_429_MAX_RETRY_AFTER_SECONDS:
                    raise RateLimitExceeded(
                        f"HTTP 429 Retry-After exceeds {HTTP_429_MAX_RETRY_AFTER_SECONDS}s"
                    ) from error
                log(f"{district_id}: HTTP 429; retry {rate_limit_retries}/{HTTP_429_RETRY_COUNT} after {retry_after:g}s")
                request_pacer.wait_after_response(max(REQUEST_INTERVAL_SECONDS, retry_after))
                continue
            if error.code < 500 or retry_index >= RETRY_COUNT:
                raise
            retry_index += 1
            log(f"{district_id}: HTTP {error.code}; retry {retry_index}/{RETRY_COUNT}")
            request_pacer.wait_after_response(
                max(REQUEST_INTERVAL_SECONDS, REQUEST_INTERVAL_SECONDS * (2 ** (retry_index - 1)))
            )
            continue
        except RateLimitExceeded as error:
            if rate_limit_retries >= HTTP_429_RETRY_COUNT:
                raise
            rate_limit_retries += 1
            retry_after = getattr(error, "retry_after", float(HTTP_429_FALLBACK_DELAY_SECONDS))
            if retry_after > HTTP_429_MAX_RETRY_AFTER_SECONDS:
                raise RateLimitExceeded(
                    f"HTTP 429 Retry-After exceeds {HTTP_429_MAX_RETRY_AFTER_SECONDS}s"
                ) from error
            log(f"{district_id}: HTTP 429; retry {rate_limit_retries}/{HTTP_429_RETRY_COUNT} after {retry_after:g}s")
            request_pacer.wait_after_response(max(REQUEST_INTERVAL_SECONDS, retry_after))
        except (TimeoutError, socket.timeout, URLError) as error:
            if not _is_timeout(error) or retry_index >= RETRY_COUNT:
                raise
            retry_index += 1
            log(f"{district_id}: timeout; retry {retry_index}/{RETRY_COUNT}")
            request_pacer.wait_after_response(
                max(REQUEST_INTERVAL_SECONDS, REQUEST_INTERVAL_SECONDS * (2 ** (retry_index - 1)))
            )


def build_manifest(
    calendars: Iterable[DownloadedCalendar],
    *,
    generated_at: datetime,
) -> dict[str, object]:
    entries: dict[str, dict[str, str]] = {}
    for calendar in calendars:
        entries[calendar.district_id] = {
            "path": f"{calendar.info.first_date.year}/{calendar.district_id}.json",
            "sha256": hashlib.sha256(calendar.raw_body).hexdigest(),
            "firstDate": calendar.info.first_date.isoformat(),
            "lastDate": calendar.info.last_date.isoformat(),
        }
    return {
        "schema": 1,
        "generatedAt": generated_at.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
        "source": SOURCE,
        "files": entries,
    }


def _atomic_publish(output_root: Path, files: dict[str, bytes], manifest: dict[str, object]) -> None:
    output_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="diyanet-mirror-") as temporary:
        stage_v1 = Path(temporary) / "v1"
        for relative_path, content in files.items():
            destination = stage_v1 / relative_path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(content)
        (stage_v1 / "manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

        destination_v1 = output_root / "v1"
        previous_v1 = output_root / ".v1-previous"
        if previous_v1.exists():
            shutil.rmtree(previous_v1)
        if destination_v1.exists():
            destination_v1.replace(previous_v1)
        try:
            stage_v1.replace(destination_v1)
        except Exception:
            if previous_v1.exists():
                previous_v1.replace(destination_v1)
            raise
        if previous_v1.exists():
            shutil.rmtree(previous_v1)


def publish_calendars(
    calendars: list[DownloadedCalendar],
    expected_ids: list[str],
    output_root: Path,
    *,
    generated_at: datetime,
) -> dict[str, object]:
    successful_ids = {calendar.district_id for calendar in calendars}
    invalid_count = len(set(expected_ids) - successful_ids)
    if invalid_count * 100 > len(expected_ids) * MAX_INVALID_PERCENT:
        raise ValueError(
            f"Refusing to publish: {invalid_count}/{len(expected_ids)} districts are missing or invalid "
            f"(limit {MAX_INVALID_PERCENT}%)"
        )

    files = {
        f"{calendar.info.first_date.year}/{calendar.district_id}.json": calendar.raw_body
        for calendar in calendars
    }
    manifest = build_manifest(calendars, generated_at=generated_at)
    _atomic_publish(output_root, files, manifest)
    return manifest


def _load_chunk_calendars(chunk_dir: Path, expected_chunk_ids: list[str], today: date) -> list[DownloadedCalendar]:
    calendars: list[DownloadedCalendar] = []
    for district_id in expected_chunk_ids:
        candidates = list(chunk_dir.glob(f"*/{district_id}.json"))
        if len(candidates) != 1:
            continue
        raw_body = candidates[0].read_bytes()
        try:
            info = validate_calendar(raw_body, today)
        except InvalidCalendarError:
            continue
        calendars.append(DownloadedCalendar(district_id, raw_body, info))
    return calendars


def build_chunk_report(
    chunk_index: int,
    chunk_ids: list[str],
    calendars: list[DownloadedCalendar],
    errors: dict[str, str],
) -> dict[str, object]:
    successful_ids = {calendar.district_id for calendar in calendars}
    return {
        "chunk": chunk_index,
        "expectedDistrictIds": chunk_ids,
        "successfulDistrictIds": [district_id for district_id in chunk_ids if district_id in successful_ids],
        "invalidDistricts": {
            district_id: errors.get(district_id, "missing or invalid response")
            for district_id in chunk_ids if district_id not in successful_ids
        },
    }


def _write_chunk(
    chunk_index: int,
    chunk_ids: list[str],
    output_dir: Path,
    today: date,
    *,
    fetcher: Callable[..., DownloadedCalendar] = fetch_calendar,
    log: Callable[[str], None] = print,
) -> list[DownloadedCalendar]:
    output_dir.mkdir(parents=True, exist_ok=True)
    calendars: list[DownloadedCalendar] = []
    errors: dict[str, str] = {}
    pacer = RequestPacer()
    for position, district_id in enumerate(chunk_ids, start=1):
        log(f"chunk {chunk_index}/9: {position}/{len(chunk_ids)} district {district_id}")
        try:
            calendar = fetcher(district_id, today, pacer=pacer)
            calendars.append(calendar)
            path = output_dir / str(calendar.info.first_date.year) / f"{district_id}.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(calendar.raw_body)
        except RateLimitExceeded:
            raise
        except Exception as error:
            errors[district_id] = f"{type(error).__name__}: {error}"
            log(f"{district_id}: skipped ({errors[district_id]})")

    report = build_chunk_report(chunk_index, chunk_ids, calendars, errors)
    (output_dir / f"chunk-{chunk_index}-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return calendars


def merge_chunks(
    chunk_dirs: list[Path],
    expected_ids: list[str],
    output_root: Path,
    today: date,
    *,
    generated_at: datetime,
) -> dict[str, object]:
    chunks = chunk_district_ids(expected_ids)
    if len(chunk_dirs) != len(chunks):
        raise ValueError(f"Expected {len(chunks)} chunk directories, got {len(chunk_dirs)}")
    calendars: list[DownloadedCalendar] = []
    for chunk_index, (chunk_dir, expected_chunk_ids) in enumerate(zip(chunk_dirs, chunks), start=1):
        report_path = chunk_dir / f"chunk-{chunk_index}-report.json"
        if not report_path.exists():
            raise ValueError(f"Missing chunk report: {report_path}")
        report = json.loads(report_path.read_text(encoding="utf-8"))
        if report.get("chunk") != chunk_index or report.get("expectedDistrictIds") != expected_chunk_ids:
            raise ValueError(f"Chunk {chunk_index} report does not match the fixed district order")
        calendars.extend(_load_chunk_calendars(chunk_dir, expected_chunk_ids, today))
    if len({calendar.district_id for calendar in calendars}) != len(calendars):
        raise ValueError("Chunk artifacts contain duplicate district IDs")
    return publish_calendars(calendars, expected_ids, output_root, generated_at=generated_at)


def update_published_chunk(
    chunk_dir: Path,
    chunk_index: int,
    expected_ids: list[str],
    existing_v1: Path,
    output_root: Path,
    today: date,
    *,
    generated_at: datetime,
) -> dict[str, object]:
    """Replace one chunk while retaining every other file from the current publication."""
    chunks = chunk_district_ids(expected_ids)
    if not 1 <= chunk_index <= len(chunks):
        raise ValueError(f"chunk_index must be between 1 and {len(chunks)}")
    report_path = chunk_dir / f"chunk-{chunk_index}-report.json"
    if not report_path.exists():
        raise ValueError(f"Missing chunk report: {report_path}")
    report = json.loads(report_path.read_text(encoding="utf-8"))
    expected_chunk_ids = chunks[chunk_index - 1]
    if report.get("chunk") != chunk_index or report.get("expectedDistrictIds") != expected_chunk_ids:
        raise ValueError("Chunk report does not match the fixed district order")

    manifest_path = existing_v1 / "manifest.json"
    if not manifest_path.is_file():
        raise ValueError(f"Existing mirror manifest is missing: {manifest_path}")
    old_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if old_manifest.get("schema") != 1 or old_manifest.get("source") != SOURCE:
        raise ValueError("Existing mirror manifest has an unsupported schema or source")
    old_entries = old_manifest.get("files")
    if not isinstance(old_entries, dict):
        raise ValueError("Existing mirror manifest files are invalid")

    calendars = _load_chunk_calendars(chunk_dir, expected_chunk_ids, today)
    new_entries = build_manifest(calendars, generated_at=generated_at)["files"]
    files: dict[str, bytes] = {}
    merged_entries: dict[str, dict[str, str]] = {}

    for district_id, entry in old_entries.items():
        if not isinstance(entry, dict):
            raise ValueError(f"Invalid existing manifest entry for {district_id}")
        relative_path = entry.get("path")
        digest = entry.get("sha256")
        if (
            not isinstance(district_id, str)
            or not DISTRICT_ID_PATTERN.fullmatch(district_id)
            or not isinstance(relative_path, str)
            or not re.fullmatch(r"[0-9]{4}/[0-9]+\.json", relative_path)
            or not isinstance(digest, str)
            or not SHA256_PATTERN.fullmatch(digest)
        ):
            raise ValueError(f"Invalid existing manifest entry for {district_id}")
        if relative_path.rsplit("/", 1)[-1] != f"{district_id}.json":
            raise ValueError(f"Existing mirror path does not match district ID {district_id}")
        source_path = existing_v1 / relative_path
        if not source_path.is_file():
            raise ValueError(f"Existing mirror file is missing: {relative_path}")
        raw_body = source_path.read_bytes()
        if hashlib.sha256(raw_body).hexdigest() != digest.lower():
            raise ValueError(f"Existing mirror file has a SHA-256 mismatch: {relative_path}")
        files[relative_path] = raw_body
        merged_entries[district_id] = {
            "path": relative_path,
            "sha256": digest.lower(),
            "firstDate": entry["firstDate"],
            "lastDate": entry["lastDate"],
        }

    # Keep extra year files and metadata already on the data branch, as requested for retries.
    for source_path in existing_v1.rglob("*"):
        if source_path.is_file() and source_path.name != "manifest.json":
            relative_path = source_path.relative_to(existing_v1).as_posix()
            if not relative_path.startswith("../"):
                files.setdefault(relative_path, source_path.read_bytes())

    for district_id, entry in new_entries.items():
        relative_path = entry["path"]
        files[relative_path] = next(calendar.raw_body for calendar in calendars if calendar.district_id == district_id)
        merged_entries[district_id] = entry

    successful_ids = {calendar.district_id for calendar in calendars}
    invalid_ids = set(expected_chunk_ids) - successful_ids
    missing_ids = set(expected_ids) - set(merged_entries)
    invalid_count = len(invalid_ids | missing_ids)
    if invalid_count * 100 > len(expected_ids) * MAX_INVALID_PERCENT:
        raise ValueError(
            f"Refusing to publish: {invalid_count}/{len(expected_ids)} districts are missing or invalid "
            f"(limit {MAX_INVALID_PERCENT}%)"
        )

    manifest = {
        "schema": 1,
        "generatedAt": generated_at.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
        "source": SOURCE,
        "files": merged_entries,
    }
    _atomic_publish(output_root, files, manifest)
    return manifest


def _iso_now() -> datetime:
    return datetime.now(timezone.utc).replace(microsecond=0)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "tools/diyanet_mirror/output")
    parser.add_argument("--chunk", type=int, choices=range(1, 10), help="Fetch one fixed chunk (1-9)")
    parser.add_argument("--limit", type=int, help="Only fetch the first N districts (for a local sample)")
    parser.add_argument("--merge-chunks", nargs=9, type=Path, metavar="DIR", help="Merge nine chunk artifacts and publish")
    parser.add_argument("--update-chunk", type=Path, help="Publish one previously fetched chunk")
    parser.add_argument("--chunk-index", type=int, choices=range(1, 10))
    parser.add_argument("--existing-v1", type=Path, help="Existing published v1 directory for --update-chunk")
    args = parser.parse_args(argv)

    today = datetime.now(ZoneInfo("Europe/Istanbul")).date()
    ids = district_ids_from_locations()
    if args.merge_chunks:
        merge_chunks(args.merge_chunks, ids, args.output, today, generated_at=_iso_now())
        print(f"Published mirror for {len(ids)} districts.")
        return 0

    if args.update_chunk:
        if args.chunk_index is None or args.existing_v1 is None:
            parser.error("--update-chunk requires --chunk-index and --existing-v1")
        update_published_chunk(
            args.update_chunk,
            args.chunk_index,
            ids,
            args.existing_v1,
            args.output,
            today,
            generated_at=_iso_now(),
        )
        print(f"Updated mirror chunk {args.chunk_index}.")
        return 0

    if args.limit is not None and args.limit < 1:
        parser.error("--limit must be positive")
    expected_ids = ids[: args.limit] if args.limit is not None else ids
    chunks = chunk_district_ids(expected_ids)
    if args.chunk is not None:
        if args.limit is not None:
            parser.error("--chunk and --limit cannot be combined")
        chunk_index = args.chunk - 1
        _write_chunk(args.chunk, chunks[chunk_index], args.output, today)
        return 0

    chunk_ids = expected_ids
    calendars: list[DownloadedCalendar] = []
    pacer = RequestPacer()
    for position, district_id in enumerate(chunk_ids, start=1):
        print(f"district {position}/{len(chunk_ids)}: {district_id}")
        try:
            calendars.append(fetch_calendar(district_id, today, pacer=pacer))
        except RateLimitExceeded:
            raise
        except Exception as error:
            print(f"{district_id}: skipped ({type(error).__name__}: {error})")
    manifest = publish_calendars(calendars, expected_ids, args.output, generated_at=_iso_now())
    print(f"Published {len(manifest['files'])}/{len(expected_ids)} district calendars.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
