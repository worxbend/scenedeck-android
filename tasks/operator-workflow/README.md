# SceneDeck operator workflow

Status: **active**. Authorized 2026-11-05: implement the feature recommendations,
excluding **Show rundown**. This is the resumption entry point for this work.
W1–W9 are implemented and verified, including Presets, foreground keyboard/compatible
foot-pedal routing and the isolated offline demo. Resume at **W10 — tablet operator layout**.
W10–W11 remain required; the durable goal is active. Use [shortcuts-design.md](shortcuts-design.md)
and [demo-design.md](demo-design.md) for the delivered contracts and verification evidence.
Keep the originating-workspace, consent and unknown-result contracts in
[presets-design.md](presets-design.md) and [history-design.md](history-design.md) when
adding shortcut entry points. Show rundown, cue lists and timed advancement remain excluded.

## Resume

1. Read `AGENTS.md`, the five product/architecture/stack/design/roadmap documents,
   and the applicable `.kimi/skills/` instructions.
2. Read [plan.md](plan.md), [progress.md](progress.md), and the current Git diff.
3. Continue the first incomplete checkpoint. Preserve unrelated changes and the
   completed historical review in `tasks/plan.md` and `tasks/todo.md`.
4. Implement and verify one bounded slice at a time. Update progress with exact
   files, commands, outcomes, limitations, and the next action before a handoff.
5. Update product docs and AGENTS when behavior ships. Planned items are not
   implemented behavior. Do not check off a feature based only on its foundation.

The durable conversation goal tracks the whole implementation. This directory
persists the work independently of conversation context. Work is local to branch
`feat/operator-workflow`; publishing, release tags, and store uploads are not part
of this workflow. No additional plan approval is required by the user's instruction.

## Scope

- Workspace isolation and named decks.
- Preflight checks.
- Actionable live alerts.
- Recording controls, bookmarks, and media progress/seeking.
- Controlled multi-action presets.
- Local session history and export.
- Local scene aliases.
- Preview freshness labels.
- Keyboard and compatible foot-pedal bindings.
- Offline demo mode.
- Tablet operator layout.

**Excluded:** Show rundown, cue lists, timed show scheduling, automatic next-cue
advancement, and rundown UI in the tablet layout. Presets are individually invoked
actions, not a substitute rundown. Existing exclusions (including Replay Buffer,
scene-collection editing, scripting, and streaming-service integrations) remain.

## Completion

Every included feature needs a working user flow, meaningful failure-path coverage,
updated docs, and the required build/test/render evidence. Full delivery requires
`scripts/check-quality.sh` using JDK 21 and uv, `./gradlew assembleDebug`, and
`./gradlew testDebugUnitTest`. Changed screens must render in the required themes.
Real-OBS/device verification and measured performance must be reported separately
from fake-server, JVM, and screenshot evidence.
