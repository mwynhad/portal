package org.mwynhad.portal.config

import org.bukkit.configuration.file.FileConfiguration

/**
 * Configuration wrapper for Portal
 */
class PortalConfig(config: FileConfiguration) {
    
    val nodeId: String = config.getString("node-id") ?: ""
    val nodeName: String = config.getString("node-name") ?: "unnamed-node"
    val region: String = config.getString("region") ?: "default"
    val isPrimary: Boolean = config.getBoolean("is-primary", false)
    
    val redis = RedisConfig(config)
    val direct = DirectConfig(config)
    val protocol = ProtocolConfig(config)
    val sync = SyncConfig(config)
    val virtualPlayers = VirtualPlayersConfig(config)
    val performance = PerformanceConfig(config)
    val debug = DebugConfig(config)
    
    class RedisConfig(config: FileConfiguration) {
        val enabled: Boolean = config.getBoolean("network.redis.enabled", true)
        val host: String = config.getString("network.redis.host") ?: "localhost"
        val port: Int = config.getInt("network.redis.port", 6379)
        val password: String = config.getString("network.redis.password") ?: ""
        val database: Int = config.getInt("network.redis.database", 0)
        val cluster: Boolean = config.getBoolean("network.redis.cluster", false)
        val clusterNodes: List<String> = config.getStringList("network.redis.cluster-nodes")
        
        val poolMinIdle: Int = config.getInt("network.redis.pool.min-idle", 4)
        val poolMaxIdle: Int = config.getInt("network.redis.pool.max-idle", 8)
        val poolMaxTotal: Int = config.getInt("network.redis.pool.max-total", 16)
    }
    
    class DirectConfig(config: FileConfiguration) {
        val enabled: Boolean = config.getBoolean("network.direct.enabled", true)
        val bindPort: Int = config.getInt("network.direct.bind-port", 25580)
        val bindAddress: String = config.getString("network.direct.bind-address") ?: "0.0.0.0"
        val peers: List<PeerConfig> = config.getMapList("network.direct.peers").map {
            PeerConfig(
                host = it["host"] as? String ?: "",
                port = it["port"] as? Int ?: 25580
            )
        }
    }
    
    data class PeerConfig(
        val host: String,
        val port: Int
    )
    
    class ProtocolConfig(config: FileConfiguration) {
        val compressionThreshold: Int = config.getInt("network.protocol.compression-threshold", 256)
        val useBinary: Boolean = config.getBoolean("network.protocol.use-binary", true)
        val batchMessages: Boolean = config.getBoolean("network.protocol.batch-messages", true)
        val batchIntervalMs: Int = config.getInt("network.protocol.batch-interval-ms", 5)
        val maxBatchSize: Int = config.getInt("network.protocol.max-batch-size", 100)
    }
    
    class SyncConfig(config: FileConfiguration) {
        val players = PlayerSyncConfig(config)
        val entities = EntitySyncConfig(config)
        val world = WorldSyncConfig(config)
        val chat = ChatSyncConfig(config)
        val events = EventSyncConfig(config)
    }
    
    class PlayerSyncConfig(config: FileConfiguration) {
        val positionInterval: Int = config.getInt("sync.players.position-interval", 1)
        val interpolationBuffer: Int = config.getInt("sync.players.interpolation-buffer", 3)
        val syncInventory: Boolean = config.getBoolean("sync.players.sync-inventory", true)
        val syncVitals: Boolean = config.getBoolean("sync.players.sync-vitals", true)
        val syncEffects: Boolean = config.getBoolean("sync.players.sync-effects", true)
        val syncGamemode: Boolean = config.getBoolean("sync.players.sync-gamemode", true)
    }
    
    class EntitySyncConfig(config: FileConfiguration) {
        val enabled: Boolean = config.getBoolean("sync.entities.enabled", true)
        val syncInterval: Int = config.getInt("sync.entities.sync-interval", 2)
        val syncTypes: List<String> = config.getStringList("sync.entities.sync-types")
        val syncMobs: Boolean = config.getBoolean("sync.entities.sync-mobs", true)
        val syncRadius: Int = config.getInt("sync.entities.sync-radius", 64)
    }
    
    class WorldSyncConfig(config: FileConfiguration) {
        val enabled: Boolean = config.getBoolean("sync.world.enabled", true)
        val immediateBlockSync: Boolean = config.getBoolean("sync.world.immediate-block-sync", true)
        val batchBlockUpdates: Boolean = config.getBoolean("sync.world.batch-block-updates", true)
        val batchIntervalMs: Int = config.getInt("sync.world.batch-interval-ms", 50)
        val syncChunksOnTeleport: Boolean = config.getBoolean("sync.world.sync-chunks-on-teleport", true)
        val maxChunksPerTick: Int = config.getInt("sync.world.max-chunks-per-tick", 4)
    }
    
    class ChatSyncConfig(config: FileConfiguration) {
        val enabled: Boolean = config.getBoolean("sync.chat.enabled", true)
        val showNodePrefix: Boolean = config.getBoolean("sync.chat.show-node-prefix", false)
    }
    
    class EventSyncConfig(config: FileConfiguration) {
        val enabled: Boolean = config.getBoolean("sync.events.enabled", true)
        val syncCombat: Boolean = config.getBoolean("sync.events.sync-combat", true)
        val syncInteractions: Boolean = config.getBoolean("sync.events.sync-interactions", true)
        val syncContainers: Boolean = config.getBoolean("sync.events.sync-containers", true)
    }
    
    class VirtualPlayersConfig(config: FileConfiguration) {
        val useNpc: Boolean = config.getBoolean("virtual-players.use-npc", true)
        val syncSkins: Boolean = config.getBoolean("virtual-players.sync-skins", true)
        val showInTablist: Boolean = config.getBoolean("virtual-players.show-in-tablist", true)
        val showNametag: Boolean = config.getBoolean("virtual-players.show-nametag", true)
        val enableCollision: Boolean = config.getBoolean("virtual-players.enable-collision", true)
    }
    
    class PerformanceConfig(config: FileConfiguration) {
        val networkThreads: Int = config.getInt("performance.network-threads", 4)
        val syncThreads: Int = config.getInt("performance.sync-threads", 2)
        val maxPendingMessages: Int = config.getInt("performance.max-pending-messages", 10000)
        val enableMetrics: Boolean = config.getBoolean("performance.enable-metrics", true)
        val latencyCheckInterval: Int = config.getInt("performance.latency-check-interval", 5)
    }
    
    class DebugConfig(config: FileConfiguration) {
        val enabled: Boolean = config.getBoolean("debug.enabled", false)
        val logPackets: Boolean = config.getBoolean("debug.log-packets", false)
        val logSync: Boolean = config.getBoolean("debug.log-sync", false)
        val logLatency: Boolean = config.getBoolean("debug.log-latency", true)
    }
}
