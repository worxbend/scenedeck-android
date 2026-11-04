# W1b integration notes

These are implementation constraints for the next slice, not shipped behavior.

## Evidence

- `ObsSessionHolder` owns connection operations and supersedes old commands using a
  generation/job. `SettingsRepository.lastUsedProfileId` changes before connecting;
  it must not be combined with an old Ready state to identify the active host.
- `ConnectionState.Ready` carries version information, not connection-profile identity.
- The event boundary currently exposes `CurrentSceneCollectionChanged`, but does not
  yet expose its corresponding changing event. Verify the pinned protocol/library
  before extending it; do not put wire types in repositories or features.
- `ObsStateRepository` combines independent scene/program/registry flows. Scope each
  snapshot so a new registry cannot combine with the previous collection's scenes.
- Screenshot caches currently use scene names. Clear or scope frames/throttle state
  on workspace replacement so a same-named scene cannot display another host's image.
- `RegistryTransfer` stages file contents, then imports into the registry when confirmed.
  Capture the origin workspace at staging, including before document I/O suspends.
- Widget cached actions contain only a scene name and connect through the last-used
  profile. They need explicit profile/collection identity to avoid same-name ambiguity.

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
