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

/**
 * Tolerant {@link ItemStack} codecs for data this mod owns on disk.
 *
 * Vanilla's DataFixerUpper has no schema for modded NBT, so it upgrades the chunk around a
 * drawer but never the item stacks inside it. A world carried across Minecraft versions
 * therefore hands us stacks in whatever shape the old version wrote, and a strict decode
 * failure costs the whole drawer, not one item: identity is stored once and quantity
 * separately, so one rejected stack empties the slot no matter how many items it held.
 *
 * Rather than replicate Mojang's migrations by hand, this codec runs the stack through
 * vanilla's own fixer chain ({@code DataFixers.getDataFixer()}, type ITEM_STACK) — the same
 * code that upgrades player inventories — and the only judgement call left is which data
 * version the stack was written by:
 *
 * 1. Every save stamps the block entity with the current data version, so anything written
 *    by this mod version onward migrates precisely, including across future updates.
 * 2. Unstamped data (anything written before this mod version) is dated by shape evidence:
 *    a pre-component {@code tag}/{@code Count} stack, or a component whose old wrapper is
 *    still present. Evidence-based dating matters because several vanilla fixes are NOT
 *    idempotent — CustomModelDataExpandFix, AttributeModifierIdFix and FoodToConsumableFix
 *    each corrupt data that is already in the new shape, and the corrupted result still
 *    decodes, so "retry until the parse succeeds" would silently accept mangled items. A
 *    stack is written atomically by one game version, so its shapes are internally
 *    consistent and the newest fix its evidence demands is a safe starting point.
 * 3. No stamp and no evidence means the stack is current-format and failed for a reason no
 *    vanilla fix can address (typically a component from a removed or changed mod). It is
 *    left to the caller, and the drawer groups park the raw NBT so the bytes survive every
 *    save until something can read them again.
 *
 * The strict decode always runs first, so valid data never takes any of this. When a repair
 * does not produce a decodable stack, a failed strict decode is returned as the ORIGINAL
 * error, never a new one. A strict decode that only succeeded lossily (markers above) is
 * handled per caller class: {@link #PARKING_CODEC} refuses it so the drawer stores park the
 * raw bytes verbatim, while {@link #CODEC}/{@link #OPTIONAL_CODEC} -- used embedded in
 * component codecs, where an error would cost the whole surrounding component -- accept it
 * as they always did, now with a log line naming the loss.
 */
public final class LegacyStackCodec
{
    public static final Codec<ItemStack> CODEC = tolerant(ItemStack.CODEC, false);
    public static final Codec<ItemStack> OPTIONAL_CODEC = tolerant(ItemStack.OPTIONAL_CODEC, false);

    /** For callers that PARK raw NBT when the parse fails (the drawer stores): a lossy decode
     *  whose repair fails is refused with an error instead of accepted stripped, so the caller
     *  preserves the original bytes. Everyone else keeps {@link #CODEC}: without a park, an
     *  error would cost the whole surrounding component where acceptance costs one field. */
    public static final Codec<ItemStack> PARKING_CODEC = tolerant(ItemStack.CODEC, true);

    // Data versions of the vanilla fixes that legacy shape evidence maps to. Each constant is
    // the version the fix is REGISTERED at (read from DataFixers.addFixers bytecode in the
    // 26.2 jar), and the chain is started one version below the oldest fix the evidence
    // demands. V_TOOLTIP_FLATTEN is deliberately the START of the 1.21.5 fix span (text
    // unflattening at 4290) rather than TooltipDisplayComponentFix's own 4307: wrapper-shaped
    // data in practice comes from 1.21.4 release saves, whose text components are still JSON
    // strings and need the whole span.
    private static final int V_COMPONENTIZATION = 3818;      // 1.20.5: {Count,tag} -> {count,components}
    private static final int V_ATTRIBUTE_MODIFIER_ID = 3945; // 1.21:   modifier uuid/name -> id
    private static final int V_FOOD_TO_CONSUMABLE = 4059;    // 1.21.2: food split into consumable
    private static final int V_LOCK_PREDICATE = 4068;        // 1.21.2: lock string -> predicate
    private static final int V_CUSTOM_MODEL_DATA = 4175;     // 1.21.4: custom_model_data number -> lists
    private static final int V_TOOLTIP_FLATTEN = 4290;       // 1.21.5: text unflattened (4290-4292), wrappers removed (4307)

    // Oldest chain start for pre-component {tag}-shaped stacks (1.18.2). Fixes older than
    // this are outside anything a drawer from a supported world can contain.
    private static final int V_PRE_COMPONENT_FLOOR = 2975;

    private static final ThreadLocal<Deque<Integer>> SOURCE_VERSION = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();

    private LegacyStackCodec () { }

    /** Set by {@link com.jaquadro.minecraft.storagedrawers.block.tile.BaseBlockEntity} around
     *  load, from the DataVersion this mod stamps on every save. -1 when absent. */
    public static void pushSourceDataVersion (int version) {
        SOURCE_VERSION.get().push(version);
    }

    public static void popSourceDataVersion () {
        SOURCE_VERSION.get().pop();
    }

    /** One loud line per item id when raw NBT is parked instead of decoded. */
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
                // Some legacy shapes decode SUCCESSFULLY under the modern codec while losing
                // data: a pre-component stack ({id, Count, tag}) parses as count=1 with tag
                // ignored, legacy food's eat_seconds/effects are unknown keys the record codec
                // skips, and show_in_tooltip flags are dropped instead of migrating to
                // tooltip_display. These markers force the fixer path despite strict success.
                boolean preComponent = data.get("tag").result().isPresent()
                    || data.get("Count").result().isPresent();

                // A lossy strict success is a decode that dropped data on the floor. Every
                // repair-failure exit below must route it through unrepairable(), which
                // refuses it (parking callers) or at least logs the loss -- never accept
                // it silently.
                boolean lossy = strict.result().isPresent()
                    && (preComponent || lossyCleanDecode(data));

                if (strict.result().isPresent() && !lossy)
                    return strict;

                int from = sourceVersion(data, preComponent);
                if (from >= legacyShapeCeiling(data, preComponent)) {
                    // A stamp is written per block entity, but parked bytes -- and item NBT
                    // merged onto a freshly-placed block entity -- ride under a stamp that
                    // never re-encoded them, and stamps go stale as the game updates while
                    // WORLD_VERSION moves on. No stack carrying a certainly-legacy shape can
                    // genuinely have been written at or after the fix that CONSUMES that
                    // shape, so any such claimed version is discarded in favor of the
                    // stack's own shape evidence. A bare show_in_tooltip never triggers
                    // this: a modded component may carry that field legitimately.
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

                // A repair that leaves a certainly-legacy shape in place repaired nothing --
                // the chain started past the fixes the shape needs (a wrong or stale hint).
                // Returning it would launder still-lossy data as a success.
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

    /** The data version to run the fixer chain from, or -1 to not attempt one. */
    private static int sourceVersion (Dynamic<?> data, boolean preComponent) {
        Integer hinted = SOURCE_VERSION.get().peek();
        if (hinted != null && hinted > 0)
            return hinted;

        return evidenceVersion(data, preComponent);
    }

    /** Shape-evidence dating only, ignoring any stamped hint. */
    private static int evidenceVersion (Dynamic<?> data, boolean preComponent) {
        if (preComponent)
            return V_PRE_COMPONENT_FLOOR;

        Optional<? extends Dynamic<?>> maybeComponents = data.get("components").result();
        if (maybeComponents.isEmpty()) {
            // A failing stack with NO components is legacy by definition -- typically an item
            // id that vanilla renamed since (e.g. minecraft:chain -> iron_chain in 26.x).
            // With nothing mangle-able in it, the full chain is safe.
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

    // Shapes that the modern codec accepts while silently discarding data. Kept narrow: this
    // runs on every successful drawer load, and a false positive costs one harmless fixer
    // pass, while a false negative silently loses a field.
    private static boolean lossyCleanDecode (Dynamic<?> data) {
        Optional<? extends Dynamic<?>> maybeComponents = data.get("components").result();
        if (maybeComponents.isEmpty())
            return false;
        Dynamic<?> components = maybeComponents.get();

        return legacyFoodShape(components) || legacyJsonText(components)
            || anyShowInTooltip(components);
    }

    // Fields FoodToConsumableFix removes from minecraft:food. Shared by the lossy-marker and
    // evidence scans so the two sets cannot drift apart: any shape that forces the fixer path
    // must also be datable by the evidence scan, or the forced repair has no starting version
    // and the stack is refused (unrepairable) instead of accepted stripped.
    private static boolean legacyFoodShape (Dynamic<?> components) {
        Dynamic<?> food = components.get("minecraft:food").orElseEmptyMap();
        return food.get("eat_seconds").result().isPresent()
            || food.get("using_converts_to").result().isPresent()
            || food.get("effects").result().isPresent();
    }

    // Old wrapper forms of vanilla-owned components, all consumed by the 1.21.5 fix span
    // starting at V_TOOLTIP_FLATTEN. Unlike a bare show_in_tooltip field (which a modded
    // component may carry), these shapes cannot occur in current-format data. Deliberately
    // NOT here: can_place_on/can_break -- the MODERN AdventureModePredicate encodes a
    // single-element list as a bare BlockPredicate map whose inlined DataComponentMatchers
    // has a legitimate top-level "predicates" field, so that wrapper is evidence only
    // (adventurePredicateWrapper), never certainty.
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

    // 1.20.5-1.21.4 text components serialize as JSON strings, which the modern codec
    // accepts as LITERALS -- an anvil-renamed item migrates with its name shown as raw
    // JSON. Kept narrow to the two shapes vanilla actually wrote ('{"...} objects and
    // "..."-quoted strings) because a modern literal name could legitimately look
    // JSON-ish; for the same reason this is evidence, never certainty -- a stamp wins.
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

    /** The registration version of the OLDEST vanilla fix that consumes any certainly-legacy
     *  shape this stack carries -- no stack with that shape can genuinely have been written
     *  at or after it, so a claimed version there is a lie (a stale or unearned stamp).
     *  MAX_VALUE when the stack has no certain shape, so the claim always stands. */
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

    /** Whether the stack carries a shape that CANNOT occur in current-format data. */
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

    // 1.20.5-1.21 attribute modifier entries carry uuid/name instead of id. Entries live
    // under "modifiers" pre-1.21.5 or directly as a list after; check both.
    private static boolean legacyAttributeEntries (Dynamic<?> components) {
        Dynamic<?> attrs = components.get("minecraft:attribute_modifiers").orElseEmptyMap();
        Dynamic<?> entries = attrs.get("modifiers").result().isPresent()
            ? attrs.get("modifiers").orElseEmptyMap() : attrs;
        return entries.asStreamOpt().result().map(stream ->
            stream.anyMatch(e -> e.get("uuid").result().isPresent()
                || (e.get("name").asString().result().isPresent() && e.get("id").result().isEmpty()))
        ).orElse(false);
    }

    /** A strict decode that succeeded only by dropping data reached a failed repair. For the
     *  parking codec, refuse it: the returned error makes the caller park the raw NBT, and it
     *  still carries the stripped stack as a partial value for any reader that recovers
     *  partials. For everyone else -- callers embedded in component codecs, where an error
     *  costs the whole surrounding component instead of one field -- accept it exactly as
     *  before, but say so in the log. */
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
