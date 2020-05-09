package dev.weiland.mods.chunkloader_peripheral

import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.turtle.ITurtleAccess
import dan200.computercraft.api.turtle.TurtleSide
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop
import net.minecraft.util.Direction
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.server.ServerWorld
import net.minecraft.world.server.TicketType
import net.minecraftforge.common.util.Constants

@OptIn(ExperimentalStdlibApi::class)
class ChunkLoadingPeripheral(
    private val turtle: ITurtleAccess,
    private val side: TurtleSide
) : IPeripheral {

    private enum class LuaMethod(val luaName: String) {
        IS_ACTIVE("isActive"),
        SET_ACTIVE("setActive")
    }

    private companion object {

        val methodNames = LuaMethod.values().map { it.luaName }.toTypedArray()
        val ticketType = TicketType.create("${Main.MOD_ID}:loaded", compareBy(ChunkPos::asLong))

        const val ACTIVE_NBT = "active"

    }

    override fun detach(computer: IComputerAccess) {
        computer.mainThreadMonitor.runWork {  }
        println("detached! $computer")
    }

    override fun attach(computer: IComputerAccess) {
        val newId = computer.id
        computerId.loop { currentId ->
            if (currentId == newId) return
            check(currentId < 0) {
                "ChunkLoadingPeripheral cannot be attached to multiple computers ($currentId and $newId)."
            }
            if (computerId.compareAndSet(currentId, newId)) return
        }
    }

    override fun getMethodNames(): Array<String> {
        return Companion.methodNames
    }

    @Suppress("CovariantEquals")
    override fun equals(other: IPeripheral?): Boolean {
        return equals(other as Any?)
    }

    override fun getType(): String {
        return "chunkloader"
    }

    private val computerId = atomic<Int>(-1)

    private var active: Boolean
        get() {
            return turtle.getUpgradeNBTData(side).getBoolean(ACTIVE_NBT)
        }
        set(value) {
            val nbt = turtle.getUpgradeNBTData(side)
            val change = nbt.getBoolean(ACTIVE_NBT) != value || !nbt.contains(ACTIVE_NBT, Constants.NBT.TAG_BYTE)
            nbt.putBoolean(ACTIVE_NBT, value)
            if (change) {
                turtle.updateUpgradeNBTData(side)
            }
        }

    override fun callMethod(computer: IComputerAccess, context: ILuaContext, method: Int, arguments: Array<out Any>): Array<Any>? {
        val luaMethod = LuaMethod.values().getOrNull(method) ?: throw LuaException("Invalid method index.")

        return when (luaMethod) {
            LuaMethod.IS_ACTIVE -> {
                context.executeMainThreadTask {
                    println("mainthread???")
                    arrayOf(active)
                }
            }
            LuaMethod.SET_ACTIVE -> {
                if (arguments.size != 1) {
                    throw LuaException("1 parameter expected for setLoading")
                }
                val (arg) = arguments
                if (arg !is Boolean) {
                    throw LuaException("parameter 1 for setLoading expected to be boolean.")
                }
                context.executeMainThreadTask {
                    active = arg
                    null
                }
            }
        }
    }

    private val forcedChunks = HashSet<ChunkPos>()

    private var prevDir: Direction? = null
    private var prevChunk: ChunkPos? = null

    private fun getChunksToForce(chunk: ChunkPos, facingDir: Direction): Set<ChunkPos> {
        return buildSet(2) {
            add(chunk)
            if (facingDir.axis.isHorizontal) {
                add(ChunkPos(chunk.asBlockPos().offset(facingDir, 16)))
            }
        }
    }

    internal fun serverTick() {
        val computerId = computerId.value
        if (computerId >= 0) {
            val prevChunk = prevChunk
            val prevDir = prevDir
            if (prevChunk == null) {
                println("$computerId is initializing!")
                LoadedChunkData.forWorld(turtle.world as ServerWorld).clear()
            }
            val currentPos: = turtle.position
            val currentChunk = ChunkPos(currentPos)
            val currentDir = turtle.direction
            if (currentChunk != prevChunk || currentDir != prevDir) {
                val chunksToForce = getChunksToForce(currentChunk, currentDir)
                val chunksToUnForce = (if (prevChunk == null || prevDir == null) emptySet() else getChunksToForce(prevChunk, prevDir)) - chunksToForce

                println("$turtle moved from $prevChunk[$prevDir] to $currentPos[$currentDir]")
                if (active) {
                    for (chunk in chunksToForce) {
                        setChunkForceStatus(turtle.world as ServerWorld, chunk, computerId, true)
                    }
                    for (chunk in chunksToUnForce) {
                        setChunkForceStatus(turtle.world as ServerWorld, chunk, computerId, false)
                    }
                }
                this.prevChunk = currentChunk
                this.prevDir = currentDir
            }
        }
    }

    private fun setChunkForceStatus(world: ServerWorld, chunk: ChunkPos, computerId: Int, forced: Boolean) {
        val data = LoadedChunkData.forWorld(world)
        if (forced) {
            if (data.add(chunk, computerId)) {
                println("forcing $chunk")
                world.chunkProvider.registerTicket(ticketType, chunk, 2, chunk)
            }
        } else {
            if (data.remove(chunk, computerId)) {
                println("unforcing $chunk")
                world.chunkProvider.releaseTicket(ticketType, chunk, 2, chunk)
            }
        }
    }


}