# UI theming workflow

Status: **implemented and verified; publication in progress**. User authorized both additional palettes and accent/true-black controls,
then explicitly requested a workflow. Continue the first incomplete checkpoint in plan.md.
The completed operator workflow under `tasks/operator-workflow` is preserved.

## Scope and decisions

- Four original paired palettes: Ocean, Rosewood, Amber and Paper.
- Accent choices: palette default, Azure, Teal, Violet, Rose, Amber and Lime. Override
  primary/highlight roles, preserving program/preview/output/meter semantics.
- Optional true-black backgrounds in dark mode; retain layered readable control surfaces.
  Light mode is unchanged. Do not claim measured battery savings.
- A focused appearance editor with local preview, explicit Apply/Cancel, defaults reset,
  saved settings and visible sanitized failures. Failed saves retain the draft. Drafts
  survive rotation, but are never applied automatically after process interruption.
- Retain light/dark/system mode and wallpaper colors. Selecting a palette turns off
  wallpaper colors. Supported wallpaper schemes resolve before accents/true-black.
  High Contrast without wallpaper colors keeps its own accent/surface treatment.
- Appearance owns one atomic save of its five fields, comparing the opening values and
  preserving other settings. Remove appearance from the app shell's reverse preference
  writer; app state observes committed settings, so storage failure cannot imply success.
- No dependency additions, OBS actions, global curation writes, font downloads, arbitrary
  imported themes, unrequested external publication. The user subsequently authorized pushing main and
  cutting v0.2.1; see progress.md for release verification. Demo keeps its separate ephemeral settings.

Verification: JDK21/uv full quality gate, explicit normal build/tests, F-Droid build,
contrast assertions for new color combinations, persistence/restoration/failure tests,
phone/tablet/compact/large-text renders and representative runtime checks where available.
Keep planned work distinct from shipped behavior. No physical device/performance claims.

## Verified delivery

T1–T4 are complete: 1,169 tests / 203 suites, full JDK21/uv quality/security gate,
normal debug build/tests, F-Droid debug build and required renders pass. Emulator evidence
covers preview/Apply/Cancel, relaunch, rotation, both variants and demo isolation. The visible
emulator is connected to the user's local OBS and left for exploration. See progress.md.

User subsequently authorized all local commits to main and release tag v0.2.1 (code 1012).
Remote publication and release workflow verification are the remaining checkpoint.
