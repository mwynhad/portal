package org.mwynhad.portal.listener

import org.mwynhad.portal.Portal
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.util.Vector

/**
 * Handles combat and damage events for PvP/PvE sync
 */
class CombatListener(private val plugin: Portal) : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val config = plugin.portalConfig.sync.events
        if (!config.enabled || !config.syncCombat) return
        
        val damager = event.damager
        val target = event.entity
        
        // Only sync if the damager is a local player
        val attackerPlayer = when {
            damager is Player -> damager
            damager is org.bukkit.entity.Projectile && damager.shooter is Player -> damager.shooter as Player
            else -> null
        }
        
        if (attackerPlayer == null) return
        
        // Don't sync if attacking a remote/virtual entity (handled by other node)
        if (plugin.entitySyncManager.isRemoteEntity(target)) return
        
        // Calculate knockback
        val knockback = calculateKnockback(attackerPlayer, target)
        
        // Broadcast the attack
        plugin.eventSyncManager.broadcastPlayerAttack(
            attacker = attackerPlayer,
            target = target,
            damage = event.finalDamage,
            knockback = knockback
        )
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        
        // Only sync player vitals
        if (entity !is Player) return
        
        // Broadcast updated vitals after damage
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (entity.isOnline) {
                plugin.playerSyncManager.broadcastVitals(entity)
            }
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
        val entity = event.entity
        
        if (entity !is Player) return
        
        // Broadcast updated vitals
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (entity.isOnline) {
                plugin.playerSyncManager.broadcastVitals(entity)
            }
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        
        // Broadcast updated vitals
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                plugin.playerSyncManager.broadcastVitals(player)
            }
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        
        // Broadcast death state
        plugin.playerSyncManager.broadcastVitals(player)
    }
    
    private fun calculateKnockback(attacker: Player, target: org.bukkit.entity.Entity): Vector {
        // Calculate knockback direction
        val direction = target.location.toVector()
            .subtract(attacker.location.toVector())
            .normalize()
        
        // Base knockback strength
        var strength = 0.4
        
        // Check for knockback enchantment
        val weapon = attacker.inventory.itemInMainHand
        val knockbackLevel = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.KNOCKBACK)
        strength += knockbackLevel * 0.5
        
        // Apply sprint bonus
        if (attacker.isSprinting) {
            strength += 0.5
        }
        
        return Vector(
            direction.x * strength,
            0.4, // Upward knockback
            direction.z * strength
        )
    }
}
