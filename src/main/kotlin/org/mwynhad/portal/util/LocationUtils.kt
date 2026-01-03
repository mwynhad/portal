package org.mwynhad.portal.util

import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Utility functions for location and vector operations
 */
object LocationUtils {
    
    /**
     * Calculate distance squared between two locations (faster than distance)
     */
    fun distanceSquared(loc1: Location, loc2: Location): Double {
        if (loc1.world != loc2.world) return Double.MAX_VALUE
        val dx = loc1.x - loc2.x
        val dy = loc1.y - loc2.y
        val dz = loc1.z - loc2.z
        return dx * dx + dy * dy + dz * dz
    }
    
    /**
     * Calculate horizontal distance squared (ignoring Y)
     */
    fun horizontalDistanceSquared(loc1: Location, loc2: Location): Double {
        if (loc1.world != loc2.world) return Double.MAX_VALUE
        val dx = loc1.x - loc2.x
        val dz = loc1.z - loc2.z
        return dx * dx + dz * dz
    }
    
    /**
     * Check if two locations are within a certain distance
     */
    fun isWithinDistance(loc1: Location, loc2: Location, distance: Double): Boolean {
        return distanceSquared(loc1, loc2) <= distance * distance
    }
    
    /**
     * Interpolate between two locations
     */
    fun interpolate(from: Location, to: Location, factor: Double): Location {
        val world = from.world ?: to.world ?: return from
        
        return Location(
            world,
            from.x + (to.x - from.x) * factor,
            from.y + (to.y - from.y) * factor,
            from.z + (to.z - from.z) * factor,
            (from.yaw + angleDifference(from.yaw, to.yaw) * factor.toFloat()),
            (from.pitch + (to.pitch - from.pitch) * factor.toFloat())
        )
    }
    
    /**
     * Calculate the smallest angle difference between two angles
     */
    fun angleDifference(from: Float, to: Float): Float {
        var diff = to - from
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        return diff
    }
    
    /**
     * Convert yaw/pitch to a direction vector
     */
    fun toDirection(yaw: Float, pitch: Float): Vector {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        return Vector(
            -kotlin.math.sin(yawRad) * kotlin.math.cos(pitchRad),
            -kotlin.math.sin(pitchRad),
            kotlin.math.cos(yawRad) * kotlin.math.cos(pitchRad)
        )
    }
    
    /**
     * Convert a direction vector to yaw/pitch
     */
    fun toYawPitch(direction: Vector): Pair<Float, Float> {
        val x = direction.x
        val y = direction.y
        val z = direction.z
        
        val yaw = Math.toDegrees(atan2(-x, z)).toFloat()
        val pitch = Math.toDegrees(atan2(-y, sqrt(x * x + z * z))).toFloat()
        
        return yaw to pitch
    }
    
    /**
     * Pack location into a compact representation
     */
    fun pack(loc: Location): LongArray {
        return longArrayOf(
            java.lang.Double.doubleToRawLongBits(loc.x),
            java.lang.Double.doubleToRawLongBits(loc.y),
            java.lang.Double.doubleToRawLongBits(loc.z),
            (java.lang.Float.floatToRawIntBits(loc.yaw).toLong() shl 32) or 
                (java.lang.Float.floatToRawIntBits(loc.pitch).toLong() and 0xFFFFFFFFL)
        )
    }
    
    /**
     * Unpack a compact location representation
     */
    fun unpack(data: LongArray, world: org.bukkit.World?): Location {
        return Location(
            world,
            java.lang.Double.longBitsToDouble(data[0]),
            java.lang.Double.longBitsToDouble(data[1]),
            java.lang.Double.longBitsToDouble(data[2]),
            java.lang.Float.intBitsToFloat((data[3] shr 32).toInt()),
            java.lang.Float.intBitsToFloat(data[3].toInt())
        )
    }
}
