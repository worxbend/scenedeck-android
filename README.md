# SceneDeck for Android 🎛️

> A beautiful, modern **OBS Studio remote control deck** for Android — inspired by the
> Linux desktop app [SceneDeck](../scenedeck), re-imagined as a tactile, award-winning
> mobile experience built with the latest Kotlin + Jetpack Compose stack.

**Stream from your PC. Control it from your phone.**

SceneDeck Android connects to OBS Studio's built-in WebSocket server (v5, OBS ≥ 28) and
turns your phone or tablet into a live production surface: tap scene cards to switch,
ride audio faders with live meters, start/stop stream & recording, and watch real-time
stream health — all wrapped in a gorgeous Material 3 Expressive UI with full theming.

## Status

📋 **Planning phase** — this repository currently contains the complete product plan,
technical design, and development roadmap. See the documents below.

## Documentation

| Document | Contents |
|---|---|
| [docs/FEATURE_SPEC.md](docs/FEATURE_SPEC.md) | Full functional specification (derived from the desktop SceneDeck codebase) + mobile-first improvements |
| [docs/TECH_STACK.md](docs/TECH_STACK.md) | Researched, versioned technology stack (Kotlin 2.4, Compose BOM 2026.09, M3 Expressive, …) |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Module map, layer rules, OBS WebSocket client design, state management |
| [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) | Visual identity: themes, color, typography, iconography, motion, assets |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Phased development roadmap (M0 → M8) with milestones and acceptance criteria |

## Agent skills

This repo ships project-level skills for AI coding agents in [`.kimi/skills/`](.kimi/skills/):

- **`android-compose`** — modern Android + Jetpack Compose development rules (Kotlin 2.4, BOM 2026.09, KSP, UDF, lifecycle).
- **`material3-expressive`** — Material 3 Expressive theming, motion, typography, iconography and award-level UI craft.
- **`obs-websocket-v5`** — the obs-websocket v5 protocol, auth handshake, requests/events, volume meters, and the `ktobs` client.

## The product in one paragraph

Like the desktop original, SceneDeck Android is a **control surface, not a streaming
tool**. It never modifies your OBS scene collection — all curation (scene roles, colors,
icons, order) lives locally on the device. The MVP surface: connect over WebSocket →
deck of tappable scene cards → mixer strips with live volume meters → stream/record
controls with safety confirmations → live stats dashboard. Beyond parity, the mobile
edition targets studio mode, transitions, screenshots/preview, and multiple saved
connection profiles — features the desktop app deliberately leaves unimplemented.
