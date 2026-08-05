package com.jaquadro.minecraft.storagedrawers.client.model;

import com.jaquadro.minecraft.storagedrawers.block.tile.modelprops.RenderDataProvider;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;

/**
 * The NeoForge side of the render-data channel.
 *
 * {@code BlockStateModelExtension.collectParts} is called on a meshing worker thread against a
 * snapshot of the world, and NeoForge is explicit that block entities must not be touched from
 * there. The supported route is {@code IBlockGetterExtension.getModelData(pos)}, whose contents
 * come from {@code BlockEntity.getModelData()} — called on the client main thread, and only after
 * something has asked for a refresh.
 *
 * So the two halves of this channel are:
 *   - every block entity that provides render data overrides getModelData() with {@link #of},
 *   - every client-side change routes through BaseBlockEntity.markBlockForRenderUpdate, which
 *     calls ChameleonRender.refreshRenderData -> requestModelDataUpdate.
 *
 * Note these live in net.neoforged.neoforge.model.data, NOT the client package: BlockEntity itself
 * declares getModelData(), so this class has to be loadable on a dedicated server.
 */
public final class NeoforgeModelData
{
    public static final ModelProperty<Object> RENDER_DATA = new ModelProperty<>();

    private NeoforgeModelData () { }

    public static ModelData of (RenderDataProvider provider) {
        Object data = provider.getRenderData();
        return data == null ? ModelData.EMPTY : ModelData.of(RENDER_DATA, data);
    }
}
