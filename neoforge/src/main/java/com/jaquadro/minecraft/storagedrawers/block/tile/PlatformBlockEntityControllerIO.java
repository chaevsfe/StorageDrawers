package com.jaquadro.minecraft.storagedrawers.block.tile;

import com.jaquadro.minecraft.storagedrawers.client.model.NeoforgeModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;

/**
 * Exists only to publish render data: the framed controller IO takes a PlatformDecoratedModel, so
 * its frame material has to reach the model through the model data channel.
 */
public class PlatformBlockEntityControllerIO extends BlockEntityControllerIO
{
    public PlatformBlockEntityControllerIO (BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public ModelData getModelData () {
        return NeoforgeModelData.of(this);
    }
}
