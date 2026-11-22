from datetime import datetime, timedelta, timezone

import pytest

from wait_for_rate_window import wait_for_rate_window


class FakeClock:
    def __init__(self, current):
        self.current = current
        self.sleeps = []

    def now(self):
        return self.current

    def sleep(self, seconds):
        self.sleeps.append(seconds)
        self.current += timedelta(seconds=seconds)


def test_previous_job_delay_already_elapsed_does_not_sleep():
    current = datetime(2026, 11, 22, 12, 0, tzinfo=timezone.utc)
    clock = FakeClock(current)

    waited = wait_for_rate_window(
        minutes=65,
        previous_finished_at="2026-11-22T10:00:00Z",
        now=clock.now,
        sleep=clock.sleep,
    )

    assert waited == 0
    assert clock.sleeps == []


def test_only_remaining_time_is_waited_after_previous_job():
    current = datetime(2026, 11, 22, 12, 0, tzinfo=timezone.utc)
    clock = FakeClock(current)

    waited = wait_for_rate_window(
        minutes=65,
        previous_finished_at="2026-11-22T11:00:00Z",
        now=clock.now,
        sleep=clock.sleep,
    )

    assert waited == 300
    assert sum(clock.sleeps) == 300


def test_manual_retry_can_wait_from_job_start():
    clock = FakeClock(datetime(2026, 11, 22, 12, 0, tzinfo=timezone.utc))

    waited = wait_for_rate_window(
        minutes=65,
        from_now=True,
        now=clock.now,
        sleep=clock.sleep,
    )

    assert waited == 65 * 60
    assert clock.current == datetime(2026, 11, 22, 13, 5, tzinfo=timezone.utc)


def test_wait_requires_a_previous_job_or_manual_start():
    with pytest.raises(ValueError, match="provide previous_finished_at"):
        wait_for_rate_window(minutes=65, now=lambda: datetime.now(timezone.utc))
