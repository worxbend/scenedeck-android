package com.scenedeck.android.feature.connections

import com.scenedeck.android.core.data.ConnectionProfile

/** Editor sheet state: hidden, adding (optionally prefilled from QR), or editing. */
sealed interface ProfileEditor {
    data object Hidden : ProfileEditor

    data class Add(val prefill: ObswsTarget?) : ProfileEditor

    data class Edit(val profile: ConnectionProfile) : ProfileEditor
}
