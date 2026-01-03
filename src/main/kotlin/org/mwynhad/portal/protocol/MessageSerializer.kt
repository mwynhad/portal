package org.mwynhad.portal.protocol

import org.mwynhad.portal.Portal
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import net.jpountz.lz4.LZ4Factory
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Efficient serializer for federation protocol messages
 * Supports both JSON (debugging) and Protobuf (production) formats
 * with optional LZ4 compression for large payloads
 */
@OptIn(ExperimentalSerializationApi::class)
object MessageSerializer {
    
    private val protobuf = ProtoBuf {
        encodeDefaults = false
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }
    
    private val lz4Factory = LZ4Factory.fastestInstance()
    private val compressor = lz4Factory.fastCompressor()
    private val decompressor = lz4Factory.fastDecompressor()
    
    private const val FLAG_COMPRESSED = 0x01
    private const val FLAG_BINARY = 0x02
    
    /**
     * Serialize a message to bytes with optional compression
     */
    fun serialize(message: PortalMessage, useBinary: Boolean = true, compressionThreshold: Int = 256): ByteArray {
        val messageType = getMessageType(message)
        val payload = if (useBinary) {
            serializeBinary(message)
        } else {
            serializeJson(message)
        }
        
        val shouldCompress = payload.size > compressionThreshold && compressionThreshold > 0
        val finalPayload = if (shouldCompress) {
            compress(payload)
        } else {
            payload
        }
        
        // Header: [flags (1 byte)][messageType (1 byte)][original size if compressed (4 bytes)][payload]
        val headerSize = if (shouldCompress) 6 else 2
        val result = ByteBuffer.allocate(headerSize + finalPayload.size)
        
        var flags = 0
        if (shouldCompress) flags = flags or FLAG_COMPRESSED
        if (useBinary) flags = flags or FLAG_BINARY
        
        result.put(flags.toByte())
        result.put(messageType.id.toByte())
        
        if (shouldCompress) {
            result.putInt(payload.size)
        }
        
        result.put(finalPayload)
        
        return result.array()
    }
    
    /**
     * Deserialize bytes back to a message
     */
    fun deserialize(data: ByteArray): PortalMessage {
        val buffer = ByteBuffer.wrap(data)
        
        val flags = buffer.get().toInt() and 0xFF
        val messageTypeId = buffer.get().toInt() and 0xFF
        
        val isCompressed = (flags and FLAG_COMPRESSED) != 0
        val isBinary = (flags and FLAG_BINARY) != 0
        
        val payload = if (isCompressed) {
            val originalSize = buffer.getInt()
            val compressed = ByteArray(buffer.remaining())
            buffer.get(compressed)
            decompress(compressed, originalSize)
        } else {
            val remaining = ByteArray(buffer.remaining())
            buffer.get(remaining)
            remaining
        }
        
        val messageType = MessageType.entries.find { it.id == messageTypeId }
            ?: throw IllegalArgumentException("Unknown message type: $messageTypeId")
        
        return if (isBinary) {
            deserializeBinary(payload, messageType)
        } else {
            deserializeJson(payload, messageType)
        }
    }
    
    private fun serializeBinary(message: PortalMessage): ByteArray {
        return when (message) {
            is NodeAnnounce -> protobuf.encodeToByteArray(message)
            is NodeHeartbeat -> protobuf.encodeToByteArray(message)
            is NodePing -> protobuf.encodeToByteArray(message)
            is NodePong -> protobuf.encodeToByteArray(message)
            is PlayerJoin -> protobuf.encodeToByteArray(message)
            is PlayerQuit -> protobuf.encodeToByteArray(message)
            is PlayerPosition -> protobuf.encodeToByteArray(message)
            is PlayerPositionBatch -> protobuf.encodeToByteArray(message)
            is PlayerAnimation -> protobuf.encodeToByteArray(message)
            is PlayerEquipment -> protobuf.encodeToByteArray(message)
            is PlayerVitals -> protobuf.encodeToByteArray(message)
            is PlayerEffects -> protobuf.encodeToByteArray(message)
            is EntitySpawn -> protobuf.encodeToByteArray(message)
            is EntityDespawn -> protobuf.encodeToByteArray(message)
            is EntityMove -> protobuf.encodeToByteArray(message)
            is EntityMoveBatch -> protobuf.encodeToByteArray(message)
            is BlockChange -> protobuf.encodeToByteArray(message)
            is BlockChangeBatch -> protobuf.encodeToByteArray(message)
            is ChunkData -> protobuf.encodeToByteArray(message)
            is ChatMessage -> protobuf.encodeToByteArray(message)
            is SystemMessage -> protobuf.encodeToByteArray(message)
            is PlayerAttack -> protobuf.encodeToByteArray(message)
            is PlayerInteractEntity -> protobuf.encodeToByteArray(message)
            is ItemDrop -> protobuf.encodeToByteArray(message)
            is ItemPickup -> protobuf.encodeToByteArray(message)
        }
    }
    
    private fun serializeJson(message: PortalMessage): ByteArray {
        val jsonString = when (message) {
            is NodeAnnounce -> json.encodeToString(NodeAnnounce.serializer(), message)
            is NodeHeartbeat -> json.encodeToString(NodeHeartbeat.serializer(), message)
            is NodePing -> json.encodeToString(NodePing.serializer(), message)
            is NodePong -> json.encodeToString(NodePong.serializer(), message)
            is PlayerJoin -> json.encodeToString(PlayerJoin.serializer(), message)
            is PlayerQuit -> json.encodeToString(PlayerQuit.serializer(), message)
            is PlayerPosition -> json.encodeToString(PlayerPosition.serializer(), message)
            is PlayerPositionBatch -> json.encodeToString(PlayerPositionBatch.serializer(), message)
            is PlayerAnimation -> json.encodeToString(PlayerAnimation.serializer(), message)
            is PlayerEquipment -> json.encodeToString(PlayerEquipment.serializer(), message)
            is PlayerVitals -> json.encodeToString(PlayerVitals.serializer(), message)
            is PlayerEffects -> json.encodeToString(PlayerEffects.serializer(), message)
            is EntitySpawn -> json.encodeToString(EntitySpawn.serializer(), message)
            is EntityDespawn -> json.encodeToString(EntityDespawn.serializer(), message)
            is EntityMove -> json.encodeToString(EntityMove.serializer(), message)
            is EntityMoveBatch -> json.encodeToString(EntityMoveBatch.serializer(), message)
            is BlockChange -> json.encodeToString(BlockChange.serializer(), message)
            is BlockChangeBatch -> json.encodeToString(BlockChangeBatch.serializer(), message)
            is ChunkData -> json.encodeToString(ChunkData.serializer(), message)
            is ChatMessage -> json.encodeToString(ChatMessage.serializer(), message)
            is SystemMessage -> json.encodeToString(SystemMessage.serializer(), message)
            is PlayerAttack -> json.encodeToString(PlayerAttack.serializer(), message)
            is PlayerInteractEntity -> json.encodeToString(PlayerInteractEntity.serializer(), message)
            is ItemDrop -> json.encodeToString(ItemDrop.serializer(), message)
            is ItemPickup -> json.encodeToString(ItemPickup.serializer(), message)
        }
        return jsonString.toByteArray(Charsets.UTF_8)
    }
    
    @OptIn(ExperimentalSerializationApi::class)
    private fun deserializeBinary(data: ByteArray, type: MessageType): PortalMessage {
        return when (type) {
            MessageType.NODE_ANNOUNCE -> protobuf.decodeFromByteArray<NodeAnnounce>(data)
            MessageType.NODE_HEARTBEAT -> protobuf.decodeFromByteArray<NodeHeartbeat>(data)
            MessageType.NODE_PING -> protobuf.decodeFromByteArray<NodePing>(data)
            MessageType.NODE_PONG -> protobuf.decodeFromByteArray<NodePong>(data)
            MessageType.PLAYER_JOIN -> protobuf.decodeFromByteArray<PlayerJoin>(data)
            MessageType.PLAYER_QUIT -> protobuf.decodeFromByteArray<PlayerQuit>(data)
            MessageType.PLAYER_POSITION -> protobuf.decodeFromByteArray<PlayerPosition>(data)
            MessageType.PLAYER_POSITION_BATCH -> protobuf.decodeFromByteArray<PlayerPositionBatch>(data)
            MessageType.PLAYER_ANIMATION -> protobuf.decodeFromByteArray<PlayerAnimation>(data)
            MessageType.PLAYER_EQUIPMENT -> protobuf.decodeFromByteArray<PlayerEquipment>(data)
            MessageType.PLAYER_VITALS -> protobuf.decodeFromByteArray<PlayerVitals>(data)
            MessageType.PLAYER_EFFECTS -> protobuf.decodeFromByteArray<PlayerEffects>(data)
            MessageType.ENTITY_SPAWN -> protobuf.decodeFromByteArray<EntitySpawn>(data)
            MessageType.ENTITY_DESPAWN -> protobuf.decodeFromByteArray<EntityDespawn>(data)
            MessageType.ENTITY_MOVE -> protobuf.decodeFromByteArray<EntityMove>(data)
            MessageType.ENTITY_MOVE_BATCH -> protobuf.decodeFromByteArray<EntityMoveBatch>(data)
            MessageType.BLOCK_CHANGE -> protobuf.decodeFromByteArray<BlockChange>(data)
            MessageType.BLOCK_CHANGE_BATCH -> protobuf.decodeFromByteArray<BlockChangeBatch>(data)
            MessageType.CHUNK_DATA -> protobuf.decodeFromByteArray<ChunkData>(data)
            MessageType.CHAT_MESSAGE -> protobuf.decodeFromByteArray<ChatMessage>(data)
            MessageType.SYSTEM_MESSAGE -> protobuf.decodeFromByteArray<SystemMessage>(data)
            MessageType.PLAYER_ATTACK -> protobuf.decodeFromByteArray<PlayerAttack>(data)
            MessageType.PLAYER_INTERACT_ENTITY -> protobuf.decodeFromByteArray<PlayerInteractEntity>(data)
            MessageType.ITEM_DROP -> protobuf.decodeFromByteArray<ItemDrop>(data)
            MessageType.ITEM_PICKUP -> protobuf.decodeFromByteArray<ItemPickup>(data)
            else -> throw IllegalArgumentException("Unsupported message type: $type")
        }
    }
    
    private fun deserializeJson(data: ByteArray, type: MessageType): PortalMessage {
        val jsonString = data.toString(Charsets.UTF_8)
        return when (type) {
            MessageType.NODE_ANNOUNCE -> json.decodeFromString(NodeAnnounce.serializer(), jsonString)
            MessageType.NODE_HEARTBEAT -> json.decodeFromString(NodeHeartbeat.serializer(), jsonString)
            MessageType.NODE_PING -> json.decodeFromString(NodePing.serializer(), jsonString)
            MessageType.NODE_PONG -> json.decodeFromString(NodePong.serializer(), jsonString)
            MessageType.PLAYER_JOIN -> json.decodeFromString(PlayerJoin.serializer(), jsonString)
            MessageType.PLAYER_QUIT -> json.decodeFromString(PlayerQuit.serializer(), jsonString)
            MessageType.PLAYER_POSITION -> json.decodeFromString(PlayerPosition.serializer(), jsonString)
            MessageType.PLAYER_POSITION_BATCH -> json.decodeFromString(PlayerPositionBatch.serializer(), jsonString)
            MessageType.PLAYER_ANIMATION -> json.decodeFromString(PlayerAnimation.serializer(), jsonString)
            MessageType.PLAYER_EQUIPMENT -> json.decodeFromString(PlayerEquipment.serializer(), jsonString)
            MessageType.PLAYER_VITALS -> json.decodeFromString(PlayerVitals.serializer(), jsonString)
            MessageType.PLAYER_EFFECTS -> json.decodeFromString(PlayerEffects.serializer(), jsonString)
            MessageType.ENTITY_SPAWN -> json.decodeFromString(EntitySpawn.serializer(), jsonString)
            MessageType.ENTITY_DESPAWN -> json.decodeFromString(EntityDespawn.serializer(), jsonString)
            MessageType.ENTITY_MOVE -> json.decodeFromString(EntityMove.serializer(), jsonString)
            MessageType.ENTITY_MOVE_BATCH -> json.decodeFromString(EntityMoveBatch.serializer(), jsonString)
            MessageType.BLOCK_CHANGE -> json.decodeFromString(BlockChange.serializer(), jsonString)
            MessageType.BLOCK_CHANGE_BATCH -> json.decodeFromString(BlockChangeBatch.serializer(), jsonString)
            MessageType.CHUNK_DATA -> json.decodeFromString(ChunkData.serializer(), jsonString)
            MessageType.CHAT_MESSAGE -> json.decodeFromString(ChatMessage.serializer(), jsonString)
            MessageType.SYSTEM_MESSAGE -> json.decodeFromString(SystemMessage.serializer(), jsonString)
            MessageType.PLAYER_ATTACK -> json.decodeFromString(PlayerAttack.serializer(), jsonString)
            MessageType.PLAYER_INTERACT_ENTITY -> json.decodeFromString(PlayerInteractEntity.serializer(), jsonString)
            MessageType.ITEM_DROP -> json.decodeFromString(ItemDrop.serializer(), jsonString)
            MessageType.ITEM_PICKUP -> json.decodeFromString(ItemPickup.serializer(), jsonString)
            else -> throw IllegalArgumentException("Unsupported message type: $type")
        }
    }
    
    private fun getMessageType(message: PortalMessage): MessageType {
        return when (message) {
            is NodeAnnounce -> MessageType.NODE_ANNOUNCE
            is NodeHeartbeat -> MessageType.NODE_HEARTBEAT
            is NodePing -> MessageType.NODE_PING
            is NodePong -> MessageType.NODE_PONG
            is PlayerJoin -> MessageType.PLAYER_JOIN
            is PlayerQuit -> MessageType.PLAYER_QUIT
            is PlayerPosition -> MessageType.PLAYER_POSITION
            is PlayerPositionBatch -> MessageType.PLAYER_POSITION_BATCH
            is PlayerAnimation -> MessageType.PLAYER_ANIMATION
            is PlayerEquipment -> MessageType.PLAYER_EQUIPMENT
            is PlayerVitals -> MessageType.PLAYER_VITALS
            is PlayerEffects -> MessageType.PLAYER_EFFECTS
            is EntitySpawn -> MessageType.ENTITY_SPAWN
            is EntityDespawn -> MessageType.ENTITY_DESPAWN
            is EntityMove -> MessageType.ENTITY_MOVE
            is EntityMoveBatch -> MessageType.ENTITY_MOVE_BATCH
            is BlockChange -> MessageType.BLOCK_CHANGE
            is BlockChangeBatch -> MessageType.BLOCK_CHANGE_BATCH
            is ChunkData -> MessageType.CHUNK_DATA
            is ChatMessage -> MessageType.CHAT_MESSAGE
            is SystemMessage -> MessageType.SYSTEM_MESSAGE
            is PlayerAttack -> MessageType.PLAYER_ATTACK
            is PlayerInteractEntity -> MessageType.PLAYER_INTERACT_ENTITY
            is ItemDrop -> MessageType.ITEM_DROP
            is ItemPickup -> MessageType.ITEM_PICKUP
        }
    }
    
    private fun compress(data: ByteArray): ByteArray {
        val maxCompressedLength = compressor.maxCompressedLength(data.size)
        val compressed = ByteArray(maxCompressedLength)
        val compressedLength = compressor.compress(data, 0, data.size, compressed, 0, maxCompressedLength)
        return compressed.copyOf(compressedLength)
    }
    
    private fun decompress(data: ByteArray, originalSize: Int): ByteArray {
        val restored = ByteArray(originalSize)
        decompressor.decompress(data, 0, restored, 0, originalSize)
        return restored
    }
    
    /**
     * Generate a unique message ID
     */
    fun generateMessageId(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    }
}
