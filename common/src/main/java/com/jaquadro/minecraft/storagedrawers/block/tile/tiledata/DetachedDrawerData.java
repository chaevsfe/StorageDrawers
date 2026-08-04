package com.jaquadro.minecraft.storagedrawers.block.tile.tiledata;

import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import com.jaquadro.minecraft.storagedrawers.config.ModCommonConfig;
import com.jaquadro.minecraft.storagedrawers.inventory.ItemStackHelper;
import com.jaquadro.minecraft.storagedrawers.util.LegacyStackCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class DetachedDrawerData implements IDrawer
{
    private ItemStack protoStack;
    private int count;
    private int storageMult;
    private boolean heavy;

    // Raw "Item" NBT that no repair could decode, plus its saved quantity -- preserved so
    // that round-tripping this object (e.g. the upgrade crafting recipe re-serializing
    // CUSTOM_DATA) never destroys data a load failed to read. Mirrors StandardDrawerGroup.
    private CompoundTag unreadableItemTag;
    private int unreadableCount;

    public DetachedDrawerData () {
        protoStack = ItemStack.EMPTY;
        count = 0;
        storageMult = 1;
        heavy = false;
    }

    public DetachedDrawerData (IDrawer sourceDrawer) {
        this(sourceDrawer, 1);
    }

    public DetachedDrawerData (IDrawer sourceDrawer, int storageMult) {
        protoStack = sourceDrawer.getStoredItemPrototype();
        count = sourceDrawer.getStoredItemCount();
        this.storageMult = storageMult;
    }

    public DetachedDrawerData (ValueInput input) {
        deserializeNBT(input);
    }

    protected DetachedDrawerData (DetachedDrawerData data) {
        protoStack = data.protoStack;
        count = data.count;
        storageMult = data.storageMult;
        heavy = data.heavy;
        unreadableItemTag = data.unreadableItemTag;
        unreadableCount = data.unreadableCount;
    }

    public int getStorageMultiplier () {
        return storageMult;
    }

    public void setStorageMultiplier (int storageMult) {
        this.storageMult = storageMult;
    }

    public boolean isHeavy () {
        return heavy;
    }

    public void setIsHeavy (boolean state) {
        heavy = state;
    }

    @Override
    public @NotNull ItemStack getStoredItemPrototype () {
        return protoStack;
    }

    @Override
    public @NotNull IDrawer setStoredItem (@NotNull ItemStack itemPrototype) {
        return this;
    }

    protected IDrawer setStoredItemRaw (@NotNull ItemStack itemPrototype) {
        unreadableItemTag = null;
        unreadableCount = 0;
        itemPrototype = ItemStackHelper.getItemPrototype(itemPrototype);
        protoStack = itemPrototype;
        if (!protoStack.isEmpty())
            protoStack.setCount(1);
        count = 0;

        return this;
    }

    @Override
    public int getStoredItemCount () {
        return count;
    }

    @Override
    public void setStoredItemCount (int amount) {

    }

    protected void setStoredItemCountRaw (int amount) {
        count = amount;
    }

    @Override
    public int getMaxCapacity (@NotNull ItemStack itemPrototype) {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getRemainingCapacity () {
        return getMaxCapacity() - getStoredItemCount();
    }

    @Override
    public boolean canItemBeStored (@NotNull ItemStack itemPrototype, Predicate<ItemStack> matchPredicate) {
        return false;
    }

    @Override
    public boolean canItemBeExtracted (@NotNull ItemStack itemPrototype, Predicate<ItemStack> matchPredicate) {
        return false;
    }

    @Override
    public boolean isEmpty () {
        return protoStack.isEmpty();
    }

    @Override
    public boolean hasParkedContents () {
        return unreadableItemTag != null;
    }

    @Override
    public IDrawer copy () {
        return new DetachedDrawerData(this);
    }

    public void serializeNBT (ValueOutput output) {
        if (storageMult > 1)
            output.putInt("StorageMult", storageMult);

        if (protoStack.isEmpty()) {
            if (unreadableItemTag != null) {
                output.store("Item", CompoundTag.CODEC, unreadableItemTag);
                output.putInt("Count", unreadableCount);
                if (heavy)
                    output.putBoolean("Heavy", true);
            }
            return;
        }

        output.store("Item", ItemStack.CODEC, protoStack);
        output.putInt("Count", count);

        if (heavy)
            output.putBoolean("Heavy", true);
    }

    public void deserializeNBT (ValueInput input) {
        if (input == null)
            return;

        storageMult = input.getIntOr("StorageMult", ModCommonConfig.INSTANCE.DRAWERS.baseStackStorage.get() * 8);

        setIsHeavy(input.getBooleanOr("Heavy", false));
        // Parse via the codec directly and accept only a FULL success -- ValueInput.read
        // hands back a failed decode's partial value, silently stripping the failed component.
        CompoundTag rawItem = input.read("Item", CompoundTag.CODEC).orElse(null);
        ItemStack stack = rawItem == null ? ItemStack.EMPTY
            : LegacyStackCodec.PARKING_CODEC.parse(
                input.lookup().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), rawItem)
                .result().orElse(ItemStack.EMPTY);
        setStoredItemRaw(stack);
        setStoredItemCountRaw(input.getIntOr("Count", 0));

        if (rawItem != null && stack.isEmpty()) {
            unreadableItemTag = rawItem;
            unreadableCount = input.getIntOr("Count", 0);
            count = 0;
            LegacyStackCodec.reportUnreadable(rawItem);
        }
    }
}
