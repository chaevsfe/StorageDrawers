package com.jaquadro.minecraft.storagedrawers.block.tile;

import com.jaquadro.minecraft.storagedrawers.client.model.NeoforgeModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;

/**
 * Exists only to publish render data: framed trim takes a PlatformDecoratedModel, so its frame
 * material has to reach the model through the model data channel.
 */
public class PlatformBlockEntityTrim extends BlockEntityTrim
{
    public PlatformBlockEntityTrim (BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public ModelData getModelData () {
        return NeoforgeModelData.of(this);
    }
}
