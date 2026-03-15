package com.pudding.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pudding.ai.data.database.MemoryDao
import com.pudding.ai.data.database.MessageDao
import com.pudding.ai.data.database.ConversationDao
import com.pudding.ai.data.model.*
import com.pudding.ai.data.repository.ChatRepository
import com.pudding.ai.data.repository.ContextManager
import com.pudding.ai.data.repository.SearchRepository
import com.pudding.ai.data.repository.SettingsRepository
import com.pudding.ai.service.BrowserDisplayState
import com.pudding.ai.service.BrowserManager
import com.pudding.ai.service.EntityExtractor
import com.pudding.ai.service.TaskTools
import com.pudding.ai.service.TaskToolExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 对话 ViewModel
 *
 * 负责对话和消息的管理，包括：
 * - 对话列表的加载和管理
 * - 消息的发送和接收
 * - 流式响应的处理
 * - 上下文管理（检查点机制）
 * - 实体提取
 * - 浏览器状态管理
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val taskToolExecutor: TaskToolExecutor,
    val browserManager: BrowserManager
) : ViewModel() {

    companion object {
        private const val TAG = "ConversationViewModel"
    }

    // 上下文管理器
    private val contextManager = ContextManager(messageDao, chatRepository)

    // 实体提取器
    private val entityExtractor = EntityExtractor(chatRepository, memoryDao)

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

    // 当前任务执行状态
    private val _currentStatus = MutableStateFlow("思考中...")
    val currentStatus: StateFlow<String> = _currentStatus

    // 流式响应状态
    private val _streamingMessage = MutableStateFlow("")
    val streamingMessage: StateFlow<String> = _streamingMessage

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError

    // 模型配置
    val modelConfig: Flow<ModelConfig> = settingsRepository.modelConfig

    // 搜索配置
    val searchConfig: Flow<SearchConfig> = settingsRepository.searchConfig

    // 实体类型列表
    val entityTypes: Flow<List<EntityType>> = memoryDao.getAllEntityTypes()

    // 浏览器显示状态
    val browserDisplayState: StateFlow<BrowserDisplayState> = browserManager.displayState

    // 浏览器当前 URL
    val browserUrl: StateFlow<String> = browserManager.displayState.map { state ->
        if (state != BrowserDisplayState.HIDDEN) {
            browserManager.getCurrentSession()?.url ?: ""
        } else {
            ""
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // 浏览器加载状态
    val isBrowserLoading: StateFlow<Boolean> = browserManager.displayState.map { state ->
        if (state != BrowserDisplayState.HIDDEN) {
            browserManager.getCurrentSession()?.state == com.pudding.ai.data.model.BrowserState.LOADING
        } else {
            false
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * 收起浏览器到气泡
     */
    fun collapseBrowser() {
        browserManager.showBubble()
    }

    /**
     * 展开浏览器浮层
     */
    fun expandBrowser() {
        browserManager.showOverlay()
    }

    /**
     * 关闭浏览器（销毁 WebView）
     */
    fun closeBrowser() {
        browserManager.destroy()
        browserManager.hide()
    }

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

            // 使用 ContextManager 获取适合模型的消息
            val messagesForModel = contextManager.getMessagesForModel(convId, config.maxTokens)

            try {
                // 使用 Function Call 支持
                sendMessageWithTools(convId, messagesForModel, config)
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
        // 开始时设置默认状态
        _currentStatus.value = "思考中..."

        val result = chatRepository.sendMessageWithTools(
            messages = allMessages,
            config = config,
            tools = TaskTools.ALL_TOOLS,
            onToolCall = { toolName, params ->
                Log.d(TAG, "Tool called: $toolName, params: $params")
                taskToolExecutor.executeToolCall(convId, toolName, params) { status ->
                    // 状态更新回调
                    _currentStatus.value = status
                }
            },
            onStatusUpdate = { status ->
                _currentStatus.value = status
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

            // 检查是否需要创建检查点
            postMessageProcessing(convId, allMessages)
        }.onFailure { error ->
            Log.e(TAG, "sendMessageWithTools failed", error)
            _streamError.value = error.message ?: "发送失败"
        }

        _streamingMessage.value = ""
        _isStreaming.value = false
        _isLoading.value = false
        // 重置状态
        _currentStatus.value = "思考中..."
    }

    /**
     * 消息发送后的处理
     *
     * 包括：检查点创建、实体提取等
     */
    private suspend fun postMessageProcessing(
        convId: Long,
        messages: List<Message>
    ) {
        // 获取更新后的消息列表
        val updatedMessages = messageDao.getMessagesByConversation(convId).first()

        // 检查并创建检查点
        val checkpointCreated = contextManager.checkAndCreateCheckpoint(convId, updatedMessages)
        if (checkpointCreated) {
            Log.i(TAG, "Checkpoint created for conversation $convId")
        }

        // 异步提取实体（不阻塞响应）
        viewModelScope.launch {
            try {
                val types = entityTypes.first()
                if (types.isNotEmpty()) {
                    entityExtractor.detectAndSaveEntities(convId, updatedMessages, types)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Entity extraction failed", e)
            }
        }
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

    /**
     * 保存搜索配置
     */
    fun saveSearchConfig(config: SearchConfig) {
        viewModelScope.launch {
            settingsRepository.saveSearchConfig(config)
        }
    }

    /**
     * 获取当前对话的 token 统计
     */
    suspend fun getTokenStats(): ContextManager.TokenStats? {
        return _currentConversationId.value?.let { convId ->
            contextManager.getTokenStats(convId)
        }
    }

    /**
     * 添加实体类型
     */
    fun addEntityType(name: String, description: String, extractionPrompt: String) {
        viewModelScope.launch {
            memoryDao.insertEntityType(
                EntityType(
                    name = name,
                    description = description,
                    extractionPrompt = extractionPrompt.ifBlank {
                        "请从对话中提取$name 类型的实体。"
                    }
                )
            )
        }
    }

    /**
     * 删除实体类型
     */
    fun deleteEntityType(entityType: EntityType) {
        viewModelScope.launch {
            memoryDao.deleteEntityType(entityType)
        }
    }

    /**
     * 删除实体
     */
    fun deleteEntity(entity: TrackedEntity) {
        viewModelScope.launch {
            memoryDao.deleteEntity(entity)
        }
    }

    /**
     * 获取实体的属性
     */
    fun getEntityAttributes(entityId: Long) = memoryDao.getCurrentAttributes(entityId)

    /**
     * 获取所有实体
     */
    fun getAllEntities() = memoryDao.getAllEntities()
}