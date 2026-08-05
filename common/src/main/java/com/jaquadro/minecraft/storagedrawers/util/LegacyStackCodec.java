package com.jaquadro.minecraft.storagedrawers.util;

import com.jaquadro.minecraft.storagedrawers.ModServices;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyStackCodec
{
    public static final Codec<ItemStack> CODEC = tolerant(ItemStack.CODEC, false);
    public static final Codec<ItemStack> OPTIONAL_CODEC = tolerant(ItemStack.OPTIONAL_CODEC, false);

    public static final Codec<ItemStack> PARKING_CODEC = tolerant(ItemStack.CODEC, true);

    private static final int V_COMPONENTIZATION = 3818;      // 1.20.5: {Count,tag} -> {count,components}
    private static final int V_ATTRIBUTE_MODIFIER_ID = 3945; // 1.21:   modifier uuid/name -> id
    private static final int V_FOOD_TO_CONSUMABLE = 4059;    // 1.21.2: food split into consumable
    private static final int V_LOCK_PREDICATE = 4068;        // 1.21.2: lock string -> predicate
    private static final int V_CUSTOM_MODEL_DATA = 4175;     // 1.21.4: custom_model_data number -> lists
    private static final int V_TOOLTIP_FLATTEN = 4290;       // 1.21.5: text unflattened (4290-4292), wrappers removed (4307)

    private static final int V_PRE_COMPONENT_FLOOR = 2975;

    private static final ThreadLocal<Deque<Integer>> SOURCE_VERSION = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();

    private LegacyStackCodec () { }

    public static void pushSourceDataVersion (int version) {
        SOURCE_VERSION.get().push(version);
    }

    public static void popSourceDataVersion () {
        SOURCE_VERSION.get().pop();
    }

    public static void reportUnreadable (CompoundTag raw) {
        String id = raw.getStringOr("id", raw.contains("Items") ? "<compacting drawer contents>" : "<unknown item>");
        if (!reported.add("unreadable:" + id))
            return;

        ModServices.log.error("Could not decode stored item '{}' in any known format. Its raw data has been "
            + "preserved and will be retried on every load -- it will reappear if a mod that understands it "
            + "is installed, but storing a new item in that slot will discard it.", id);
    }

    private static Codec<ItemStack> tolerant (Codec<ItemStack> base, boolean park) {
        return new Codec<ItemStack>() {
            @Override
            public <T> DataResult<Pair<ItemStack, T>> decode (DynamicOps<T> ops, T input) {
                DataResult<Pair<ItemStack, T>> strict = base.decode(ops, input);

                Dynamic<T> data = new Dynamic<>(ops, input);
                boolean preComponent = data.get("tag").result().isPresent()
                    || data.get("Count").result().isPresent();

                boolean lossy = strict.result().isPresent()
                    && (preComponent || lossyCleanDecode(data));

                if (strict.result().isPresent() && !lossy)
                    return strict;

                int from = sourceVersion(data, preComponent);
                if (from >= legacyShapeCeiling(data, preComponent)) {
                    from = evidenceVersion(data, preComponent);
                }
                if (from >= SharedConstants.WORLD_VERSION)
                    return strict;
                if (from < 0)
                    return lossy ? unrepairable(strict, park, data, "its source version could not be determined") : strict;

                T fixed;
                try {
                    fixed = DataFixers.getDataFixer()
                        .update(References.ITEM_STACK, data, from, SharedConstants.WORLD_VERSION)
                        .getValue();
                } catch (Exception e) {
                    ModServices.log.error("Vanilla data fixer failed on a stored item stack", e);
                    return lossy ? unrepairable(strict, park, data, "the vanilla data fixer failed on it") : strict;
                }

                DataResult<Pair<ItemStack, T>> retry = base.decode(ops, fixed);
                if (retry.result().isEmpty())
                    return lossy ? unrepairable(strict, park, data, "its repaired form still failed to decode") : strict;

                if (lossy && certainLegacyShape(new Dynamic<>(ops, fixed)))
                    return unrepairable(strict, park, data, "repair left its legacy shape in place");

                reportRepaired(data, from);
                return retry;
            }

            @Override
            public <T> DataResult<T> encode (ItemStack value, DynamicOps<T> ops, T prefix) {
                return base.encode(value, ops, prefix);
            }

            @Override
            public String toString () {
                return "LegacyTolerant[" + base + "]";
            }
        };
    }

    private static int sourceVersion (Dynamic<?> data, boolean preComponent) {
        Integer hinted = SOURCE_VERSION.get().peek();
        if (hinted != null && hinted > 0)
            return hinted;

        return evidenceVersion(data, preComponent);
    }

    private static int evidenceVersion (Dynamic<?> data, boolean preComponent) {
        if (preComponent)
            return V_PRE_COMPONENT_FLOOR;

        Optional<? extends Dynamic<?>> maybeComponents = data.get("components").result();
        if (maybeComponents.isEmpty()) {
            return data.get("id").result().isPresent() ? V_PRE_COMPONENT_FLOOR : -1;
        }
        Dynamic<?> components = maybeComponents.get();

        int needed = Integer.MAX_VALUE;

        if (vanillaWrapperShape(components) || adventurePredicateWrapper(components)
            || legacyJsonText(components) || anyShowInTooltip(components))
            needed = Math.min(needed, V_TOOLTIP_FLATTEN);

        if (components.get("minecraft:custom_model_data").asNumber().result().isPresent())
            needed = Math.min(needed, V_CUSTOM_MODEL_DATA);

        if (legacyFoodShape(components)
            || components.get("minecraft:fire_resistant").result().isPresent())
            needed = Math.min(needed, V_FOOD_TO_CONSUMABLE);

        if (components.get("minecraft:lock").asString().result().isPresent())
            needed = Math.min(needed, V_LOCK_PREDICATE);

        if (legacyAttributeEntries(components))
            needed = Math.min(needed, V_ATTRIBUTE_MODIFIER_ID);

        return needed == Integer.MAX_VALUE ? -1 : needed - 1;
    }

    private static boolean lossyCleanDecode (Dynamic<?> data) {
        Optional<? extends Dynamic<?>> maybeComponents = data.get("components").result();
        if (maybeComponents.isEmpty())
            return false;
        Dynamic<?> components = maybeComponents.get();

        return legacyFoodShape(components) || legacyJsonText(components)
            || anyShowInTooltip(components);
    }

    private static boolean legacyFoodShape (Dynamic<?> components) {
        Dynamic<?> food = components.get("minecraft:food").orElseEmptyMap();
        return food.get("eat_seconds").result().isPresent()
            || food.get("using_converts_to").result().isPresent()
            || food.get("effects").result().isPresent();
    }

    private static boolean vanillaWrapperShape (Dynamic<?> components) {
        return hasWrapper(components, "minecraft:enchantments", "levels")
            || hasWrapper(components, "minecraft:stored_enchantments", "levels")
            || hasWrapper(components, "minecraft:attribute_modifiers", "modifiers")
            || hasWrapper(components, "minecraft:dyed_color", "rgb")
            || hasWrapper(components, "minecraft:jukebox_playable", "song")
            || components.get("minecraft:hide_tooltip").result().isPresent()
            || components.get("minecraft:hide_additional_tooltip").result().isPresent();
    }

    private static boolean adventurePredicateWrapper (Dynamic<?> components) {
        return hasWrapper(components, "minecraft:can_place_on", "predicates")
            || hasWrapper(components, "minecraft:can_break", "predicates");
    }

    private static boolean legacyJsonText (Dynamic<?> components) {
        if (jsonLike(components.get("minecraft:custom_name").asString().result())
            || jsonLike(components.get("minecraft:item_name").asString().result()))
            return true;

        return components.get("minecraft:lore").asStreamOpt().result()
            .map(s -> s.anyMatch(e -> jsonLike(e.asString().result())))
            .orElse(false);
    }

    private static boolean jsonLike (Optional<String> value) {
        if (value.isEmpty())
            return false;

        String s = value.get().trim();
        return (s.startsWith("{\"") && s.endsWith("}"))
            || (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""));
    }

    private static int legacyShapeCeiling (Dynamic<?> data, boolean preComponent) {
        int ceiling = Integer.MAX_VALUE;
        if (preComponent)
            ceiling = V_COMPONENTIZATION;

        Optional<? extends Dynamic<?>> components = data.get("components").result();
        if (components.isPresent()) {
            if (legacyFoodShape(components.get()))
                ceiling = Math.min(ceiling, V_FOOD_TO_CONSUMABLE);
            if (vanillaWrapperShape(components.get()))
                ceiling = Math.min(ceiling, V_TOOLTIP_FLATTEN);
        }
        return ceiling;
    }

    private static boolean certainLegacyShape (Dynamic<?> data) {
        if (data.get("tag").result().isPresent() || data.get("Count").result().isPresent())
            return true;

        return data.get("components").result()
            .map(c -> legacyFoodShape(c) || vanillaWrapperShape(c))
            .orElse(false);
    }

    private static boolean hasWrapper (Dynamic<?> components, String component, String wrapper) {
        return components.get(component).orElseEmptyMap().get(wrapper).result().isPresent();
    }

    private static boolean anyShowInTooltip (Dynamic<?> components) {
        return components.getMapValues().result().map(map ->
            map.values().stream().anyMatch(v -> v.get("show_in_tooltip").result().isPresent())
        ).orElse(false);
    }

    private static boolean legacyAttributeEntries (Dynamic<?> components) {
        Dynamic<?> attrs = components.get("minecraft:attribute_modifiers").orElseEmptyMap();
        Dynamic<?> entries = attrs.get("modifiers").result().isPresent()
            ? attrs.get("modifiers").orElseEmptyMap() : attrs;
        return entries.asStreamOpt().result().map(stream ->
            stream.anyMatch(e -> e.get("uuid").result().isPresent()
                || (e.get("name").asString().result().isPresent() && e.get("id").result().isEmpty()))
        ).orElse(false);
    }

    private static <R> DataResult<R> unrepairable (DataResult<R> lossySuccess, boolean park, Dynamic<?> data, String reason) {
        String id = data.get("id").asString().result().orElse("<unknown item>");
        if (park) {
            if (reported.add("lossypark:" + id))
                ModServices.log.warn("Stored item '{}' is in a legacy format that could not be repaired ({}); "
                    + "refusing to load it with data missing. Its raw data is parked and retried on every "
                    + "load.", id, reason);
            return DataResult.error(() -> "LegacyStackCodec: lossy decode refused: " + reason,
                lossySuccess.result().orElseThrow());
        }

        if (reported.add("lossyaccept:" + id))
            ModServices.log.warn("Stored item '{}' is in a legacy format that could not be repaired ({}); "
                + "it was loaded with its unrepaired legacy fields dropped.", id, reason);
        return lossySuccess;
    }

    private static void reportRepaired (Dynamic<?> data, int from) {
        String id = data.get("id").asString().result().orElse("<unknown item>");
        if (!reported.add("repaired:" + id))
            return;

        ModServices.log.warn("Upgraded stored item '{}' from data version {} while loading saved contents. "
            + "Vanilla's data fixer does not reach item stacks held by modded block entities, so this mod "
            + "runs it explicitly; the stack will be saved in the current format.", id, from);
    }
}
