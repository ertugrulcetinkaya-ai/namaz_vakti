import os
from pathlib import Path
import re
import subprocess
import sys

import pytest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/diyanet-mirror.yml"


def selection_script() -> str:
    source = WORKFLOW.read_text(encoding="utf-8")
    step_start = source.index("        name: Validate chunk selection\n")
    run_start = source.index("        run: |\n", step_start) + len("        run: |\n")
    step_end = source.index("\n  chunk_1:\n", run_start)
    run_body = source[run_start:step_end]
    shell = "\n".join(line[10:] if line.startswith("          ") else line for line in run_body.splitlines())
    match = re.search(r"python3 - <<'PY'\n(.*?)\nPY\s*$", shell, flags=re.DOTALL)
    assert match, "expected the chunk selection validator in the workflow"
    return match.group(1)


def run_selection(tmp_path: Path, *, only_chunk: str, reuse_run_id: str = "") -> subprocess.CompletedProcess:
    output_path = tmp_path / "github-output"
    env = os.environ.copy()
    env.update(
        {
            "ONLY_CHUNK": only_chunk,
            "REUSE_RUN_ID": reuse_run_id,
            "GITHUB_OUTPUT": str(output_path),
        }
    )
    result = subprocess.run(
        [sys.executable, "-c", selection_script()],
        cwd=ROOT,
        env=env,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode == 0:
        result.output_values = output_path.read_text(encoding="utf-8")
    return result


def test_chunk_selection_accepts_and_orders_comma_separated_values(tmp_path):
    result = run_selection(tmp_path, only_chunk="3,1,2", reuse_run_id="45071177141")

    assert result.returncode == 0, result.stderr
    assert result.output_values.splitlines() == [
        "selected_chunks=1,2,3",
        "reuse_run_id=45071177141",
    ]


@pytest.mark.parametrize("only_chunk", ["1,1", "0", "10", "all,1", "1, 2"])
def test_chunk_selection_rejects_invalid_values(tmp_path, only_chunk):
    result = run_selection(tmp_path, only_chunk=only_chunk)

    assert result.returncode != 0
    assert "only_chunk" in result.stderr


def test_workflow_downloads_prior_run_artifacts_only_for_unselected_chunks():
    source = WORKFLOW.read_text(encoding="utf-8")

    assert "  actions: read\n" in source
    assert source.count("run-id: ${{ needs.validate.outputs.reuse_run_id }}") == 9
    assert source.count("github-token: ${{ secrets.GITHUB_TOKEN }}") == 9
    for chunk_index in range(1, 10):
        assert (
            f"!contains(format(',{{0}},', needs.validate.outputs.selected_chunks), ',{chunk_index},')"
            in source
        )
    assert "--allow-missing-chunks" in source
