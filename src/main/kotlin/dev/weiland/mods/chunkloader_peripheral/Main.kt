package dev.weiland.mods.chunkloader_peripheral

import com.mojang.brigadier.arguments.IntegerArgumentType
import dan200.computercraft.api.ComputerCraftAPI
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.material.Material
import net.minecraft.client.audio.TickableSound
import net.minecraft.command.Commands
import net.minecraft.command.arguments.ColumnPosArgument
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.tileentity.ITickableTileEntity
import net.minecraft.tileentity.TileEntity
import net.minecraft.tileentity.TileEntityType
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.IBlockReader
import net.minecraft.world.server.TicketType
import net.minecraftforge.event.world.ChunkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.DeferredWorkQueue
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.server.FMLServerStartingEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries

@Mod("chunkloader_upgrade")
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
internal class Main {

    class TestBlock(props: Properties) : Block(props) {
        override fun hasTileEntity(state: BlockState?): Boolean = true
        override fun createTileEntity(state: BlockState?, world: IBlockReader?): TileEntity? = TestTE()
    }

    class TestTE : TileEntity(testTe.get()), ITickableTileEntity {

        override fun tick() {
            if (world!!.gameTime % 40L == 0L) {
                println("ticking")
            }
        }

    }

    init {
        for (reg in arrayOf(blocks, teTypes, items)) {
            reg.register(FMLJavaModLoadingContext.get().modEventBus)
        }
    }

    companion object {

        const val MOD_ID = "chunkloader_upgrade"

        private val blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID)
        private val testBlock = blocks.register("testblock") {
            TestBlock(Block.Properties.create(Material.ROCK))
        }

        private val items = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)
        private val testBlockItem = items.register("testblock") {
            BlockItem(testBlock.get(), Item.Properties())
        }


        private val teTypes = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, MOD_ID)
        private val testTe = teTypes.register("testte") {
            TileEntityType(::TestTE, setOf(testBlock.get()), null)
        }


        @SubscribeEvent
        @JvmStatic
        fun commonSetup(event: FMLCommonSetupEvent) {
            @Suppress("DEPRECATION")
            DeferredWorkQueue.runLater(this::computerCraftSetup)
        }

        private fun computerCraftSetup() {
            ComputerCraftAPI.registerTurtleUpgrade(ChunkLoadingTurtleUpgrade())
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
    internal object ForgeBusSubscriber {

        @JvmStatic
        @SubscribeEvent
        fun chunkLoad(evt: ChunkEvent.Load) {
            println("Load ${evt.chunk.pos}")
        }

        @SubscribeEvent
        @JvmStatic
        fun serverStarting(event: FMLServerStartingEvent) {
            event.commandDispatcher.register(
                Commands.literal("myforceload").then(
                    Commands.literal("add").then(
                        Commands.argument("from", ColumnPosArgument.columnPos())
                            .then(
                                Commands.argument("dist", IntegerArgumentType.integer()).executes { command ->
                                    val world = command.source.world
                                    val columnPos = ColumnPosArgument.fromBlockPos(command, "from")
                                    val chunkPos = ChunkPos(columnPos.x shr 4, columnPos.z shr 4)
                                    val dist = IntegerArgumentType.getInteger(command, "dist")
                                    world.chunkProvider.registerTicket(
                                        ChunkLoadingPeripheral.permanentTicket, chunkPos, dist, chunkPos
                                    )
                                    1
                                }
                            )
                    )
                )
            )
        }

        @JvmStatic
        @SubscribeEvent
        fun chunkUnload(evt: ChunkEvent.Unload) {
            println("Unload ${evt.chunk.pos}")
        }

    }

}