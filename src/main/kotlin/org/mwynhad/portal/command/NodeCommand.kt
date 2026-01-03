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
 * Node management command handler
 */
class NodeCommand(private val plugin: Portal) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("portal.admin.nodes")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return true
        }
        
        if (args.isEmpty()) {
            showNodeList(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "list" -> showNodeList(sender)
            "info" -> {
                if (args.size < 2) {
                    sender.sendMessage(Component.text("Usage: /fnode info <nodeId>", NamedTextColor.YELLOW))
                } else {
                    showNodeInfo(sender, args[1])
                }
            }
            "ping" -> {
                if (args.size < 2) {
                    pingAllNodes(sender)
                } else {
                    pingNode(sender, args[1])
                }
            }
            "disconnect" -> {
                sender.sendMessage(Component.text("Node disconnection not yet implemented.", NamedTextColor.YELLOW))
            }
            else -> {
                sender.sendMessage(Component.text("Unknown subcommand. Use: list, info, ping", NamedTextColor.RED))
            }
        }
        
        return true
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("list", "info", "ping", "disconnect")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "info", "ping", "disconnect" -> plugin.nodeManager.getNodes().keys.toList()
                    .filter { it.startsWith(args[1]) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
    
    private fun showNodeList(sender: CommandSender) {
        val nodes = plugin.nodeManager.getNodes()
        
        sender.sendMessage(Component.empty())
        sender.sendMessage(
            Component.text("═══ Federation Nodes ═══", NamedTextColor.GOLD, TextDecoration.BOLD)
        )
        
        if (nodes.isEmpty()) {
            sender.sendMessage(Component.text("No other nodes connected.", NamedTextColor.GRAY))
        } else {
            nodes.values.sortedByDescending { it.isPrimary }.forEach { node ->
                val latency = plugin.nodeManager.getLatency(node.nodeId)
                val latencyColor = when {
                    latency == null -> NamedTextColor.GRAY
                    latency < 50 -> NamedTextColor.GREEN
                    latency < 100 -> NamedTextColor.YELLOW
                    else -> NamedTextColor.RED
                }
                
                val primaryBadge = if (node.isPrimary) {
                    Component.text(" [PRIMARY]", NamedTextColor.GOLD)
                } else {
                    Component.empty()
                }
                
                sender.sendMessage(
                    Component.text("• ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(node.nodeName, NamedTextColor.WHITE))
                        .append(primaryBadge)
                        .append(Component.text(" (${node.nodeId})", NamedTextColor.DARK_GRAY))
                )
                
                sender.sendMessage(
                    Component.text("    Region: ", NamedTextColor.GRAY)
                        .append(Component.text(node.region, NamedTextColor.AQUA))
                        .append(Component.text(" | Latency: ", NamedTextColor.GRAY))
                        .append(Component.text(latency?.let { "${it}ms" } ?: "Unknown", latencyColor))
                        .append(Component.text(" | Players: ", NamedTextColor.GRAY))
                        .append(Component.text("${node.playerCount}/${node.maxPlayers}", NamedTextColor.GREEN))
                )
            }
        }
        
        sender.sendMessage(Component.empty())
    }
    
    private fun showNodeInfo(sender: CommandSender, nodeId: String) {
        val node = plugin.nodeManager.getNodes()[nodeId]
        
        if (node == null) {
            sender.sendMessage(Component.text("Node not found: $nodeId", NamedTextColor.RED))
            return
        }
        
        val latency = plugin.nodeManager.getLatency(nodeId)
        
        sender.sendMessage(Component.empty())
        sender.sendMessage(
            Component.text("═══ Node: ${node.nodeName} ═══", NamedTextColor.GOLD, TextDecoration.BOLD)
        )
        
        sender.sendMessage(
            Component.text("ID: ", NamedTextColor.GRAY)
                .append(Component.text(node.nodeId, NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Region: ", NamedTextColor.GRAY)
                .append(Component.text(node.region, NamedTextColor.AQUA))
        )
        
        sender.sendMessage(
            Component.text("Primary: ", NamedTextColor.GRAY)
                .append(Component.text(
                    if (node.isPrimary) "Yes" else "No",
                    if (node.isPrimary) NamedTextColor.GOLD else NamedTextColor.WHITE
                ))
        )
        
        sender.sendMessage(
            Component.text("Direct Connection: ", NamedTextColor.GRAY)
                .append(Component.text("${node.directHost}:${node.directPort}", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Players: ", NamedTextColor.GRAY)
                .append(Component.text("${node.playerCount}/${node.maxPlayers}", NamedTextColor.GREEN))
        )
        
        sender.sendMessage(
            Component.text("Latency: ", NamedTextColor.GRAY)
                .append(Component.text(latency?.let { "${it}ms" } ?: "Unknown", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("TPS: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f", node.tps), 
                    if (node.tps >= 19.0) NamedTextColor.GREEN else NamedTextColor.YELLOW))
        )
        
        sender.sendMessage(
            Component.text("MSPT: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2fms", node.mspt), NamedTextColor.WHITE))
        )
        
        sender.sendMessage(
            Component.text("Version: ", NamedTextColor.GRAY)
                .append(Component.text(node.version, NamedTextColor.WHITE))
        )
        
        val lastSeenAgo = (System.currentTimeMillis() - node.lastSeen) / 1000
        sender.sendMessage(
            Component.text("Last Seen: ", NamedTextColor.GRAY)
                .append(Component.text("${lastSeenAgo}s ago", NamedTextColor.WHITE))
        )
        
        sender.sendMessage(Component.empty())
    }
    
    private fun pingAllNodes(sender: CommandSender) {
        val nodes = plugin.nodeManager.getNodes()
        
        if (nodes.isEmpty()) {
            sender.sendMessage(Component.text("No nodes to ping.", NamedTextColor.YELLOW))
            return
        }
        
        sender.sendMessage(Component.text("Pinging all nodes...", NamedTextColor.GRAY))
        
        nodes.keys.forEach { nodeId ->
            plugin.nodeManager.sendPing(nodeId)
        }
        
        // Show results after a delay
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            sender.sendMessage(Component.empty())
            sender.sendMessage(Component.text("Ping Results:", NamedTextColor.GOLD))
            
            nodes.values.forEach { node ->
                val latency = plugin.nodeManager.getLatency(node.nodeId)
                val latencyColor = when {
                    latency == null -> NamedTextColor.GRAY
                    latency < 50 -> NamedTextColor.GREEN
                    latency < 100 -> NamedTextColor.YELLOW
                    else -> NamedTextColor.RED
                }
                
                sender.sendMessage(
                    Component.text("  ${node.nodeName}: ", NamedTextColor.WHITE)
                        .append(Component.text(latency?.let { "${it}ms" } ?: "timeout", latencyColor))
                )
            }
        }, 40L) // 2 second delay
    }
    
    private fun pingNode(sender: CommandSender, nodeId: String) {
        if (!plugin.nodeManager.getNodes().containsKey(nodeId)) {
            sender.sendMessage(Component.text("Node not found: $nodeId", NamedTextColor.RED))
            return
        }
        
        sender.sendMessage(Component.text("Pinging node $nodeId...", NamedTextColor.GRAY))
        plugin.nodeManager.sendPing(nodeId)
        
        // Show result after a delay
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val latency = plugin.nodeManager.getLatency(nodeId)
            if (latency != null) {
                sender.sendMessage(
                    Component.text("Pong from $nodeId: ", NamedTextColor.GREEN)
                        .append(Component.text("${latency}ms", NamedTextColor.WHITE))
                )
            } else {
                sender.sendMessage(Component.text("No response from $nodeId", NamedTextColor.RED))
            }
        }, 40L)
    }
}
