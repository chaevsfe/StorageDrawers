package com.jaquadro.minecraft.storagedrawers.client.model;

import com.mojang.blaze3d.platform.Transparency;
import com.texelsaurus.minecraft.chameleon.ChameleonServices;
import com.texelsaurus.minecraft.chameleon.render.ChameleonBlockModelPart;
import com.texelsaurus.minecraft.chameleon.render.ReplacementBlockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpriteReplacementModel extends ParentModel
{
    private record Resolved (Object epoch, Material.Baked material) { }

    private final ItemStack source;
    private volatile Resolved resolved;
    private ChunkSectionLayer layer;
    private Map<BlockStateModelPart, ChameleonBlockModelPart> cache = new HashMap<>();

    public SpriteReplacementModel (@NotNull BlockStateModel parent, Material.Baked material) {
        super(parent);
        this.source = null;
        this.resolved = new Resolved(null, material);
    }

    public SpriteReplacementModel (@NotNull BlockStateModel parent, BlockStateModel replacement, ChunkSectionLayer renderLayer) {
        super(parent);
        this.source = null;
        this.resolved = new Resolved(null, replacement.particleMaterial());
        this.layer = renderLayer;
    }

    public SpriteReplacementModel (@NotNull BlockStateModel parent, ItemStack stack, ChunkSectionLayer renderLayer) {
        super(parent);

        this.source = (stack != null && stack.getItem() instanceof BlockItem) ? stack : null;
        this.resolved = resolve(this.source);

        layer = renderLayer;
    }

    public SpriteReplacementModel (@NotNull BlockStateModel parent, ItemStack stack) {
        this(parent, stack, null);
    }

    private static Resolved resolve (ItemStack stack) {
        if (stack == null)
            return new Resolved(null, null);

        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        Block block = ((BlockItem)stack.getItem()).getBlock();

        return new Resolved(models, models.getParticleMaterial(block.defaultBlockState()));
    }

    private Material.Baked material () {
        Resolved current = resolved;
        if (source != null && current.epoch() != DrawerModelStore.modelEpoch()) {
            current = resolve(source);
            resolved = current;
        }

        return current.material();
    }

    @Override
    public void collectParts (RandomSource randomSource, List<BlockStateModelPart> list) {
        Material.Baked material = material();
        if (material == null) {
            super.collectParts(randomSource, list);
            return;
        }

        List<BlockStateModelPart> parts = new ArrayList<>();
        parent.collectParts(randomSource, parts);

        for (BlockStateModelPart part : parts) {
            ChameleonBlockModelPart replacement = cache.get(part);
            if (replacement == null) {
                replacement = ChameleonServices.RENDER.createReplacementPart(part, material, layer);

                //if (cache.size() < 10)
                //    cache.put(part, replacement);
            }

            list.add(replacement);
        }
    }

    @Override
    public Material.Baked particleMaterial () {
        Material.Baked material = material();
        if (material == null)
            return super.particleMaterial();

        return material;
    }

    @Override
    public int materialFlags () {
        Material.Baked material = material();
        if (material == null)
            return super.materialFlags();

        Transparency transparency = ReplacementBlockPart.resolveTransparency(
            material, material.sprite().transparency(), layer);

        int flags = 0;
        if (ChunkSectionLayer.byTransparency(transparency).translucent())
            flags |= BakedQuad.FLAG_TRANSLUCENT;
        if (material.sprite().contents().isAnimated())
            flags |= BakedQuad.FLAG_ANIMATED;

        return flags;
    }
}
