package com.pudding.ai.service

import android.util.Log
import com.pudding.ai.data.database.MemoryDao
import com.pudding.ai.data.model.*
import com.pudding.ai.data.repository.ChatRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.first

/**
 * 实体提取服务
 *
 * 负责从对话消息中提取实体（任务、人物、事件等），
 * 并追踪实体属性的变化。
 */
class EntityExtractor(
    private val chatRepository: ChatRepository,
    private val memoryDao: MemoryDao
) {
    companion object {
        private const val TAG = "EntityExtractor"
    }

    private val gson = Gson()

    /**
     * 从消息中提取实体
     *
     * @param messages 要提取实体的消息列表
     * @param entityTypes 实体类型列表
     * @return 提取到的实体列表
     */
    suspend fun extractEntities(
        messages: List<Message>,
        entityTypes: List<EntityType>
    ): List<ExtractedEntity> {
        if (messages.isEmpty() || entityTypes.isEmpty()) {
            return emptyList()
        }

        // 构建提取提示词
        val extractionPrompt = buildExtractionPrompt(messages, entityTypes)

        // 调用 AI 提取
        return try {
            val result = chatRepository.sendMessageSimple(
                messages = listOf(
                    Message(
                        conversationId = 0,
                        role = MessageRole.SYSTEM,
                        content = extractionPrompt
                    )
                ),
                config = ModelConfig()
            )

            result.getOrNull()?.let { parseExtractionResult(it) } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract entities", e)
            emptyList()
        }
    }

    /**
     * 构建实体提取提示词
     */
    private fun buildExtractionPrompt(
        messages: List<Message>,
        entityTypes: List<EntityType>
    ): String {
        val conversationText = messages.joinToString("\n") { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }
            "[$role]: ${msg.content}"
        }

        val typeDescriptions = entityTypes.joinToString("\n") { type ->
            "- ${type.name}: ${type.description}\n  提取提示: ${type.extractionPrompt}"
        }

        return """
            请从以下对话中提取实体信息。

            实体类型定义：
            $typeDescriptions

            对话内容：
            $conversationText

            请以 JSON 格式返回提取的实体，格式如下：
            ```json
            {
              "entities": [
                {
                  "type": "实体类型名称",
                  "name": "实体名称",
                  "attributes": {
                    "属性名": "属性值"
                  }
                }
              ]
            }
            ```

            注意：
            1. 只提取明确提到的实体，不要猜测
            2. 如果是已知实体的属性更新，也要列出
            3. 属性值要尽可能准确和具体

            JSON 结果：
        """.trimIndent()
    }

    /**
     * 解析提取结果
     */
    private fun parseExtractionResult(result: String): List<ExtractedEntity> {
        return try {
            // 提取 JSON 块
            val jsonStart = result.indexOf("```json")
            val jsonEnd = result.lastIndexOf("```")

            val jsonText = if (jsonStart >= 0 && jsonEnd > jsonStart) {
                result.substring(jsonStart + 7, jsonEnd).trim()
            } else {
                // 尝试直接解析
                result.trim()
            }

            val response = gson.fromJson(jsonText, ExtractionResponse::class.java)
            response.entities
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse extraction result: $result", e)
            emptyList()
        }
    }

    /**
     * 检测并保存实体
     *
     * @param conversationId 对话 ID
     * @param messages 消息列表
     * @param entityTypes 实体类型列表
     */
    suspend fun detectAndSaveEntities(
        conversationId: Long,
        messages: List<Message>,
        entityTypes: List<EntityType>
    ) {
        val extractedEntities = extractEntities(messages, entityTypes)

        for (extracted in extractedEntities) {
            // 查找实体类型
            val entityType = entityTypes.find { it.name == extracted.type }
            if (entityType == null) {
                Log.w(TAG, "Unknown entity type: ${extracted.type}")
                continue
            }

            // 查找或创建实体
            var entity = memoryDao.getEntityByNameAndType(extracted.name, entityType.id)
            if (entity == null) {
                val entityId = memoryDao.insertEntity(
                    TrackedEntity(
                        typeId = entityType.id,
                        name = extracted.name
                    )
                )
                entity = TrackedEntity(id = entityId, typeId = entityType.id, name = extracted.name)
                Log.i(TAG, "Created new entity: ${entity.name} (${entityType.name})")
            }

            // 保存属性
            extracted.attributes.forEach { (key, value) ->
                saveEntityAttribute(entity.id, key, value, messages.lastOrNull()?.id)
            }
        }
    }

    /**
     * 保存实体属性
     */
    private suspend fun saveEntityAttribute(
        entityId: Long,
        key: String,
        value: String,
        sourceMessageId: Long?
    ) {
        // 获取当前属性
        val currentAttr = memoryDao.getCurrentAttribute(entityId, key)

        if (currentAttr != null) {
            if (currentAttr.value == value) {
                // 值相同，无需更新
                return
            }

            // 将当前属性设为过期
            memoryDao.invalidateCurrentAttribute(entityId, key)

            // 创建新属性，记录取代关系
            memoryDao.insertAttribute(
                EntityAttribute(
                    entityId = entityId,
                    key = key,
                    value = value,
                    sourceMessageId = sourceMessageId,
                    supersedesId = currentAttr.id,
                    isCurrent = true
                )
            )
            Log.i(TAG, "Updated attribute: $key = $value (was: ${currentAttr.value})")
        } else {
            // 创建新属性
            memoryDao.insertAttribute(
                EntityAttribute(
                    entityId = entityId,
                    key = key,
                    value = value,
                    sourceMessageId = sourceMessageId,
                    isCurrent = true
                )
            )
            Log.i(TAG, "Created attribute: $key = $value")
        }
    }

    /**
     * 获取实体的完整信息（包括所有当前属性）
     */
    suspend fun getEntityWithAttributes(entityId: Long): EntityWithAttributes? {
        val entity = memoryDao.getEntityById(entityId) ?: return null
        val entityType = memoryDao.getEntityTypeById(entity.typeId) ?: return null
        val attributes = memoryDao.getCurrentAttributes(entityId).first()

        return EntityWithAttributes(
            entity = entity,
            entityType = entityType,
            attributes = attributes
        )
    }

    /**
     * 获取实体的属性历史
     */
    suspend fun getAttributeHistory(entityId: Long, key: String): List<EntityAttribute> {
        return memoryDao.getAttributeHistory(entityId, key).first()
    }

    // ========== 数据类 ==========

    /**
     * 提取响应
     */
    data class ExtractionResponse(
        val entities: List<ExtractedEntity>
    )

    /**
     * 提取的实体
     */
    data class ExtractedEntity(
        val type: String,
        val name: String,
        val attributes: Map<String, String>
    )

    /**
     * 实体及其属性的完整信息
     */
    data class EntityWithAttributes(
        val entity: TrackedEntity,
        val entityType: EntityType,
        val attributes: List<EntityAttribute>
    )
}