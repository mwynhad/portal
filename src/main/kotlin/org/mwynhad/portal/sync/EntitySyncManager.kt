package org.mwynhad.portal.sync

import org.mwynhad.portal.Portal
import org.mwynhad.portal.network.MessageHandler
import org.mwynhad.portal.protocol.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages synchronization of entity state across federation nodes
 */
class EntitySyncManager(private val plugin: Portal) : MessageHandler {
    
    // Track remote entities by UUID
    private val remoteEntities = ConcurrentHashMap<UUID, RemoteEntityState>()
    
    // Map of entity type names to EntityType
    private val entityTypeMap = EntityType.values().associateBy { it.name }
    
    // Local entities we're tracking
    private val trackedLocalEntities = ConcurrentHashMap<UUID, Long>()
    
    data class RemoteEntityState(
        val uuid: UUID,
        val entityType: EntityType,
        var sourceNode: String,
        var world: String,
        var x: Double,
        var y: Double,
        var z: Double,
        var yaw: Float,
        var pitch: Float,
        var customName: String?,
        var localEntityId: Int? = null,
        var lastUpdate: Long = System.currentTimeMillis()
    )
    
    override fun handledMessageTypes(): List<Class<out PortalMessage>> = listOf(
        EntitySpawn::class.java,
        EntityDespawn::class.java,
        EntityMove::class.java,
        EntityMoveBatch::class.java
    )
    
    override fun handleMessage(message: PortalMessage) {
        when (message) {
            is EntitySpawn -> handleEntitySpawn(message)
            is EntityDespawn -> handleEntityDespawn(message)
            is EntityMove -> handleEntityMove(message)
            is EntityMoveBatch -> handleEntityMoveBatch(message)
            else -> {}
        }
    }
    
    private fun handleEntitySpawn(message: EntitySpawn) {
        val uuid = UUID.fromString(message.entityUuid)
        
        // Check if we already have this entity locally
        if (Bukkit.getEntity(uuid) != null) return
        
        val entityType = entityTypeMap[message.entityType] ?: return
        
        val state = RemoteEntityState(
            uuid = uuid,
            entityType = entityType,
            sourceNode = message.sourceNode,
            world = message.world,
            x = message.x,
            y = message.y,
            z = message.z,
            yaw = message.yaw,
            pitch = message.pitch,
            customName = message.customName
        )
        
        remoteEntities[uuid] = state
        
        // Spawn the entity on the main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            spawnRemoteEntity(state)
        })
        
        if (plugin.portalConfig.debug.logSync) {
            plugin.logger.info("[SYNC] Remote entity spawned: ${message.entityType} from ${message.sourceNode}")
        }
    }
    
    private fun handleEntityDespawn(message: EntityDespawn) {
        val uuid = UUID.fromString(message.entityUuid)
        
        val state = remoteEntities.remove(uuid) ?: return
        
        // Remove the entity on the main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            Bukkit.getEntity(uuid)?.remove()
        })
        
        if (plugin.portalConfig.debug.logSync) {
            plugin.logger.info("[SYNC] Remote entity despawned: $uuid")
        }
    }
    
    private fun handleEntityMove(message: EntityMove) {
        val uuid = UUID.fromString(message.entityUuid)
        
        val state = remoteEntities[uuid] ?: return
        
        state.apply {
            x = message.x
            y = message.y
            z = message.z
            yaw = message.yaw
            pitch = message.pitch
            lastUpdate = System.currentTimeMillis()
        }
        
        // Update entity position on main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            updateEntityPosition(uuid, state)
        })
    }
    
    private fun handleEntityMoveBatch(message: EntityMoveBatch) {
        val updates = mutableMapOf<UUID, CompactEntityMove>()
        
        message.moves.forEach { move ->
            val uuid = UUID.fromString(move.entityUuid)
            val state = remoteEntities[uuid] ?: return@forEach
            
            state.apply {
                x = move.x
                y = move.y
                z = move.z
                yaw = move.yaw
                pitch = move.pitch
                lastUpdate = System.currentTimeMillis()
            }
            
            updates[uuid] = move
        }
        
        // Batch update on main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            updates.forEach { (uuid, _) ->
                remoteEntities[uuid]?.let { state ->
                    updateEntityPosition(uuid, state)
                }
            }
        })
    }
    
    private fun spawnRemoteEntity(state: RemoteEntityState) {
        val world = Bukkit.getWorld(state.world) ?: return
        val location = Location(world, state.x, state.y, state.z, state.yaw, state.pitch)
        
        try {
            val entity = world.spawnEntity(location, state.entityType)
            
            // Tag as remote entity
            entity.persistentDataContainer.set(
                org.bukkit.NamespacedKey(plugin, "remote"),
                org.bukkit.persistence.PersistentDataType.STRING,
                state.sourceNode
            )
            
            // Set custom name if present
            state.customName?.let { name ->
                entity.customName(net.kyori.adventure.text.Component.text(name))
                entity.isCustomNameVisible = true
            }
            
            state.localEntityId = entity.entityId
            
        } catch (e: Exception) {
            plugin.logger.warning("Failed to spawn remote entity ${state.entityType}: ${e.message}")
        }
    }
    
    private fun updateEntityPosition(uuid: UUID, state: RemoteEntityState) {
        val entity = Bukkit.getEntity(uuid) ?: return
        val world = Bukkit.getWorld(state.world) ?: return
        
        val location = Location(world, state.x, state.y, state.z, state.yaw, state.pitch)
        entity.teleport(location)
    }
    
    /**
     * Broadcast local entities to the federation
     */
    fun broadcastLocalEntities() {
        val config = plugin.portalConfig.sync.entities
        val syncTypes = config.syncTypes.mapNotNull { entityTypeMap[it] }.toSet()
        val syncRadius = config.syncRadius
        
        val moves = mutableListOf<CompactEntityMove>()
        
        // Get entities near players
        plugin.server.onlinePlayers.forEach { player ->
            player.getNearbyEntities(syncRadius.toDouble(), syncRadius.toDouble(), syncRadius.toDouble())
                .filter { entity ->
                    // Only sync configured types or mobs if enabled
                    (syncTypes.contains(entity.type) || 
                     (config.syncMobs && entity is Mob)) &&
                    // Not a virtual/remote entity
                    !isRemoteEntity(entity) &&
                    // Not a player (handled by PlayerSyncManager)
                    entity !is Player
                }
                .forEach { entity ->
                    val lastBroadcast = trackedLocalEntities[entity.uniqueId] ?: 0L
                    
                    // Only broadcast if entity moved or hasn't been broadcast recently
                    if (System.currentTimeMillis() - lastBroadcast > 100) {
                        val loc = entity.location
                        
                        moves.add(CompactEntityMove(
                            entityUuid = entity.uniqueId.toString(),
                            x = loc.x,
                            y = loc.y,
                            z = loc.z,
                            yaw = loc.yaw,
                            pitch = loc.pitch
                        ))
                        
                        trackedLocalEntities[entity.uniqueId] = System.currentTimeMillis()
                    }
                }
        }
        
        if (moves.isNotEmpty()) {
            val batch = EntityMoveBatch(
                sourceNode = Portal.nodeId,
                timestamp = System.currentTimeMillis(),
                messageId = MessageSerializer.generateMessageId(),
                moves = moves
            )
            
            plugin.networkManager.broadcast(batch)
        }
        
        // Cleanup old tracked entities
        val cutoff = System.currentTimeMillis() - 10000
        trackedLocalEntities.entries.removeIf { it.value < cutoff }
    }
    
    /**
     * Broadcast entity spawn
     */
    fun broadcastEntitySpawn(entity: Entity) {
        if (isRemoteEntity(entity) || entity is Player) return
        
        val loc = entity.location
        
        val spawn = EntitySpawn(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            entityId = entity.entityId,
            entityUuid = entity.uniqueId.toString(),
            entityType = entity.type.name,
            world = entity.world.name,
            x = loc.x,
            y = loc.y,
            z = loc.z,
            yaw = loc.yaw,
            pitch = loc.pitch,
            metadata = ByteArray(0), // TODO: serialize entity metadata
            customName = entity.customName()?.let { 
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
            }
        )
        
        plugin.networkManager.broadcast(spawn)
        trackedLocalEntities[entity.uniqueId] = System.currentTimeMillis()
    }
    
    /**
     * Broadcast entity despawn
     */
    fun broadcastEntityDespawn(entity: Entity) {
        if (isRemoteEntity(entity) || entity is Player) return
        
        trackedLocalEntities.remove(entity.uniqueId)
        
        val despawn = EntityDespawn(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            entityUuid = entity.uniqueId.toString()
        )
        
        plugin.networkManager.broadcast(despawn)
    }
    
    /**
     * Check if an entity is a remote entity
     */
    fun isRemoteEntity(entity: Entity): Boolean {
        return entity.persistentDataContainer.has(
            org.bukkit.NamespacedKey(plugin, "remote"),
            org.bukkit.persistence.PersistentDataType.STRING
        )
    }
    
    /**
     * Get all remote entities
     */
    fun getRemoteEntities(): Map<UUID, RemoteEntityState> = remoteEntities.toMap()
}
