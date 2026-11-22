from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/diyanet-mirror-prune.yml"


def prune_script() -> str:
    source = WORKFLOW.read_text(encoding="utf-8")
    step_start = source.index("      - name: Prune the previous calendar year\n")
    step_end = source.index("      - name: Commit cleanup to the data branch\n", step_start)
    step = source[step_start:step_end]
    run_body = step.split("        run: |\n", maxsplit=1)[1]
    shell = "\n".join(line[10:] if line.startswith("          ") else line for line in run_body.splitlines())
    match = re.search(r"python3 - <<'PY'\n(.*?)\nPY\s*$", shell, flags=re.DOTALL)
    assert match, "expected an inline Python prune script in the workflow"
    return match.group(1)


def make_manifest(v1: Path, current_year_count: int) -> tuple[dict, int]:
    current_year = datetime.now(timezone.utc).year
    previous_year = current_year - 1
    files = {}
    previous_count = 869 - current_year_count
    previous_dir = v1 / str(previous_year)
    previous_dir.mkdir(parents=True, exist_ok=True)

    for index in range(1, 870):
        district_id = str(index)
        if index <= current_year_count:
            year = current_year
        else:
            year = previous_year
            (previous_dir / f"{district_id}.json").write_text("{}", encoding="utf-8")
        files[district_id] = {
            "path": f"{year}/{district_id}.json",
            "sha256": "0" * 64,
            "firstDate": f"{year}-01-01",
            "lastDate": f"{year}-12-31",
        }

    manifest = {
        "schema": 1,
        "generatedAt": "2026-01-02T00:00:00Z",
        "source": "namazvakti.mtopal.dev",
        "files": files,
    }
    v1.mkdir(parents=True, exist_ok=True)
    (v1 / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    return manifest, previous_count


def run_prune(tmp_path: Path) -> subprocess.CompletedProcess:
    env = os.environ.copy()
    env["GITHUB_ACTIONS"] = "true"
    return subprocess.run(
        [sys.executable, "-c", prune_script()],
        cwd=tmp_path,
        env=env,
        check=False,
        capture_output=True,
        text=True,
    )


def test_prune_removes_previous_year_directory_and_manifest_entries(tmp_path):
    v1 = tmp_path / "v1"
    manifest, previous_count = make_manifest(v1, current_year_count=852)
    previous_year = datetime.now(timezone.utc).year - 1

    result = run_prune(tmp_path)

    assert result.returncode == 0, result.stderr
    updated = json.loads((v1 / "manifest.json").read_text(encoding="utf-8"))
    assert len(updated["files"]) == 852
    assert all(entry["path"].startswith(f"{previous_year + 1}/") for entry in updated["files"].values())
    assert not (v1 / str(previous_year)).exists()
    assert f"Pruned {previous_count} manifest entries" in result.stdout


def test_prune_warns_and_leaves_publication_untouched_below_98_percent(tmp_path):
    v1 = tmp_path / "v1"
    manifest, _ = make_manifest(v1, current_year_count=851)
    original_manifest = (v1 / "manifest.json").read_bytes()
    previous_year = datetime.now(timezone.utc).year - 1
    previous_file = v1 / str(previous_year) / "852.json"

    result = run_prune(tmp_path)

    assert result.returncode != 0
    assert "::warning::" in result.stdout
    assert "851/869" in result.stdout
    assert (v1 / "manifest.json").read_bytes() == original_manifest
    assert previous_file.is_file()
    assert json.loads(original_manifest)["files"] == manifest["files"]
