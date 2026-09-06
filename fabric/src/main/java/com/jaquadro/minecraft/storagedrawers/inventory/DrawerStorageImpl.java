package com.jaquadro.minecraft.storagedrawers.inventory;

import com.google.common.collect.MapMaker;
import com.jaquadro.minecraft.storagedrawers.api.storage.Drawers;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DrawerStorageImpl extends CombinedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> implements SlottedStorage<ItemVariant>
{
    private static final Map<IDrawerGroup, DrawerStorageImpl> WRAPPERS = new MapMaker().weakValues().makeMap();

    public static DrawerStorageImpl of (IDrawerGroup group) {
        DrawerStorageImpl storage = WRAPPERS.computeIfAbsent(group, DrawerStorageImpl::new);
        storage.resizeSlotList();
        return storage;
    }

    final IDrawerGroup group;
    final List<DrawerStackStorage> backingList;

    public DrawerStorageImpl (IDrawerGroup group) {
        super(Collections.emptyList());
        this.group = group;
        backingList = new ArrayList<>();
    }

    @Override
    public @UnmodifiableView List<SingleSlotStorage<ItemVariant>> getSlots () {
        return parts;
    }

    private void resizeSlotList() {
        int[] slots = group.getAccessibleDrawerSlots();

        if (slots.length != parts.size()) {
            while (backingList.size() < slots.length)
                backingList.add(new DrawerStackStorage(this, backingList.size()));

            parts = Collections.unmodifiableList(backingList.subList(0, slots.length));
        }

        for (int i = 0; i < slots.length; i++)
            backingList.get(i).updateSlot(slots[i]);
    }

    // drawers already holding something first, read live: the controller re-sorts its slots only
    // every 100 ticks, so a slot emptied since then still ranks as populated and would otherwise
    // capture an item that has a home elsewhere on the network
    @Override
    public long insert (ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);

        long amount = insertInto(resource, maxAmount, transaction, false);
        if (amount < maxAmount)
            amount += insertInto(resource, maxAmount - amount, transaction, true);

        return amount;
    }

    private long insertInto (ItemVariant resource, long maxAmount, TransactionContext transaction, boolean emptyDrawers) {
        long amount = 0;
        int count = parts.size();
        for (int i = 0; i < count; i++) {
            DrawerStackStorage part = backingList.get(i);
            if (getDrawer(part.slot).isEmpty() != emptyDrawers)
                continue;

            amount += part.insert(resource, maxAmount - amount, transaction);
            if (amount >= maxAmount)
                break;
        }
        return amount;
    }

    @Override
    public int getSlotCount () {
        return getSlots().size();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot (int slot) {
        return getSlots().get(slot);
    }

    IDrawer getDrawer (int slot) {
        if (slot < 0 || slot >= group.getDrawerCount())
            return Drawers.DISABLED;

        return group.getDrawer(slot);
    }
}
