package com.example.ui.viewmodel

import com.example.data.model.FeedbackEntry
import com.example.data.model.FeedbackSubmitState
import com.example.data.repository.FeedbackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedbackDelegate(
    private val viewModelScope: CoroutineScope,
    private val feedbackRepository: FeedbackRepository
) {
    private val _feedbackSubmitState = MutableStateFlow<FeedbackSubmitState>(FeedbackSubmitState.Idle)
    val feedbackSubmitState: StateFlow<FeedbackSubmitState> = _feedbackSubmitState.asStateFlow()

    fun submitFeedback(entry: FeedbackEntry) {
        viewModelScope.launch {
            _feedbackSubmitState.value = FeedbackSubmitState.Submitting
            val result = feedbackRepository.submitFeedback(entry)
            _feedbackSubmitState.value = result
        }
    }

    fun resetFeedbackState() {
        _feedbackSubmitState.value = FeedbackSubmitState.Idle
    }
}
