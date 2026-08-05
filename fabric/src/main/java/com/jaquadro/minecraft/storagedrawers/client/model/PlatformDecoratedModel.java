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
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Fabric binding for a decorated block model. The Fabric-specific surface is the FRAPI
 * {@link FabricBlockStateModel} interface; everything else lives in :common.
 *
 * The Fabric API is on the compile classpath as a plain {@code implementation} dependency and
 * declares no injected interfaces, so vanilla types do not carry FRAPI's interfaces at compile
 * time. Every call into one therefore goes through an explicit cast: FRAPI's own mixins add
 * {@code FabricBlockStateModel} to {@code BlockStateModel} and {@code FabricBlockGetter} to
 * {@code BlockGetter}, so the casts always succeed at runtime.
 */
@Environment(EnvType.CLIENT)
public class PlatformDecoratedModel<C extends ModelContext> extends ParentModel implements FabricBlockStateModel
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

    /**
     * Every pass is emitted through FRAPI's own default {@code emitQuads}, which walks the six
     * cullable faces plus the unculled bucket, honours the cull test and applies vanilla's
     * ambient-occlusion and shade-mode defaults.
     *
     * There is deliberately no per-pass render layer forced onto the emitter any more. As of 26.2
     * the chunk section layer and the item sheet are properties of the quad, not of the emitter:
     * {@code QuadEmitter.fromBakedQuad} stamps both from the quad's own
     * {@code BakedQuad.MaterialInfo}, overwriting whatever the emitter was set to beforehand. The
     * layer is chosen instead where the quad is built -- see
     * {@code ReplacementBlockPart.resolveTransparency} in :common, which already honours the
     * material's opacity and the renderTranslucentMaterials config.
     */
    @Override
    public void emitQuads (QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
        if (state == null) {
            ((FabricBlockStateModel)parent).emitQuads(emitter, blockView, pos, null, random, cullTest);
            return;
        }

        if (blockView == null)
            return;

        Object renderData = ((FabricBlockGetter)blockView).getBlockEntityRenderData(pos);
        Supplier<C> supplier = () -> contextSupplier.makeContext(state, random, renderData);

        if (decorator.shouldRenderBase(supplier))
            ((FabricBlockStateModel)parent).emitQuads(emitter, blockView, pos, state, random, cullTest);

        Consumer<BlockStateModel> emitModel = (model) -> {
            if (model != null)
                ((FabricBlockStateModel)model).emitQuads(emitter, blockView, pos, state, random, cullTest);
        };

        try {
            for (DecoratorRenderType renderType : decoratorRenderTypes)
                decorator.emitQuads(supplier, emitModel, renderType);
        } catch (Exception e) {
            // The entire framed/decorated geometry path. If this fires, drawers render
            // as bare or missing blocks with no other symptom.
            ModServices.reportOnce("PlatformDecoratedModel.emitQuads", e);
        }
    }

    @Override
    public Material.Baked particleMaterial (BlockAndTintGetter blockView, BlockPos pos, BlockState state) {
        if (blockView == null)
            return particleMaterial();

        Object renderData = ((FabricBlockGetter)blockView).getBlockEntityRenderData(pos);
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

            // Resolve the baked parent BEFORE building the model, not after. ItemModelStore is filled
            // during bake, so the parent is always available by the time anything renders -- but with
            // the old order the very first update() for a stack found parent still null, emitted zero
            // layers, and GuiItemAtlas cached that empty result under a key it then marks READY for
            // the life of that stack instance. The result was a permanently blank GUI icon.
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
                    Consumer<BlockStateModel> emitModel = (model) -> {
                        if (model != null)
                            model.collectParts(null, parts);
                    };

                    PlatformDecoratedModel<ModelContext> pd = (PlatformDecoratedModel<ModelContext>) parent;
                    Supplier<ModelContext> supplier = () -> pd.contextSupplier.makeContext(stack);
                    pd.decorator.emitItemQuads(supplier, emitModel, stack, renderType);

                    if (parts.isEmpty())
                        continue;

                    if (!layers.containsKey(renderType)) {
                        ItemStackRenderState.LayerRenderState renderState = itemStackRenderState.newLayer();
                        layers.put(renderType, renderState);

                        // No setRenderType any more: LayerRenderState.submit hands the raw quad
                        // list to SubmitNodeCollector.submitItem and each quad's
                        // MaterialInfo.itemRenderType() selects its own sheet.
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

            /**
             * @param transform the accumulated local transform of any enclosing composite model.
             *     It is handed straight to the layer, as CuboidItemModelWrapper does; ModelBakery
             *     passes identity for a top-level model.
             */
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
                // Made from meta parts, nothing to resolve
            }
        }
    }
}
