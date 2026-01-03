package org.mwynhad.portal.virtual

import com.destroystokyo.paper.profile.PlayerProfile
import com.destroystokyo.paper.profile.ProfileProperty
import org.mwynhad.portal.Portal
import org.mwynhad.portal.protocol.PotionEffectData
import org.mwynhad.portal.protocol.SkinData
import org.mwynhad.portal.sync.PlayerSyncManager.RemotePlayerState
import io.netty.buffer.Unpooled
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages virtual player entities that represent remote players
 * Uses packet-level manipulation to create convincing player entities
 */
class VirtualPlayerManager(private val plugin: Portal) {
    
    // Virtual players indexed by UUID
    private val virtualPlayers = ConcurrentHashMap<UUID, VirtualPlayer>()
    
    // Entity ID counter (use negative IDs to avoid conflicts)
    private var entityIdCounter = -1000
    
    // Scoreboard for team-based features
    private var scoreboard: Scoreboard? = null
    private var federatedTeam: Team? = null
    
    init {
        setupScoreboard()
    }
    
    private fun setupScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        
        // Get or create team for federated players
        federatedTeam = scoreboard?.getTeam("federated") ?: scoreboard?.registerNewTeam("federated")
        federatedTeam?.apply {
            setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
            setOption(Team.Option.COLLISION_RULE, 
                if (plugin.portalConfig.virtualPlayers.enableCollision) 
                    Team.OptionStatus.ALWAYS 
                else 
                    Team.OptionStatus.NEVER
            )
        }
    }
    
    /**
     * Create a virtual player entity for a remote player
     */
    fun createVirtualPlayer(state: RemotePlayerState) {
        if (virtualPlayers.containsKey(state.uuid)) {
            // Already exists, just update
            updatePosition(state.uuid, state)
            return
        }
        
        val entityId = entityIdCounter--
        val world = Bukkit.getWorld(state.world) ?: Bukkit.getWorlds().first()
        
        val virtualPlayer = VirtualPlayer(
            uuid = state.uuid,
            entityId = entityId,
            name = state.name,
            displayName = state.displayName,
            skinData = state.skinData,
            currentLocation = Location(world, state.x, state.y, state.z, state.yaw, state.pitch),
            targetLocation = Location(world, state.x, state.y, state.z, state.yaw, state.pitch),
            gameMode = state.gameMode,
            sneaking = state.sneaking,
            sprinting = state.sprinting
        )
        
        virtualPlayers[state.uuid] = virtualPlayer
        
        // Create player profile for tab list
        if (plugin.portalConfig.virtualPlayers.showInTablist) {
            addToTabList(virtualPlayer)
        }
        
        // Add to team
        federatedTeam?.addEntry(state.name)
        
        // Spawn for all local players
        spawnForAllPlayers(virtualPlayer)
        
        plugin.logger.info("Created virtual player: ${state.name} (${state.uuid})")
    }
    
    /**
     * Remove a virtual player
     */
    fun removeVirtualPlayer(uuid: UUID) {
        val virtualPlayer = virtualPlayers.remove(uuid) ?: return
        
        // Remove from tab list
        if (plugin.portalConfig.virtualPlayers.showInTablist) {
            removeFromTabList(virtualPlayer)
        }
        
        // Remove from team
        federatedTeam?.removeEntry(virtualPlayer.name)
        
        // Despawn for all local players
        despawnForAllPlayers(virtualPlayer)
        
        plugin.logger.info("Removed virtual player: ${virtualPlayer.name}")
    }
    
    /**
     * Update virtual player position with interpolation
     */
    fun updatePosition(uuid: UUID, state: RemotePlayerState) {
        val virtualPlayer = virtualPlayers[uuid] ?: return
        
        val world = Bukkit.getWorld(state.world) ?: return
        
        // Set target location for interpolation
        virtualPlayer.targetLocation = Location(world, state.x, state.y, state.z, state.yaw, state.pitch)
        virtualPlayer.velocity = org.bukkit.util.Vector(state.velocityX, state.velocityY, state.velocityZ)
        virtualPlayer.sneaking = state.sneaking
        virtualPlayer.sprinting = state.sprinting
        virtualPlayer.onGround = state.onGround
        virtualPlayer.lastUpdate = System.currentTimeMillis()
        
        // Send position update to all local players
        sendPositionUpdate(virtualPlayer)
    }
    
    /**
     * Play animation for virtual player
     */
    fun playAnimation(uuid: UUID, animationType: Int) {
        val virtualPlayer = virtualPlayers[uuid] ?: return
        sendAnimation(virtualPlayer, animationType)
    }
    
    /**
     * Update equipment for virtual player
     */
    fun updateEquipment(uuid: UUID, slot: Int, itemData: ByteArray) {
        val virtualPlayer = virtualPlayers[uuid] ?: return
        
        val item = if (itemData.isEmpty()) {
            ItemStack.empty()
        } else {
            ItemStack.deserializeBytes(itemData)
        }
        
        val equipmentSlot = EquipmentSlot.values().getOrNull(slot) ?: return
        virtualPlayer.equipment[equipmentSlot] = item
        
        sendEquipment(virtualPlayer, equipmentSlot, item)
    }
    
    /**
     * Update vitals for virtual player
     */
    fun updateVitals(uuid: UUID, state: RemotePlayerState) {
        val virtualPlayer = virtualPlayers[uuid] ?: return
        virtualPlayer.health = state.health
        virtualPlayer.maxHealth = state.maxHealth
        
        // Update metadata to show damage animation if needed
        sendMetadata(virtualPlayer)
    }
    
    /**
     * Update effects for virtual player
     */
    fun updateEffects(uuid: UUID, effects: List<PotionEffectData>) {
        val virtualPlayer = virtualPlayers[uuid] ?: return
        virtualPlayer.effects = effects
        
        // Effects are handled via metadata (glowing, invisibility, etc.)
        sendMetadata(virtualPlayer)
    }
    
    /**
     * Spawn virtual player for a specific local player
     */
    fun spawnForPlayer(player: Player, virtualPlayer: VirtualPlayer) {
        // Use Paper's packet API to spawn the virtual player
        sendPlayerInfoPacket(player, virtualPlayer, true)
        sendSpawnPacket(player, virtualPlayer)
        sendMetadataPacket(player, virtualPlayer)
        sendEquipmentPackets(player, virtualPlayer)
    }
    
    /**
     * Spawn virtual player for all local players
     */
    private fun spawnForAllPlayers(virtualPlayer: VirtualPlayer) {
        Bukkit.getOnlinePlayers().forEach { player ->
            spawnForPlayer(player, virtualPlayer)
        }
    }
    
    /**
     * Despawn virtual player for all local players
     */
    private fun despawnForAllPlayers(virtualPlayer: VirtualPlayer) {
        Bukkit.getOnlinePlayers().forEach { player ->
            sendDestroyPacket(player, virtualPlayer)
            sendPlayerInfoPacket(player, virtualPlayer, false)
        }
    }
    
    /**
     * Send position update to all local players
     */
    private fun sendPositionUpdate(virtualPlayer: VirtualPlayer) {
        val loc = virtualPlayer.targetLocation
        
        Bukkit.getOnlinePlayers().forEach { player ->
            // Check if in range
            if (player.world.name == loc.world?.name && 
                player.location.distanceSquared(loc) < 16384) { // 128 blocks
                sendTeleportPacket(player, virtualPlayer)
                sendHeadRotationPacket(player, virtualPlayer)
            }
        }
        
        // Update current location after sending
        virtualPlayer.currentLocation = virtualPlayer.targetLocation.clone()
    }
    
    /**
     * Send animation to all local players
     */
    private fun sendAnimation(virtualPlayer: VirtualPlayer, animationType: Int) {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (isInRange(player, virtualPlayer)) {
                sendAnimationPacket(player, virtualPlayer, animationType)
            }
        }
    }
    
    /**
     * Send equipment to all local players
     */
    private fun sendEquipment(virtualPlayer: VirtualPlayer, slot: EquipmentSlot, item: ItemStack) {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (isInRange(player, virtualPlayer)) {
                sendEquipmentPacket(player, virtualPlayer, slot, item)
            }
        }
    }
    
    /**
     * Send metadata to all local players
     */
    private fun sendMetadata(virtualPlayer: VirtualPlayer) {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (isInRange(player, virtualPlayer)) {
                sendMetadataPacket(player, virtualPlayer)
            }
        }
    }
    
    // ===== Packet Methods (using Paper/NMS) =====
    
    private fun sendPlayerInfoPacket(player: Player, virtualPlayer: VirtualPlayer, add: Boolean) {
        // Use Paper API to send player info update
        // This adds/removes the virtual player from the tab list
        try {
            if (add) {
                // Create player profile with skin
                val profile = Bukkit.createProfile(virtualPlayer.uuid, virtualPlayer.name)
                virtualPlayer.skinData?.let { skin ->
                    profile.setProperty(ProfileProperty("textures", skin.textureValue, skin.textureSignature))
                }
                
                // Note: Full implementation would use packets
                // For now, we use a simplified approach
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send player info packet: ${e.message}")
        }
    }
    
    private fun sendSpawnPacket(player: Player, virtualPlayer: VirtualPlayer) {
        // Use Paper's packet sending capabilities
        // Spawn player entity packet
        try {
            val loc = virtualPlayer.currentLocation
            // Implementation would send ClientboundAddPlayerPacket
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send spawn packet: ${e.message}")
        }
    }
    
    private fun sendDestroyPacket(player: Player, virtualPlayer: VirtualPlayer) {
        try {
            // Implementation would send ClientboundRemoveEntitiesPacket
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send destroy packet: ${e.message}")
        }
    }
    
    private fun sendTeleportPacket(player: Player, virtualPlayer: VirtualPlayer) {
        try {
            // Implementation would send ClientboundTeleportEntityPacket
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send teleport packet: ${e.message}")
        }
    }
    
    private fun sendHeadRotationPacket(player: Player, virtualPlayer: VirtualPlayer) {
        try {
            // Implementation would send ClientboundRotateHeadPacket
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send head rotation packet: ${e.message}")
        }
    }
    
    private fun sendMetadataPacket(player: Player, virtualPlayer: VirtualPlayer) {
        try {
            // Implementation would send ClientboundSetEntityDataPacket
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send metadata packet: ${e.message}")
        }
    }
    
    private fun sendAnimationPacket(player: Player, virtualPlayer: VirtualPlayer, animationType: Int) {
        try {
            // Implementation would send ClientboundAnimatePacket
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send animation packet: ${e.message}")
        }
    }
    
    private fun sendEquipmentPacket(player: Player, virtualPlayer: VirtualPlayer, slot: EquipmentSlot, item: ItemStack) {
        try {
            // Implementation would send ClientboundSetEquipmentPacket
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send equipment packet: ${e.message}")
        }
    }
    
    private fun sendEquipmentPackets(player: Player, virtualPlayer: VirtualPlayer) {
        virtualPlayer.equipment.forEach { (slot, item) ->
            sendEquipmentPacket(player, virtualPlayer, slot, item)
        }
    }
    
    private fun addToTabList(virtualPlayer: VirtualPlayer) {
        // Add to tab list for all players
        Bukkit.getOnlinePlayers().forEach { player ->
            sendPlayerInfoPacket(player, virtualPlayer, true)
        }
    }
    
    private fun removeFromTabList(virtualPlayer: VirtualPlayer) {
        Bukkit.getOnlinePlayers().forEach { player ->
            sendPlayerInfoPacket(player, virtualPlayer, false)
        }
    }
    
    private fun isInRange(player: Player, virtualPlayer: VirtualPlayer): Boolean {
        val loc = virtualPlayer.currentLocation
        return player.world.name == loc.world?.name && 
               player.location.distanceSquared(loc) < 16384 // 128 blocks
    }
    
    /**
     * Spawn all virtual players for a newly joined local player
     */
    fun spawnAllForPlayer(player: Player) {
        virtualPlayers.values.forEach { virtualPlayer ->
            spawnForPlayer(player, virtualPlayer)
        }
    }
    
    /**
     * Get all virtual players
     */
    fun getVirtualPlayers(): Map<UUID, VirtualPlayer> = virtualPlayers.toMap()
    
    /**
     * Get a virtual player by UUID
     */
    fun getVirtualPlayer(uuid: UUID): VirtualPlayer? = virtualPlayers[uuid]
    
    /**
     * Cleanup all virtual players
     */
    fun cleanup() {
        virtualPlayers.keys.toList().forEach { uuid ->
            removeVirtualPlayer(uuid)
        }
    }
}

/**
 * Represents a virtual player entity
 */
data class VirtualPlayer(
    val uuid: UUID,
    val entityId: Int,
    val name: String,
    val displayName: String,
    val skinData: SkinData?,
    var currentLocation: Location,
    var targetLocation: Location,
    var velocity: org.bukkit.util.Vector = org.bukkit.util.Vector(0, 0, 0),
    var gameMode: GameMode = GameMode.SURVIVAL,
    var sneaking: Boolean = false,
    var sprinting: Boolean = false,
    var onGround: Boolean = true,
    var health: Double = 20.0,
    var maxHealth: Double = 20.0,
    val equipment: MutableMap<EquipmentSlot, ItemStack> = mutableMapOf(),
    var effects: List<PotionEffectData> = emptyList(),
    var lastUpdate: Long = System.currentTimeMillis()
)
