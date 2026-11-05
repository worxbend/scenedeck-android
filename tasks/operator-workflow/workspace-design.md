# W1b integration notes

These are integration constraints. Session identity and the registry/UI/widget/cache
activation are implemented and verified (see progress.md). Named decks remain the
next independent slice.

## Evidence

- `ObsSessionHolder` owns connection operations and supersedes old commands using a
  generation/job. `SettingsRepository.lastUsedProfileId` changes before connecting;
  it must not be combined with an old Ready state to identify the active host.
- Ready now carries client-local `sessionId`, `collectionRevision`, and
  `collectionChanging`; the production event/meter context streams stamp the token
  before buffering. The old untagged streams remain only for consumer migration.
- The protocol boundary now exposes both collection-changing and collection-changed.
  Their official protocol semantics and availability in pinned ktobs 0.5.0 were
  checked before implementation. New requests are rejected during changing; queued
  work is revision-bound and cannot cross into the new collection. Initial Ready
  preserves changes received during GetVersion. Keep wire types in core/obs.
- `ActiveConnection` is updated synchronously by Holder commands. `WorkspaceRepository`
  discovers the collection after the matching target is confirmed and offers
  `currentSession()` / `isCurrent(session)` guards for operation boundaries. Do not
  use `state.value as? Ready` alone as an action guard: flow delivery can lag identity.
- `ObsStateRepository` owns a scoped snapshot and combines it with matching registry
  metadata. A new registry cannot combine with the previous collection's scenes.
- Graph's initial event subscription race was reproduced and fixed while validating
  this foundation. Preserve subscription-before-initial-read when migrating Graph
  to the context stream (whose public type is Flow). Its deterministic startup-event
  regression must continue to pass; do not reintroduce an onStart gap.
- Screenshot frames carry workspace identity; replacing a workspace clears both
  frames and throttle state, including when replacement captures fail.
- `RegistryTransfer` captures origin before opening a document picker, retains it
  through staging/confirmation, and rejects a replaced session before writing.
- Widget caches/actions carry profile + collection. Cold actions use the saved
  profile and verify collection identity; unscoped caches have no scene actions.

## Required implementation properties

1. Maintain an authoritative context with profile ID, collection name, and a revision
   for invalidating obsolete asynchronous work. Begin a profile switch before any
   suspension; activate only after that connection succeeds and collection discovery
   finishes. Cancellation/failure from an older operation must not clear a newer context.
2. Automatic reconnect retains its target profile but resolves the collection again.
   Disconnect/changing/unknown context must not enable edits against guessed identity.
3. Reset scene/program/preview snapshots at scope transitions. Publish registry,
   scene list, and diagnostics only when they belong to the same context revision.
4. Registry compound mutations still serialize and use one indexed snapshot/bulk
   write. Capture scope before waiting for the mutation mutex. Renames, stale cleanup,
   reorder, imports, and Undo must never act on a replacement workspace by accident.
5. Bind widget action parameters to the cached workspace. A cold action may connect
   to that profile, but must verify the OBS collection before switching a scene;
   never silently switch scene collections to satisfy an old widget action.
6. Preserve profile deletion/credential rollback guarantees. Curation has no cascading
   profile foreign key; retaining recoverable metadata is intentional. Explicit
   workspace removal can be designed with named-deck management later.

## Legacy curation recovery

Old data does not identify its originating profile/collection, so its owner cannot
be inferred reliably. Keep it intact under the legacy key. Add an explicit local
"Copy previous curation" flow with a merge preview into the current workspace;
reuse import semantics and never copy automatically into every connection. Make the
recovery entry discoverable when legacy data exists. Capture the destination before
preview and reject a confirmation after a context change. Explain the transition in
the implementation's feature docs. Existing export/import files remain usable.

## Tests to carry forward

- Two profiles with the same collection and scene names, plus two collections within
  one profile; switch back and verify independent roles/order/colors.
- Old Ready plus newly selected profile never publishes a mixed context.
- Delayed collection discovery, rename, import, Doctor result, and registry mutation
  cannot publish or write into a replacement context.
- Automatic reconnect to a different collection resets state and preserves both stores.
- Same-named thumbnails and Mixer discoveries from the prior context never appear as
  current content or overwrite a later discovery in the new context.
- Failed/partial discovery remains unknown; curation is never treated as stale merely
  because the connection or collection lookup failed.
- Legacy recovery preserves its source, respects conflicts/order, survives failed
  storage, and cannot apply to a different workspace after preview.
- Cached widget scene names cannot switch a same-named scene in another workspace.
