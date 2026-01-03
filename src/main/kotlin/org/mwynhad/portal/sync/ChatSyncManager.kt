package org.mwynhad.portal.sync

import org.mwynhad.portal.Portal
import org.mwynhad.portal.network.MessageHandler
import org.mwynhad.portal.protocol.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import java.util.*

/**
 * Manages synchronization of chat messages across federation nodes
 */
class ChatSyncManager(private val plugin: Portal) : MessageHandler {
    
    private val gsonSerializer = GsonComponentSerializer.gson()
    
    // Track recently processed messages to avoid duplicates
    private val recentMessages = LinkedHashMap<String, Long>(100, 0.75f, true)
    
    override fun handledMessageTypes(): List<Class<out PortalMessage>> = listOf(
        ChatMessage::class.java,
        SystemMessage::class.java
    )
    
    override fun handleMessage(message: PortalMessage) {
        when (message) {
            is ChatMessage -> handleChatMessage(message)
            is SystemMessage -> handleSystemMessage(message)
            else -> {}
        }
    }
    
    private fun handleChatMessage(message: ChatMessage) {
        // Check for duplicate
        if (recentMessages.containsKey(message.messageId)) return
        recentMessages[message.messageId] = System.currentTimeMillis()
        
        // Cleanup old messages
        cleanupRecentMessages()
        
        // Deserialize the component
        val component = try {
            gsonSerializer.deserialize(message.componentJson)
        } catch (e: Exception) {
            // Fallback to plain text
            Component.text("<${message.playerName}> ${message.message}")
        }
        
        // Optionally add node prefix
        val finalComponent = if (plugin.portalConfig.sync.chat.showNodePrefix) {
            val nodeName = plugin.nodeManager.getNodes()[message.sourceNode]?.nodeName ?: message.sourceNode
            Component.text("[$nodeName] ").append(component)
        } else {
            component
        }
        
        // Broadcast to local players
        plugin.server.scheduler.runTask(plugin, Runnable {
            Bukkit.getServer().sendMessage(finalComponent)
        })
        
        if (plugin.portalConfig.debug.logSync) {
            plugin.logger.info("[CHAT] ${message.playerName}: ${message.message}")
        }
    }
    
    private fun handleSystemMessage(message: SystemMessage) {
        // Check for duplicate
        if (recentMessages.containsKey(message.messageId)) return
        recentMessages[message.messageId] = System.currentTimeMillis()
        
        val component = try {
            gsonSerializer.deserialize(message.componentJson)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to deserialize system message: ${e.message}")
            return
        }
        
        plugin.server.scheduler.runTask(plugin, Runnable {
            Bukkit.getServer().sendMessage(component)
        })
    }
    
    /**
     * Broadcast a chat message to the federation
     */
    fun broadcastChatMessage(playerUuid: UUID, playerName: String, message: String, component: Component) {
        val messageId = MessageSerializer.generateMessageId()
        recentMessages[messageId] = System.currentTimeMillis()
        
        val chatMessage = ChatMessage(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = messageId,
            playerUuid = playerUuid.toString(),
            playerName = playerName,
            message = message,
            componentJson = gsonSerializer.serialize(component)
        )
        
        plugin.networkManager.broadcast(chatMessage)
    }
    
    /**
     * Broadcast a system message to the federation
     */
    fun broadcastSystemMessage(component: Component) {
        val messageId = MessageSerializer.generateMessageId()
        recentMessages[messageId] = System.currentTimeMillis()
        
        val systemMessage = SystemMessage(
            sourceNode = Portal.nodeId,
            timestamp = System.currentTimeMillis(),
            messageId = messageId,
            componentJson = gsonSerializer.serialize(component)
        )
        
        plugin.networkManager.broadcast(systemMessage)
    }
    
    private fun cleanupRecentMessages() {
        val cutoff = System.currentTimeMillis() - 30000 // 30 seconds
        recentMessages.entries.removeIf { it.value < cutoff }
    }
}
