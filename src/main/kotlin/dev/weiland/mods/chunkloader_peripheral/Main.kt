package dev.weiland.mods.chunkloader_peripheral

import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.api.turtle.TurtleSide
import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.server.ServerWorld
import net.minecraft.world.server.TicketType
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.ModelRegistryEvent
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.RegistryObject
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries

@Mod("chunkloader_upgrade")
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
internal class Main {

    init {
        for (reg in arrayOf(blocks, items)) {
            reg.register(FMLJavaModLoadingContext.get().modEventBus)
        }
    }

    companion object {

        const val MOD_ID = "chunkloader_upgrade"

        val initialLoadTicket = TicketType.create("$MOD_ID:initial_load", chunkPosComparator, 2 * 20)
        val permanentTicket: TicketType<ChunkPos> = TicketType.create("$MOD_ID:permanent", compareBy(ChunkPos::asLong))
        val temporaryTicket: TicketType<ChunkPos> = TicketType.create("$MOD_ID:temporary", compareBy(ChunkPos::asLong), 20 * 20)

        private val blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID)
        private val items = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)

        private val itemGroup = object : ItemGroup(MOD_ID) {
            override fun createIcon(): ItemStack = ItemStack(chunkLoaderUpgradeItem.get())
        }

        val chunkLoaderUpgradeItem: RegistryObject<Item> = items.register("upgrade") {
            Item(
                Item.Properties()
                    .group(itemGroup)
            )
        }

        @SubscribeEvent
        @JvmStatic
        fun commonSetup(event: FMLCommonSetupEvent) {
            @Suppress("DEPRECATION")
            net.minecraftforge.fml.DeferredWorkQueue.runLater(this::computerCraftSetup)
        }

        private fun computerCraftSetup() {
            ComputerCraftAPI.registerTurtleUpgrade(ChunkLoadingTurtleUpgrade(chunkLoaderUpgradeItem.get()))
        }

        @SubscribeEvent
        @JvmStatic
        @OnlyIn(Dist.CLIENT)
        fun modelRegistry(event: ModelRegistryEvent) {
            for (side in TurtleSide.values()) {
                ModelLoader.addSpecialModel(ChunkLoadingTurtleUpgrade.modelFor(side))
            }
        }

    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    internal object ForgeBusSubscriber {

        @JvmStatic
        @SubscribeEvent
        fun worldTick(evt: TickEvent.WorldTickEvent) {
            if (!evt.world.isRemote && evt.phase == TickEvent.Phase.END) {
                (evt.world as? ServerWorld)?.let { world ->
                    LoadedChunkData.forWorld(world).tick(world)
                }
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun worldLoad(evt: WorldEvent.Load) {
            if (!evt.world.isRemote) {
                (evt.world as? ServerWorld)?.let { world ->
                    LoadedChunkData.forWorld(world).worldLoad(world)
                }
            }
        }
    }
}