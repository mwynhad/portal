package org.mwynhad.portal.node

import org.mwynhad.portal.Portal
import org.mwynhad.portal.network.MessageHandler
import org.mwynhad.portal.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * Manages information about federation nodes
 */
class NodeManager(private val plugin: Portal) : MessageHandler {
    
    // Known nodes in the federation
    private val nodes = ConcurrentHashMap<String, NodeInfo>()
    
    // Latency to each node
    private val latencies = ConcurrentHashMap<String, Long>()
    
    // Pending ping requests
    private val pendingPings = ConcurrentHashMap<String, Long>()
    
    data class NodeInfo(
        val nodeId: String,
        val nodeName: String,
        val region: String,
        val isPrimary: Boolean,
        val directHost: String,
        val directPort: Int,
        var playerCount: Int,
        var maxPlayers: Int,
        val version: String,
        var lastSeen: Long,
        var tps: Double = 20.0,
        var mspt: Double = 0.0
    )
    
    override fun handledMessageTypes(): List<Class<out PortalMessage>> = listOf(
        NodeAnnounce::class.java,
        NodeHeartbeat::class.java,
        NodePing::class.java,
        NodePong::class.java
    )
    
    override fun handleMessage(message: PortalMessage) {
        when (message) {
            is NodeAnnounce -> handleNodeAnnounce(message)
            is NodeHeartbeat -> handleNodeHeartbeat(message)
            is NodePing -> handleNodePing(message)
            is NodePong -> handleNodePong(message)
            else -> {}
        }
    }
    
    private fun handleNodeAnnounce(message: NodeAnnounce) {
        val nodeInfo = NodeInfo(
            nodeId = message.sourceNode,
            nodeName = message.nodeName,
            region = message.region,
            isPrimary = message.isPrimary,
            directHost = message.directHost,
            directPort = message.directPort,
            playerCount = message.playerCount,
            maxPlayers = message.maxPlayers,
            version = message.version,
            lastSeen = System.currentTimeMillis()
        )
        
        val isNew = nodes.put(message.sourceNode, nodeInfo) == null
        
        if (isNew) {
            plugin.logger.info("Discovered new node: ${message.nodeName} (${message.sourceNode}) in region ${message.region}")
            
            // Immediately ping the new node for latency measurement
            sendPing(message.sourceNode)
        }
    }
    
    private fun handleNodeHeartbeat(message: NodeHeartbeat) {
        nodes[message.sourceNode]?.apply {
            playerCount = message.playerCount
            tps = message.tps
            mspt = message.mspt
            lastSeen = System.currentTimeMillis()
        }
    }
    
    private fun handleNodePing(message: NodePing) {
        // Only respond if we're the target
        if (message.targetNode != Portal.nodeId) return
        
        val pong = NodePong(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            pingMessageId = message.messageId,
            originalTimestamp = message.timestamp
        )
        
        plugin.networkManager.sendToNode(message.sourceNode, pong)
    }
    
    private fun handleNodePong(message: NodePong) {
        val sentTime = pendingPings.remove(message.pingMessageId) ?: return
        val latency = System.currentTimeMillis() - sentTime
        
        latencies[message.sourceNode] = latency
        
        if (plugin.portalConfig.debug.logLatency) {
            val nodeName = nodes[message.sourceNode]?.nodeName ?: message.sourceNode
            plugin.logger.info("Latency to $nodeName: ${latency}ms")
        }
    }
    
    /**
     * Announce this node to the federation
     */
    fun announceNode() {
        val config = plugin.portalConfig
        val server = plugin.server
        
        val announce = NodeAnnounce(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            nodeName = config.nodeName,
            region = config.region,
            isPrimary = config.isPrimary,
            directHost = config.direct.bindAddress,
            directPort = config.direct.bindPort,
            playerCount = server.onlinePlayers.size,
            maxPlayers = server.maxPlayers,
            version = server.minecraftVersion
        )
        
        plugin.networkManager.broadcast(announce)
    }
    
    /**
     * Send heartbeat to all nodes
     */
    fun sendHeartbeat() {
        val server = plugin.server
        val runtime = Runtime.getRuntime()
        
        val heartbeat = NodeHeartbeat(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            playerCount = server.onlinePlayers.size,
            tps = server.tps.firstOrNull() ?: 20.0,
            mspt = server.averageTickTime,
            usedMemory = runtime.totalMemory() - runtime.freeMemory(),
            maxMemory = runtime.maxMemory()
        )
        
        plugin.networkManager.broadcast(heartbeat)
    }
    
    /**
     * Send ping to a specific node
     */
    fun sendPing(nodeId: String) {
        val messageId = MessageSerializer.generateMessageId()
        val now = System.currentTimeMillis()
        
        pendingPings[messageId] = now
        
        val ping = NodePing(
            sourceNode = Portal.nodeId,
            timestamp = now,
            messageId = messageId,
            targetNode = nodeId
        )
        
        plugin.networkManager.sendToNode(nodeId, ping)
    }
    
    /**
     * Check latencies to all nodes
     */
    suspend fun checkLatencies() = withContext(Dispatchers.Default) {
        // Clean up old pending pings (> 10 seconds old)
        val cutoff = System.currentTimeMillis() - 10000
        pendingPings.entries.removeIf { it.value < cutoff }
        
        // Clean up stale nodes (not seen in 60 seconds)
        val staleCutoff = System.currentTimeMillis() - 60000
        val staleNodes = nodes.entries.filter { it.value.lastSeen < staleCutoff }
        staleNodes.forEach { 
            nodes.remove(it.key)
            latencies.remove(it.key)
            plugin.logger.info("Node ${it.value.nodeName} (${it.key}) is stale, removing")
        }
        
        // Ping all active nodes
        nodes.keys.forEach { nodeId ->
            sendPing(nodeId)
        }
        
        // Send heartbeat
        sendHeartbeat()
    }
    
    /**
     * Get all known nodes
     */
    fun getNodes(): Map<String, NodeInfo> = nodes.toMap()
    
    /**
     * Get latency to a node
     */
    fun getLatency(nodeId: String): Long? = latencies[nodeId]
    
    /**
     * Get the primary node
     */
    fun getPrimaryNode(): NodeInfo? = nodes.values.find { it.isPrimary }
    
    /**
     * Get nodes sorted by latency
     */
    fun getNodesByLatency(): List<Pair<NodeInfo, Long>> {
        return nodes.values.mapNotNull { node ->
            latencies[node.nodeId]?.let { latency ->
                node to latency
            }
        }.sortedBy { it.second }
    }
    
    /**
     * Shutdown
     */
    fun shutdown() {
        nodes.clear()
        latencies.clear()
        pendingPings.clear()
    }
}
