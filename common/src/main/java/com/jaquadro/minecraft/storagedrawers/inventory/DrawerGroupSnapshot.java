package com.jaquadro.minecraft.storagedrawers.inventory;

import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityController;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityControllerIO;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.block.tile.tiledata.FractionalDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.util.ItemStackMatcher;
import net.minecraft.world.item.ItemStack;

public class DrawerGroupSnapshot
{
    private final IDrawerGroup group;
    private final ItemStack[] protoStacks;
    private final int[] counts;
    private final int pooledCount;

    private DrawerGroupSnapshot (IDrawerGroup group) {
        this.group = group;

        int slotCount = group.getDrawerCount();
        protoStacks = new ItemStack[slotCount];
        counts = new int[slotCount];

        for (int i = 0; i < slotCount; i++) {
            IDrawer drawer = group.getDrawer(i);
            protoStacks[i] = drawer.getStoredItemPrototype().copy();
            counts[i] = drawer.getStoredItemCount();
        }

        pooledCount = group instanceof FractionalDrawerGroup fracGroup ? fracGroup.getPooledCount() : 0;
    }

    public static DrawerGroupSnapshot capture (IDrawerGroup group) {
        return new DrawerGroupSnapshot(group);
    }

    public static IDrawerGroup resolveStateGroup (IDrawerGroup group, int slot) {
        if (group instanceof BlockEntityControllerIO controllerIO) {
            BlockEntityController controller = controllerIO.getController();
            if (controller != null)
                group = controller;
        }

        if (group instanceof BlockEntityController controller) {
            IDrawerGroup slotGroup = controller.getGroupForDrawerSlot(slot);
            if (slotGroup != null)
                group = slotGroup;
        }

        if (group instanceof BlockEntityDrawers blockEntity)
            group = blockEntity.getGroup();

        return group;
    }

    public void restore () {
        if (group instanceof FractionalDrawerGroup fracGroup) {
            restoreFractional(fracGroup);
            return;
        }

        for (int i = protoStacks.length - 1; i >= 0; i--)
            restoreSlot(i);
    }

    private static boolean isSameItem (ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() || stack2.isEmpty())
            return stack1.isEmpty() && stack2.isEmpty();

        return ItemStackMatcher.areItemsEqual(stack1, stack2);
    }

    private void restoreFractional (FractionalDrawerGroup fracGroup) {
        int lastFilled = -1;
        boolean sameItems = true;
        for (int i = 0; i < protoStacks.length; i++) {
            if (!protoStacks[i].isEmpty())
                lastFilled = i;
            sameItems &= isSameItem(fracGroup.getDrawer(i).getStoredItemPrototype(), protoStacks[i]);
        }

        if (lastFilled < 0) {
            if (!fracGroup.getDrawer(0).isEmpty())
                fracGroup.getDrawer(0).setStoredItem(ItemStack.EMPTY);
            return;
        }

        if (!sameItems) {
            fracGroup.getDrawer(0).setStoredItem(ItemStack.EMPTY);
            fracGroup.getDrawer(lastFilled).setStoredItem(protoStacks[lastFilled]);
        }

        if (fracGroup.getPooledCount() != pooledCount)
            fracGroup.setPooledCount(pooledCount);
    }

    private void restoreSlot (int slot) {
        IDrawer drawer = group.getDrawer(slot);
        ItemStack proto = protoStacks[slot];

        if (proto.isEmpty()) {
            if (!drawer.getStoredItemPrototype().isEmpty())
                drawer.setStoredItem(ItemStack.EMPTY);
            return;
        }

        boolean sameItem = isSameItem(drawer.getStoredItemPrototype(), proto);
        if (sameItem && drawer.getStoredItemCount() == counts[slot])
            return;

        if (counts[slot] == 0) {
            drawer.setStoredItem(ItemStack.EMPTY);
            drawer.setStoredItem(proto);
        } else {
            if (!sameItem)
                drawer.setStoredItem(ItemStack.EMPTY);
            drawer.setStoredItem(proto, counts[slot]);
        }
    }
}
