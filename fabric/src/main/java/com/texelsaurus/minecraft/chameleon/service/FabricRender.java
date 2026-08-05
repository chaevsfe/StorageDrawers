package com.texelsaurus.minecraft.chameleon.service;

import com.texelsaurus.minecraft.chameleon.render.ChameleonBlockModelPart;
import com.texelsaurus.minecraft.chameleon.render.ReplacementBlockPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material;
import org.jetbrains.annotations.Nullable;

public class FabricRender implements ChameleonRender
{
    @Override
    public ChameleonBlockModelPart createReplacementPart (BlockStateModelPart part, Material.Baked material, @Nullable ChunkSectionLayer layer) {
        return new ReplacementBlockPart(part, material, layer);
    }
}
