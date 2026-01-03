package org.mwynhad.portal.util

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Utility functions for item serialization
 */
object ItemSerializer {
    
    /**
     * Serialize an ItemStack to bytes using Paper's native method
     */
    fun serialize(item: ItemStack?): ByteArray {
        if (item == null || item.type.isAir) return ByteArray(0)
        return item.serializeAsBytes()
    }
    
    /**
     * Deserialize bytes back to an ItemStack
     */
    fun deserialize(data: ByteArray): ItemStack? {
        if (data.isEmpty()) return null
        return ItemStack.deserializeBytes(data)
    }
    
    /**
     * Serialize an ItemStack to Base64 string
     */
    fun serializeToBase64(item: ItemStack?): String {
        if (item == null || item.type.isAir) return ""
        return Base64.getEncoder().encodeToString(item.serializeAsBytes())
    }
    
    /**
     * Deserialize Base64 string back to ItemStack
     */
    fun deserializeFromBase64(base64: String): ItemStack? {
        if (base64.isEmpty()) return null
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64))
    }
    
    /**
     * Serialize an array of ItemStacks
     */
    fun serializeArray(items: Array<ItemStack?>): ByteArray {
        val output = ByteArrayOutputStream()
        BukkitObjectOutputStream(output).use { stream ->
            stream.writeInt(items.size)
            items.forEach { item ->
                stream.writeObject(item?.serialize())
            }
        }
        return output.toByteArray()
    }
    
    /**
     * Deserialize an array of ItemStacks
     */
    fun deserializeArray(data: ByteArray): Array<ItemStack?> {
        if (data.isEmpty()) return emptyArray()
        
        val input = ByteArrayInputStream(data)
        return BukkitObjectInputStream(input).use { stream ->
            val size = stream.readInt()
            Array(size) { 
                @Suppress("UNCHECKED_CAST")
                val map = stream.readObject() as? Map<String, Any>
                map?.let { ItemStack.deserialize(it) }
            }
        }
    }
}
