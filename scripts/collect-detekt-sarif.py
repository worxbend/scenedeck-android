#!/usr/bin/env python3
"""Preserve module reports and give each SARIF run a distinct GitHub category."""

import json
import shutil
from pathlib import Path


def collect_reports(root: Path) -> int:
    reports = sorted(root.glob("**/build/reports/detekt/detekt.sarif"))
    destination = root / "build/sarif/detekt"
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    for report in reports:
        module = report.relative_to(root).parts[:-4]
        payload = json.loads(report.read_text())
        for index, run in enumerate(payload.get("runs", [])):
            run["automationDetails"] = {"id": f"detekt/{'/'.join(module)}/{index}/"}
        filename = "-".join(module) + ".sarif"
        (destination / filename).write_text(json.dumps(payload))
    return len(reports)


if __name__ == "__main__":
    count = collect_reports(Path(__file__).resolve().parent.parent)
    if not count:
        raise SystemExit("No Detekt reports found")
    print(f"Prepared {count} module reports with distinct SARIF categories")
