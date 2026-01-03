package org.mwynhad.portal.network.redis

import org.mwynhad.portal.Portal
import org.mwynhad.portal.network.NetworkManager
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Redis manager for pub/sub messaging between nodes
 * Uses Lettuce for non-blocking async operations
 */
class RedisManager(
    private val plugin: Portal,
    private val networkManager: NetworkManager
) {
    
    private val config get() = plugin.portalConfig.redis
    
    private var client: RedisClient? = null
    private var connection: StatefulRedisConnection<ByteArray, ByteArray>? = null
    private var pubSubConnection: StatefulRedisPubSubConnection<ByteArray, ByteArray>? = null
    
    private var commands: RedisAsyncCommands<ByteArray, ByteArray>? = null
    private var pubSubCommands: RedisPubSubAsyncCommands<ByteArray, ByteArray>? = null
    
    companion object {
        // Channel names
        const val CHANNEL_BROADCAST = "portal:broadcast"
        const val CHANNEL_NODE_PREFIX = "portal:node:"
        
        // Key prefixes
        const val KEY_NODE_INFO = "portal:nodes:"
        const val KEY_PLAYER_NODE = "portal:player:"
    }
    
    /**
     * Connect to Redis
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        val uri = RedisURI.builder()
            .withHost(config.host)
            .withPort(config.port)
            .withDatabase(config.database)
            .withTimeout(Duration.ofSeconds(10))
            .apply {
                if (config.password.isNotEmpty()) {
                    withPassword(config.password.toCharArray())
                }
            }
            .build()
        
        client = RedisClient.create(uri)
        
        // Main connection for commands
        connection = client!!.connect(ByteArrayCodec.INSTANCE)
        commands = connection!!.async()
        
        // Pub/sub connection for subscriptions
        pubSubConnection = client!!.connectPubSub(ByteArrayCodec.INSTANCE)
        pubSubCommands = pubSubConnection!!.async()
        
        // Set up pub/sub listener
        pubSubConnection!!.addListener(object : RedisPubSubAdapter<ByteArray, ByteArray>() {
            override fun message(channel: ByteArray, message: ByteArray) {
                networkManager.onMessageReceived(message)
            }
            
            override fun message(pattern: ByteArray, channel: ByteArray, message: ByteArray) {
                networkManager.onMessageReceived(message)
            }
        })
        
        // Subscribe to broadcast channel
        pubSubCommands!!.subscribe(CHANNEL_BROADCAST.toByteArray()).get(5, TimeUnit.SECONDS)
        
        // Subscribe to this node's specific channel
        val nodeChannel = "$CHANNEL_NODE_PREFIX${Portal.nodeId}".toByteArray()
        pubSubCommands!!.subscribe(nodeChannel).get(5, TimeUnit.SECONDS)
        
        plugin.logger.info("Connected to Redis at ${config.host}:${config.port}")
    }
    
    /**
     * Publish a message to the broadcast channel
     */
    fun publish(data: ByteArray) {
        commands?.publish(CHANNEL_BROADCAST.toByteArray(), data)
    }
    
    /**
     * Publish a message to a specific node
     */
    fun publishToNode(nodeId: String, data: ByteArray) {
        val channel = "$CHANNEL_NODE_PREFIX$nodeId".toByteArray()
        commands?.publish(channel, data)
    }
    
    /**
     * Store node information
     */
    suspend fun storeNodeInfo(nodeId: String, info: Map<String, String>, ttlSeconds: Long = 30) {
        withContext(Dispatchers.IO) {
            val key = "$KEY_NODE_INFO$nodeId"
            commands?.hset(key.toByteArray(), info.mapKeys { it.key.toByteArray() }.mapValues { it.value.toByteArray() })
            commands?.expire(key.toByteArray(), ttlSeconds)
        }
    }
    
    /**
     * Get all node info
     */
    suspend fun getAllNodes(): Map<String, Map<String, String>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Map<String, String>>()
        
        val keys = commands?.keys("$KEY_NODE_INFO*".toByteArray())?.get(5, TimeUnit.SECONDS) ?: return@withContext result
        
        for (key in keys) {
            val nodeId = String(key).removePrefix(KEY_NODE_INFO)
            val info = commands?.hgetall(key)?.get(5, TimeUnit.SECONDS) ?: continue
            result[nodeId] = info.mapKeys { String(it.key) }.mapValues { String(it.value) }
        }
        
        result
    }
    
    /**
     * Store which node a player is on
     */
    fun storePlayerNode(playerUuid: String, nodeId: String) {
        val key = "$KEY_PLAYER_NODE$playerUuid"
        commands?.set(key.toByteArray(), nodeId.toByteArray())
        commands?.expire(key.toByteArray(), 300) // 5 minute TTL
    }
    
    /**
     * Get which node a player is on
     */
    suspend fun getPlayerNode(playerUuid: String): String? = withContext(Dispatchers.IO) {
        val key = "$KEY_PLAYER_NODE$playerUuid"
        commands?.get(key.toByteArray())?.get(1, TimeUnit.SECONDS)?.let { String(it) }
    }
    
    /**
     * Remove player node mapping
     */
    fun removePlayerNode(playerUuid: String) {
        val key = "$KEY_PLAYER_NODE$playerUuid"
        commands?.del(key.toByteArray())
    }
    
    /**
     * Disconnect from Redis
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            pubSubConnection?.close()
            connection?.close()
            client?.shutdown()
        } catch (e: Exception) {
            plugin.logger.warning("Error disconnecting from Redis: ${e.message}")
        }
    }
}
