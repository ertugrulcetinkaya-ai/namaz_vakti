#!/usr/bin/env python3
"""Wait until a minimum delay has elapsed after the previous job completed."""

from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import time
from typing import Callable


def wait_for_rate_window(
    *,
    minutes: int,
    previous_finished_at: str | None = None,
    from_now: bool = False,
    now: Callable[[], datetime] = lambda: datetime.now(timezone.utc),
    sleep: Callable[[float], None] = time.sleep,
) -> float:
    if minutes < 1:
        raise ValueError("minutes must be positive")
    if previous_finished_at:
        previous = datetime.fromisoformat(previous_finished_at.replace("Z", "+00:00"))
        if previous.tzinfo is None:
            raise ValueError("previous_finished_at must include a timezone")
        target = previous.astimezone(timezone.utc) + timedelta(minutes=minutes)
    elif from_now:
        target = now().astimezone(timezone.utc) + timedelta(minutes=minutes)
    else:
        raise ValueError("provide previous_finished_at or set from_now")

    waited = 0.0
    while True:
        remaining = (target - now().astimezone(timezone.utc)).total_seconds()
        if remaining <= 0:
            return waited
        delay = min(60.0, remaining)
        sleep(delay)
        waited += delay


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minutes", type=int, default=65)
    parser.add_argument("--previous-completion")
    parser.add_argument("--from-now", action="store_true")
    args = parser.parse_args()
    waited = wait_for_rate_window(
        minutes=args.minutes,
        previous_finished_at=args.previous_completion,
        from_now=args.from_now,
    )
    print(f"Rate-limit wait complete; slept {waited:.0f}s.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
