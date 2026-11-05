package com.scenedeck.android.shortcuts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.ShortcutRepository
import com.scenedeck.android.core.data.ShortcutRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShortcutHostViewModel
@Inject
constructor(repository: ShortcutRepository, runner: ShortcutRunner) : ViewModel() {
    internal val host = ShortcutHost(repository, runner, viewModelScope)
}
