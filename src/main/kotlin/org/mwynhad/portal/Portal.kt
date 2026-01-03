package org.mwynhad.portal

import org.mwynhad.portal.command.PortalCommand
import org.mwynhad.portal.command.NodeCommand
import org.mwynhad.portal.command.SyncCommand
import org.mwynhad.portal.config.PortalConfig
import org.mwynhad.portal.listener.*
import org.mwynhad.portal.network.NetworkManager
import org.mwynhad.portal.network.direct.DirectConnectionManager
import org.mwynhad.portal.network.redis.RedisManager
import org.mwynhad.portal.node.NodeManager
import org.mwynhad.portal.sync.*
import org.mwynhad.portal.virtual.VirtualPlayerManager
import org.mwynhad.portal.metrics.MetricsManager
import kotlinx.coroutines.*
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.logging.Level

/**
 * Portal - CDN-style distributed Minecraft server federation
 * 
 * This plugin enables multiple Minecraft server instances to act as one,
 * with all players able to see and interact with each other regardless
 * of which instance they're connected to.
 */
class Portal : JavaPlugin() {
    
    companion object {
        lateinit var instance: Portal
            private set
        
        val nodeId: String by lazy {
            instance.config.getString("node-id")?.takeIf { it.isNotEmpty() }
                ?: UUID.randomUUID().toString().substring(0, 8).also {
                    instance.config.set("node-id", it)
                    instance.saveConfig()
                }
        }
    }
    
    // Core managers
    lateinit var portalConfig: PortalConfig
        private set
    lateinit var networkManager: NetworkManager
        private set
    lateinit var nodeManager: NodeManager
        private set
    lateinit var virtualPlayerManager: VirtualPlayerManager
        private set
    lateinit var metricsManager: MetricsManager
        private set
    
    // Sync managers
    lateinit var playerSyncManager: PlayerSyncManager
        private set
    lateinit var entitySyncManager: EntitySyncManager
        private set
    lateinit var worldSyncManager: WorldSyncManager
        private set
    lateinit var chatSyncManager: ChatSyncManager
        private set
    lateinit var eventSyncManager: EventSyncManager
        private set
    
    // Coroutine scope for async operations
    private val pluginScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("Portal")
    )
    
    override fun onEnable() {
        instance = this
        
        logger.info("╔══════════════════════════════════════╗")
        logger.info("║         Portal v${description.version}              ║")
        logger.info("║  CDN-Style Distributed MC Servers    ║")
        logger.info("╚══════════════════════════════════════╝")
        
        try {
            // Load configuration
            saveDefaultConfig()
            portalConfig = PortalConfig(config)
            
            logger.info("Node ID: $nodeId")
            logger.info("Node Name: ${portalConfig.nodeName}")
            logger.info("Region: ${portalConfig.region}")
            logger.info("Primary Node: ${portalConfig.isPrimary}")
            
            // Initialize managers
            initializeManagers()
            
            // Register listeners
            registerListeners()
            
            // Register commands
            registerCommands()
            
            // Start network connections
            pluginScope.launch {
                startNetworking()
            }
            
            // Start sync tasks
            startSyncTasks()
            
            logger.info("Portal enabled successfully!")
            
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to enable Portal", e)
            server.pluginManager.disablePlugin(this)
        }
    }
    
    override fun onDisable() {
        logger.info("Disabling Portal...")
        
        // Cancel all coroutines
        pluginScope.cancel()
        
        // Cleanup managers
        runBlocking {
            try {
                networkManager.shutdown()
                virtualPlayerManager.cleanup()
                nodeManager.shutdown()
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error during shutdown", e)
            }
        }
        
        logger.info("Portal disabled.")
    }
    
    private fun initializeManagers() {
        // Core infrastructure
        metricsManager = MetricsManager(this)
        nodeManager = NodeManager(this)
        networkManager = NetworkManager(this)
        virtualPlayerManager = VirtualPlayerManager(this)
        
        // Sync managers
        playerSyncManager = PlayerSyncManager(this)
        entitySyncManager = EntitySyncManager(this)
        worldSyncManager = WorldSyncManager(this)
        chatSyncManager = ChatSyncManager(this)
        eventSyncManager = EventSyncManager(this)
        
        // Wire up network message handlers
        networkManager.registerHandler(playerSyncManager)
        networkManager.registerHandler(entitySyncManager)
        networkManager.registerHandler(worldSyncManager)
        networkManager.registerHandler(chatSyncManager)
        networkManager.registerHandler(eventSyncManager)
        networkManager.registerHandler(nodeManager)
    }
    
    private fun registerListeners() {
        val pm = server.pluginManager
        
        // Player events
        pm.registerEvents(PlayerConnectionListener(this), this)
        pm.registerEvents(PlayerMovementListener(this), this)
        pm.registerEvents(PlayerActionListener(this), this)
        pm.registerEvents(PlayerInventoryListener(this), this)
        
        // World events
        pm.registerEvents(BlockEventListener(this), this)
        pm.registerEvents(EntityEventListener(this), this)
        
        // Chat events
        pm.registerEvents(ChatListener(this), this)
        
        // Combat events
        pm.registerEvents(CombatListener(this), this)
    }
    
    private fun registerCommands() {
        getCommand("portal")?.setExecutor(PortalCommand(this))
        getCommand("pnode")?.setExecutor(NodeCommand(this))
        getCommand("psync")?.setExecutor(SyncCommand(this))
    }
    
    private suspend fun startNetworking() {
        try {
            // Initialize Redis if enabled
            if (portalConfig.redis.enabled) {
                logger.info("Connecting to Redis...")
                networkManager.initRedis()
                logger.info("Redis connected!")
            }
            
            // Initialize direct connections if enabled
            if (portalConfig.direct.enabled) {
                logger.info("Starting direct connection server on port ${portalConfig.direct.bindPort}...")
                networkManager.initDirect()
                logger.info("Direct connection server started!")
                
                // Connect to known peers
                portalConfig.direct.peers.forEach { peer ->
                    logger.info("Connecting to peer: ${peer.host}:${peer.port}")
                    networkManager.connectToPeer(peer.host, peer.port)
                }
            }
            
            // Announce this node
            nodeManager.announceNode()
            
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to start networking", e)
            throw e
        }
    }
    
    private fun startSyncTasks() {
        // Player position sync (highest frequency)
        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            playerSyncManager.broadcastLocalPlayers()
        }, 1L, portalConfig.sync.players.positionInterval.toLong())
        
        // Entity sync
        if (portalConfig.sync.entities.enabled) {
            server.scheduler.runTaskTimerAsynchronously(this, Runnable {
                entitySyncManager.broadcastLocalEntities()
            }, 2L, portalConfig.sync.entities.syncInterval.toLong())
        }
        
        // Latency checks
        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            pluginScope.launch {
                nodeManager.checkLatencies()
            }
        }, 100L, (portalConfig.performance.latencyCheckInterval * 20).toLong())
        
        // Metrics update
        if (portalConfig.performance.enableMetrics) {
            server.scheduler.runTaskTimerAsynchronously(this, Runnable {
                metricsManager.update()
            }, 20L, 20L)
        }
    }
    
    fun runAsync(block: suspend CoroutineScope.() -> Unit) {
        pluginScope.launch(block = block)
    }
}
