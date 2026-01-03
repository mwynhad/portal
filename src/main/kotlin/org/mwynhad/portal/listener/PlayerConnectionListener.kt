package org.mwynhad.portal.listener

import org.mwynhad.portal.Portal
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent

/**
 * Handles player connection events for federation sync
 */
class PlayerConnectionListener(private val plugin: Portal) : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        
        // Broadcast this player's join to the federation
        plugin.playerSyncManager.broadcastPlayerJoin(player)
        
        // Spawn all virtual players for this player
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.virtualPlayerManager.spawnAllForPlayer(player)
        }, 10L) // Slight delay to ensure player is fully loaded
        
        plugin.logger.info("Player ${player.name} joined, syncing to federation")
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        
        // Broadcast this player's quit to the federation
        plugin.playerSyncManager.broadcastPlayerQuit(player)
        
        plugin.logger.info("Player ${player.name} quit, notifying federation")
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        
        // Re-broadcast player state after respawn
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.playerSyncManager.broadcastPlayerJoin(player)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        
        // Broadcast world change
        plugin.playerSyncManager.broadcastPlayerJoin(player)
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        val player = event.player
        
        // Broadcast gamemode change
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.playerSyncManager.broadcastPlayerJoin(player)
        }, 1L)
    }
}
