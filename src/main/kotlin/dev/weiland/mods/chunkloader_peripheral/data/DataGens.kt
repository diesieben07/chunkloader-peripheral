package dev.weiland.mods.chunkloader_peripheral.data

import dan200.computercraft.api.turtle.TurtleSide
import dev.weiland.mods.chunkloader_peripheral.ChunkLoadingTurtleUpgrade
import dev.weiland.mods.chunkloader_peripheral.Main
import dev.weiland.mods.chunkloader_peripheral.modelName
import net.minecraft.data.DataGenerator
import net.minecraft.util.ResourceLocation
import net.minecraft.util.Util
import net.minecraftforge.client.model.generators.ExistingFileHelper
import net.minecraftforge.client.model.generators.ItemModelProvider
import net.minecraftforge.client.model.generators.ModelFile
import net.minecraftforge.common.data.LanguageProvider
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent

@Mod.EventBusSubscriber(modid = Main.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
internal object DataGens {

    @JvmStatic
    @SubscribeEvent
    fun gather(evt: GatherDataEvent) {
        evt.generator.addProvider(ItemModels(evt.generator, evt.existingFileHelper))
        evt.generator.addProvider(Lang(evt.generator, "en_us"))
    }

    private class Lang(gen: DataGenerator, locale: String) : LanguageProvider(gen, Main.MOD_ID, locale) {

        override fun addTranslations() {
            addItem(Main.chunkLoaderUpgradeItem, "Chunk loading Upgrade")
            add(
                "${Util.makeTranslationKey("upgrade", Main.chunkLoaderUpgradeItem.id)}.adjective",
                "Chunk loading"
            )
        }
    }

    private class ItemModels(generator: DataGenerator, existingFileHelper: ExistingFileHelper) : ItemModelProvider(generator, Main.MOD_ID, existingFileHelper) {

        override fun registerModels() {
            val textureFace = ResourceLocation(Main.MOD_ID, "block/upgrade_face")
            withExistingParent(Main.chunkLoaderUpgradeItem.id.path, ResourceLocation("computercraft", "block/modem"))
                .texture("front", textureFace)
                .texture("back", ResourceLocation("computercraft", "block/modem_back"))

            for (side in TurtleSide.values()) {
                withExistingParent(
                    ChunkLoadingTurtleUpgrade.modelPathFor(side),
                    ResourceLocation("computercraft", "block/turtle_upgrade_base_${side.modelName}")
                )
                    .texture("texture", textureFace)
            }
        }
    }
}