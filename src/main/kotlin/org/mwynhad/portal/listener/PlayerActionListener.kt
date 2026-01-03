package org.mwynhad.portal.listener

import org.mwynhad.portal.Portal
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * Handles player action events for animation and interaction sync
 */
class PlayerActionListener(private val plugin: Portal) : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerAnimation(event: PlayerAnimationEvent) {
        val player = event.player
        
        // Broadcast animation (arm swing)
        val animationType = when (event.animationType) {
            org.bukkit.event.player.PlayerAnimationType.ARM_SWING -> 0
            else -> return
        }
        
        plugin.playerSyncManager.broadcastAnimation(player, animationType)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // Block interactions are handled by BlockEventListener
        // This handles other interactions like item use
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val entity = event.rightClicked
        
        // Don't sync interactions with virtual players (handled separately)
        if (plugin.entitySyncManager.isRemoteEntity(entity)) return
        
        val hand = when (event.hand) {
            EquipmentSlot.HAND -> 0
            EquipmentSlot.OFF_HAND -> 1
            else -> 0
        }
        
        plugin.eventSyncManager.broadcastPlayerInteractEntity(player, entity, 0, hand)
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        
        // Broadcast equipment change
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.playerSyncManager.broadcastEquipment(player, EquipmentSlot.HAND)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        
        // Broadcast both hand equipment changes
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.playerSyncManager.broadcastEquipment(player, EquipmentSlot.HAND)
            plugin.playerSyncManager.broadcastEquipment(player, EquipmentSlot.OFF_HAND)
        }, 1L)
    }
}
