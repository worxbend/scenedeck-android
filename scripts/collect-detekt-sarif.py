#!/usr/bin/env python3
"""Group module reports below GitHub's run limit, preserving distinct categories."""

import json
import shutil
from pathlib import Path


def collect_reports(root: Path) -> int:
    reports = sorted(root.glob("**/build/reports/detekt/detekt.sarif"))
    destination = root / "build/sarif/detekt"
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    run_counts = {}
    for report in reports:
        module = report.relative_to(root).parts[:-4]
        group = module[0]
        if group not in {"app", "core", "feature"}:
            raise ValueError(f"Configure a SARIF upload for module group {group}")
        payload = json.loads(report.read_text())
        run_counts[group] = run_counts.get(group, 0) + len(payload.get("runs", []))
        if run_counts[group] > 20:
            raise ValueError(f"Split the {group} SARIF upload: GitHub allows at most 20 runs")
        for index, run in enumerate(payload.get("runs", [])):
            run["automationDetails"] = {"id": f"detekt/{'/'.join(module)}/{index}/"}
        filename = "-".join(module) + ".sarif"
        group_directory = destination / group
        group_directory.mkdir(exist_ok=True)
        (group_directory / filename).write_text(json.dumps(payload))
    return len(reports)


if __name__ == "__main__":
    count = collect_reports(Path(__file__).resolve().parent.parent)
    if not count:
        raise SystemExit("No Detekt reports found")
    print(f"Prepared {count} module reports with distinct SARIF categories")
