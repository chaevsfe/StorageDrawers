package com.texelsaurus.minecraft.chameleon.service;

import com.texelsaurus.minecraft.chameleon.render.ChameleonBlockModelPart;
import com.texelsaurus.minecraft.chameleon.render.ReplacementBlockPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class NeoforgeRender implements ChameleonRender
{
    // ReplacementBlockPart is platform-neutral as of 26.x: the chunk section layer is baked into
    // every quad's MaterialInfo at construction time, so there is nothing left for a NeoForge
    // subclass to carry. NeoforgeReplacementBlockPart existed only to hold a render type through
    // BlockModelPartExtension, which now exposes ambientOcclusion() and nothing else.
    @Override
    public ChameleonBlockModelPart createReplacementPart (BlockStateModelPart part, Material.Baked material, @Nullable ChunkSectionLayer layer) {
        return new ReplacementBlockPart(part, material, layer);
    }

    // ModelDataManager only re-reads a block entity that has asked for it, and it refuses refresh
    // requests from any thread but its own -- which is fine, every caller here is the client thread
    // handling a block entity update. requestModelDataUpdate itself no-ops off the client.
    @Override
    public void refreshRenderData (BlockEntity entity) {
        entity.requestModelDataUpdate();
    }
}
