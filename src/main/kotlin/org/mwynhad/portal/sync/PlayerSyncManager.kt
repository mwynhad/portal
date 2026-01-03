package org.mwynhad.portal.sync

import org.mwynhad.portal.Portal
import org.mwynhad.portal.network.MessageHandler
import org.mwynhad.portal.protocol.*
import org.mwynhad.portal.virtual.VirtualPlayer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages synchronization of player state across federation nodes
 */
class PlayerSyncManager(private val plugin: Portal) : MessageHandler {
    
    // Track remote players by UUID
    private val remotePlayers = ConcurrentHashMap<UUID, RemotePlayerState>()
    
    // Track which node each player is on
    private val playerNodes = ConcurrentHashMap<UUID, String>()
    
    // Last position broadcast for deduplication
    private val lastBroadcastPositions = ConcurrentHashMap<UUID, PositionCache>()
    
    data class RemotePlayerState(
        val uuid: UUID,
        var name: String,
        var displayName: String,
        var sourceNode: String,
        var world: String,
        var x: Double,
        var y: Double,
        var z: Double,
        var yaw: Float,
        var pitch: Float,
        var velocityX: Double = 0.0,
        var velocityY: Double = 0.0,
        var velocityZ: Double = 0.0,
        var sneaking: Boolean = false,
        var sprinting: Boolean = false,
        var onGround: Boolean = true,
        var gameMode: GameMode = GameMode.SURVIVAL,
        var health: Double = 20.0,
        var maxHealth: Double = 20.0,
        var food: Int = 20,
        var effects: List<PotionEffectData> = emptyList(),
        var skinData: SkinData? = null,
        var lastUpdate: Long = System.currentTimeMillis()
    )
    
    data class PositionCache(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        val timestamp: Long
    )
    
    override fun handledMessageTypes(): List<Class<out PortalMessage>> = listOf(
        PlayerJoin::class.java,
        PlayerQuit::class.java,
        PlayerPosition::class.java,
        PlayerPositionBatch::class.java,
        PlayerAnimation::class.java,
        PlayerEquipment::class.java,
        PlayerVitals::class.java,
        PlayerEffects::class.java
    )
    
    override fun handleMessage(message: PortalMessage) {
        when (message) {
            is PlayerJoin -> handlePlayerJoin(message)
            is PlayerQuit -> handlePlayerQuit(message)
            is PlayerPosition -> handlePlayerPosition(message)
            is PlayerPositionBatch -> handlePlayerPositionBatch(message)
            is PlayerAnimation -> handlePlayerAnimation(message)
            is PlayerEquipment -> handlePlayerEquipment(message)
            is PlayerVitals -> handlePlayerVitals(message)
            is PlayerEffects -> handlePlayerEffects(message)
            else -> {}
        }
    }
    
    private fun handlePlayerJoin(message: PlayerJoin) {
        val uuid = UUID.fromString(message.playerUuid)
        
        // Don't process if this is a local player
        if (plugin.server.getPlayer(uuid) != null) return
        
        val state = RemotePlayerState(
            uuid = uuid,
            name = message.playerName,
            displayName = message.displayName,
            sourceNode = message.sourceNode,
            world = message.world,
            x = message.x,
            y = message.y,
            z = message.z,
            yaw = message.yaw,
            pitch = message.pitch,
            gameMode = GameMode.values().getOrNull(message.gameMode) ?: GameMode.SURVIVAL,
            skinData = message.skinData
        )
        
        remotePlayers[uuid] = state
        playerNodes[uuid] = message.sourceNode
        
        // Create virtual player on main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.virtualPlayerManager.createVirtualPlayer(state)
        })
        
        if (plugin.portalConfig.debug.logSync) {
            plugin.logger.info("[SYNC] Remote player joined: ${message.playerName} from ${message.sourceNode}")
        }
    }
    
    private fun handlePlayerQuit(message: PlayerQuit) {
        val uuid = UUID.fromString(message.playerUuid)
        
        remotePlayers.remove(uuid)
        playerNodes.remove(uuid)
        
        // Remove virtual player on main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.virtualPlayerManager.removeVirtualPlayer(uuid)
        })
        
        if (plugin.portalConfig.debug.logSync) {
            plugin.logger.info("[SYNC] Remote player quit: ${message.playerUuid}")
        }
    }
    
    private fun handlePlayerPosition(message: PlayerPosition) {
        val uuid = UUID.fromString(message.playerUuid)
        
        // Don't process if this is a local player
        if (plugin.server.getPlayer(uuid) != null) return
        
        val state = remotePlayers[uuid] ?: return
        
        state.apply {
            world = message.world
            x = message.x
            y = message.y
            z = message.z
            yaw = message.yaw
            pitch = message.pitch
            velocityX = message.velocityX
            velocityY = message.velocityY
            velocityZ = message.velocityZ
            sneaking = message.sneaking
            sprinting = message.sprinting
            onGround = message.onGround
            lastUpdate = System.currentTimeMillis()
        }
        
        // Update virtual player position (with interpolation)
        plugin.virtualPlayerManager.updatePosition(uuid, state)
    }
    
    private fun handlePlayerPositionBatch(message: PlayerPositionBatch) {
        message.positions.forEach { pos ->
            val uuid = UUID.fromString(pos.playerUuid)
            
            // Don't process if this is a local player
            if (plugin.server.getPlayer(uuid) != null) return@forEach
            
            val state = remotePlayers[uuid] ?: return@forEach
            
            // Unpack flags
            val onGround = (pos.flags and 0x01) != 0
            val sneaking = (pos.flags and 0x02) != 0
            val sprinting = (pos.flags and 0x04) != 0
            
            state.apply {
                world = pos.world
                x = pos.x
                y = pos.y
                z = pos.z
                yaw = pos.yaw
                pitch = pos.pitch
                this.onGround = onGround
                this.sneaking = sneaking
                this.sprinting = sprinting
                lastUpdate = System.currentTimeMillis()
            }
            
            plugin.virtualPlayerManager.updatePosition(uuid, state)
        }
    }
    
    private fun handlePlayerAnimation(message: PlayerAnimation) {
        val uuid = UUID.fromString(message.playerUuid)
        plugin.virtualPlayerManager.playAnimation(uuid, message.animationType)
    }
    
    private fun handlePlayerEquipment(message: PlayerEquipment) {
        val uuid = UUID.fromString(message.playerUuid)
        plugin.virtualPlayerManager.updateEquipment(uuid, message.slot, message.itemData)
    }
    
    private fun handlePlayerVitals(message: PlayerVitals) {
        val uuid = UUID.fromString(message.playerUuid)
        val state = remotePlayers[uuid] ?: return
        
        state.apply {
            health = message.health
            maxHealth = message.maxHealth
            food = message.food
        }
        
        plugin.virtualPlayerManager.updateVitals(uuid, state)
    }
    
    private fun handlePlayerEffects(message: PlayerEffects) {
        val uuid = UUID.fromString(message.playerUuid)
        val state = remotePlayers[uuid] ?: return
        
        state.effects = message.effects
        plugin.virtualPlayerManager.updateEffects(uuid, state.effects)
    }
    
    /**
     * Broadcast all local players' positions to the federation
     */
    fun broadcastLocalPlayers() {
        val config = plugin.portalConfig.sync.players
        val positions = mutableListOf<CompactPlayerPosition>()
        
        plugin.server.onlinePlayers.forEach { player ->
            val loc = player.location
            val cache = lastBroadcastPositions[player.uniqueId]
            
            // Only broadcast if position changed significantly
            val shouldBroadcast = cache == null ||
                System.currentTimeMillis() - cache.timestamp > 50 ||
                distanceSquared(cache, loc) > 0.0001 ||
                angleDiff(cache.yaw, loc.yaw) > 1f ||
                angleDiff(cache.pitch, loc.pitch) > 1f
            
            if (shouldBroadcast) {
                val flags = packFlags(player.isOnGround, player.isSneaking, player.isSprinting)
                
                positions.add(CompactPlayerPosition(
                    playerUuid = player.uniqueId.toString(),
                    world = player.world.name,
                    x = loc.x,
                    y = loc.y,
                    z = loc.z,
                    yaw = loc.yaw,
                    pitch = loc.pitch,
                    flags = flags
                ))
                
                lastBroadcastPositions[player.uniqueId] = PositionCache(
                    loc.x, loc.y, loc.z, loc.yaw, loc.pitch, System.currentTimeMillis()
                )
            }
        }
        
        if (positions.isNotEmpty()) {
            val batch = PlayerPositionBatch(
                sourceNode = Portal.nodeId,
                timestamp = System.currentTimeMillis(),
                messageId = MessageSerializer.generateMessageId(),
                positions = positions
            )
            
            plugin.networkManager.broadcast(batch)
        }
    }
    
    /**
     * Broadcast a player join to the federation
     */
    fun broadcastPlayerJoin(player: Player) {
        val loc = player.location
        val displayNamePlain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(player.displayName())
        
        val join = PlayerJoin(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            playerUuid = player.uniqueId.toString(),
            playerName = player.name,
            displayName = displayNamePlain,
            world = player.world.name,
            x = loc.x,
            y = loc.y,
            z = loc.z,
            yaw = loc.yaw,
            pitch = loc.pitch,
            gameMode = player.gameMode.ordinal,
            skinData = extractSkinData(player)
        )
        
        plugin.networkManager.broadcast(join)
    }
    
    /**
     * Broadcast a player quit to the federation
     */
    fun broadcastPlayerQuit(player: Player, reason: String = "disconnect") {
        val quit = PlayerQuit(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            playerUuid = player.uniqueId.toString(),
            reason = reason
        )
        
        plugin.networkManager.broadcast(quit)
        lastBroadcastPositions.remove(player.uniqueId)
    }
    
    /**
     * Broadcast player animation
     */
    fun broadcastAnimation(player: Player, animationType: Int) {
        val animation = PlayerAnimation(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            playerUuid = player.uniqueId.toString(),
            animationType = animationType
        )
        
        plugin.networkManager.broadcast(animation)
    }
    
    /**
     * Broadcast equipment change
     */
    fun broadcastEquipment(player: Player, slot: EquipmentSlot) {
        val item = when (slot) {
            EquipmentSlot.HAND -> player.inventory.itemInMainHand
            EquipmentSlot.OFF_HAND -> player.inventory.itemInOffHand
            EquipmentSlot.HEAD -> player.inventory.helmet
            EquipmentSlot.CHEST -> player.inventory.chestplate
            EquipmentSlot.LEGS -> player.inventory.leggings
            EquipmentSlot.FEET -> player.inventory.boots
            else -> return
        }
        
        val itemData = serializeItem(item)
        
        val equipment = PlayerEquipment(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            playerUuid = player.uniqueId.toString(),
            slot = slot.ordinal,
            itemData = itemData
        )
        
        plugin.networkManager.broadcast(equipment)
    }
    
    /**
     * Broadcast vitals change
     */
    fun broadcastVitals(player: Player) {
        val vitals = PlayerVitals(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            playerUuid = player.uniqueId.toString(),
            health = player.health,
            maxHealth = player.maxHealth,
            food = player.foodLevel,
            saturation = player.saturation,
            airTicks = player.remainingAir
        )
        
        plugin.networkManager.broadcast(vitals)
    }
    
    /**
     * Broadcast potion effects
     */
    fun broadcastEffects(player: Player) {
        val effectData = player.activePotionEffects.map { effect ->
            PotionEffectData(
                effectType = effect.type.key.key,
                amplifier = effect.amplifier,
                duration = effect.duration,
                ambient = effect.isAmbient,
                particles = effect.hasParticles(),
                icon = effect.hasIcon()
            )
        }
        
        val effects = PlayerEffects(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            playerUuid = player.uniqueId.toString(),
            effects = effectData
        )
        
        plugin.networkManager.broadcast(effects)
    }
    
    /**
     * Get all remote players
     */
    fun getRemotePlayers(): Map<UUID, RemotePlayerState> = remotePlayers.toMap()
    
    /**
     * Get which node a player is on
     */
    fun getPlayerNode(uuid: UUID): String? = playerNodes[uuid]
    
    /**
     * Check if a player is remote
     */
    fun isRemotePlayer(uuid: UUID): Boolean = remotePlayers.containsKey(uuid)
    
    private fun extractSkinData(player: Player): SkinData? {
        return try {
            val profile = player.playerProfile
            val textures = profile.properties.find { it.name == "textures" }
            textures?.let {
                SkinData(it.value, it.signature ?: "")
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun serializeItem(item: org.bukkit.inventory.ItemStack?): ByteArray {
        if (item == null || item.type.isAir) return ByteArray(0)
        return item.serializeAsBytes()
    }
    
    private fun distanceSquared(cache: PositionCache, loc: Location): Double {
        val dx = cache.x - loc.x
        val dy = cache.y - loc.y
        val dz = cache.z - loc.z
        return dx * dx + dy * dy + dz * dz
    }
    
    private fun angleDiff(a: Float, b: Float): Float {
        var diff = kotlin.math.abs(a - b)
        if (diff > 180) diff = 360 - diff
        return diff
    }
    
    private fun packFlags(onGround: Boolean, sneaking: Boolean, sprinting: Boolean): Int {
        var flags = 0
        if (onGround) flags = flags or 0x01
        if (sneaking) flags = flags or 0x02
        if (sprinting) flags = flags or 0x04
        return flags
    }
}
