package org.mwynhad.portal.sync

import org.mwynhad.portal.Portal
import org.mwynhad.portal.network.MessageHandler
import org.mwynhad.portal.protocol.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages synchronization of world/block state across federation nodes
 */
class WorldSyncManager(private val plugin: Portal) : MessageHandler {
    
    // Pending block changes to batch
    private val pendingBlockChanges = ConcurrentLinkedQueue<PendingBlockChange>()
    
    // Track recently synced blocks to avoid infinite loops
    private val recentlySyncedBlocks = ConcurrentHashMap<BlockKey, Long>()
    
    // Last batch send time
    private var lastBatchSend = 0L
    
    data class PendingBlockChange(
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val blockData: String,
        val causePlayerUuid: String?,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class BlockKey(
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int
    )
    
    override fun handledMessageTypes(): List<Class<out PortalMessage>> = listOf(
        BlockChange::class.java,
        BlockChangeBatch::class.java,
        ChunkData::class.java
    )
    
    override fun handleMessage(message: PortalMessage) {
        when (message) {
            is BlockChange -> handleBlockChange(message)
            is BlockChangeBatch -> handleBlockChangeBatch(message)
            is ChunkData -> handleChunkData(message)
            else -> {}
        }
    }
    
    private fun handleBlockChange(message: BlockChange) {
        val key = BlockKey(message.world, message.x, message.y, message.z)
        
        // Check if we recently synced this block
        val lastSync = recentlySyncedBlocks[key]
        if (lastSync != null && System.currentTimeMillis() - lastSync < 100) {
            return // Avoid duplicate updates
        }
        
        recentlySyncedBlocks[key] = System.currentTimeMillis()
        
        // Apply block change on main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            applyBlockChange(message.world, message.x, message.y, message.z, message.blockData)
        })
        
        if (plugin.portalConfig.debug.logSync) {
            plugin.logger.info("[SYNC] Block change at ${message.x},${message.y},${message.z} in ${message.world}")
        }
    }
    
    private fun handleBlockChangeBatch(message: BlockChangeBatch) {
        val changes = message.changes.map { change ->
            BlockKey(message.world, change.x, change.y, change.z) to change
        }
        
        // Filter out recently synced blocks
        val now = System.currentTimeMillis()
        val validChanges = changes.filter { (key, _) ->
            val lastSync = recentlySyncedBlocks[key]
            if (lastSync != null && now - lastSync < 100) {
                false
            } else {
                recentlySyncedBlocks[key] = now
                true
            }
        }
        
        if (validChanges.isEmpty()) return
        
        // Apply changes on main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            validChanges.forEach { (_, change) ->
                applyBlockChange(message.world, change.x, change.y, change.z, change.blockData)
            }
        })
        
        if (plugin.portalConfig.debug.logSync) {
            plugin.logger.info("[SYNC] Batch block changes: ${validChanges.size} blocks in ${message.world}")
        }
    }
    
    private fun handleChunkData(message: ChunkData) {
        // Chunk data sync for teleportation or initial load
        // This is more complex and requires NBT/chunk deserialization
        plugin.server.scheduler.runTask(plugin, Runnable {
            applyChunkData(message)
        })
    }
    
    private fun applyBlockChange(worldName: String, x: Int, y: Int, z: Int, blockDataString: String) {
        val world = Bukkit.getWorld(worldName) ?: return
        
        try {
            val blockData = Bukkit.createBlockData(blockDataString)
            val block = world.getBlockAt(x, y, z)
            
            // Apply without triggering physics to avoid cascading updates
            block.setBlockData(blockData, false)
            
        } catch (e: Exception) {
            plugin.logger.warning("Failed to apply block change at $x,$y,$z: ${e.message}")
        }
    }
    
    private fun applyChunkData(message: ChunkData) {
        val world = Bukkit.getWorld(message.world) ?: return
        
        try {
            // Load the chunk first
            val chunk = world.getChunkAt(message.chunkX, message.chunkZ)
            
            // Decompress and apply chunk data
            // This would require more complex NBT handling
            // For now, we just ensure the chunk is loaded
            
            if (!chunk.isLoaded) {
                chunk.load()
            }
            
        } catch (e: Exception) {
            plugin.logger.warning("Failed to apply chunk data: ${e.message}")
        }
    }
    
    /**
     * Queue a block change for broadcast
     */
    fun queueBlockChange(world: String, x: Int, y: Int, z: Int, blockData: BlockData, causePlayerUuid: String? = null) {
        val config = plugin.portalConfig.sync.world
        
        val key = BlockKey(world, x, y, z)
        
        // Check if this was recently synced from another node
        val lastSync = recentlySyncedBlocks[key]
        if (lastSync != null && System.currentTimeMillis() - lastSync < 100) {
            return // This change came from sync, don't broadcast back
        }
        
        val change = PendingBlockChange(
            world = world,
            x = x,
            y = y,
            z = z,
            blockData = blockData.asString,
            causePlayerUuid = causePlayerUuid
        )
        
        if (config.immediateBlockSync && !config.batchBlockUpdates) {
            // Send immediately
            broadcastBlockChange(change)
        } else {
            // Queue for batching
            pendingBlockChanges.add(change)
            
            // Check if we should send batch
            checkSendBatch()
        }
    }
    
    /**
     * Check if we should send a batch of block changes
     */
    private fun checkSendBatch() {
        val config = plugin.portalConfig.sync.world
        val now = System.currentTimeMillis()
        
        if (now - lastBatchSend >= config.batchIntervalMs || pendingBlockChanges.size >= 100) {
            sendBatch()
        }
    }
    
    /**
     * Send queued block changes as a batch
     */
    fun sendBatch() {
        if (pendingBlockChanges.isEmpty()) return
        
        lastBatchSend = System.currentTimeMillis()
        
        // Group by world
        val changesByWorld = mutableMapOf<String, MutableList<PendingBlockChange>>()
        
        while (true) {
            val change = pendingBlockChanges.poll() ?: break
            changesByWorld.getOrPut(change.world) { mutableListOf() }.add(change)
        }
        
        // Send batch per world
        changesByWorld.forEach { (world, changes) ->
            val batch = BlockChangeBatch(
                sourceNode = Portal.nodeId,
                timestamp = System.currentTimeMillis(),
                messageId = MessageSerializer.generateMessageId(),
                world = world,
                changes = changes.map { change ->
                    CompactBlockChange(
                        x = change.x,
                        y = change.y,
                        z = change.z,
                        blockData = change.blockData
                    )
                }
            )
            
            plugin.networkManager.broadcast(batch)
        }
    }
    
    /**
     * Broadcast a single block change immediately
     */
    private fun broadcastBlockChange(change: PendingBlockChange) {
        val message = BlockChange(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            world = change.world,
            x = change.x,
            y = change.y,
            z = change.z,
            blockData = change.blockData,
            causePlayerUuid = change.causePlayerUuid
        )
        
        plugin.networkManager.broadcast(message)
    }
    
    /**
     * Request chunk data from another node
     */
    fun requestChunkData(world: String, chunkX: Int, chunkZ: Int) {
        // This would send a request to other nodes for chunk data
        // Useful when a player teleports to an area that might have changes
    }
    
    /**
     * Cleanup old entries from the sync cache
     */
    fun cleanup() {
        val cutoff = System.currentTimeMillis() - 5000
        recentlySyncedBlocks.entries.removeIf { it.value < cutoff }
    }
}
