package com.jaquadro.minecraft.storagedrawers.client.model;

import com.jaquadro.minecraft.storagedrawers.block.tile.modelprops.RenderDataProvider;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;

public final class NeoforgeModelData
{
    public static final ModelProperty<Object> RENDER_DATA = new ModelProperty<>();

    private NeoforgeModelData () { }

    public static ModelData of (RenderDataProvider provider) {
        Object data = provider.getRenderData();
        return data == null ? ModelData.EMPTY : ModelData.of(RENDER_DATA, data);
    }
}
