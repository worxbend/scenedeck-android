# Final independent security/OBS verification v4

Date: 2026-10-04. This resolves the scanner-coverage hold added to
SECURITY_OBS_VERIFICATION_v3.md. The completed gate results and module-count table
in v3 remain valid; no application or quality-script correction was required after
that snapshot. All approved security/OBS implementation and independent review
work is complete.

## Direct scanner coverage evidence

The pinned Semgrep 1.156.0 CLI's generic statement that a scan is limited to tracked
files was insufficient evidence to infer a production coverage gap. Root ran actual
target discovery and a JSON confirmation scan:

- `/tmp/scenedeck-hardening-semgrep-targets.txt` explicitly lists the newly created,
  unstaged `core/data/.../OptimisticControl.kt`.
- `/tmp/scenedeck-hardening-semgrep-coverage.json` records **203 scanned paths,
  zero errors and zero findings** and includes that exact helper path.
- Independent comparison against every current source artifact under `app/src`,
  `core/*/src` and `feature/*/src` outside `/test/` yielded **203 expected paths,
  zero missing paths and zero extra paths**.
- The source roots contain 333 files overall. The 130 omitted files are test
  sources/fixtures excluded by Semgrep's defaults. Production and variant sources,
  including the new helper, were already covered. Test code was reviewed, compiled
  and exercised by the regression suite; this report does not claim Semgrep scanned
  those 130 files.

The completed security scan therefore covers the intended existing production
scope. No staging, ignore-policy change or speculative quality-script fix was
needed. `/tmp/scenedeck-hardening-semgrep-coverage.log` preserves the scanner output.

## Final completed checks

| Check | Result and evidence |
|---|---|
| JDK21 + uv full quality script | Exit 0, `/tmp/scenedeck-hardening-quality-pass.log` |
| Formatting, Detekt, Android lint, tests and debug build | qualityCheck BUILD SUCCESSFUL in 14s |
| Production Semgrep rules | Seven rules, 203 verified production/variant source artifacts, zero findings/errors |
| History and working-tree secret scans | 30 commits and ~221.81 MB working tree; no leaks found |
| Explicit assembleDebug + testDebugUnitTest | Exit 0, BUILD SUCCESSFUL in 3s, `/tmp/scenedeck-hardening-build-unit.log` |
| Final source whitespace check | `git diff --check` passes |

JUnit XML totals **426 tests in 85 suites, zero failures, errors or skips**.
This includes **111 passing Roborazzi render tests in 22 suites**, with Connections
editor six, Onboarding three and StudioStylebook three. OBS has 48 passing tests
in nine suites and data has 135 in 21 suites. Critical new lifecycle, sanitation,
image bounds, Mixer ordering, registry batching and widget projection regressions
all pass. The explicit final command correctly reused up-to-date passing outputs
from the preceding quality run.

## Final acceptance and limits

The current OBS source and the independently reviewed data, widget, Live-message
and shared-document changes are accepted. The material observed concurrent UUID
leak is fixed: three eight-timeout rounds and three eight-cancellation rounds each
leave the actual upstream store empty and allow a healthy next request without
reset/reconnect. Session binding, total request deadlines, caller cancellation,
late replies and removal of raw remote diagnostic details remain passing.

The explicit shared dispatcher includes the whole Ktor-owned receiver and the
initial version request. Eight suspended requests remain in flight; the pinned
ktobs non-suspending critical-section assumption must be rechecked on upgrade.
Initial handshake and GetVersion each already have a separate ten-second deadline,
so sequential initial startup may take roughly twenty seconds. No ambiguous write
is automatically retried.

The screenshot compressed-byte and pixel guards do not impose a received-WebSocket
frame limit or a total scene-thumbnail-cache cap. Mixer pending storage scales with
live writes. These limits are documented, and no device-wide benchmark speedup or
universal security guarantee is claimed. Earlier reports retain the initial failing
evidence; this report is the current final acceptance record.
