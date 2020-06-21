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
import net.minecraft.util.text.StringTextComponent
import net.minecraft.world.World
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

    companion object {

        val methodNames = LuaMethod.values().map { it.luaName }.toTypedArray()


        const val TICKET_DISTANCE = 2

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
    private var currentWorld: ServerWorld? = null
    private var currentPos: BlockPos? = null

    private var currentChunkStatus: TurtleChunkForceStatus? = null

    private data class TurtleChunkForceStatus(
        val chunk: ChunkPos,
        val direction: Direction,
        val atEdgeOfChunk: Boolean
    ) {

        val chunks = if (atEdgeOfChunk) setOf(chunk, chunk.offset(direction)) else setOf(chunk)

        companion object {
            fun of(pos: BlockPos, direction: Direction): TurtleChunkForceStatus {
                return TurtleChunkForceStatus(
                    chunk = ChunkPos(pos),
                    direction = direction,
                    atEdgeOfChunk = pos.isAtEdgeOfChunk(direction)
                )
            }
        }
    }

    private fun World.debug(msg: String) {
        world.server?.playerList?.sendMessage(StringTextComponent(msg))
    }

    private fun releaseChunk(world: ServerWorld, data: LoadedChunkData, chunk: ChunkPos, computerId: Int, cooldown: Boolean) {
        if (data.remove(chunk, computerId)) {
            if (cooldown) {
                world.chunkProvider.registerTicket(Main.temporaryTicket, chunk, TICKET_DISTANCE, chunk)
            }
            world.chunkProvider.releaseTicket(Main.permanentTicket, chunk, TICKET_DISTANCE, chunk)
        }
    }

    private fun registerChunk(world: ServerWorld, data: LoadedChunkData, chunk: ChunkPos, computerId: Int) {
        if (data.add(chunk, computerId)) {
            world.chunkProvider.registerTicket(Main.permanentTicket, chunk, TICKET_DISTANCE, chunk)
        }
    }

    internal fun serverTick(world: ServerWorld) {
        val computerId = computerId.value
        if (computerId >= 0) {
            if (!active) {
                currentWorld = null
                currentPos = null
                currentDir = null
                currentChunkStatus = null
                for (chunk in forcedChunks) {
                    releaseChunk(world, LoadedChunkData.forWorld(world), chunk, computerId, false)
                }
            } else {
                val prevWorld = currentWorld
                currentWorld = world

                val prevChunkStatus: TurtleChunkForceStatus?
                val prevPos: BlockPos?
                val prevDir: Direction?

                if (prevWorld != null && prevWorld !== world) {
                    // dimension teleport, un-force old chunks immediately
                    val data = LoadedChunkData.forWorld(prevWorld)
                    for (chunk in forcedChunks) {
                        releaseChunk(prevWorld, data, chunk, computerId, false)
                    }
                    forcedChunks.clear()
                    prevChunkStatus = null
                    prevPos = null
                    prevDir = null
                } else {
                    prevChunkStatus = currentChunkStatus
                    prevPos = currentPos
                    prevDir = currentDir
                }

                val newPos = turtle.position
                val newDir = turtle.direction

                val newChunkStatus: TurtleChunkForceStatus
                if (newPos != prevPos || newDir != prevDir) {
                    currentPos = newPos
                    currentDir = newDir
                    newChunkStatus = TurtleChunkForceStatus.of(newPos, newDir)
                } else {
                    newChunkStatus = prevChunkStatus ?: TurtleChunkForceStatus.of(newPos, newDir)
                }

                if (newChunkStatus != prevChunkStatus) {
                    currentChunkStatus = newChunkStatus

                    val data = LoadedChunkData.forWorld(world)
                    val newChunks = newChunkStatus.chunks

                    for (chunk in newChunks) {
                        if (chunk !in forcedChunks) {
                            registerChunk(world, data, chunk, computerId)
                            forcedChunks += chunk
                        }
                    }

                    val it = forcedChunks.iterator()
                    for (chunk in it) {
                        if (chunk !in newChunks) {
                            releaseChunk(world, data, chunk, computerId, true)
                            it.remove()
                        }
                    }
                }
            }
        }
    }
}