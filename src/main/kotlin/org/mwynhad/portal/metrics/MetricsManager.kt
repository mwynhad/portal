package org.mwynhad.portal.metrics

import org.mwynhad.portal.Portal
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Metrics collection and reporting for Portal
 */
class MetricsManager(private val plugin: Portal) {
    
    // Sync latency tracking
    private val syncLatencies = ConcurrentLinkedQueue<Long>()
    private val maxLatencySamples = 100
    
    // Counters
    private val syncOperations = AtomicLong(0)
    private val failedSyncs = AtomicLong(0)
    
    // Timing
    private var lastUpdateTime = System.currentTimeMillis()
    private var messagesPerSecond = 0.0
    private var bytesPerSecond = 0.0
    
    private var lastMessageCount = 0L
    private var lastByteCount = 0L
    
    /**
     * Record a sync operation latency
     */
    fun recordSyncLatency(latencyMs: Long) {
        syncLatencies.add(latencyMs)
        syncOperations.incrementAndGet()
        
        // Keep only last N samples
        while (syncLatencies.size > maxLatencySamples) {
            syncLatencies.poll()
        }
    }
    
    /**
     * Record a failed sync operation
     */
    fun recordFailedSync() {
        failedSyncs.incrementAndGet()
    }
    
    /**
     * Get average sync latency
     */
    fun getAvgSyncLatency(): Long {
        val latencies = syncLatencies.toList()
        if (latencies.isEmpty()) return 0
        return latencies.average().toLong()
    }
    
    /**
     * Get 95th percentile latency
     */
    fun getP95Latency(): Long {
        val latencies = syncLatencies.toList().sorted()
        if (latencies.isEmpty()) return 0
        val index = (latencies.size * 0.95).toInt().coerceAtMost(latencies.size - 1)
        return latencies[index]
    }
    
    /**
     * Get total sync operations
     */
    fun getSyncOperations(): Long = syncOperations.get()
    
    /**
     * Get failed sync count
     */
    fun getFailedSyncs(): Long = failedSyncs.get()
    
    /**
     * Get messages per second
     */
    fun getMessagesPerSecond(): Double = messagesPerSecond
    
    /**
     * Get bytes per second
     */
    fun getBytesPerSecond(): Double = bytesPerSecond
    
    /**
     * Update metrics (called periodically)
     */
    fun update() {
        val now = System.currentTimeMillis()
        val elapsed = (now - lastUpdateTime) / 1000.0
        
        if (elapsed > 0) {
            val networkManager = plugin.networkManager
            
            val currentMessages = networkManager.getMessagesSent() + networkManager.getMessagesReceived()
            val currentBytes = networkManager.getBytesSent() + networkManager.getBytesReceived()
            
            messagesPerSecond = (currentMessages - lastMessageCount) / elapsed
            bytesPerSecond = (currentBytes - lastByteCount) / elapsed
            
            lastMessageCount = currentMessages
            lastByteCount = currentBytes
            lastUpdateTime = now
        }
    }
    
    /**
     * Get a summary of all metrics
     */
    fun getSummary(): MetricsSummary {
        return MetricsSummary(
            avgSyncLatency = getAvgSyncLatency(),
            p95Latency = getP95Latency(),
            syncOperations = syncOperations.get(),
            failedSyncs = failedSyncs.get(),
            messagesPerSecond = messagesPerSecond,
            bytesPerSecond = bytesPerSecond,
            connectedNodes = plugin.nodeManager.getNodes().size,
            remotePlayers = plugin.playerSyncManager.getRemotePlayers().size,
            remoteEntities = plugin.entitySyncManager.getRemoteEntities().size
        )
    }
    
    data class MetricsSummary(
        val avgSyncLatency: Long,
        val p95Latency: Long,
        val syncOperations: Long,
        val failedSyncs: Long,
        val messagesPerSecond: Double,
        val bytesPerSecond: Double,
        val connectedNodes: Int,
        val remotePlayers: Int,
        val remoteEntities: Int
    )
}
