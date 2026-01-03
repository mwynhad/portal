package org.mwynhad.portal.network

import org.mwynhad.portal.Portal
import org.mwynhad.portal.config.PortalConfig
import org.mwynhad.portal.network.direct.DirectConnectionManager
import org.mwynhad.portal.network.redis.RedisManager
import org.mwynhad.portal.protocol.PortalMessage
import org.mwynhad.portal.protocol.MessageSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level

/**
 * Central network manager that handles all inter-node communication
 * Supports both Redis pub/sub and direct TCP connections for lowest latency
 */
class NetworkManager(private val plugin: Portal) {
    
    private val config: PortalConfig get() = plugin.portalConfig
    
    // Network transports
    private var redisManager: RedisManager? = null
    private var directManager: DirectConnectionManager? = null
    
    // Message handlers
    private val handlers = ConcurrentHashMap<Class<out PortalMessage>, MutableList<MessageHandler>>()
    
    // Message batching for efficiency
    private val outgoingBatch = Channel<PortalMessage>(Channel.BUFFERED)
    private var batchJob: Job? = null
    
    // Metrics
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val bytessSent = AtomicLong(0)
    private val bytesReceived = AtomicLong(0)
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Initialize Redis connection
     */
    suspend fun initRedis() {
        if (!config.redis.enabled) return
        
        redisManager = RedisManager(plugin, this).apply {
            connect()
        }
    }
    
    /**
     * Initialize direct connection server
     */
    suspend fun initDirect() {
        if (!config.direct.enabled) return
        
        directManager = DirectConnectionManager(plugin, this).apply {
            startServer()
        }
    }
    
    /**
     * Connect to a peer node directly
     */
    suspend fun connectToPeer(host: String, port: Int) {
        directManager?.connectToPeer(host, port)
    }
    
    /**
     * Register a message handler
     */
    fun registerHandler(handler: MessageHandler) {
        handler.handledMessageTypes().forEach { messageClass ->
            handlers.getOrPut(messageClass) { mutableListOf() }.add(handler)
        }
    }
    
    /**
     * Broadcast a message to all nodes
     */
    fun broadcast(message: PortalMessage) {
        if (config.protocol.batchMessages) {
            scope.launch {
                outgoingBatch.send(message)
            }
        } else {
            sendImmediate(message)
        }
    }
    
    /**
     * Send a message immediately without batching
     */
    fun sendImmediate(message: PortalMessage) {
        scope.launch {
            try {
                val data = MessageSerializer.serialize(
                    message,
                    config.protocol.useBinary,
                    config.protocol.compressionThreshold
                )
                
                messagesSent.incrementAndGet()
                bytessSent.addAndGet(data.size.toLong())
                
                // Prefer direct connections for lowest latency
                val sentDirect = directManager?.broadcast(data) ?: false
                
                // Fall back to Redis if no direct connections or as redundancy
                if (!sentDirect || config.redis.enabled) {
                    redisManager?.publish(data)
                }
                
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to send message", e)
            }
        }
    }
    
    /**
     * Send a message to a specific node
     */
    fun sendToNode(nodeId: String, message: PortalMessage) {
        scope.launch {
            try {
                val data = MessageSerializer.serialize(
                    message,
                    config.protocol.useBinary,
                    config.protocol.compressionThreshold
                )
                
                messagesSent.incrementAndGet()
                bytessSent.addAndGet(data.size.toLong())
                
                // Try direct connection first
                val sentDirect = directManager?.sendToNode(nodeId, data) ?: false
                
                if (!sentDirect) {
                    // Fall back to Redis targeted publish
                    redisManager?.publishToNode(nodeId, data)
                }
                
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to send message to node $nodeId", e)
            }
        }
    }
    
    /**
     * Called when a message is received from the network
     */
    fun onMessageReceived(data: ByteArray) {
        scope.launch {
            try {
                messagesReceived.incrementAndGet()
                bytesReceived.addAndGet(data.size.toLong())
                
                val message = MessageSerializer.deserialize(data)
                
                // Don't process messages from ourselves
                if (message.sourceNode == Portal.nodeId) {
                    return@launch
                }
                
                if (config.debug.logPackets) {
                    plugin.logger.info("[NET] Received ${message::class.simpleName} from ${message.sourceNode}")
                }
                
                // Dispatch to handlers
                dispatchMessage(message)
                
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to process received message", e)
            }
        }
    }
    
    private fun dispatchMessage(message: PortalMessage) {
        val messageHandlers = handlers[message::class.java] ?: return
        
        messageHandlers.forEach { handler ->
            try {
                handler.handleMessage(message)
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Handler failed to process message", e)
            }
        }
    }
    
    /**
     * Start the message batching processor
     */
    fun startBatching() {
        if (!config.protocol.batchMessages) return
        
        batchJob = scope.launch {
            val batch = mutableListOf<PortalMessage>()
            val batchInterval = config.protocol.batchIntervalMs.toLong()
            val maxBatchSize = config.protocol.maxBatchSize
            
            while (isActive) {
                // Collect messages for the batch interval
                val startTime = System.currentTimeMillis()
                
                while (System.currentTimeMillis() - startTime < batchInterval && batch.size < maxBatchSize) {
                    val message = outgoingBatch.tryReceive().getOrNull() ?: break
                    batch.add(message)
                }
                
                if (batch.isNotEmpty()) {
                    // Group messages by type and send
                    batch.forEach { sendImmediate(it) }
                    batch.clear()
                }
                
                delay(1) // Prevent tight loop
            }
        }
    }
    
    /**
     * Shutdown the network manager
     */
    suspend fun shutdown() {
        batchJob?.cancel()
        scope.cancel()
        
        directManager?.shutdown()
        redisManager?.disconnect()
    }
    
    // Metrics getters
    fun getMessagesSent(): Long = messagesSent.get()
    fun getMessagesReceived(): Long = messagesReceived.get()
    fun getBytesSent(): Long = bytessSent.get()
    fun getBytesReceived(): Long = bytesReceived.get()
    
    fun getConnectedNodeCount(): Int {
        return (directManager?.getConnectedNodeCount() ?: 0)
    }
}

/**
 * Interface for message handlers
 */
interface MessageHandler {
    fun handledMessageTypes(): List<Class<out PortalMessage>>
    fun handleMessage(message: PortalMessage)
}
