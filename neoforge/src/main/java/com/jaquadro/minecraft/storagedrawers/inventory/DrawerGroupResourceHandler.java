package com.jaquadro.minecraft.storagedrawers.inventory;

import com.google.common.collect.MapMaker;
import com.jaquadro.minecraft.storagedrawers.api.storage.EmptyDrawerAttributes;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerAttributes;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityController;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityControllerIO;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.capabilities.Capabilities;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.*;

public class DrawerGroupResourceHandler implements ResourceHandler<ItemResource>
{
    private static final Map<IDrawerGroup, DrawerGroupResourceHandler> WRAPPERS = new MapMaker().weakValues().makeMap();
    private static final Map<IDrawerGroup, GroupSnapshotJournal> GROUP_JOURNALS = new MapMaker().weakValues().makeMap();

    private final IDrawerGroup group;

    final List<DrawerWrapper> drawerWrappers = new ArrayList();
    private final RootCommitJournal setChangedJournal;

    public static ResourceHandler<ItemResource> of (IDrawerGroup group) {
        return internalOf(group);
    }

    static DrawerGroupResourceHandler internalOf (IDrawerGroup group) {
        return WRAPPERS.computeIfAbsent(group, DrawerGroupResourceHandler::new);
    }

    private int[] slotOrder () {
        return group.getAccessibleDrawerSlots();
    }

    DrawerGroupResourceHandler (IDrawerGroup group) {
        this.group = group;
        this.setChangedJournal = new RootCommitJournal(this::onRootCommit);
    }

    DrawerWrapper getDrawerWrapper (int index) {
        Objects.checkIndex(index, this.size());

        while (drawerWrappers.size() <= index)
            drawerWrappers.add(new DrawerWrapper(drawerWrappers.size()));

        return this.drawerWrappers.get(index);
    }

    void onRootCommit () { }

    int translateSlot (int i) {
        int[] order = slotOrder();
        if (i >= 0 && i < order.length)
            i = order[i];

        return i;
    }

    @Override
    public int size () {
        if (!isGroupValid())
            return 0;

        return slotOrder().length;
    }

    @Override
    public ItemResource getResource (int i) {
        return getDrawerWrapper(translateSlot(i)).getResource(0);
    }

    @Override
    public long getAmountAsLong (int i) {
        return getDrawerWrapper(translateSlot(i)).getAmountAsLong(0);
    }

    @Override
    public long getCapacityAsLong (int i, ItemResource itemResource) {
        return getDrawerWrapper(translateSlot(i)).getCapacityAsLong(0, itemResource);
    }

    @Override
    public boolean isValid (int i, ItemResource itemResource) {
        return getDrawerWrapper(translateSlot(i)).isValid(0, itemResource);
    }

    @Override
    public int insert (int i, ItemResource itemResource, int amount, TransactionContext transactionContext) {
        return getDrawerWrapper(translateSlot(i)).insert(0, itemResource, amount, transactionContext);
    }

    // drawers already holding something first, read live: the controller re-sorts its slots only
    // every 100 ticks, so a slot emptied since then still ranks as populated and would otherwise
    // capture an item that has a home elsewhere on the network
    @Override
    public int insert (ItemResource itemResource, int amount, TransactionContext transactionContext) {
        TransferPreconditions.checkNonEmptyNonNegative(itemResource, amount);

        int inserted = insertInto(itemResource, amount, transactionContext, false);
        if (inserted < amount)
            inserted += insertInto(itemResource, amount - inserted, transactionContext, true);

        return inserted;
    }

    private int insertInto (ItemResource itemResource, int amount, TransactionContext transactionContext, boolean emptyDrawers) {
        int inserted = 0;
        int size = size();
        for (int i = 0; i < size; i++) {
            int slot = translateSlot(i);
            if (group.getDrawer(slot).isEmpty() != emptyDrawers)
                continue;

            inserted += getDrawerWrapper(slot).insert(0, itemResource, amount - inserted, transactionContext);
            if (inserted >= amount)
                break;
        }
        return inserted;
    }

    @Override
    public int extract (int i, ItemResource itemResource, int amount, TransactionContext transactionContext) {
        return getDrawerWrapper(translateSlot(i)).extract(0, itemResource, amount, transactionContext);
    }

    protected boolean isGroupValid () {
        return group.isGroupValid();
    }

    public class DrawerWrapper extends ItemStackResourceHandler
    {
        int slot;

        public DrawerWrapper (int slot) {
            this.slot = slot;
        }

        @Override
        public ItemResource getResource (int index) {
            if (!isGroupValid())
                return ItemResource.EMPTY;

            return super.getResource(index);
        }


        @Override
        public void updateSnapshots (TransactionContext transaction) {
            IDrawerGroup stateGroup = DrawerGroupSnapshot.resolveStateGroup(group, slot);
            GROUP_JOURNALS.computeIfAbsent(stateGroup, GroupSnapshotJournal::new).updateSnapshots(transaction);
        }

        @Override
        protected ItemStack getStack () {
            if (!isGroupValid())
                return ItemStack.EMPTY;

            IDrawer drawer = group.getDrawer(slot);
            return drawer.getStoredItemPrototype().copyWithCount(drawer.getStoredItemCount());
        }

        void updateSlot (int slot) {
            this.slot = slot;
        }

        @Override
        protected void setStack (ItemStack itemStack) {
            if (!isGroupValid())
                return;

            if (itemStack.getCount() > 0)
                group.getDrawer(slot).setStoredItem(itemStack, itemStack.getCount());
            else
                group.getDrawer(slot).setStoredItemCount(0);
        }

        @Override
        protected boolean isValid (ItemResource resource) {
            return isGroupValid() && group.getDrawer(slot).canItemBeStored(resource.toStack());
        }

        @Override
        public long getAmountAsLong (int index) {
            if (!isGroupValid())
                return 0;

            return group.getDrawer(slot).getStoredItemCount();
        }

        @Override
        protected int getCapacity (ItemResource resource) {
            if (!isGroupValid())
                return 0;

            IDrawer drawer = group.getDrawer(slot);
            if (drawer.isEmpty())
                return drawer.getMaxCapacity(resource.toStack());

            long capacity = (long) drawer.getStoredItemCount() + drawer.getRemainingCapacity();
            return (int) Math.min(capacity, Integer.MAX_VALUE);
        }

        @Override
        public long getCapacityAsLong (int index, ItemResource resource) {
            if (!isGroupValid())
                return 0;
            if (!resource.isEmpty() && !isValid(resource))
                return 0;

            return group.getDrawer(slot).getAcceptingMaxCapacity(resource.toStack());
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

            IDrawer drawer = group.getDrawer(slot);
            if (drawer == null || !drawer.isEnabled())
                return false;

            IDrawerGroup controllerGroup = controller.getGroupForDrawerSlot(slot);
            if (controllerGroup == null)
                return false;

            IDrawerAttributes attrs = getDrawerAttributes(controllerGroup);
            return attrs != null && attrs.isVoid();
        }

        @Override
        public int insert (int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (!isGroupValid())
                return 0;
            if (group.getDrawer(slot).getAttributes().isSuspended())
                return 0;
            if (!group.getDrawer(slot).canItemBeStored(resource.toStack()))
                return 0;

            int inserted = super.insert(index, resource, amount, transaction);

            if (inserted < amount && resource.matches(getStack())) {
                boolean isVoid;

                if (group instanceof BlockEntityController controller)
                    isVoid = checkControllerVoid(controller);
                else if (group instanceof BlockEntityControllerIO controllerIO) {
                    BlockEntityController controller = controllerIO.getController();
                    isVoid = checkControllerVoid(controller);
                }
                else {
                    IDrawerAttributes attr = getDrawerAttributes(group);
                    isVoid = attr != null && attr.isVoid();
                }

                if (isVoid)
                    inserted = amount;
            }

            return inserted;
        }

        @Override
        public int extract (int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (!isGroupValid())
                return 0;
            if (group.getDrawer(slot).getAttributes().isSuspended())
                return 0;
            if (!group.getDrawer(slot).canItemBeExtracted(resource.toStack()))
                return 0;

            return super.extract(index, resource, amount, transaction);
        }
    }

    private static class GroupSnapshotJournal extends SnapshotJournal<DrawerGroupSnapshot>
    {
        private final IDrawerGroup group;

        GroupSnapshotJournal (IDrawerGroup group) {
            this.group = group;
        }

        @Override
        protected DrawerGroupSnapshot createSnapshot () {
            return DrawerGroupSnapshot.capture(group);
        }

        @Override
        protected void revertToSnapshot (DrawerGroupSnapshot snapshot) {
            snapshot.restore();
        }
    }
}
