package com.scenedeck.android.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.RecordingAction
import com.scenedeck.android.core.data.RecordingOrigin
import com.scenedeck.android.core.data.RecordingToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

@HiltViewModel
class RecordingToolsViewModel
@Inject
constructor(private val repository: RecordingToolsRepository) : ViewModel() {
    val state = repository.state

    fun perform(origin: RecordingOrigin, action: RecordingAction, chapterName: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.perform(origin, action, chapterName)
        }
    }
}
