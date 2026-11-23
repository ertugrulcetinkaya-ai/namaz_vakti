import hashlib
import json
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

import pytest

from build_mirror import (
    CalendarInfo,
    DownloadedCalendar,
    InvalidCalendarError,
    RateLimitExceeded,
    REPOSITORY_URL,
    RequestPacer,
    build_chunk_report,
    build_manifest,
    chunk_district_ids,
    fetch_calendar,
    district_ids_from_locations,
    merge_chunks,
    publish_calendars,
    update_published_chunk,
    validate_calendar,
)


ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "tools/diyanet_snapshot/vakitler"
TODAY = date(2026, 11, 16)
GENERATED_AT = datetime(2026, 11, 22, 1, 2, 3, tzinfo=timezone.utc)


def fixture(district_id="9206"):
    return (FIXTURES / f"{district_id}.json").read_bytes()


def shift_calendar(raw, days):
    rows = json.loads(raw)
    for row in rows:
        row_date = datetime.strptime(row["MiladiTarihKisa"], "%d.%m.%Y").date()
        row["MiladiTarihKisa"] = (row_date + timedelta(days=days)).strftime("%d.%m.%Y")
    return json.dumps(rows, ensure_ascii=False).encode("utf-8")


def test_snapshot_fixture_matches_the_calendar_contract():
    info = validate_calendar(fixture(), TODAY)

    assert info.first_date == TODAY
    assert info.last_date == date(2026, 12, 31)
    assert len(info.dates) == 46
    assert all(next_day.toordinal() == current.toordinal() + 1
               for current, next_day in zip(info.dates, info.dates[1:]))


@pytest.mark.parametrize("district_id", ["9206", "9541", "9560"])
def test_all_checked_in_calendar_examples_validate(district_id):
    assert validate_calendar(fixture(district_id), TODAY).first_date == TODAY


def test_calendar_may_start_yesterday_when_it_contains_today():
    info = validate_calendar(shift_calendar(fixture(), -1), TODAY)

    assert info.first_date == TODAY - timedelta(days=1)
    assert TODAY in info.dates
    assert info.last_date == date(2026, 12, 30)


def test_calendar_starting_two_days_ago_is_rejected():
    with pytest.raises(InvalidCalendarError, match="start today or yesterday"):
        validate_calendar(shift_calendar(fixture(), -2), TODAY)


def test_calendar_that_starts_yesterday_but_omits_today_is_rejected():
    row = json.loads(shift_calendar(fixture(), -1))[0]

    with pytest.raises(InvalidCalendarError, match="contain today"):
        validate_calendar(json.dumps([row], ensure_ascii=False), TODAY)


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("MiladiTarihKisa", "31.11.2026"),
        ("MiladiTarihKisa", "2026-11-16"),
        ("Imsak", "24:00"),
        ("Gunes", "7:01"),
        ("HicriTarihKisa", "0 Ramazan 1448"),
        ("HicriTarihKisa", "7 Rabi II 1448"),
    ],
)
def test_invalid_fields_are_rejected(field, value):
    rows = json.loads(fixture())
    rows[0][field] = value

    with pytest.raises(InvalidCalendarError):
        validate_calendar(json.dumps(rows, ensure_ascii=False), TODAY)


def test_missing_today_gap_and_duplicate_rows_are_rejected():
    rows = json.loads(fixture())

    with pytest.raises(InvalidCalendarError, match="start today"):
        validate_calendar(json.dumps(rows[1:], ensure_ascii=False), TODAY)

    rows = json.loads(fixture())
    rows.pop(1)
    with pytest.raises(InvalidCalendarError, match="consecutive"):
        validate_calendar(json.dumps(rows, ensure_ascii=False), TODAY)

    rows = json.loads(fixture())
    rows.append(rows[0])
    with pytest.raises(InvalidCalendarError, match="duplicate"):
        validate_calendar(json.dumps(rows, ensure_ascii=False), TODAY)


def test_chunking_is_stable_and_never_exceeds_97_districts():
    ids = [str(value) for value in range(869)]
    chunks = chunk_district_ids(ids)

    assert len(chunks) == 9
    assert [len(chunk) for chunk in chunks] == [97] * 5 + [96] * 4
    assert [district_id for chunk in chunks for district_id in chunk] == ids


def test_district_ids_come_from_location_generator_data():
    ids = district_ids_from_locations()

    assert len(ids) == 869
    assert len(ids) == len(set(ids))
    assert ids[:3] == ["9146", "9147", "9148"]


def test_manifest_uses_exact_response_bytes_and_relative_paths():
    raw = fixture()
    info = validate_calendar(raw, TODAY)
    calendar = DownloadedCalendar("9206", raw, info)

    manifest = build_manifest([calendar], generated_at=GENERATED_AT)

    assert manifest == {
        "schema": 1,
        "generatedAt": "2026-11-22T01:02:03Z",
        "source": "namazvakti.mtopal.dev",
        "files": {
            "9206": {
                "path": "2026/9206.json",
                "sha256": hashlib.sha256(raw).hexdigest(),
                "firstDate": "2026-11-16",
                "lastDate": "2026-12-31",
            }
        },
    }


def test_manifest_records_the_actual_yesterday_start_date():
    raw = shift_calendar(fixture(), -1)
    info = validate_calendar(raw, TODAY)

    manifest = build_manifest(
        [DownloadedCalendar("9206", raw, info)],
        generated_at=GENERATED_AT,
    )

    assert manifest["files"]["9206"]["firstDate"] == "2026-11-15"


def test_publish_writes_raw_payload_and_manifest(tmp_path):
    raw = fixture()
    info = validate_calendar(raw, TODAY)
    manifest = publish_calendars(
        [DownloadedCalendar("9206", raw, info)],
        ["9206"],
        tmp_path,
        generated_at=GENERATED_AT,
    )

    assert (tmp_path / "v1/2026/9206.json").read_bytes() == raw
    assert json.loads((tmp_path / "v1/manifest.json").read_text()) == manifest
    assert set(path.relative_to(tmp_path).as_posix() for path in tmp_path.rglob("*")) == {
        "v1", "v1/2026", "v1/2026/9206.json", "v1/manifest.json"
    }


def test_invalid_ratio_above_two_percent_leaves_previous_publication_untouched(tmp_path):
    old_manifest = b'{"schema": 1, "files": {}}\n'
    output = tmp_path / "v1/manifest.json"
    output.parent.mkdir(parents=True)
    output.write_bytes(old_manifest)
    raw = fixture()
    info = validate_calendar(raw, TODAY)
    second_raw = fixture("9541")
    second_info = validate_calendar(second_raw, TODAY)

    with pytest.raises(ValueError, match="Refusing to publish: 1/3"):
        publish_calendars(
            [DownloadedCalendar("9206", raw, info), DownloadedCalendar("9541", second_raw, second_info)],
            ["9206", "9541", "9560"],
            tmp_path,
            generated_at=GENERATED_AT,
        )

    assert output.read_bytes() == old_manifest


def test_manual_chunk_update_preserves_the_other_published_districts(tmp_path):
    ids = ["9206", "9541", "9560"]
    old_calendars = [
        DownloadedCalendar(district_id, fixture(district_id), validate_calendar(fixture(district_id), TODAY))
        for district_id in ids
    ]
    existing = tmp_path / "existing"
    publish_calendars(old_calendars, ids, existing, generated_at=GENERATED_AT)

    chunk_dir = tmp_path / "chunk-1"
    chunk_dir.mkdir()
    selected = old_calendars[0]
    (chunk_dir / "2026").mkdir()
    (chunk_dir / "2026/9206.json").write_bytes(selected.raw_body)
    report = build_chunk_report(1, ["9206"], [selected], {})
    (chunk_dir / "chunk-1-report.json").write_text(json.dumps(report), encoding="utf-8")
    output = tmp_path / "updated"

    manifest = update_published_chunk(
        chunk_dir,
        1,
        ids,
        existing / "v1",
        output,
        TODAY,
        generated_at=datetime(2026, 11, 23, 1, 2, 3, tzinfo=timezone.utc),
    )

    assert set(manifest["files"]) == set(ids)
    for district_id in ids:
        assert (output / "v1/2026" / f"{district_id}.json").read_bytes() == fixture(district_id)


def test_chunk_artifacts_merge_into_one_manifest(tmp_path):
    ids = ["9206", "9541", "9560"]
    chunks = chunk_district_ids(ids)
    chunk_dirs = []

    for chunk_index, chunk_ids in enumerate(chunks, start=1):
        chunk_dir = tmp_path / f"chunk-{chunk_index}"
        chunk_dir.mkdir()
        calendars = []
        for district_id in chunk_ids:
            raw = fixture(district_id)
            if district_id == "9206":
                raw = shift_calendar(raw, -1)
            info = validate_calendar(raw, TODAY)
            calendar = DownloadedCalendar(district_id, raw, info)
            calendars.append(calendar)
            file_dir = chunk_dir / "2026"
            file_dir.mkdir(exist_ok=True)
            (file_dir / f"{district_id}.json").write_bytes(raw)
        report = build_chunk_report(chunk_index, chunk_ids, calendars, {})
        (chunk_dir / f"chunk-{chunk_index}-report.json").write_text(
            json.dumps(report), encoding="utf-8"
        )
        chunk_dirs.append(chunk_dir)

    output = tmp_path / "publication"
    manifest = merge_chunks(
        chunk_dirs,
        ids,
        output,
        TODAY,
        generated_at=GENERATED_AT,
    )

    assert set(manifest["files"]) == set(ids)
    assert manifest["files"]["9206"]["firstDate"] == "2026-11-15"
    assert all((output / "v1/2026" / f"{district_id}.json").is_file() for district_id in ids)


def test_partial_merge_without_reports_still_obeys_the_two_percent_limit(tmp_path):
    ids = [str(value) for value in range(869)]
    chunks = chunk_district_ids(ids)
    chunk_dirs = [tmp_path / f"chunk-{index}" for index in range(1, 10)]
    chunk_dirs[0].mkdir()
    report = build_chunk_report(1, chunks[0], [], {})
    (chunk_dirs[0] / "chunk-1-report.json").write_text(json.dumps(report), encoding="utf-8")

    with pytest.raises(ValueError, match="Refusing to publish: 869/869"):
        merge_chunks(
            chunk_dirs,
            ids,
            tmp_path / "publication",
            TODAY,
            generated_at=GENERATED_AT,
            allow_missing_chunks=True,
        )

    assert not (tmp_path / "publication/v1").exists()



def test_five_hundred_errors_retry_three_times_with_exponential_backoff():
    raw = fixture()
    responses = [FakeResponse(500), FakeResponse(500), FakeResponse(500), FakeResponse(200, raw)]
    clock = FakeClock()

    result = fetch_calendar(
        "9206",
        TODAY,
        opener=lambda *_args, **_kwargs: responses.pop(0),
        sleep=clock.sleep,
        monotonic=clock.monotonic,
        log=lambda _message: None,
    )

    assert result.raw_body == raw
    assert clock.sleeps == [7, 14, 28]


def test_shared_pacer_spaces_district_requests_and_user_agent_links_repository():
    raw = fixture()
    clock = FakeClock()
    requests = []
    pacer = RequestPacer(sleep=clock.sleep, monotonic=clock.monotonic)

    def opener(request, **_kwargs):
        requests.append(request)
        return FakeResponse(200, raw)

    fetch_calendar("9206", TODAY, opener=opener, pacer=pacer, log=lambda _message: None)
    fetch_calendar("9541", TODAY, opener=opener, pacer=pacer, log=lambda _message: None)

    assert clock.sleeps == [7]
    assert len(requests) == 2
    assert REPOSITORY_URL in requests[0].get_header("User-agent")


def test_http_429_obeys_retry_after_and_stops_after_two_retries():
    raw = fixture()
    responses = [FakeResponse(429, headers={"Retry-After": "3"}), FakeResponse(200, raw)]
    clock = FakeClock()

    result = fetch_calendar(
        "9206",
        TODAY,
        opener=lambda *_args, **_kwargs: responses.pop(0),
        sleep=clock.sleep,
        monotonic=clock.monotonic,
        log=lambda _message: None,
    )

    assert result.raw_body == raw
    assert clock.sleeps == [7]

    responses = [FakeResponse(429), FakeResponse(429), FakeResponse(429)]
    clock = FakeClock()
    with pytest.raises(RateLimitExceeded):
        fetch_calendar(
            "9206",
            TODAY,
            opener=lambda *_args, **_kwargs: responses.pop(0),
            sleep=clock.sleep,
            monotonic=clock.monotonic,
            log=lambda _message: None,
        )
    assert clock.sleeps == [900, 900]

    responses = [FakeResponse(429, headers={"Retry-After": "1801"})]
    clock = FakeClock()
    with pytest.raises(RateLimitExceeded, match="Retry-After exceeds"):
        fetch_calendar(
            "9206",
            TODAY,
            opener=lambda *_args, **_kwargs: responses.pop(0),
            sleep=clock.sleep,
            monotonic=clock.monotonic,
            log=lambda _message: None,
        )
    assert clock.sleeps == []


class FakeResponse:
    def __init__(self, status, body=b"", headers=None):
        self.status = status
        self.body = body
        self.headers = headers or {}

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self):
        return self.body


class FakeClock:
    def __init__(self):
        self.now = 0.0
        self.sleeps = []

    def sleep(self, seconds):
        self.sleeps.append(seconds)
        self.now += seconds

    def monotonic(self):
        return self.now
