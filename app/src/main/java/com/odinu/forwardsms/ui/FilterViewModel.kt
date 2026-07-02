package com.odinu.forwardsms.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odinu.forwardsms.FilterRepository
import com.odinu.forwardsms.data.Filter
import com.odinu.forwardsms.data.FilterHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FilterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FilterRepository.getInstance(application)

    val filters = repository.getAllFilters()
    val history = repository.getAllHistory()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun addFilter(
        keyword: String,
        url: String,
        method: String,
        filterType: String = "KEYWORD",
        phoneNumber: String = ""
    ) {
        val context = getApplication<Application>().applicationContext
        val validationError = ErrorHandler.validateFilter(
            context, keyword, url, method, filterType, phoneNumber
        )
        if (validationError != null) {
            _errorMessage.value = validationError
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.insertFilter(
                    Filter(
                        keyword = keyword.trim(),
                        phoneNumber = phoneNumber.trim(),
                        filterType = filterType.uppercase().trim(),
                        url = url.trim(),
                        method = method.uppercase().trim()
                    )
                )
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = ErrorHandler.getAddFilterErrorMessage(context, e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFilter(filter: Filter, enabled: Boolean) {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            try {
                repository.updateFilter(filter.copy(enabled = enabled))
            } catch (e: Exception) {
                _errorMessage.value = ErrorHandler.getUpdateFilterErrorMessage(context, e)
            }
        }
    }

    fun updateFilter(filter: Filter) {
        val context = getApplication<Application>().applicationContext
        val validationError = ErrorHandler.validateFilter(
            context, filter.keyword, filter.url, filter.method, filter.filterType, filter.phoneNumber
        )
        if (validationError != null) {
            _errorMessage.value = validationError
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.updateFilter(filter)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = ErrorHandler.getUpdateFilterErrorMessage(context, e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFilter(filter: Filter) {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.deleteFilter(filter)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = ErrorHandler.getDeleteFilterErrorMessage(context, e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getFilterById(id: Int): Filter? {
        return repository.getFilterById(id)
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}