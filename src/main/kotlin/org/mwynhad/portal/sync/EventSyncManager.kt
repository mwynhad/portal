package org.mwynhad.portal.sync

import org.mwynhad.portal.Portal
import org.mwynhad.portal.network.MessageHandler
import org.mwynhad.portal.protocol.*
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages synchronization of events (combat, interactions) across federation nodes
 */
class EventSyncManager(private val plugin: Portal) : MessageHandler {
    
    // Track recently processed events to avoid duplicates
    private val recentEvents = ConcurrentHashMap<String, Long>()
    
    // Track pending damage for virtual players
    private val pendingDamage = ConcurrentHashMap<UUID, DamageInfo>()
    
    data class DamageInfo(
        val attackerUuid: UUID,
        val damage: Double,
        val knockback: Vector,
        val timestamp: Long
    )
    
    override fun handledMessageTypes(): List<Class<out PortalMessage>> = listOf(
        PlayerAttack::class.java,
        PlayerInteractEntity::class.java,
        ItemDrop::class.java,
        ItemPickup::class.java
    )
    
    override fun handleMessage(message: PortalMessage) {
        when (message) {
            is PlayerAttack -> handlePlayerAttack(message)
            is PlayerInteractEntity -> handlePlayerInteractEntity(message)
            is ItemDrop -> handleItemDrop(message)
            is ItemPickup -> handleItemPickup(message)
            else -> {}
        }
    }
    
    private fun handlePlayerAttack(message: PlayerAttack) {
        // Check for duplicate
        if (recentEvents.containsKey(message.messageId)) return
        recentEvents[message.messageId] = System.currentTimeMillis()
        
        val targetUuid = UUID.fromString(message.targetUuid)
        val attackerUuid = UUID.fromString(message.attackerUuid)
        
        if (message.targetIsPlayer) {
            // Target is a player on this node
            val targetPlayer = Bukkit.getPlayer(targetUuid) ?: return
            
            plugin.server.scheduler.runTask(plugin, Runnable {
                // Apply damage
                targetPlayer.damage(message.damage)
                
                // Apply knockback
                val knockback = Vector(message.knockbackX, message.knockbackY, message.knockbackZ)
                targetPlayer.velocity = targetPlayer.velocity.add(knockback)
                
                if (plugin.portalConfig.debug.logSync) {
                    plugin.logger.info("[COMBAT] Remote player attacked ${targetPlayer.name} for ${message.damage} damage")
                }
            })
        } else {
            // Target is an entity on this node
            val entity = Bukkit.getEntity(targetUuid) ?: return
            
            plugin.server.scheduler.runTask(plugin, Runnable {
                // Apply damage to entity
                if (entity is org.bukkit.entity.Damageable) {
                    entity.damage(message.damage)
                    
                    // Apply knockback
                    val knockback = Vector(message.knockbackX, message.knockbackY, message.knockbackZ)
                    entity.velocity = entity.velocity.add(knockback)
                }
            })
        }
    }
    
    private fun handlePlayerInteractEntity(message: PlayerInteractEntity) {
        // Check for duplicate
        if (recentEvents.containsKey(message.messageId)) return
        recentEvents[message.messageId] = System.currentTimeMillis()
        
        val entityUuid = UUID.fromString(message.entityUuid)
        val entity = Bukkit.getEntity(entityUuid) ?: return
        
        plugin.server.scheduler.runTask(plugin, Runnable {
            when (message.action) {
                0 -> {
                    // Interact - trigger interaction logic
                    // This could open inventories, trigger villager trades, etc.
                }
                1 -> {
                    // Attack - already handled by PlayerAttack
                }
                2 -> {
                    // Interact at specific position
                }
            }
        })
    }
    
    private fun handleItemDrop(message: ItemDrop) {
        // Check for duplicate
        if (recentEvents.containsKey(message.messageId)) return
        recentEvents[message.messageId] = System.currentTimeMillis()
        
        val world = Bukkit.getWorld(message.world) ?: return
        
        plugin.server.scheduler.runTask(plugin, Runnable {
            try {
                val item = ItemStack.deserializeBytes(message.itemData)
                val location = org.bukkit.Location(world, message.x, message.y, message.z)
                
                val droppedItem = world.dropItem(location, item)
                droppedItem.velocity = Vector(message.velocityX, message.velocityY, message.velocityZ)
                
                // Tag as remote item
                droppedItem.persistentDataContainer.set(
                    org.bukkit.NamespacedKey(plugin, "remote"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    message.sourceNode
                )
                
                if (plugin.portalConfig.debug.logSync) {
                    plugin.logger.info("[SYNC] Remote item dropped: ${item.type}")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to spawn remote dropped item: ${e.message}")
            }
        })
    }
    
    private fun handleItemPickup(message: ItemPickup) {
        // Check for duplicate
        if (recentEvents.containsKey(message.messageId)) return
        recentEvents[message.messageId] = System.currentTimeMillis()
        
        val itemEntityUuid = UUID.fromString(message.itemEntityUuid)
        
        plugin.server.scheduler.runTask(plugin, Runnable {
            // Remove the item entity
            val entity = Bukkit.getEntity(itemEntityUuid)
            entity?.remove()
        })
    }
    
    /**
     * Broadcast a player attack event
     */
    fun broadcastPlayerAttack(attacker: Player, target: Entity, damage: Double, knockback: Vector) {
        val targetIsPlayer = target is Player
        
        val weaponData = attacker.inventory.itemInMainHand.let { item ->
            if (item.type.isAir) null else item.serializeAsBytes()
        }
        
        val attack = PlayerAttack(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            attackerUuid = attacker.uniqueId.toString(),
            targetUuid = target.uniqueId.toString(),
            targetIsPlayer = targetIsPlayer,
            damage = damage,
            knockbackX = knockback.x,
            knockbackY = knockback.y,
            knockbackZ = knockback.z,
            weaponData = weaponData
        )
        
        recentEvents[attack.messageId] = System.currentTimeMillis()
        plugin.networkManager.broadcast(attack)
    }
    
    /**
     * Broadcast a player interact entity event
     */
    fun broadcastPlayerInteractEntity(player: Player, entity: Entity, action: Int, hand: Int = 0) {
        val interact = PlayerInteractEntity(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            playerUuid = player.uniqueId.toString(),
            entityUuid = entity.uniqueId.toString(),
            action = action,
            hand = hand,
            targetX = 0f,
            targetY = 0f,
            targetZ = 0f
        )
        
        recentEvents[interact.messageId] = System.currentTimeMillis()
        plugin.networkManager.broadcast(interact)
    }
    
    /**
     * Broadcast an item drop event
     */
    fun broadcastItemDrop(player: Player, item: org.bukkit.entity.Item) {
        val loc = item.location
        val vel = item.velocity
        
        val drop = ItemDrop(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            playerUuid = player.uniqueId.toString(),
            itemEntityUuid = item.uniqueId.toString(),
            world = item.world.name,
            x = loc.x,
            y = loc.y,
            z = loc.z,
            itemData = item.itemStack.serializeAsBytes(),
            velocityX = vel.x,
            velocityY = vel.y,
            velocityZ = vel.z
        )
        
        recentEvents[drop.messageId] = System.currentTimeMillis()
        plugin.networkManager.broadcast(drop)
    }
    
    /**
     * Broadcast an item pickup event
     */
    fun broadcastItemPickup(player: Player, item: org.bukkit.entity.Item) {
        val pickup = ItemPickup(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = MessageSerializer.generateMessageId(),
            playerUuid = player.uniqueId.toString(),
            itemEntityUuid = item.uniqueId.toString()
        )
        
        recentEvents[pickup.messageId] = System.currentTimeMillis()
        plugin.networkManager.broadcast(pickup)
    }
    
    /**
     * Cleanup old event entries
     */
    fun cleanup() {
        val cutoff = System.currentTimeMillis() - 10000
        recentEvents.entries.removeIf { it.value < cutoff }
    }
}
