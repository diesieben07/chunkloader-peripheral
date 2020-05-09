package dev.weiland.mods.chunkloader_peripheral

import dan200.computercraft.api.client.TransformedModel
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.turtle.*
import net.minecraft.client.renderer.Matrix4f
import net.minecraft.client.renderer.TransformationMatrix
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

@OptIn(ExperimentalStdlibApi::class)
internal class ChunkLoadingTurtleUpgrade : AbstractTurtleUpgrade(
    ResourceLocation(Main.MOD_ID, "chunkloader"),
    TurtleUpgradeType.PERIPHERAL,
    Items.APPLE
) {
    @OnlyIn(Dist.CLIENT)
    override fun getModel(turtle: ITurtleAccess?, side: TurtleSide): TransformedModel {
        val xOffset = if (side == TurtleSide.LEFT) -0.40625f else 0.40625f
        val transform = Matrix4f(floatArrayOf(0.0f, 0.0f, -1.0f, 1.0f + xOffset, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f))
        return TransformedModel.of(ItemStack(Items.APPLE), TransformationMatrix(transform))
    }

    override fun createPeripheral(turtle: ITurtleAccess, side: TurtleSide): IPeripheral? {
        return ChunkLoadingPeripheral(turtle, side)
    }

    override fun update(turtle: ITurtleAccess, side: TurtleSide) {
        if (!turtle.world.isRemote) {
            (turtle.getPeripheral(side) as? ChunkLoadingPeripheral)?.serverTick()
            if (turtle.world.gameTime % 40L == 0L) {
                println("$turtle is alive")
            }
        }
    }

}