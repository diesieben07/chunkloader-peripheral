package dev.weiland.mods.chunkloader_peripheral

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos

fun main() {
    val pos = BlockPos(Int.MAX_VALUE, 255, Int.MAX_VALUE)
    println(pos)
    val l = pos.toLong()
    println(BlockPos.fromLong(l))
    println(l)
}