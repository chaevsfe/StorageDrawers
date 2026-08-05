package com.texelsaurus.minecraft.chameleon.service;

import com.texelsaurus.minecraft.chameleon.render.ChameleonBlockModelPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public interface ChameleonRender
{
    /**
     * Tells the loader that a block entity's render data has changed, from the client thread.
     *
     * Fabric pulls render data fresh on every chunk rebuild, so this is a no-op there. NeoForge
     * caches it per position in a ModelDataManager and only re-reads a block entity that has asked
     * for a refresh, so without this a drawer's contents would render as they were when the chunk
     * was first meshed.
     */
    default void refreshRenderData (BlockEntity entity) { }

    /**
     * @param layer the chunk section layer the returned part renders in, or null to let each quad's
     *     own alpha content decide. It must be supplied here because the layer is baked into every
     *     quad and cannot be changed afterwards.
     */
    ChameleonBlockModelPart createReplacementPart (BlockStateModelPart part, Material.Baked material, @Nullable ChunkSectionLayer layer);
}
