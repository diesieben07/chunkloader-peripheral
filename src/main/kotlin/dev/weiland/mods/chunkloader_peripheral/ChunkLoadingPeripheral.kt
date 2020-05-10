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
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.server.ServerWorld
import net.minecraft.world.server.TicketType
import net.minecraftforge.common.util.Constants
import java.util.concurrent.TimeUnit

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

        val temporaryLifespan = (TimeUnit.SECONDS.toMillis(20L) * 20).toInt()
        val permanentTicket: TicketType<ChunkPos> = TicketType.create("${Main.MOD_ID}:permanent", compareBy(ChunkPos::asLong))
        val temporaryTicket: TicketType<ChunkPos> = TicketType.create("${Main.MOD_ID}:temporary", compareBy(ChunkPos::asLong), temporaryLifespan)

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

    private var currentDir: Direction? = null
    private var currentPos: BlockPos? = null
    private var currentChunkStatus: TurtleChunkForceStatus? = null

    private data class TurtleChunkForceStatus(val chunk: ChunkPos, val direction: Direction, val atEdgeOfChunk: Boolean) {

        fun getChunksToForce(): Set<ChunkPos> {
            return if (!atEdgeOfChunk) {
                setOf(chunk)
            } else {
                setOf(chunk, chunk.offset(direction))
            }
        }

    }

    private fun computeChunkStatus(pos: BlockPos, direction: Direction): TurtleChunkForceStatus {
        return TurtleChunkForceStatus(
            chunk = ChunkPos(pos),
            direction = direction,
            atEdgeOfChunk = pos.isAtEdgeOfChunk(direction)
        )
    }

    internal fun serverTick() {
        val computerId = computerId.value
        if (computerId >= 0) {
            val prevPos = currentPos
            val prevDir = currentDir
            if (prevPos == null) {
                println("$computerId is initializing!")
                LoadedChunkData.forWorld(turtle.world as ServerWorld).clear()
            }
            val newPos = turtle.position
            val newDir = turtle.direction

            val prevStatus: TurtleChunkForceStatus? = currentChunkStatus
            val newStatus: TurtleChunkForceStatus

            if (newPos != prevPos || newDir != prevDir) {
                currentPos = newPos
                currentDir = newDir
                newStatus = computeChunkStatus(newPos, newDir)
            } else {
                newStatus = prevStatus ?: computeChunkStatus(newPos, newDir)
            }

            if (newStatus != prevStatus) {
                currentChunkStatus = newStatus
                // recalc chunk force here
                val prevChunks = prevStatus?.getChunksToForce() ?: emptySet()
                val newChunks = newStatus.getChunksToForce()

            }
        }
    }

    private fun setChunkForceStatus(world: ServerWorld, chunk: ChunkPos, computerId: Int, forced: Boolean) {
        val data = LoadedChunkData.forWorld(world)
        if (forced) {
            if (data.add(chunk, computerId)) {
                println("forcing $chunk")
                world.chunkProvider.registerTicket(permanentTicket, chunk, 2, chunk)
            }
        } else {
            if (data.remove(chunk, computerId)) {
                println("unforcing $chunk")
                world.chunkProvider.releaseTicket(permanentTicket, chunk, 2, chunk)
            }
        }
    }


}