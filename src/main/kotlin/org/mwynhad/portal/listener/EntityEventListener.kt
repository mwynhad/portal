package org.mwynhad.portal.listener

import org.mwynhad.portal.Portal
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.entity.Projectile
import org.bukkit.entity.Item
import org.bukkit.entity.Mob

/**
 * Handles entity events for entity sync
 */
class EntityEventListener(private val plugin: Portal) : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntitySpawn(event: EntitySpawnEvent) {
        val entity = event.entity
        
        // Don't sync players (handled by PlayerSyncManager)
        if (entity is Player) return
        
        // Don't sync remote entities
        if (plugin.entitySyncManager.isRemoteEntity(entity)) return
        
        val config = plugin.portalConfig.sync.entities
        if (!config.enabled) return
        
        // Check if this entity type should be synced
        val syncTypes = config.syncTypes
        val shouldSync = syncTypes.contains(entity.type.name) ||
                        (config.syncMobs && entity is Mob)
        
        if (shouldSync) {
            plugin.entitySyncManager.broadcastEntitySpawn(entity)
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val entity = event.entity
        
        // Don't sync remote entities
        if (plugin.entitySyncManager.isRemoteEntity(entity)) return
        
        val config = plugin.portalConfig.sync.entities
        if (!config.enabled || !config.syncMobs) return
        
        plugin.entitySyncManager.broadcastEntitySpawn(entity)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        val projectile = event.entity
        
        // Don't sync remote entities
        if (plugin.entitySyncManager.isRemoteEntity(projectile)) return
        
        val config = plugin.portalConfig.sync.entities
        if (!config.enabled) return
        
        // Check if this projectile type should be synced
        if (config.syncTypes.contains(projectile.type.name)) {
            plugin.entitySyncManager.broadcastEntitySpawn(projectile)
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemSpawn(event: ItemSpawnEvent) {
        val item = event.entity
        
        // Don't sync remote items
        if (plugin.entitySyncManager.isRemoteEntity(item)) return
        
        val config = plugin.portalConfig.sync.entities
        if (!config.enabled) return
        
        // Items are synced via ItemDrop events in EventSyncManager
        // Only sync items that spawn naturally (not from player drops)
        if (config.syncTypes.contains("ITEM")) {
            // Check if this was recently created by a player drop
            // (player drops are handled separately)
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (item.isValid && !plugin.entitySyncManager.isRemoteEntity(item)) {
                    plugin.entitySyncManager.broadcastEntitySpawn(item)
                }
            }, 5L)
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        
        // Don't sync players
        if (entity is Player) return
        
        // Don't sync remote entities
        if (plugin.entitySyncManager.isRemoteEntity(entity)) return
        
        plugin.entitySyncManager.broadcastEntityDespawn(entity)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityRemove(event: EntityRemoveEvent) {
        val entity = event.entity
        
        // Don't sync players
        if (entity is Player) return
        
        // Don't sync remote entities
        if (plugin.entitySyncManager.isRemoteEntity(entity)) return
        
        // Only sync if it was being tracked
        plugin.entitySyncManager.broadcastEntityDespawn(entity)
    }
}
