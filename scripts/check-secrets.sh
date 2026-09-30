#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
scanner_dir=$(mktemp -d)
trap 'rm -rf "$scanner_dir"' EXIT
scanner_version=8.30.1
archive="gitleaks_${scanner_version}_linux_x64.tar.gz"
release_url="https://github.com/gitleaks/gitleaks/releases/download/v${scanner_version}"
curl --fail --location --silent --show-error --connect-timeout 15 --max-time 120 "$release_url/$archive" -o "$scanner_dir/$archive"
curl --fail --location --silent --show-error --connect-timeout 15 --max-time 120 "$release_url/gitleaks_${scanner_version}_checksums.txt" -o "$scanner_dir/checksums.txt"
(cd "$scanner_dir" && awk -v archive="$archive" '$2 == archive' checksums.txt | sha256sum --check -)
tar -xzf "$scanner_dir/$archive" -C "$scanner_dir"
mkdir -p build/reports/security
"$scanner_dir/gitleaks" git . --redact --report-format sarif --report-path build/reports/security/gitleaks-history.sarif
"$scanner_dir/gitleaks" dir . --redact --report-format sarif --report-path build/reports/security/gitleaks-working.sarif
