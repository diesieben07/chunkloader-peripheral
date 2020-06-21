package dev.weiland.mods.chunkloader_peripheral.data

import dan200.computercraft.api.turtle.TurtleSide
import dev.weiland.mods.chunkloader_peripheral.ChunkLoadingTurtleUpgrade
import dev.weiland.mods.chunkloader_peripheral.Main
import dev.weiland.mods.chunkloader_peripheral.modelName
import net.minecraft.advancements.criterion.InventoryChangeTrigger
import net.minecraft.data.DataGenerator
import net.minecraft.data.IFinishedRecipe
import net.minecraft.data.RecipeProvider
import net.minecraft.data.ShapedRecipeBuilder
import net.minecraft.item.Items
import net.minecraft.util.ResourceLocation
import net.minecraft.util.Util
import net.minecraftforge.client.model.generators.ExistingFileHelper
import net.minecraftforge.client.model.generators.ItemModelProvider
import net.minecraftforge.common.Tags
import net.minecraftforge.common.data.LanguageProvider
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent
import java.util.function.Consumer

@Mod.EventBusSubscriber(modid = Main.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
internal object DataGens {

    @JvmStatic
    @SubscribeEvent
    fun gather(evt: GatherDataEvent) {
        for (p in arrayOf(
            ItemModels(evt.generator, evt.existingFileHelper),
            Lang(evt.generator, "en_us"),
            Recipes(evt.generator)
        )) {
            evt.generator.addProvider(p)
        }
    }

    private class Lang(gen: DataGenerator, locale: String) : LanguageProvider(gen, Main.MOD_ID, locale) {

        override fun addTranslations() {
            addItem(Main.chunkLoaderUpgradeItem, "Chunk loading upgrade")
            add(
                "${Util.makeTranslationKey("upgrade", Main.chunkLoaderUpgradeItem.id)}.adjective",
                "Chunk loading"
            )
            add("itemGroup.${Main.MOD_ID}", "Chunk loading upgrade")
        }
    }

    private class Recipes(generatorIn: DataGenerator) : RecipeProvider(generatorIn) {

        override fun registerRecipes(consumer: Consumer<IFinishedRecipe>) {
            ShapedRecipeBuilder.shapedRecipe(Main.chunkLoaderUpgradeItem.get())
                .addCriterion("has_ender_eye", InventoryChangeTrigger.Instance.forItems(Items.ENDER_EYE))
                .patternLine("*#*")
                .patternLine("#*#")
                .patternLine("*#*")
                .key('*', Items.ENDER_EYE)
                .key('#', Tags.Items.STONE)
                .build(consumer, Main.chunkLoaderUpgradeItem.id)
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