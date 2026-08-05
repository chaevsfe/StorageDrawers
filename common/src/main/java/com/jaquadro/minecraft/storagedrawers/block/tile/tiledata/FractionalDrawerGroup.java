package com.jaquadro.minecraft.storagedrawers.block.tile.tiledata;

import com.jaquadro.minecraft.storagedrawers.ModServices;
import com.jaquadro.minecraft.storagedrawers.api.storage.*;
import com.jaquadro.minecraft.storagedrawers.api.storage.attribute.LockAttribute;
import com.jaquadro.minecraft.storagedrawers.capabilities.Capabilities;
import com.jaquadro.minecraft.storagedrawers.config.ModCommonConfig;
import com.jaquadro.minecraft.storagedrawers.config.StorageBlacklist;
import com.jaquadro.minecraft.storagedrawers.inventory.ItemStackHelper;
import com.jaquadro.minecraft.storagedrawers.item.ItemDetachedDrawer;
import com.jaquadro.minecraft.storagedrawers.item.ItemDrawers;
import com.jaquadro.minecraft.storagedrawers.util.CompactingHelper;
import com.jaquadro.minecraft.storagedrawers.util.ItemStackMatcher;
import com.jaquadro.minecraft.storagedrawers.util.ItemStackTagMatcher;
import com.jaquadro.minecraft.storagedrawers.util.LegacyStackCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

import java.util.Stack;
import java.util.function.Predicate;

public class FractionalDrawerGroup extends BlockEntityDataShim implements IDrawerGroup
{
    private final FractionalStorage storage;
    private final FractionalDrawer[] slots;
    private final int[] order;

    public FractionalDrawerGroup (int slotCount) {

        storage = new FractionalStorage(this, slotCount);

        slots = new FractionalDrawer[slotCount];
        order = new int[slotCount];

        for (int i = 0; i < slotCount; i++) {
            slots[i] = new FractionalDrawer(storage, i);
            order[i] = i;
        }
    }

    @Override
    public int getDrawerCount () {
        return slots.length;
    }

    @NotNull
    @Override
    public IFractionalDrawer getDrawer (int slot) {
        if (slot < 0 || slot >= slots.length)
            return Drawers.DISABLED_FRACTIONAL;

        return slots[slot];
    }

    @Override
    public int[] getAccessibleDrawerSlots () {
        return order;
    }

    public int getPooledCount () {
        return storage.getPooledCount();
    }

    public void setPooledCount (int count) {
        storage.setPooledCount(count);
    }

    @Override
    public void read (ValueInput input) {
        input.child("Drawers").ifPresent(storage::deserializeNBT);
    }

    @Override
    public void write (ValueOutput output) {
        storage.serializeNBT(output.child("Drawers"));
    }

    public void syncAttributes () {
        storage.syncAttributes();
    }

    protected Level getWorld () { return null; }

    protected void log (String message) { }

    protected int getStackCapacity () {
        return 0;
    }

    protected void onItemChanged () { }

    protected void onAmountChanged () { }

    private static class FractionalStorage
    {
        private final FractionalDrawerGroup group;
        private final int slotCount;
        private final ItemStack[] protoStack;
        private final int[] convRate;
        private final ItemStackMatcher[] matchers;
        private int pooledCount;

        private CompoundTag unreadablePayload;

        boolean hasUnreadablePayload () {
            return unreadablePayload != null;
        }

        private ItemStack cacheKey;
        private final ItemStack[] cachedProtoStack;
        private final int[] cachedConvRate;
        private final ItemStackMatcher[] cachedMatchers;

        IDrawerAttributes cachedAttrs;

        public FractionalStorage (FractionalDrawerGroup group, int slotCount) {
            cacheKey = ItemStack.EMPTY;

            this.group = group;
            this.slotCount = slotCount;

            protoStack = new ItemStack[slotCount];
            matchers = new ItemStackMatcher[slotCount];

            cachedProtoStack = new ItemStack[slotCount];
            cachedMatchers = new ItemStackMatcher[slotCount];

            for (int i = 0; i < slotCount; i++) {
                protoStack[i] = ItemStack.EMPTY;
                matchers[i] = ItemStackMatcher.EMPTY;

                cachedProtoStack[i] = ItemStack.EMPTY;
                cachedMatchers[i] = ItemStackMatcher.EMPTY;
            }

            convRate = new int[slotCount];
            cachedConvRate = new int[slotCount];
        }

        @NotNull
        public IDrawerAttributes getAttributes() {
            if (cachedAttrs != null)
                return cachedAttrs;

            cachedAttrs = group.getCapability(Capabilities.DRAWER_ATTRIBUTES);
            if (cachedAttrs != null)
                return cachedAttrs;

            return EmptyDrawerAttributes.EMPTY;
        }

        public int getPooledCount () {
            return pooledCount;
        }

        public void setPooledCount (int count) {
            if (pooledCount != count) {
                pooledCount = count;
                group.onAmountChanged();
            }
        }

        @NotNull
        public ItemStack getStack (int slot) {
            return protoStack[slot];
        }

        @NotNull
        public ItemStack baseStack () {
            return protoStack[0];
        }

        public int baseRate () {
            return convRate[0];
        }

        public IFractionalDrawer setStoredItem (int slot, @NotNull ItemStack itemPrototype) {
            itemPrototype = ItemStackHelper.getItemPrototype(itemPrototype);
            if (itemPrototype.isEmpty()) {
                reset();
                return group.getDrawer(slot);
            }

            if (baseRate() == 0) {
                populateSlots(itemPrototype);
                for (int i = 0; i < slotCount; i++) {
                    if (ItemStackMatcher.areItemsEqual(protoStack[i], itemPrototype)) { // TODO: ItemStackOreMatcher
                        slot = i;
                        pooledCount = 0;
                    }
                }

                group.onItemChanged();
            }

            return group.getDrawer(slot);
        }

        public int getStoredCount (int slot) {
            if (convRate[slot] == 0)
                return 0;

            IDrawerAttributes attrs = getAttributes();
            if (attrs.isUnlimitedVending())
                return Integer.MAX_VALUE;

            return pooledCount / convRate[slot];
        }

        public void setStoredItemCount (int slot, int amount) {
            if (convRate[slot] == 0)
                return;

            IDrawerAttributes attrs = getAttributes();
            if (attrs.isUnlimitedVending())
                return;

            int oldCount = pooledCount;

            long newCount = (pooledCount % convRate[slot]) + (long) convRate[slot] * amount;
            long poolMax = (long) getMaxCapacity(0) * convRate[0];
            pooledCount = (int) Math.max(0, Math.min(newCount, Math.min(poolMax, Integer.MAX_VALUE)));

            if (pooledCount == oldCount)
                return;

            if (pooledCount == 0 && !attrs.isItemLocked(LockAttribute.LOCK_POPULATED))
                reset();
            else
                group.onAmountChanged();
        }

        public int adjustStoredItemCount (int slot, int amount) {
            if (convRate[slot] == 0 || amount == 0)
                return Math.abs(amount);

            IDrawerAttributes attrs = getAttributes();
            if (amount > 0) {
                if (attrs.isUnlimitedVending())
                    return 0;

                int poolMax = getMaxCapacity(0) * convRate[0];
                if (poolMax < 0)
                    poolMax = Integer.MAX_VALUE;

                int canAdd = (poolMax - pooledCount) / convRate[slot];
                int willAdd = Math.min(amount, canAdd);
                if (willAdd > 0) {
                    pooledCount += convRate[slot] * willAdd;
                    group.onAmountChanged();
                }

                if (attrs.isVoid())
                    return 0;

                return amount - willAdd;
            }
            else {
                amount = -amount;

                int canRemove = pooledCount / convRate[slot];
                int willRemove = Math.min(amount, canRemove);
                if (willRemove == 0)
                    return amount;

                pooledCount -= willRemove * convRate[slot];

                if (pooledCount == 0 && !attrs.isItemLocked(LockAttribute.LOCK_POPULATED))
                    reset();
                else
                    group.onAmountChanged();

                return amount - willRemove;
            }
        }

        public int getMaxCapacity (int slot) {
            if (baseStack().isEmpty() || convRate[slot] == 0)
                return 0;

            IDrawerAttributes attrs = getAttributes();
            if (attrs.isUnlimitedStorage() || attrs.isUnlimitedVending())
                return Integer.MAX_VALUE / convRate[slot];

            int maxSize = ItemStackHelper.getMaxStackSize(baseStack());
            try {
                int cap = Math.multiplyExact(maxSize * baseRate(), group.getStackCapacity());
                return cap / convRate[slot];
            } catch (ArithmeticException e) {
                return Integer.MAX_VALUE / convRate[slot];
            }
        }

        public int getMaxCapacity (int slot, @NotNull ItemStack itemPrototype) {
            IDrawerAttributes attrs = getAttributes();
            if (attrs.isUnlimitedStorage() || attrs.isUnlimitedVending()) {
                if (convRate[slot] == 0)
                    return Integer.MAX_VALUE;
                return Integer.MAX_VALUE / convRate[slot];
            }

            if (baseStack().isEmpty()) {
                int itemStackLimit = 64;
                if (!itemPrototype.isEmpty())
                    itemStackLimit = ItemStackHelper.getMaxStackSize(itemPrototype);

                try {
                    return Math.multiplyExact(itemStackLimit, group.getStackCapacity());
                } catch (ArithmeticException e) {
                    return Integer.MAX_VALUE;
                }
            }

            if (ItemStackMatcher.areItemsEqual(protoStack[slot], itemPrototype)) // TODO: ItemStackOreMatcher
                return getMaxCapacity(slot);

            return 0;
        }

        public int getAcceptingMaxCapacity (int slot, @NotNull ItemStack itemPrototype) {
            IDrawerAttributes attrs = getAttributes();
            if (attrs.isVoid())
                return Integer.MAX_VALUE;

            return getMaxCapacity(slot, itemPrototype);
        }

        public int getRemainingCapacity (int slot) {
            if (baseStack().isEmpty() || convRate[slot] == 0)
                return 0;

            IDrawerAttributes attrs = getAttributes();
            if (attrs.isUnlimitedVending())
                return Integer.MAX_VALUE;

            int rawMaxCapacity = getMaxCapacity(0) * baseRate();
            int rawRemaining = rawMaxCapacity - pooledCount;

            return rawRemaining / convRate[slot];
        }

        public int getAcceptingRemainingCapacity (int slot) {
            if (baseStack().isEmpty() || convRate[slot] == 0)
                return 0;

            IDrawerAttributes attrs = getAttributes();
            if (attrs.isUnlimitedVending() || attrs.isVoid())
                return Integer.MAX_VALUE;

            int rawMaxCapacity = getMaxCapacity(0) * baseRate();
            int rawRemaining = rawMaxCapacity - pooledCount;

            return rawRemaining / convRate[slot];
        }

        public boolean isEmpty (int slot) {
            return protoStack[slot].isEmpty();
        }

        public boolean isEnabled (int slot) {
            if (baseStack().isEmpty())
                return true;

            return !protoStack[slot].isEmpty();
        }

        public boolean canItemBeStored (int slot, @NotNull ItemStack itemPrototype, Predicate<ItemStack> predicate, boolean manualStore) {
            if (hasUnreadablePayload() && !manualStore)
                return false;

            if (StorageBlacklist.INSTANCE.isBlacklisted(itemPrototype))
                return false;

            IDrawerAttributes attrs = getAttributes();
            if (protoStack[slot].isEmpty() && protoStack[0].isEmpty() && (manualStore || !attrs.isItemLocked(LockAttribute.LOCK_EMPTY)))
                return true;

            if (predicate == null)
                return matchers[slot].matches(itemPrototype);

            return predicate.test(protoStack[slot]);
        }

        public boolean canItemBeExtracted (int slot, @NotNull ItemStack itemPrototype, Predicate<ItemStack> predicate) {
            if (protoStack[slot].isEmpty())
                return false;

            if (predicate == null)
                return matchers[slot].matches(itemPrototype);
            return predicate.test(protoStack[slot]);
        }

        public int getConversionRate (int slot) {
            if (baseStack().isEmpty() || convRate[slot] == 0)
                return 0;

            return convRate[0] / convRate[slot];
        }

        public int getStoredItemRemainder (int slot) {
            if (convRate[slot] == 0)
                return 0;

            if (slot == 0)
                return pooledCount / baseRate();

            return (pooledCount / convRate[slot]) % (convRate[slot - 1] / convRate[slot]);
        }

        public boolean isSmallestUnit (int slot) {
            if (baseStack().isEmpty() || convRate[slot] == 0)
                return false;

            return convRate[slot] == 1;
        }

        private void reset () {
            pooledCount = 0;

            for (int i = 0; i < slotCount; i++) {
                protoStack[i] = ItemStack.EMPTY;
                matchers[i] = ItemStackMatcher.EMPTY;
                convRate[i] = 0;
            }

            group.onItemChanged();
        }

        private void populateSlotsFromCache() {
            for (int slot = 0; slot < slotCount; slot++) {
                protoStack[slot] = cachedProtoStack[slot];
                convRate[slot] = cachedConvRate[slot];
                matchers[slot] = cachedMatchers[slot];
            }
        }

        private void populateSlots (@NotNull ItemStack itemPrototype) {
            IDrawerAttributes attrs = getAttributes();
            Level world = group.getWorld();
            if (world == null) {
                protoStack[0] = itemPrototype;
                convRate[0] = 1;
                matchers[0] = attrs.isDictConvertible()
                    ? new ItemStackTagMatcher(protoStack[0])
                    : new ItemStackMatcher(protoStack[0]);

                return;
            }

            // If a drawer cleared and is re-populated with the same initial item, restore that from memory
            // A drawer emptying and filling with the same item is a common expensive degenerate case
            if (ItemStackMatcher.areItemsEqual(itemPrototype, cacheKey)) {
                populateSlotsFromCache();
                return;
            }

            cacheKey = itemPrototype;

            CompactingHelper compacting = new CompactingHelper(world);
            Stack<CompactingHelper.Result> resultStack = new Stack<>();

            ItemStack lookupTarget = itemPrototype;
            int index = 0;

            if (ModCommonConfig.INSTANCE.DRAWERS.compacting.enabled.get()) {
                for (int i = 0; i < slotCount - 1; i++) {
                    CompactingHelper.Result lookup = compacting.findHigherTier(lookupTarget);
                    if (lookup.getStack().isEmpty())
                        break;

                    resultStack.push(lookup);
                    lookupTarget = lookup.getStack();
                }

                for (int n = resultStack.size(); index < n; index++) {
                    CompactingHelper.Result result = resultStack.pop();
                    populateRawSlot(index, result.getStack(), result.getSize());
                    group.log("Picked candidate " + result.getStack().toString() + " with conv=" + result.getSize());

                    for (int i = 0; i < index; i++) {
                        convRate[i] *= result.getSize();
                        cachedConvRate[i] = convRate[i];
                    }
                }
            }

            if (index == slotCount)
                return;

            populateRawSlot(index++, itemPrototype, 1);

            if (ModCommonConfig.INSTANCE.DRAWERS.compacting.enabled.get()) {
                lookupTarget = itemPrototype;
                for (; index < slotCount; index++) {
                    CompactingHelper.Result lookup = compacting.findLowerTier(lookupTarget);
                    ItemStack itemStack = lookup.getStack();
                    if (!itemStack.isEmpty()) {
                        populateRawSlot(index, itemStack, 1);
                        group.log("Picked candidate " + itemStack + " with conv=" + lookup.getSize());

                        for (int i = 0; i < index; i++) {
                            convRate[i] *= lookup.getSize();
                            cachedConvRate[i] = convRate[i];
                        }
                    } else {
                        populateRawSlot(index, ItemStack.EMPTY, 0);
                    }
                    lookupTarget = itemStack;
                }
            }
        }

        private void populateRawSlot (int slot, @NotNull ItemStack itemPrototype, int rate) {
            protoStack[slot] = itemPrototype;
            convRate[slot] = rate;

            IDrawerAttributes attrs = getAttributes();
            matchers[slot] = attrs.isDictConvertible()
                ? new ItemStackTagMatcher(protoStack[slot])
                : new ItemStackMatcher(protoStack[slot]);

            cachedProtoStack[slot] = itemPrototype;
            cachedConvRate[slot] = rate;
            cachedMatchers[slot] = matchers[slot];
        }

        private void normalizeGroup () {
            for (int limit = slotCount - 1; limit > 0; limit--) {
                for (int i = 0; i < limit; i++) {
                    if (protoStack[i].isEmpty()) {
                        protoStack[i] = protoStack[i + 1];
                        matchers[i] = matchers[i + 1];
                        convRate[i] = convRate[i + 1];

                        protoStack[i + 1] = ItemStack.EMPTY;
                        matchers[i + 1] = ItemStackMatcher.EMPTY;
                        convRate[i + 1] = 0;
                    }
                }
            }

            int minConvRate = Integer.MAX_VALUE;
            for (int i = 0; i < slotCount; i++) {
                if (convRate[i] > 0)
                    minConvRate = Math.min(minConvRate, convRate[i]);
            }

            if (minConvRate > 1) {
                for (int i = 0; i < slotCount; i++)
                    convRate[i] /= minConvRate;

                pooledCount /= minConvRate;
            }
        }

        public void serializeNBT (ValueOutput output) {
            boolean hasContent = false;
            var itemList = output.childrenList("Items");
            for (int i = 0; i < slotCount; i++) {
                if (protoStack[i].isEmpty())
                    continue;

                var slotTag = itemList.addChild();
                slotTag.store("Item", ItemStack.CODEC, protoStack[i]);
                slotTag.putByte("Slot", (byte)i);
                slotTag.putInt("Conv", convRate[i]);
                hasContent = true;
            }

            output.putInt("Count", pooledCount);

            if (unreadablePayload != null) {
                if (hasContent) {
                    unreadablePayload = null;
                } else {
                    output.store("Unreadable", CompoundTag.CODEC, unreadablePayload);
                }
            }
        }

        public void deserializeNBT (ValueInput input) {
            for (int i = 0; i < slotCount; i++) {
                protoStack[i] = ItemStack.EMPTY;
                matchers[i] = ItemStackMatcher.EMPTY;
                convRate[i] = 0;
            }
            unreadablePayload = null;

            pooledCount = input.getIntOr("Count", 0);

            boolean anyUnreadable = false;
            var deserializeOps = input.lookup().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var itemList = input.childrenListOrEmpty("Items");
            for (var slotTag : itemList) {
                int slot = slotTag.getIntOr("Slot", 0);
                if (slot < 0 || slot >= slotCount) {
                    anyUnreadable = true;
                    continue;
                }

                CompoundTag rawItem = slotTag.read("Item", CompoundTag.CODEC).orElse(null);
                ItemStack stack = rawItem == null ? ItemStack.EMPTY
                    : LegacyStackCodec.PARKING_CODEC.parse(deserializeOps, rawItem).result().orElse(ItemStack.EMPTY);
                if (rawItem != null && stack.isEmpty())
                    anyUnreadable = true;

                protoStack[slot] = stack;
                convRate[slot] = slotTag.getIntOr("Conv", 0);

                IDrawerAttributes attrs = getAttributes();
                matchers[slot] = attrs.isDictConvertible()
                    ? new ItemStackTagMatcher(protoStack[slot])
                    : new ItemStackMatcher(protoStack[slot]);
            }

            if (anyUnreadable) {
                CompoundTag payload = new CompoundTag();
                input.read("Items", com.mojang.serialization.Codec.PASSTHROUGH)
                    .ifPresent(dyn -> payload.put("Items",
                        (Tag) dyn.convert(net.minecraft.nbt.NbtOps.INSTANCE).getValue()));
                payload.putInt("Count", pooledCount);
                unreadablePayload = payload;
                LegacyStackCodec.reportUnreadable(payload);

                for (int i = 0; i < slotCount; i++) {
                    protoStack[i] = ItemStack.EMPTY;
                    matchers[i] = ItemStackMatcher.EMPTY;
                    convRate[i] = 0;
                }
                pooledCount = 0;
            } else {
                tryRecoverUnreadable(input);
            }

            // TODO: We should only need to normalize if we had blank items with a conv rate, but this fixes blocks that were saved broken
            normalizeGroup();

            // Check if cache needs to be invalidated
            if (!itemList.isEmpty()) {
                boolean cacheMatch = true;
                for (int i = 0; i < slotCount; i++) {
                    cacheMatch &= ItemStackMatcher.areItemsEqual(protoStack[i], cachedProtoStack[i]);
                    cacheMatch &= convRate[i] == cachedConvRate[i];
                }

                if (!cacheMatch)
                    cacheKey = ItemStack.EMPTY;
            }
        }

        private void tryRecoverUnreadable (ValueInput input) {
            CompoundTag payload = input.read("Unreadable", CompoundTag.CODEC).orElse(null);
            if (payload == null)
                return;

            boolean liveContent = false;
            for (int i = 0; i < slotCount; i++)
                liveContent |= !protoStack[i].isEmpty();
            if (liveContent) {
                group.log("Dropping parked unreadable contents: the drawer has been reused");
                ModServices.log.warn("Dropping parked unreadable compacting-drawer contents: the drawer has been reused");
                return;
            }

            var ops = input.lookup().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            ListTag items = payload.getListOrEmpty("Items");

            ItemStack[] recovered = new ItemStack[slotCount];
            int[] recoveredConv = new int[slotCount];
            java.util.Arrays.fill(recovered, ItemStack.EMPTY);

            for (Tag entry : items) {
                if (!(entry instanceof CompoundTag slotTag)) {
                    unreadablePayload = payload;
                    return;
                }
                int slot = slotTag.getIntOr("Slot", 0);
                if (slot < 0 || slot >= slotCount) {
                    unreadablePayload = payload;
                    return;
                }
                Tag itemTag = slotTag.get("Item");
                ItemStack stack = itemTag == null ? ItemStack.EMPTY
                    : LegacyStackCodec.PARKING_CODEC.parse(ops, itemTag).result().orElse(ItemStack.EMPTY);
                if (stack.isEmpty()) {
                    unreadablePayload = payload;   // still unreadable; keep carrying it
                    return;
                }
                recovered[slot] = stack;
                recoveredConv[slot] = slotTag.getIntOr("Conv", 0);
            }

            IDrawerAttributes attrs = getAttributes();
            for (int i = 0; i < slotCount; i++) {
                protoStack[i] = recovered[i];
                convRate[i] = recoveredConv[i];
                matchers[i] = attrs.isDictConvertible()
                    ? new ItemStackTagMatcher(protoStack[i])
                    : new ItemStackMatcher(protoStack[i]);
            }
            pooledCount = payload.getIntOr("Count", 0);
            group.log("Recovered previously unreadable compacting drawer contents");
            ModServices.log.info("Recovered previously unreadable compacting drawer contents");
        }

        public void syncAttributes () {
            for (int i = 0; i < slotCount; i++) {
                if (!protoStack[i].isEmpty()) {
                    IDrawerAttributes attrs = getAttributes();
                    matchers[i] = attrs.isDictConvertible()
                        ? new ItemStackTagMatcher(protoStack[i])
                        : new ItemStackMatcher(protoStack[i]);
                }
            }
        }
    }

    private static class FractionalDrawer implements IFractionalDrawer {
        private final FractionalStorage storage;
        private final int slot;

        private FractionalDrawer(FractionalStorage storage, int slot) {
            this.storage = storage;
            this.slot = slot;
        }

        private FractionalDrawer(FractionalDrawer data) {
            this(data.storage, data.slot);
        }

        @Override
        public boolean hasParkedContents () {
            return storage.hasUnreadablePayload();
        }

        @NotNull
        @Override
        public ItemStack getStoredItemPrototype() {
            return storage.getStack(slot);
        }

        @NotNull
        @Override
        public IDrawer setStoredItem(@NotNull ItemStack itemPrototype) {
            if (ItemStackHelper.isStackEncoded(itemPrototype))
                itemPrototype = ItemStackHelper.decodeItemStackPrototype(itemPrototype);

            return storage.setStoredItem(slot, itemPrototype);
        }

        @Override
        public int getStoredItemCount() {
            return storage.getStoredCount(slot);
        }

        @Override
        public void setStoredItemCount(int amount) {
            storage.setStoredItemCount(slot, amount);
        }

        @Override
        public int adjustStoredItemCount(int amount) {
            return storage.adjustStoredItemCount(slot, amount);
        }

        @Override
        public int getMaxCapacity() {
            return storage.getMaxCapacity(slot);
        }

        @Override
        public int getMaxCapacity(@NotNull ItemStack itemPrototype) {
            return storage.getMaxCapacity(slot, itemPrototype);
        }

        @Override
        public int getAcceptingMaxCapacity(@NotNull ItemStack itemPrototype) {
            return storage.getAcceptingMaxCapacity(slot, itemPrototype);
        }

        @Override
        public int getRemainingCapacity() {
            return storage.getRemainingCapacity(slot);
        }

        @Override
        public int getAcceptingRemainingCapacity() {
            return storage.getAcceptingRemainingCapacity(slot);
        }

        @Override
        public boolean canItemBeStored (@NotNull ItemStack itemPrototype, Predicate<ItemStack> matchPredicate) {
            return storage.canItemBeStored(slot, itemPrototype, matchPredicate, false);
        }

        @Override
        public boolean canItemBeStoredManual (@NotNull ItemStack itemPrototype, Predicate<ItemStack> matchPredicate) {
            return storage.canItemBeStored(slot, itemPrototype, matchPredicate, true);
        }

        @Override
        public boolean canItemBeExtracted(@NotNull ItemStack itemPrototype, Predicate<ItemStack> matchPredicate) {
            return storage.canItemBeExtracted(slot, itemPrototype, matchPredicate);
        }

        @Override
        public boolean isEmpty() {
            return storage.isEmpty(slot);
        }

        @Override
        public boolean isEnabled() {
            return storage.isEnabled(slot);
        }

        @Override
        public int getConversionRate() {
            return storage.getConversionRate(slot);
        }

        @Override
        public int getStoredItemRemainder() {
            return storage.getStoredItemRemainder(slot);
        }

        @Override
        public boolean isSmallestUnit() {
            return storage.isSmallestUnit(slot);
        }

        @Override
        public @NotNull IDrawerAttributes getAttributes () {
            return storage.getAttributes();
        }

        @Override
        public IFractionalDrawer copy () {
            return new FractionalDrawer(this);
        }
    }
}
