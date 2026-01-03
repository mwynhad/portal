package org.mwynhad.portal.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.util.UUID

/**
 * Base message types for federation protocol
 */
@Serializable
sealed class PortalMessage {
    abstract val sourceNode: String
    abstract val timestamp: Long
    abstract val messageId: String
}

/**
 * Message type identifiers for efficient routing
 */
enum class MessageType(val id: Int) {
    // Node management (0-9)
    NODE_ANNOUNCE(0),
    NODE_HEARTBEAT(1),
    NODE_DISCONNECT(2),
    NODE_PING(3),
    NODE_PONG(4),
    
    // Player sync (10-29)
    PLAYER_JOIN(10),
    PLAYER_QUIT(11),
    PLAYER_POSITION(12),
    PLAYER_POSITION_BATCH(13),
    PLAYER_LOOK(14),
    PLAYER_ANIMATION(15),
    PLAYER_INVENTORY(16),
    PLAYER_EQUIPMENT(17),
    PLAYER_VITALS(18),
    PLAYER_EFFECTS(19),
    PLAYER_GAMEMODE(20),
    PLAYER_SKIN(21),
    PLAYER_METADATA(22),
    
    // Entity sync (30-49)
    ENTITY_SPAWN(30),
    ENTITY_DESPAWN(31),
    ENTITY_MOVE(32),
    ENTITY_MOVE_BATCH(33),
    ENTITY_METADATA(34),
    ENTITY_VELOCITY(35),
    ENTITY_TELEPORT(36),
    
    // World sync (50-69)
    BLOCK_CHANGE(50),
    BLOCK_CHANGE_BATCH(51),
    BLOCK_BREAK_ANIMATION(52),
    CHUNK_DATA(53),
    EXPLOSION(54),
    PARTICLE(55),
    SOUND(56),
    
    // Chat/Communication (70-79)
    CHAT_MESSAGE(70),
    SYSTEM_MESSAGE(71),
    ACTION_BAR(72),
    TITLE(73),
    
    // Events (80-99)
    PLAYER_ATTACK(80),
    PLAYER_DAMAGE(81),
    PLAYER_INTERACT(82),
    PLAYER_INTERACT_ENTITY(83),
    CONTAINER_OPEN(84),
    CONTAINER_CLOSE(85),
    ITEM_PICKUP(86),
    ITEM_DROP(87),
    
    // Commands (100+)
    COMMAND_EXECUTE(100),
    COMMAND_RESPONSE(101)
}

// ===== Node Messages =====

@Serializable
data class NodeAnnounce(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val nodeName: String,
    @ProtoNumber(5) val region: String,
    @ProtoNumber(6) val isPrimary: Boolean,
    @ProtoNumber(7) val directHost: String,
    @ProtoNumber(8) val directPort: Int,
    @ProtoNumber(9) val playerCount: Int,
    @ProtoNumber(10) val maxPlayers: Int,
    @ProtoNumber(11) val version: String
) : PortalMessage()

@Serializable
data class NodeHeartbeat(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerCount: Int,
    @ProtoNumber(5) val tps: Double,
    @ProtoNumber(6) val mspt: Double,
    @ProtoNumber(7) val usedMemory: Long,
    @ProtoNumber(8) val maxMemory: Long
) : PortalMessage()

@Serializable
data class NodePing(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val targetNode: String
) : PortalMessage()

@Serializable
data class NodePong(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val pingMessageId: String,
    @ProtoNumber(5) val originalTimestamp: Long
) : PortalMessage()

// ===== Player Messages =====

@Serializable
data class PlayerJoin(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val playerName: String,
    @ProtoNumber(6) val displayName: String,
    @ProtoNumber(7) val world: String,
    @ProtoNumber(8) val x: Double,
    @ProtoNumber(9) val y: Double,
    @ProtoNumber(10) val z: Double,
    @ProtoNumber(11) val yaw: Float,
    @ProtoNumber(12) val pitch: Float,
    @ProtoNumber(13) val gameMode: Int,
    @ProtoNumber(14) val skinData: SkinData?
) : PortalMessage()

@Serializable
data class PlayerQuit(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val reason: String
) : PortalMessage()

@Serializable
data class PlayerPosition(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val world: String,
    @ProtoNumber(6) val x: Double,
    @ProtoNumber(7) val y: Double,
    @ProtoNumber(8) val z: Double,
    @ProtoNumber(9) val yaw: Float,
    @ProtoNumber(10) val pitch: Float,
    @ProtoNumber(11) val onGround: Boolean,
    @ProtoNumber(12) val velocityX: Double,
    @ProtoNumber(13) val velocityY: Double,
    @ProtoNumber(14) val velocityZ: Double,
    @ProtoNumber(15) val sneaking: Boolean,
    @ProtoNumber(16) val sprinting: Boolean
) : PortalMessage()

@Serializable
data class PlayerPositionBatch(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val positions: List<CompactPlayerPosition>
) : PortalMessage()

@Serializable
data class CompactPlayerPosition(
    @ProtoNumber(1) val playerUuid: String,
    @ProtoNumber(2) val world: String,
    @ProtoNumber(3) val x: Double,
    @ProtoNumber(4) val y: Double,
    @ProtoNumber(5) val z: Double,
    @ProtoNumber(6) val yaw: Float,
    @ProtoNumber(7) val pitch: Float,
    @ProtoNumber(8) val flags: Int // Packed: onGround, sneaking, sprinting
)

@Serializable
data class PlayerAnimation(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val animationType: Int
) : PortalMessage()

@Serializable
data class PlayerEquipment(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val slot: Int,
    @ProtoNumber(6) val itemData: ByteArray
) : PortalMessage() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayerEquipment) return false
        return playerUuid == other.playerUuid && slot == other.slot && itemData.contentEquals(other.itemData)
    }
    override fun hashCode(): Int = playerUuid.hashCode() * 31 + slot
}

@Serializable
data class PlayerVitals(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val health: Double,
    @ProtoNumber(6) val maxHealth: Double,
    @ProtoNumber(7) val food: Int,
    @ProtoNumber(8) val saturation: Float,
    @ProtoNumber(9) val airTicks: Int
) : PortalMessage()

@Serializable
data class PlayerEffects(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val effects: List<PotionEffectData>
) : PortalMessage()

@Serializable
data class PotionEffectData(
    @ProtoNumber(1) val effectType: String,
    @ProtoNumber(2) val amplifier: Int,
    @ProtoNumber(3) val duration: Int,
    @ProtoNumber(4) val ambient: Boolean,
    @ProtoNumber(5) val particles: Boolean,
    @ProtoNumber(6) val icon: Boolean
)

@Serializable
data class SkinData(
    @ProtoNumber(1) val textureValue: String,
    @ProtoNumber(2) val textureSignature: String
)

// ===== Entity Messages =====

@Serializable
data class EntitySpawn(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val entityId: Int,
    @ProtoNumber(5) val entityUuid: String,
    @ProtoNumber(6) val entityType: String,
    @ProtoNumber(7) val world: String,
    @ProtoNumber(8) val x: Double,
    @ProtoNumber(9) val y: Double,
    @ProtoNumber(10) val z: Double,
    @ProtoNumber(11) val yaw: Float,
    @ProtoNumber(12) val pitch: Float,
    @ProtoNumber(13) val metadata: ByteArray,
    @ProtoNumber(14) val customName: String?
) : PortalMessage() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntitySpawn) return false
        return entityUuid == other.entityUuid
    }
    override fun hashCode(): Int = entityUuid.hashCode()
}

@Serializable
data class EntityDespawn(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val entityUuid: String
) : PortalMessage()

@Serializable
data class EntityMove(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val entityUuid: String,
    @ProtoNumber(5) val x: Double,
    @ProtoNumber(6) val y: Double,
    @ProtoNumber(7) val z: Double,
    @ProtoNumber(8) val yaw: Float,
    @ProtoNumber(9) val pitch: Float,
    @ProtoNumber(10) val onGround: Boolean
) : PortalMessage()

@Serializable
data class EntityMoveBatch(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val moves: List<CompactEntityMove>
) : PortalMessage()

@Serializable
data class CompactEntityMove(
    @ProtoNumber(1) val entityUuid: String,
    @ProtoNumber(2) val x: Double,
    @ProtoNumber(3) val y: Double,
    @ProtoNumber(4) val z: Double,
    @ProtoNumber(5) val yaw: Float,
    @ProtoNumber(6) val pitch: Float
)

// ===== World Messages =====

@Serializable
data class BlockChange(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val world: String,
    @ProtoNumber(5) val x: Int,
    @ProtoNumber(6) val y: Int,
    @ProtoNumber(7) val z: Int,
    @ProtoNumber(8) val blockData: String, // Serialized block data
    @ProtoNumber(9) val causePlayerUuid: String? // Who caused the change
) : PortalMessage()

@Serializable
data class BlockChangeBatch(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val world: String,
    @ProtoNumber(5) val changes: List<CompactBlockChange>
) : PortalMessage()

@Serializable
data class CompactBlockChange(
    @ProtoNumber(1) val x: Int,
    @ProtoNumber(2) val y: Int,
    @ProtoNumber(3) val z: Int,
    @ProtoNumber(4) val blockData: String
)

@Serializable
data class ChunkData(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val world: String,
    @ProtoNumber(5) val chunkX: Int,
    @ProtoNumber(6) val chunkZ: Int,
    @ProtoNumber(7) val data: ByteArray, // Compressed chunk data
    @ProtoNumber(8) val heightmaps: ByteArray,
    @ProtoNumber(9) val blockEntities: List<ByteArray>
) : PortalMessage() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChunkData) return false
        return world == other.world && chunkX == other.chunkX && chunkZ == other.chunkZ
    }
    override fun hashCode(): Int = world.hashCode() * 31 * 31 + chunkX * 31 + chunkZ
}

// ===== Chat Messages =====

@Serializable
data class ChatMessage(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val playerName: String,
    @ProtoNumber(6) val message: String,
    @ProtoNumber(7) val componentJson: String // Adventure component JSON
) : PortalMessage()

@Serializable
data class SystemMessage(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val componentJson: String
) : PortalMessage()

// ===== Event Messages =====

@Serializable
data class PlayerAttack(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val attackerUuid: String,
    @ProtoNumber(5) val targetUuid: String, // Can be player or entity
    @ProtoNumber(6) val targetIsPlayer: Boolean,
    @ProtoNumber(7) val damage: Double,
    @ProtoNumber(8) val knockbackX: Double,
    @ProtoNumber(9) val knockbackY: Double,
    @ProtoNumber(10) val knockbackZ: Double,
    @ProtoNumber(11) val weaponData: ByteArray?
) : PortalMessage() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayerAttack) return false
        return messageId == other.messageId
    }
    override fun hashCode(): Int = messageId.hashCode()
}

@Serializable
data class PlayerInteractEntity(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val entityUuid: String,
    @ProtoNumber(6) val action: Int, // 0 = interact, 1 = attack, 2 = interact_at
    @ProtoNumber(7) val hand: Int,
    @ProtoNumber(8) val targetX: Float,
    @ProtoNumber(9) val targetY: Float,
    @ProtoNumber(10) val targetZ: Float
) : PortalMessage()

@Serializable
data class ItemDrop(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val itemEntityUuid: String,
    @ProtoNumber(6) val world: String,
    @ProtoNumber(7) val x: Double,
    @ProtoNumber(8) val y: Double,
    @ProtoNumber(9) val z: Double,
    @ProtoNumber(10) val itemData: ByteArray,
    @ProtoNumber(11) val velocityX: Double,
    @ProtoNumber(12) val velocityY: Double,
    @ProtoNumber(13) val velocityZ: Double
) : PortalMessage() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ItemDrop) return false
        return itemEntityUuid == other.itemEntityUuid
    }
    override fun hashCode(): Int = itemEntityUuid.hashCode()
}

@Serializable
data class ItemPickup(
    @ProtoNumber(1) override val sourceNode: String,
    @ProtoNumber(2) override val timestamp: Long,
    @ProtoNumber(3) override val messageId: String,
    @ProtoNumber(4) val playerUuid: String,
    @ProtoNumber(5) val itemEntityUuid: String
) : PortalMessage()
