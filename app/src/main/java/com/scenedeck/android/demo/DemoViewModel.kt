package com.scenedeck.android.demo

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DemoViewModel @Inject constructor(private val runtime: DemoRuntime) : ViewModel() {
    private val visit = Any()
    val state = runtime.state

    init {
        runtime.open(visit)
    }

    fun retry() = runtime.open(visit)

    fun exit() = runtime.close(visit)

    override fun onCleared() {
        runtime.close(visit)
    }
}
