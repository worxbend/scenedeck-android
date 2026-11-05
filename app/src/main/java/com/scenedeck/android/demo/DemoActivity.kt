package com.scenedeck.android.demo

import android.content.Context
import android.content.Intent
import com.scenedeck.android.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/** Non-exported, separate task and process. Never receives external scene links. */
@AndroidEntryPoint
class DemoActivity : MainActivity() {
    companion object {
        fun intent(context: Context): Intent =
            Intent(context, DemoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
