package org.mwynhad.portal.listener

import org.mwynhad.portal.Portal
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerToggleSprintEvent

/**
 * Handles player movement events for position sync
 */
class PlayerMovementListener(private val plugin: Portal) : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        // Position sync is handled by the scheduled task in PlayerSyncManager
        // This listener is for immediate updates on significant movement
        
        val from = event.from
        val to = event.to
        
        // Only trigger immediate sync for significant movement
        if (from.world != to.world ||
            from.distanceSquared(to) > 4.0) { // More than 2 blocks
            
            // Force immediate position broadcast
            plugin.runAsync {
                plugin.playerSyncManager.broadcastLocalPlayers()
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        
        // Immediate sync on teleport
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.playerSyncManager.broadcastPlayerJoin(player)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onToggleSneak(event: PlayerToggleSneakEvent) {
        // Sneak state is included in position sync
        // This triggers an immediate update
        plugin.runAsync {
            plugin.playerSyncManager.broadcastLocalPlayers()
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onToggleSprint(event: PlayerToggleSprintEvent) {
        // Sprint state is included in position sync
        plugin.runAsync {
            plugin.playerSyncManager.broadcastLocalPlayers()
        }
    }
}
