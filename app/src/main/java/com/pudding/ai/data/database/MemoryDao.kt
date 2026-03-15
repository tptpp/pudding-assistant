package com.pudding.ai.data.database

import androidx.room.*
import com.pudding.ai.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 记忆系统数据访问对象
 *
 * 提供实体类型、实体、实体属性和每日记忆的数据库操作。
 */
@Dao
interface MemoryDao {

    // ========== 实体类型操作 ==========

    /**
     * 获取所有实体类型
     */
    @Query("SELECT * FROM entity_types ORDER BY name")
    fun getAllEntityTypes(): Flow<List<EntityType>>

    /**
     * 根据ID获取实体类型
     */
    @Query("SELECT * FROM entity_types WHERE id = :id")
    suspend fun getEntityTypeById(id: Long): EntityType?

    /**
     * 根据名称获取实体类型
     */
    @Query("SELECT * FROM entity_types WHERE name = :name LIMIT 1")
    suspend fun getEntityTypeByName(name: String): EntityType?

    /**
     * 插入实体类型
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntityType(type: EntityType): Long

    /**
     * 更新实体类型
     */
    @Update
    suspend fun updateEntityType(type: EntityType)

    /**
     * 删除实体类型
     */
    @Delete
    suspend fun deleteEntityType(type: EntityType)

    // ========== 实体操作 ==========

    /**
     * 根据类型获取实体列表
     */
    @Query("SELECT * FROM entities WHERE typeId = :typeId ORDER BY name")
    fun getEntitiesByType(typeId: Long): Flow<List<TrackedEntity>>

    /**
     * 获取所有实体
     */
    @Query("SELECT * FROM entities ORDER BY name")
    fun getAllEntities(): Flow<List<TrackedEntity>>

    /**
     * 根据ID获取实体
     */
    @Query("SELECT * FROM entities WHERE id = :id")
    suspend fun getEntityById(id: Long): TrackedEntity?

    /**
     * 搜索实体（按名称或类型名称）
     */
    @Query("""
        SELECT e.* FROM entities e
        JOIN entity_types t ON e.typeId = t.id
        WHERE e.name LIKE '%' || :keyword || '%' OR t.name LIKE '%' || :keyword || '%'
        ORDER BY e.name
    """)
    fun searchEntities(keyword: String): Flow<List<TrackedEntity>>

    /**
     * 根据名称和类型查找实体
     */
    @Query("SELECT * FROM entities WHERE name = :name AND typeId = :typeId LIMIT 1")
    suspend fun getEntityByNameAndType(name: String, typeId: Long): TrackedEntity?

    /**
     * 插入实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntity(entity: TrackedEntity): Long

    /**
     * 更新实体
     */
    @Update
    suspend fun updateEntity(entity: TrackedEntity)

    /**
     * 删除实体
     */
    @Delete
    suspend fun deleteEntity(entity: TrackedEntity)

    // ========== 实体属性操作 ==========

    /**
     * 获取实体的所有属性
     */
    @Query("SELECT * FROM entity_attributes WHERE entityId = :entityId ORDER BY createdAt DESC")
    fun getEntityAttributes(entityId: Long): Flow<List<EntityAttribute>>

    /**
     * 获取实体的当前属性
     */
    @Query("SELECT * FROM entity_attributes WHERE entityId = :entityId AND isCurrent = 1")
    fun getCurrentAttributes(entityId: Long): Flow<List<EntityAttribute>>

    /**
     * 获取实体指定属性的当前值
     */
    @Query("SELECT * FROM entity_attributes WHERE entityId = :entityId AND key = :key AND isCurrent = 1 LIMIT 1")
    suspend fun getCurrentAttribute(entityId: Long, key: String): EntityAttribute?

    /**
     * 获取实体指定属性的历史记录
     */
    @Query("SELECT * FROM entity_attributes WHERE entityId = :entityId AND key = :key ORDER BY createdAt DESC")
    fun getAttributeHistory(entityId: Long, key: String): Flow<List<EntityAttribute>>

    /**
     * 插入属性
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttribute(attribute: EntityAttribute): Long

    /**
     * 更新属性
     */
    @Update
    suspend fun updateAttribute(attribute: EntityAttribute)

    /**
     * 将实体的某个属性的当前值设为过期
     */
    @Query("UPDATE entity_attributes SET isCurrent = 0 WHERE entityId = :entityId AND key = :key AND isCurrent = 1")
    suspend fun invalidateCurrentAttribute(entityId: Long, key: String)

    /**
     * 删除属性
     */
    @Delete
    suspend fun deleteAttribute(attribute: EntityAttribute)

    // ========== 每日记忆操作 ==========

    /**
     * 获取所有每日记忆
     */
    @Query("SELECT * FROM daily_memories ORDER BY date DESC")
    fun getAllDailyMemories(): Flow<List<DailyMemory>>

    /**
     * 根据日期获取每日记忆
     */
    @Query("SELECT * FROM daily_memories WHERE date = :date")
    suspend fun getDailyMemoryByDate(date: String): DailyMemory?

    /**
     * 根据ID获取每日记忆
     */
    @Query("SELECT * FROM daily_memories WHERE id = :id")
    suspend fun getDailyMemoryById(id: Long): DailyMemory?

    /**
     * 搜索每日记忆
     */
    @Query("SELECT * FROM daily_memories WHERE title LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%' OR summary LIKE '%' || :keyword || '%' ORDER BY date DESC")
    fun searchDailyMemories(keyword: String): Flow<List<DailyMemory>>

    /**
     * 插入每日记忆
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyMemory(memory: DailyMemory): Long

    /**
     * 更新每日记忆
     */
    @Update
    suspend fun updateDailyMemory(memory: DailyMemory)

    /**
     * 删除每日记忆
     */
    @Delete
    suspend fun deleteDailyMemory(memory: DailyMemory)
}