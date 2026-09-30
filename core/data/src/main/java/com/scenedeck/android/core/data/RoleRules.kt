package com.scenedeck.android.core.data

/** Verdict of a scene-dependency edge against the role rules (FEATURE_SPEC §7). */
enum class EdgeVerdict {
    /** Allowed dependency. */
    OK,

    /** Surprising but not fatal (amber). */
    SUSPICIOUS,

    /** Must not happen (red). */
    FORBIDDEN,
}

/**
 * Role-rule policy for scene dependencies (hardcoded default policy; the registry model can grow
 * user-editable rules later — documented in docs/FEATURE_SPEC.md §7):
 *
 * - Live scenes (PRIMARY) and SECONDARY may only depend on MODULE or RAW scenes — depending on
 *   another live/parked scene is SUSPICIOUS.
 * - MODULE may depend on MODULE or RAW.
 * - RAW may only depend on RAW — anything richer above it is SUSPICIOUS.
 * - NOTHING in production may depend on DEBUG or ARCHIVE scenes — FORBIDDEN (DEBUG/ARCHIVE scenes
 *   themselves may reference anything; they're excluded from production but can still nest test
 *   content). (Cycles are a separate Doctor check, not an edge verdict.)
 */
object RoleRules {

    @Suppress("ReturnCount") // early exits are the clearest form for a rule table
    fun classifyEdge(parent: SceneRole, child: SceneRole): EdgeVerdict {
        // Debug/archive scenes are not part of production: they may reference
        // anything (they're just never referenced BY production scenes).
        if (parent == SceneRole.DEBUG || parent == SceneRole.ARCHIVE) return EdgeVerdict.OK
        if (child == SceneRole.DEBUG || child == SceneRole.ARCHIVE) return EdgeVerdict.FORBIDDEN
        val allowed =
            when (parent) {
                SceneRole.PRIMARY,
                SceneRole.SECONDARY -> child == SceneRole.MODULE || child == SceneRole.RAW

                SceneRole.MODULE -> child == SceneRole.MODULE || child == SceneRole.RAW

                SceneRole.RAW -> child == SceneRole.RAW

                SceneRole.DEBUG,
                SceneRole.ARCHIVE ->
                    // Debug/archive scenes are not part of production; allow them to
                    // reference anything (they're never referenced by live scenes).
                    true
            }
        return if (allowed) EdgeVerdict.OK else EdgeVerdict.SUSPICIOUS
    }
}
