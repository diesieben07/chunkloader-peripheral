package dev.weiland.mods.chunkloader_peripheral

import net.minecraft.util.Direction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Vec3i

internal val chunkPosComparator = compareBy(ChunkPos::asLong)

internal operator fun Vec3i.times(value: Int) = Vec3i(x * value, y * value, z * value)

internal operator fun Vec3i.get(axis: Direction.Axis) = when (axis) {
    Direction.Axis.X -> x
    Direction.Axis.Y -> y
    Direction.Axis.Z -> z
}

/**
 * Checks if this position is at the edge of a chunk, considering the given facing direction.
 */
internal fun BlockPos.isAtEdgeOfChunk(facingDir: Direction): Boolean {
    return if (facingDir.axis.isHorizontal) {
        val requiredDirectionVec = when (this[facingDir.axis] and 15) {
            0 -> -1
            15 -> 1
            else -> Int.MIN_VALUE
        }
        facingDir.axisDirection.offset == requiredDirectionVec
    } else {
        // facing up or down? Doesn't make much sense in this context.
        false
    }
}

internal fun ChunkPos.offset(dir: Direction): ChunkPos {
    return ChunkPos(x + dir.xOffset, z + dir.zOffset)
}


fun main() {
    val pos = BlockPos(15, 10, 13)
    println(pos.isAtEdgeOfChunk(Direction.EAST))
}