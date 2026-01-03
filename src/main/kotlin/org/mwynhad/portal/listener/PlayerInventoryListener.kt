package org.mwynhad.portal.listener

import org.mwynhad.portal.Portal
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot

/**
 * Handles player inventory events for equipment and item sync
 */
class PlayerInventoryListener(private val plugin: Portal) : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        
        // Check if equipment slots were affected
        val slot = event.slot
        
        // Armor slots (5-8), offhand (40), or hotbar (0-8)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            // Broadcast all visible equipment
            broadcastAllEquipment(player)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        
        // Refresh equipment on inventory close
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            broadcastAllEquipment(player)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        val item = event.itemDrop
        
        // Broadcast item drop
        plugin.eventSyncManager.broadcastItemDrop(player, item)
        
        // Update main hand equipment
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.playerSyncManager.broadcastEquipment(player, EquipmentSlot.HAND)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val item = event.item
        
        // Only broadcast if item is local (not from another node)
        if (!plugin.entitySyncManager.isRemoteEntity(item)) {
            plugin.eventSyncManager.broadcastItemPickup(player, item)
        }
        
        // Update equipment after pickup
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.playerSyncManager.broadcastEquipment(player, EquipmentSlot.HAND)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onConsumeItem(event: PlayerItemConsumeEvent) {
        val player = event.player
        
        // Update equipment after consuming
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.playerSyncManager.broadcastEquipment(player, EquipmentSlot.HAND)
            plugin.playerSyncManager.broadcastVitals(player)
        }, 1L)
    }
    
    private fun broadcastAllEquipment(player: Player) {
        EquipmentSlot.values().forEach { slot ->
            when (slot) {
                EquipmentSlot.HAND,
                EquipmentSlot.OFF_HAND,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET -> {
                    plugin.playerSyncManager.broadcastEquipment(player, slot)
                }
                else -> {}
            }
        }
    }
}
