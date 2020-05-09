package dev.weiland.mods.chunkloader_peripheral

import dan200.computercraft.api.ComputerCraftAPI
import net.minecraft.world.server.TicketType
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.DeferredWorkQueue
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries

@Mod("chunkloader_upgrade")
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
internal class Main {

    companion object {

        const val MOD_ID = "chunkloader_upgrade"

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

}