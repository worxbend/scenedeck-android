# W2 — Local aliases and preview freshness

Status: **verified W2 checkpoint**; evidence is in `progress.md`. W1 completed in
`9e987ed`. This goal turn is **progress**: labels and preview freshness were delivered
with the full gate, explicit build/tests and renders. W3–W11 remain active.

## Local aliases

- Add optional `localAlias` to scoped registry metadata in lossless Room schema 5.
  OBS scene names remain every command target, key, membership reference and order key.
- Trim aliases, treat blank as removal, bound to 80 characters and reject controls.
  Aliases need not be unique: cards/Inventory/editors retain OBS identity and TalkBack
  includes both names. Search matches either without changing canonical order.
- Edit from scene quick edit and Inventory. Saving an alias must preserve an unchanged
  role, including unassigned/Archive/etc. Failed saves retain the input. Old workspace
  or renamed-scene editors must not recreate obsolete references.
- Registry YAML/JSON retains format version 1 with an optional alias field. Legacy
  documents still import; absent/null aliases preserve destination aliases under the
  existing merge semantics. Nonblank aliases replace them; explicit removal is an edit.
  Exports to an empty workspace round-trip all aliases. Rename/recovery/Undo retain it.
- Named decks continue storing raw OBS references; membership editors can show aliases
  alongside their raw identity. Diagnostic/transport addressing stays literal.

## Preview freshness

- Store each accepted frame with successful-capture monotonic time; track unsuccessful
  attempts independently. Failure keeps a prior image but never refreshes its age.
- Show capture age and a stale label (20-second age threshold, or latest attempt failed),
  plus explicit pending/unavailable/offline states. Hidden previews have no age badge.
  Clear old namespace frames on disconnect/replacement and never restore them under a
  new Ready token. Disconnected screens must not present cached images as live.
- Keep existing 2.5s hot / 10s cold intervals, two-job awaited batches and size limits.
  A screen-only age clock must not trigger captures. Accept valid dark/black images;
  discarding them would leave a misleading old frame after a successful capture.
- Add pure age-state tests, delayed/failed/black/reconnected capture regressions and
  render fresh/stale/unavailable/disconnected states in SceneDeck/OBS light/dark.

## Completion

W2a–c are checked after UI, migration/transfer/context tests and required
quality/build/test/render gates passed. Preserve W1/W2 evidence and keep W3–W11 in scope.
