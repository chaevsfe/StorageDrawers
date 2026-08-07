package com.jaquadro.minecraft.storagedrawers.client.model;

import com.google.common.base.Suppliers;
import com.jaquadro.minecraft.storagedrawers.ModConstants;
import com.jaquadro.minecraft.storagedrawers.ModServices;
import com.jaquadro.minecraft.storagedrawers.block.tile.modelprops.DrawerModelProperties;
import com.jaquadro.minecraft.storagedrawers.block.tile.modelprops.FramedModelProperties;
import com.jaquadro.minecraft.storagedrawers.block.tile.tiledata.MaterialData;
import com.jaquadro.minecraft.storagedrawers.client.model.context.ModelContext;
import com.jaquadro.minecraft.storagedrawers.client.model.decorator.DecoratorRenderType;
import com.jaquadro.minecraft.storagedrawers.client.model.decorator.ModelDecorator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PlatformDecoratedModel<C extends ModelContext> extends ParentModel implements DynamicBlockStateModel
{
    private final ModelDecorator<C> decorator;
    private final ModelContextSupplier<C> contextSupplier;
    private final ItemStack stack;

    private static final List<DecoratorRenderType> decoratorRenderTypes = List.of(DecoratorRenderType.SOLID, DecoratorRenderType.CUTOUT, DecoratorRenderType.TRANSLUCENT);

    public PlatformDecoratedModel (BlockStateModel parent, ModelDecorator<C> decorator, ModelContextSupplier<C> contextSupplier) {
        super(parent);
        this.decorator = decorator;
        this.contextSupplier = contextSupplier;
        this.stack = null;
    }

    public PlatformDecoratedModel (PlatformDecoratedModel<C> p, ItemStack stack) {
        super(p.parent);
        this.decorator = p.decorator;
        this.contextSupplier = p.contextSupplier;
        this.stack = stack;
    }

    @Override
    public void collectParts (BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        Object renderData = renderData(level, pos);
        if (renderData == null) {
            parent.collectParts(level, pos, state, random, parts);
            return;
        }

        Supplier<C> supplier = () -> contextSupplier.makeContext(state, random, renderData);

        if (decorator.shouldRenderBase(supplier))
            parent.collectParts(level, pos, state, random, parts);

        Consumer<BlockStateModel> emitModel = (model) -> {
            if (model != null)
                model.collectParts(level, pos, state, random, parts);
        };

        try {
            for (DecoratorRenderType renderType : decoratorRenderTypes)
                decorator.emitQuads(supplier, emitModel, renderType);
        } catch (Exception e) {
            ModServices.reportOnce("PlatformDecoratedModel.collectParts", e);
        }
    }

    @Override
    public Material.Baked particleMaterial (BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Object renderData = renderData(level, pos);
        MaterialData matData = null;
        if (renderData instanceof DrawerModelProperties drawerProps)
            matData = new MaterialData(drawerProps.material);
        else if (renderData instanceof FramedModelProperties frameProps)
            matData = new MaterialData(frameProps.material);

        if (matData != null) {
            ItemStack side = matData.getEffectiveSide();
            if (side != ItemStack.EMPTY) {
                if (side.getItem() instanceof BlockItem blockItem) {
                    return Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                        .getParticleMaterial(blockItem.getBlock().defaultBlockState());
                }
            }
        }

        return particleMaterial();
    }

    private static @Nullable Object renderData (@Nullable BlockAndTintGetter level, BlockPos pos) {
        if (level == null)
            return null;
        return level.getModelData(pos).get(NeoforgeModelData.RENDER_DATA);
    }

    public static class PlatformDecoratedItemModel implements ItemModel
    {
        private final Identifier location;
        private final String variant;
        private final ModelRenderProperties properties;
        private final Matrix4fc transform;
        private final Supplier<Vector3fc[]> extents;

        PlatformDecoratedModel<? extends ModelContext> parent;
        BlockStateModel model;
        ItemStack stack;
        BlockState state;

        public PlatformDecoratedItemModel (Identifier location, String variant, ModelRenderProperties properties, Matrix4fc transform) {
            this.location = location;
            this.variant = variant;
            this.properties = properties;
            this.transform = transform;

            this.extents = Suppliers.memoize(() -> {
                Vector3fc[] ext = new Vector3fc[2];
                ext[0] = new Vector3f(0.0f, 0.0f, 0.0f);
                ext[1] = new Vector3f(1.0f, 1.0f, 1.0f);
                return ext;
            });
        }

        @Override
        public void update (ItemStackRenderState itemStackRenderState, ItemStack itemStack, ItemModelResolver itemModelResolver, ItemDisplayContext itemDisplayContext, @Nullable ClientLevel clientLevel, @Nullable ItemOwner itemOwner, int i) {
            itemStackRenderState.appendModelIdentityElement(this);
            itemStackRenderState.appendModelIdentityElement(itemStack);

            if (state == null) {
                var blockOption = BuiltInRegistries.BLOCK.get(location);
                if (blockOption.isEmpty())
                    return;

                Block block = blockOption.get().value();
                state = block.defaultBlockState();

                String[] props = variant.split(",");
                for (String prop : props) {
                    String[] keyVal = prop.split("=");
                    if (keyVal.length != 2) continue;

                    String key = keyVal[0].trim();
                    String value = keyVal[1].trim();

                    Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                    if (property != null)
                        state = setProperty(state, property, value);
                }
            }

            if (parent == null) {
                BlockStateModel stored = ItemModelStore.models.get(state);
                if (stored instanceof PlatformDecoratedModel<?> p)
                    parent = p;
            }

            if ((stack == null || !ItemStack.isSameItemSameComponents(stack, itemStack)) && parent != null) {
                stack = itemStack;
                model = new PlatformDecoratedModel<>(parent, itemStack);
            }

            if (model != null) {
                Map<DecoratorRenderType, ItemStackRenderState.LayerRenderState> layers = new HashMap<>();
                for (var renderType : decoratorRenderTypes) {
                    List<BlockStateModelPart> parts = new ArrayList<>();
                    Consumer<BlockStateModel> emitModel = (m) -> {
                        if (m != null)
                            m.collectParts(null, parts);
                    };

                    @SuppressWarnings("unchecked")
                    PlatformDecoratedModel<ModelContext> pd = (PlatformDecoratedModel<ModelContext>) parent;
                    Supplier<ModelContext> supplier = () -> pd.contextSupplier.makeContext(stack);
                    if (renderType == DecoratorRenderType.SOLID && pd.decorator.shouldRenderBase(supplier, stack))
                        emitModel.accept(pd.parent);
                    pd.decorator.emitItemQuads(supplier, emitModel, stack, renderType);

                    if (parts.isEmpty())
                        continue;

                    if (!layers.containsKey(renderType)) {
                        ItemStackRenderState.LayerRenderState renderState = itemStackRenderState.newLayer();
                        layers.put(renderType, renderState);

                        renderState.setExtents(extents);
                        renderState.setLocalTransform(transform);
                    }

                    for (BlockStateModelPart part : parts) {
                        ItemStackRenderState.LayerRenderState layer = layers.get(renderType);
                        properties.applyToLayer(layer, itemDisplayContext);

                        layer.prepareQuadList().addAll(part.getQuads(null));
                        for (Direction direction : Direction.values())
                            layer.prepareQuadList().addAll(part.getQuads(direction));
                    }
                }
            }
        }

        private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String valueName) {
            Optional<T> parsed = property.getValue(valueName);
            return parsed.map(v -> state.setValue(property, v)).orElse(state);
        }

        public record Unbaked (Identifier model, String variant) implements ItemModel.Unbaked {
            public static final MapCodec<PlatformDecoratedItemModel.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec((builder) ->
                builder.group(
                    Identifier.CODEC.fieldOf("model").forGetter(PlatformDecoratedItemModel.Unbaked::model),
                    Codec.STRING.fieldOf("variant").forGetter(PlatformDecoratedItemModel.Unbaked::variant)
                ).apply(builder, PlatformDecoratedItemModel.Unbaked::new)
            );

            @Override
            public MapCodec<? extends ItemModel.Unbaked> type () {
                return MAP_CODEC;
            }

            @Override
            public ItemModel bake (BakingContext bakingContext, Matrix4fc transform) {
                ModelBaker modelbaker = bakingContext.blockModelBaker();
                ResolvedModel resolvedmodel = modelbaker.getModel(Identifier.fromNamespaceAndPath(ModConstants.MOD_ID, "block/oak_full_drawers_2"));
                TextureSlots textureslots = resolvedmodel.getTopTextureSlots();

                ModelRenderProperties modelrenderproperties = ModelRenderProperties.fromResolvedModel(modelbaker, resolvedmodel, textureslots);
                return new PlatformDecoratedItemModel(model, variant, modelrenderproperties, transform);
            }

            @Override
            public void resolveDependencies (Resolver resolver) {
            }
        }
    }
}
