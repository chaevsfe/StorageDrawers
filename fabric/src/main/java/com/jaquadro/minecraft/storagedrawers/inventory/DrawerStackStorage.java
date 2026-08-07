package com.jaquadro.minecraft.storagedrawers.inventory;

import java.util.Map;

import com.google.common.collect.MapMaker;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerAttributes;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityController;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityControllerIO;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.capabilities.Capabilities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

public class DrawerStackStorage extends SingleStackStorage
{
    private static final Map<IDrawerGroup, GroupSnapshotParticipant> GROUP_PARTICIPANTS = new MapMaker().weakValues().makeMap();

    DrawerStorageImpl storage;
    int slot;

    DrawerStackStorage (DrawerStorageImpl storage, int slot) {
        this.storage = storage;
        this.slot = slot;
    }

    void updateSlot (int slot) {
        this.slot = slot;
    }

    @Override
    public void updateSnapshots (TransactionContext transaction) {
        IDrawerGroup stateGroup = DrawerGroupSnapshot.resolveStateGroup(storage.group, slot);
        GROUP_PARTICIPANTS.computeIfAbsent(stateGroup, GroupSnapshotParticipant::new).updateSnapshots(transaction);
    }

    @Override
    protected ItemStack getStack () {
        IDrawer drawer = storage.getDrawer(slot);
        return drawer.getStoredItemPrototype().copyWithCount(drawer.getStoredItemCount());
    }

    @Override
    protected void setStack (ItemStack stack) {
        if (stack.getCount() > 0)
            storage.getDrawer(slot).setStoredItem(stack, stack.getCount());
        else
            storage.getDrawer(slot).setStoredItemCount(0);
    }

    @Override
    protected int getCapacity (ItemVariant itemVariant) {
        IDrawer drawer = storage.getDrawer(slot);
        if (drawer.isEmpty())
            return drawer.getMaxCapacity(itemVariant.toStack());

        long capacity = (long) drawer.getStoredItemCount() + drawer.getRemainingCapacity();
        return (int) Math.min(capacity, Integer.MAX_VALUE);
    }

    private IDrawerAttributes getDrawerAttributes (IDrawerGroup group) {
        if (group == null)
            return null;

        IDrawerAttributes attr = group.getCapability(Capabilities.DRAWER_ATTRIBUTES);
        if (attr == null && group instanceof BlockEntityDrawers)
            attr = ((BlockEntityDrawers) group).getDrawerAttributes();

        return attr;
    }

    private boolean checkControllerVoid (BlockEntityController controller) {
        if (controller == null)
            return false;

        IDrawer drawer = storage.getDrawer(slot);
        if (drawer == null || !drawer.isEnabled())
            return false;

        IDrawerGroup drawerGroup = controller.getGroupForDrawerSlot(slot);
        if (drawerGroup == null)
            return false;

        IDrawerAttributes attrs = getDrawerAttributes(drawerGroup);
        return attrs != null && attrs.isVoid();
    }

    @Override
    public long insert (ItemVariant insertedVariant, long maxAmount, TransactionContext transaction) {
        if (storage.getDrawer(slot).getAttributes().isSuspended())
            return 0;

        if (!storage.getDrawer(slot).canItemBeStored(insertedVariant.toStack()))
            return 0;

        long inserted = super.insert(insertedVariant, maxAmount, transaction);

        if (inserted < maxAmount && insertedVariant.matches(getStack())) {
            boolean isVoid;

            if (storage.group instanceof BlockEntityController)
                isVoid = checkControllerVoid((BlockEntityController) storage.group);
            else if (storage.group instanceof BlockEntityControllerIO) {
                BlockEntityController controller = ((BlockEntityControllerIO) storage.group).getController();
                isVoid = checkControllerVoid(controller);
            }
            else {
                IDrawerAttributes attr = getDrawerAttributes(storage.group);
                isVoid = attr != null && attr.isVoid();
            }

            if (isVoid)
                inserted = maxAmount;
        }

        return inserted;
    }

    @Override
    public long extract (ItemVariant variant, long maxAmount, TransactionContext transaction) {
        if (storage.getDrawer(slot).getAttributes().isSuspended())
            return 0;

        if (!storage.getDrawer(slot).canItemBeExtracted(variant.toStack()))
            return 0;

        return super.extract(variant, maxAmount, transaction);
    }

    private static class GroupSnapshotParticipant extends SnapshotParticipant<DrawerGroupSnapshot>
    {
        private final IDrawerGroup group;

        GroupSnapshotParticipant (IDrawerGroup group) {
            this.group = group;
        }

        @Override
        protected DrawerGroupSnapshot createSnapshot () {
            return DrawerGroupSnapshot.capture(group);
        }

        @Override
        protected void readSnapshot (DrawerGroupSnapshot snapshot) {
            snapshot.restore();
        }
    }
}
