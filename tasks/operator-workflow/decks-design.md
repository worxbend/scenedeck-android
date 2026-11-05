# W1c — Named deck implementation notes

Status: planned, not implemented. W1b is verified. Continue the existing branch and
contracts; no additional approval is needed for this authorized slice.

## User behavior

- Preserve built-in All and Primary-only Deck. Named decks are additional selections
  within the current connection profile + OBS collection.
- A named deck has a local name and an ordered, unique set of scene references.
  Membership does not assign roles or alter canonical scene order in Inventory.
  Include any scene explicitly selected by the user, irrespective of diagnostic role.
- Add create, rename, delete, member selection/removal and explicit reorder. Disable
  reorder during search. Missing members stay recognizable/removable without becoming
  commands to a different workspace. OBS scenes are never renamed or edited.
- Persist selection per workspace. Deleting the selected named deck falls back to All.
  A reconnect restores that workspace's choice only after authoritative resolution.
- Keep the compact Scenes controls. Use a focused deck-management surface reachable
  from the deck selector/menu; preserve Scenes / Mixer / Stats and More.
- Provide deliberate deck backup/import with a destination preview. Keep existing
  registry YAML/JSON compatibility. A dedicated versioned deck document can preserve
  names, members and order without exporting device-specific profile IDs or credentials.

## Persistence and state

- Inspect existing Room 3 migration fixtures, then add deck, membership and scoped
  selection tables in a lossless migration. Save matching exported schema. No cascade
  from connection profiles; deleting a deck may cascade only its own local membership.
- Key all tables by WorkspaceKey. Deck IDs are local stable IDs; names should be
  trimmed, bounded and distinguishable within one workspace. Choose and document
  conflict handling before import implementation (do not silently overwrite ambiguously).
- Registry roles/order remain owned by RegistryRepository. Add a cohesive deck
  repository for transactional CRUD, membership/order and selection. Carry caller
  WorkspaceSession and a stable deck target through suspensions and dialogs.
- Use explicit loading/failed/ready data. Join deck membership only with a scene
  snapshot from the same current workspace. A named-deck selection must not change
  Graph/Doctor's canonical collection or the widget's existing Primary projection.
- Propagate scene renames into membership atomically within the originating namespace,
  preserving duplicate/conflict behavior and dense per-deck positions. Inspect the
  existing registry rename transaction before choosing the shared transaction boundary.

## Evidence to add

- Real Room migration and independent same-name decks across profiles/collections.
- CRUD, member order, duplicate names/members, stale scenes, rename conflicts, selected
  deck deletion, process restoration and export/import round trips.
- Context replacement during queued writes, import preview, selection and reorder.
- All/Primary behavior, search preserving deck numbering, no role assignment, widget
  projection staying unchanged, and accessible phone/light/dark/OBS renders.
- Run the required gates at the coherent slice boundary. Keep W1c/W1d unchecked until
  their actual behavior and verification are complete.
