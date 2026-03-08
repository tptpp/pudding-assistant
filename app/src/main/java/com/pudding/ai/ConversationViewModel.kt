package com.pudding.ai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pudding.ai.data.model.*
import com.pudding.ai.data.repository.ChatRepository
import com.pudding.ai.data.repository.SettingsRepository
import com.pudding.ai.service.TaskTools
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 对话 ViewModel
 *
 * 负责对话和消息的管理，包括：
 * - 对话列表的加载和管理
 * - 消息的发送和接收
 * - 流式响应的处理
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ConversationViewModel"
    }

    private val app = application as AssistantApp
    private val database = app.database
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val chatRepository = app.chatRepository
    private val settingsRepository = app.settingsRepository
    private val taskToolExecutor = app.taskToolExecutor

    // 当前对话ID
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId

    // 对话列表
    val conversations: Flow<List<Conversation>> = conversationDao.getAllConversations()

    // 当前对话的消息列表
    val messages: Flow<List<Message>> = _currentConversationId.flatMapLatest { id ->
        if (id != null) messageDao.getMessagesByConversation(id) else flowOf(emptyList())
    }

    // 输入文本
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // 流式响应状态
    private val _streamingMessage = MutableStateFlow("")
    val streamingMessage: StateFlow<String> = _streamingMessage

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError

    // 模型配置
    val modelConfig: Flow<ModelConfig> = settingsRepository.modelConfig

    /**
     * 设置输入文本
     */
    fun setInputText(text: String) {
        _inputText.value = text
    }

    /**
     * 选择对话
     */
    fun selectConversation(id: Long) {
        _currentConversationId.value = id
    }

    /**
     * 创建新对话
     */
    fun createNewConversation() {
        viewModelScope.launch {
            val id = conversationDao.insertConversation(Conversation(title = "新对话"))
            _currentConversationId.value = id
        }
    }

    /**
     * 删除对话
     */
    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            messageDao.deleteMessagesByConversation(id)
            conversationDao.deleteConversationById(id)
            if (_currentConversationId.value == id) _currentConversationId.value = null
        }
    }

    /**
     * 清空当前对话的消息
     */
    fun clearCurrentConversation() {
        viewModelScope.launch {
            _currentConversationId.value?.let { convId ->
                messageDao.deleteMessagesByConversation(convId)
            }
        }
    }

    /**
     * 发送消息
     */
    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isLoading.value || _isStreaming.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _isStreaming.value = true
            _streamError.value = null
            _streamingMessage.value = ""
            _inputText.value = ""

            val convId = _currentConversationId.value ?: run {
                val id = conversationDao.insertConversation(Conversation(title = text.take(30)))
                _currentConversationId.value = id
                id
            }

            // 保存用户消息
            messageDao.insertMessage(Message(conversationId = convId, content = text, role = MessageRole.USER))

            val config = settingsRepository.modelConfig.first()
            Log.d(TAG, "Config loaded: provider=${config.provider}, baseUrl=${config.baseUrl}, model=${config.model}")
            val allMessages = messageDao.getMessagesByConversation(convId).first()

            try {
                // 如果有 TaskToolExecutor，使用 Function Call 支持
                if (taskToolExecutor != null) {
                    sendMessageWithTools(convId, allMessages, config)
                } else {
                    // 回退到普通流式消息
                    sendMessageStream(convId, allMessages, config)
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage failed", e)
                _streamError.value = e.message ?: "发送失败"
                _streamingMessage.value = ""
                _isStreaming.value = false
                _isLoading.value = false
            }
        }
    }

    /**
     * 使用 Function Call 支持发送消息
     */
    private suspend fun sendMessageWithTools(
        convId: Long,
        allMessages: List<Message>,
        config: ModelConfig
    ) {
        val result = chatRepository.sendMessageWithTools(
            messages = allMessages,
            config = config,
            tools = TaskTools.ALL_TOOLS,
            onToolCall = { toolName, params ->
                Log.d(TAG, "Tool called: $toolName, params: $params")
                taskToolExecutor?.executeToolCall(convId, toolName, params)
                    ?: com.pudding.ai.service.ToolResult(false, "TaskToolExecutor 未初始化")
            }
        )

        result.onSuccess { response ->
            // 保存助手响应
            if (response.isNotEmpty()) {
                messageDao.insertMessage(
                    Message(
                        conversationId = convId,
                        content = response,
                        role = MessageRole.ASSISTANT
                    )
                )
                _streamingMessage.value = response
            }
        }.onFailure { error ->
            Log.e(TAG, "sendMessageWithTools failed", error)
            _streamError.value = error.message ?: "发送失败"
        }

        _streamingMessage.value = ""
        _isStreaming.value = false
        _isLoading.value = false
    }

    /**
     * 普通流式消息发送（无 Function Call）
     */
    private suspend fun sendMessageStream(
        convId: Long,
        allMessages: List<Message>,
        config: ModelConfig
    ) {
        val fullResponse = StringBuilder()

        chatRepository.sendMessageStream(allMessages, config)
            .catch { e ->
                _streamError.value = e.message ?: "发送失败"
                _isStreaming.value = false
                _isLoading.value = false
            }
            .collect { chunk ->
                fullResponse.append(chunk)
                _streamingMessage.value = fullResponse.toString()
            }

        // 流式完成，保存完整消息
        if (fullResponse.isNotEmpty()) {
            messageDao.insertMessage(
                Message(
                    conversationId = convId,
                    content = fullResponse.toString(),
                    role = MessageRole.ASSISTANT
                )
            )
        }

        _streamingMessage.value = ""
        _isStreaming.value = false
        _isLoading.value = false
    }

    /**
     * 保存模型配置
     */
    fun saveModelConfig(config: ModelConfig) {
        viewModelScope.launch {
            settingsRepository.saveModelConfig(config)
            chatRepository.updateConfig(config)
        }
    }
}