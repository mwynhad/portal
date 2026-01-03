package org.mwynhad.portal.listener

import org.mwynhad.portal.Portal
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.block.BlockFormEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.block.Block

/**
 * Handles block events for world state sync
 */
class BlockEventListener(private val plugin: Portal) : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val player = event.player
        
        // Queue the block change (now air)
        queueBlockChange(block, player.uniqueId.toString())
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.block
        val player = event.player
        
        // Queue the block change
        queueBlockChange(block, player.uniqueId.toString())
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        queueBlockChange(event.block)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().forEach { block ->
            queueBlockChange(block)
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().forEach { block ->
            queueBlockChange(block)
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockFade(event: BlockFadeEvent) {
        // Ice melting, snow disappearing, etc.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            queueBlockChange(event.block)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockForm(event: BlockFormEvent) {
        // Snow forming, ice forming, etc.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            queueBlockChange(event.block)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockGrow(event: BlockGrowEvent) {
        // Crop growth, tree growth, etc.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            queueBlockChange(event.block)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockFromTo(event: BlockFromToEvent) {
        // Water/lava flow
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            queueBlockChange(event.toBlock)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockSpread(event: BlockSpreadEvent) {
        // Fire spread, grass spread, etc.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            queueBlockChange(event.block)
        }, 1L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockIgnite(event: BlockIgniteEvent) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            queueBlockChange(event.block)
        }, 1L)
    }
    
    private fun queueBlockChange(block: Block, causePlayerUuid: String? = null) {
        plugin.worldSyncManager.queueBlockChange(
            world = block.world.name,
            x = block.x,
            y = block.y,
            z = block.z,
            blockData = block.blockData,
            causePlayerUuid = causePlayerUuid
        )
    }
}
