package com.example.aiwithlove.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.util.ErrorLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class EventType(
    val label: String,
    val level: String,
    val service: String,
    val message: String,
    val code: String
) {
    DB_ERROR("DB ошибка", "ERROR", "auth-service", "Failed to connect to database", "DB_CONN_001"),
    PAYMENT_TIMEOUT("Таймаут оплаты", "ERROR", "payment-service", "Payment gateway timeout", "GW_TIMEOUT_005"),
    SMTP_ERROR("SMTP ошибка", "ERROR", "notification-service", "SMTP connection refused", "SMTP_ERR_004"),
    SEARCH_DOWN("Поиск недоступен", "ERROR", "search-service", "Elasticsearch cluster not reachable", "ES_CONN_009"),
    SLOW_RESPONSE("Медленный ответ", "WARN", "api-gateway", "Slow response time 2800ms", "PERF_WARN_002"),
    USER_LOGIN("Успешный вход", "INFO", "user-service", "User login successful", "AUTH_OK_003"),
}

class EmulatorViewModel(private val repo: ErrorLogRepository) : ViewModel() {

    private val _rowCount = MutableStateFlow(repo.rowCount)
    val rowCount: StateFlow<Int> = _rowCount.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<String>>(emptyList())
    val recentEvents: StateFlow<List<String>> = _recentEvents.asStateFlow()

    init {
        repo.ensureInitialized()
    }

    fun addEvent(type: EventType) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.append(type.level, type.service, type.message, type.code)
            _rowCount.value = repo.rowCount
            _recentEvents.value = (_recentEvents.value + "[${type.level}] ${type.service}: ${type.code}").takeLast(8)
        }
    }

    fun clearLog() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.clear()
            _rowCount.value = 0
            _recentEvents.value = emptyList()
        }
    }
}
