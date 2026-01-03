package org.mwynhad.portal.command

import org.mwynhad.portal.Portal
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Sync management command handler
 */
class SyncCommand(private val plugin: Portal) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("portal.admin.sync")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return true
        }
        
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /fsync <players|entities|world|all>", NamedTextColor.YELLOW))
            return true
        }
        
        when (args[0].lowercase()) {
            "players" -> forceSyncPlayers(sender)
            "entities" -> forceSyncEntities(sender)
            "world" -> forceSyncWorld(sender)
            "all" -> {
                forceSyncPlayers(sender)
                forceSyncEntities(sender)
                forceSyncWorld(sender)
            }
            "status" -> showSyncStatus(sender)
            else -> {
                sender.sendMessage(Component.text("Unknown sync type. Use: players, entities, world, all", NamedTextColor.RED))
            }
        }
        
        return true
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("players", "entities", "world", "all", "status")
                .filter { it.startsWith(args[0].lowercase()) }
            else -> emptyList()
        }
    }
    
    private fun forceSyncPlayers(sender: CommandSender) {
        sender.sendMessage(Component.text("Force syncing all players...", NamedTextColor.GRAY))
        
        // Re-broadcast all local players
        plugin.server.onlinePlayers.forEach { player ->
            plugin.playerSyncManager.broadcastPlayerJoin(player)
        }
        
        val count = plugin.server.onlinePlayers.size
        sender.sendMessage(Component.text("Synced $count local players.", NamedTextColor.GREEN))
    }
    
    private fun forceSyncEntities(sender: CommandSender) {
        sender.sendMessage(Component.text("Force syncing entities...", NamedTextColor.GRAY))
        
        plugin.entitySyncManager.broadcastLocalEntities()
        
        sender.sendMessage(Component.text("Entity sync triggered.", NamedTextColor.GREEN))
    }
    
    private fun forceSyncWorld(sender: CommandSender) {
        sender.sendMessage(Component.text("Force syncing world changes...", NamedTextColor.GRAY))
        
        // Send any pending block changes
        plugin.worldSyncManager.sendBatch()
        
        sender.sendMessage(Component.text("World sync triggered.", NamedTextColor.GREEN))
    }
    
    private fun showSyncStatus(sender: CommandSender) {
        val remotePlayers = plugin.playerSyncManager.getRemotePlayers()
        val remoteEntities = plugin.entitySyncManager.getRemoteEntities()
        val virtualPlayers = plugin.virtualPlayerManager.getVirtualPlayers()
        
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("═══ Sync Status ═══", NamedTextColor.GOLD))
        
        sender.sendMessage(
            Component.text("Remote Players: ", NamedTextColor.GRAY)
                .append(Component.text("${remotePlayers.size}", NamedTextColor.WHITE))
        )
        
        if (remotePlayers.isNotEmpty()) {
            remotePlayers.values.take(5).forEach { state ->
                val age = (System.currentTimeMillis() - state.lastUpdate) / 1000.0
                sender.sendMessage(
                    Component.text("  • ${state.name}", NamedTextColor.WHITE)
                        .append(Component.text(" from ${state.sourceNode}", NamedTextColor.DARK_GRAY))
                        .append(Component.text(" (${String.format("%.1f", age)}s ago)", NamedTextColor.GRAY))
                )
            }
            if (remotePlayers.size > 5) {
                sender.sendMessage(Component.text("  ... and ${remotePlayers.size - 5} more", NamedTextColor.DARK_GRAY))
            }
        }
        
        sender.sendMessage(
            Component.text("Remote Entities: ", NamedTextColor.GRAY)
                .append(Component.text("${remoteEntities.size}", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Virtual Players: ", NamedTextColor.GRAY)
                .append(Component.text("${virtualPlayers.size}", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(Component.empty())
    }
}
