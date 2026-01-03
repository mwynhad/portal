package org.mwynhad.portal.listener

import org.mwynhad.portal.Portal
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Handles chat events for chat sync
 */
class ChatListener(private val plugin: Portal) : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAsyncChat(event: AsyncChatEvent) {
        if (!plugin.portalConfig.sync.chat.enabled) return
        
        val player = event.player
        val message = event.message()
        
        // Get plain text version of message
        val plainMessage = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(message)
        
        // Build the full chat component
        val chatComponent = event.renderer().render(player, player.displayName(), message, player)
        
        // Broadcast to federation
        plugin.chatSyncManager.broadcastChatMessage(
            playerUuid = player.uniqueId,
            playerName = player.name,
            message = plainMessage,
            component = chatComponent
        )
    }
}
