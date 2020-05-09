package dev.weiland.mods.chunkloader_peripheral

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import net.minecraft.nbt.CompoundNBT
import net.minecraft.nbt.IntArrayNBT
import net.minecraft.nbt.ListNBT
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.server.ServerWorld
import net.minecraft.world.storage.WorldSavedData
import net.minecraftforge.common.util.Constants

internal class LoadedChunkData : WorldSavedData(NAME) {

    private val map = HashMap<ChunkPos, IntOpenHashSet>()

    companion object {
        private const val NAME = "${Main.MOD_ID}.chunks"

        private const val ENTRIES_KEY = "entries"
        private const val CHUNK_KEY = "chunk"
        private const val COMPUTER_IDS_KEY = "computers"

        fun forWorld(world: ServerWorld): LoadedChunkData {
            return world.savedData.getOrCreate(::LoadedChunkData, NAME)
        }

    }

    fun clear() {
        map.clear()
        markDirty()
    }

    /**
     * Starts forcing given chunk by given computerId.
     * Returns true if chunk ticket needs to be registered.
     */
    fun add(chunk: ChunkPos, computerId: Int): Boolean {
        val changed = map.getOrPut(chunk, ::IntOpenHashSet).add(computerId)
        if (changed) {
            markDirty()
        }
        return changed
    }

    /**
     * Stops forcing given chunk by given computerId.
     * Returns true if chunk ticket needs to be released
     */
    fun remove(chunk: ChunkPos, computerId: Int): Boolean {
        val set = map[chunk] ?: return true
        val changed = set.remove(computerId)
        val chunkEmpty = changed && set.isEmpty()
        if (chunkEmpty) {
            map.remove(chunk)
        }
        if (changed) {
            markDirty()
        }
        return chunkEmpty
    }

    override fun write(compound: CompoundNBT): CompoundNBT {
        val entriesNbt = map.mapTo(ListNBT()) { (chunk, computerIds) ->
            CompoundNBT().apply {
                putLong(CHUNK_KEY, chunk.asLong())
                put(COMPUTER_IDS_KEY, IntArrayNBT(computerIds.toIntArray()))
            }
        }
        return CompoundNBT().apply {
            put(ENTRIES_KEY, entriesNbt)
        }
    }

    override fun read(nbt: CompoundNBT) {
        map.clear()
        val entriesNbt = nbt.getList(ENTRIES_KEY, Constants.NBT.TAG_COMPOUND)
        for (entry in entriesNbt) {
            check(entry is CompoundNBT)
            val chunk = ChunkPos(entry.getLong(CHUNK_KEY))
            val computerIds = IntOpenHashSet(entry.getIntArray(COMPUTER_IDS_KEY))
            map[chunk] = computerIds
        }
    }
}