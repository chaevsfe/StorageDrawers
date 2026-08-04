package com.jaquadro.minecraft.storagedrawers.client.model;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ItemModelStore
{
    // Concurrent: written during parallel model bake.
    public static Map<BlockState, BlockStateModel> models = new ConcurrentHashMap<>();
}
