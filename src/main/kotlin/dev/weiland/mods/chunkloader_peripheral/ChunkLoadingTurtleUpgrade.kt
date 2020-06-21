package dev.weiland.mods.chunkloader_peripheral

import dan200.computercraft.api.client.TransformedModel
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.turtle.*
import net.minecraft.client.renderer.Matrix4f
import net.minecraft.client.renderer.TransformationMatrix
import net.minecraft.client.renderer.model.ModelResourceLocation
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.ResourceLocation
import net.minecraft.world.server.ServerWorld
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import java.util.*

@OptIn(ExperimentalStdlibApi::class)
internal class ChunkLoadingTurtleUpgrade(item: Item) : AbstractTurtleUpgrade(
    checkNotNull(item.registryName),
    TurtleUpgradeType.PERIPHERAL,
    item
) {

    companion object {

        fun modelPathFor(side: TurtleSide) = "${Main.chunkLoaderUpgradeItem.id.path}_turtle_${side.modelName}"

        fun modelFor(side: TurtleSide): ModelResourceLocation {
            return ModelResourceLocation(
                ResourceLocation(Main.MOD_ID, modelPathFor(side)),
                "inventory"
            )
        }

        private val models = EnumMap(
            TurtleSide.values().associate { it to modelFor(it) }
        )

    }

    @OnlyIn(Dist.CLIENT)
    override fun getModel(turtle: ITurtleAccess?, side: TurtleSide): TransformedModel {
        return TransformedModel.of(checkNotNull(models[side]))
    }

    override fun createPeripheral(turtle: ITurtleAccess, side: TurtleSide): IPeripheral? {
        return ChunkLoadingPeripheral(turtle, side)
    }

    override fun update(turtle: ITurtleAccess, side: TurtleSide) {
        val world = turtle.world
        if (world is ServerWorld) {
            (turtle.getPeripheral(side) as? ChunkLoadingPeripheral)?.serverTick(world)
        }
    }

}