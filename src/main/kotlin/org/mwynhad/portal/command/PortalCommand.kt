package org.mwynhad.portal.command

import org.mwynhad.portal.Portal
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Main command handler for Portal
 */
class PortalCommand(private val plugin: Portal) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "status" -> showStatus(sender)
            "info" -> showInfo(sender)
            "stats" -> showStats(sender)
            "debug" -> toggleDebug(sender, args)
            "reload" -> reloadConfig(sender)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage(Component.text("Unknown subcommand. Use /portal help", NamedTextColor.RED))
            }
        }
        
        return true
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("status", "info", "stats", "debug", "reload", "help")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "debug" -> listOf("on", "off", "packets", "sync", "latency")
                    .filter { it.startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
    
    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(Component.empty())
        sender.sendMessage(
            Component.text("═══ Portal Help ═══", NamedTextColor.GOLD, TextDecoration.BOLD)
        )
        sender.sendMessage(Component.text("/portal status", NamedTextColor.YELLOW)
            .append(Component.text(" - Show federation status", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/portal info", NamedTextColor.YELLOW)
            .append(Component.text(" - Show node information", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/portal stats", NamedTextColor.YELLOW)
            .append(Component.text(" - Show network statistics", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/portal debug <option>", NamedTextColor.YELLOW)
            .append(Component.text(" - Toggle debug options", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/portal reload", NamedTextColor.YELLOW)
            .append(Component.text(" - Reload configuration", NamedTextColor.GRAY)))
        sender.sendMessage(Component.empty())
    }
    
    private fun showStatus(sender: CommandSender) {
        val nodes = plugin.nodeManager.getNodes()
        val remotePlayers = plugin.playerSyncManager.getRemotePlayers()
        val virtualPlayers = plugin.virtualPlayerManager.getVirtualPlayers()
        
        sender.sendMessage(Component.empty())
        sender.sendMessage(
            Component.text("═══ Federation Status ═══", NamedTextColor.GOLD, TextDecoration.BOLD)
        )
        
        // This node
        sender.sendMessage(
            Component.text("This Node: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.portalConfig.nodeName, NamedTextColor.GREEN))
                .append(Component.text(" (${Portal.nodeId})", NamedTextColor.DARK_GRAY))
        )
        
        sender.sendMessage(
            Component.text("Region: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.portalConfig.region, NamedTextColor.AQUA))
        )
        
        sender.sendMessage(
            Component.text("Primary: ", NamedTextColor.GRAY)
                .append(Component.text(
                    if (plugin.portalConfig.isPrimary) "Yes" else "No",
                    if (plugin.portalConfig.isPrimary) NamedTextColor.GREEN else NamedTextColor.YELLOW
                ))
        )
        
        sender.sendMessage(Component.empty())
        
        // Connected nodes
        sender.sendMessage(
            Component.text("Connected Nodes: ", NamedTextColor.GRAY)
                .append(Component.text("${nodes.size}", NamedTextColor.WHITE))
        )
        
        nodes.values.forEach { node ->
            val latency = plugin.nodeManager.getLatency(node.nodeId)
            val latencyText = latency?.let { "${it}ms" } ?: "?"
            val latencyColor = when {
                latency == null -> NamedTextColor.GRAY
                latency < 50 -> NamedTextColor.GREEN
                latency < 100 -> NamedTextColor.YELLOW
                else -> NamedTextColor.RED
            }
            
            sender.sendMessage(
                Component.text("  • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(node.nodeName, NamedTextColor.WHITE))
                    .append(Component.text(" [${node.region}]", NamedTextColor.DARK_AQUA))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(latencyText, latencyColor))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text("${node.playerCount} players", NamedTextColor.GREEN))
            )
        }
        
        sender.sendMessage(Component.empty())
        
        // Remote players
        sender.sendMessage(
            Component.text("Remote Players: ", NamedTextColor.GRAY)
                .append(Component.text("${remotePlayers.size}", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Virtual Entities: ", NamedTextColor.GRAY)
                .append(Component.text("${virtualPlayers.size}", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(Component.empty())
    }
    
    private fun showInfo(sender: CommandSender) {
        sender.sendMessage(Component.empty())
        sender.sendMessage(
            Component.text("═══ Node Information ═══", NamedTextColor.GOLD, TextDecoration.BOLD)
        )
        
        sender.sendMessage(
            Component.text("Node ID: ", NamedTextColor.GRAY)
                .append(Component.text(Portal.nodeId, NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Node Name: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.portalConfig.nodeName, NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Region: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.portalConfig.region, NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Version: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.description.version, NamedTextColor.WHITE))
        )
        
        sender.sendMessage(Component.empty())
        
        sender.sendMessage(
            Component.text("Redis: ", NamedTextColor.GRAY)
                .append(Component.text(
                    if (plugin.portalConfig.redis.enabled) "Enabled" else "Disabled",
                    if (plugin.portalConfig.redis.enabled) NamedTextColor.GREEN else NamedTextColor.RED
                ))
        )
        
        sender.sendMessage(
            Component.text("Direct Connections: ", NamedTextColor.GRAY)
                .append(Component.text(
                    if (plugin.portalConfig.direct.enabled) 
                        "Enabled (port ${plugin.portalConfig.direct.bindPort})" 
                    else "Disabled",
                    if (plugin.portalConfig.direct.enabled) NamedTextColor.GREEN else NamedTextColor.RED
                ))
        )
        
        sender.sendMessage(Component.empty())
    }
    
    private fun showStats(sender: CommandSender) {
        val networkManager = plugin.networkManager
        
        sender.sendMessage(Component.empty())
        sender.sendMessage(
            Component.text("═══ Network Statistics ═══", NamedTextColor.GOLD, TextDecoration.BOLD)
        )
        
        sender.sendMessage(
            Component.text("Messages Sent: ", NamedTextColor.GRAY)
                .append(Component.text("${networkManager.getMessagesSent()}", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Messages Received: ", NamedTextColor.GRAY)
                .append(Component.text("${networkManager.getMessagesReceived()}", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Bytes Sent: ", NamedTextColor.GRAY)
                .append(Component.text(formatBytes(networkManager.getBytesSent()), NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Bytes Received: ", NamedTextColor.GRAY)
                .append(Component.text(formatBytes(networkManager.getBytesReceived()), NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Connected Nodes: ", NamedTextColor.GRAY)
                .append(Component.text("${networkManager.getConnectedNodeCount()}", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(Component.empty())
        
        // Metrics
        val metrics = plugin.metricsManager
        sender.sendMessage(
            Component.text("Avg Sync Latency: ", NamedTextColor.GRAY)
                .append(Component.text("${metrics.getAvgSyncLatency()}ms", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(Component.empty())
    }
    
    private fun toggleDebug(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("portal.admin.debug")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(Component.text("Usage: /portal debug <on|off|packets|sync|latency>", NamedTextColor.YELLOW))
            return
        }
        
        val config = plugin.config
        
        when (args[1].lowercase()) {
            "on" -> {
                config.set("debug.enabled", true)
                sender.sendMessage(Component.text("Debug mode enabled.", NamedTextColor.GREEN))
            }
            "off" -> {
                config.set("debug.enabled", false)
                config.set("debug.log-packets", false)
                config.set("debug.log-sync", false)
                sender.sendMessage(Component.text("Debug mode disabled.", NamedTextColor.YELLOW))
            }
            "packets" -> {
                val current = config.getBoolean("debug.log-packets", false)
                config.set("debug.log-packets", !current)
                sender.sendMessage(Component.text("Packet logging: ${!current}", 
                    if (!current) NamedTextColor.GREEN else NamedTextColor.YELLOW))
            }
            "sync" -> {
                val current = config.getBoolean("debug.log-sync", false)
                config.set("debug.log-sync", !current)
                sender.sendMessage(Component.text("Sync logging: ${!current}",
                    if (!current) NamedTextColor.GREEN else NamedTextColor.YELLOW))
            }
            "latency" -> {
                val current = config.getBoolean("debug.log-latency", true)
                config.set("debug.log-latency", !current)
                sender.sendMessage(Component.text("Latency logging: ${!current}",
                    if (!current) NamedTextColor.GREEN else NamedTextColor.YELLOW))
            }
            else -> {
                sender.sendMessage(Component.text("Unknown debug option.", NamedTextColor.RED))
            }
        }
        
        plugin.saveConfig()
    }
    
    private fun reloadConfig(sender: CommandSender) {
        if (!sender.hasPermission("portal.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return
        }
        
        plugin.reloadConfig()
        sender.sendMessage(Component.text("Configuration reloaded.", NamedTextColor.GREEN))
        sender.sendMessage(Component.text("Note: Some changes require a server restart.", NamedTextColor.YELLOW))
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
