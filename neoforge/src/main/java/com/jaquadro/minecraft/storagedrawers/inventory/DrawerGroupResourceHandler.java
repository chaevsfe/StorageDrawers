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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.*;

public class DrawerGroupResourceHandler implements ResourceHandler<ItemResource>
{
    private static final Map<IDrawerGroup, DrawerGroupResourceHandler> WRAPPERS = new MapMaker().weakValues().makeMap();

    private final IDrawerGroup group;

    final List<DrawerWrapper> drawerWrappers = new ArrayList();
    private final RootCommitJournal setChangedJournal;

    public static ResourceHandler<ItemResource> of (IDrawerGroup group) {
        return internalOf(group);
    }

    // The slot list is deliberately NOT cached on this object. NeoForge's BlockCapabilityCache
    // holds the handler instance and only re-runs this provider when something invalidates the
    // position -- chunk load/unload, block-entity load/unload, placement, destruction. Adding or
    // removing a drawer in a controller network is none of those (it happens at the DRAWER's
    // position, not the controller's), and the mod never calls Level.invalidateCapabilities
    // anywhere. Caching here left every modded pipe attached to a controller frozen at whatever
    // the network looked like when it first resolved. Fabric never had this problem: its
    // BlockApiCache re-invokes the provider on every lookup.
    static DrawerGroupResourceHandler internalOf (IDrawerGroup group) {
        return WRAPPERS.computeIfAbsent(group, DrawerGroupResourceHandler::new);
    }

    // A plain field read on all three groups (BlockEntityController.drawerSlots,
    // StandardDrawerGroup.order, BlockEntityControllerIO forwarding to its controller), which is
    // why common's own DrawerItemHandler already calls it per operation rather than caching.
    private int[] slotOrder () {
        return group.getAccessibleDrawerSlots();
    }

    DrawerGroupResourceHandler (IDrawerGroup group) {
        this.group = group;
        this.setChangedJournal = new RootCommitJournal(this::onRootCommit);
    }

    DrawerWrapper getDrawerWrapper (int index) {
        Objects.checkIndex(index, this.size());

        // Grown on demand instead of up front, now that size() is live. Each wrapper's slot is
        // its own list index, which is what makes getDrawerWrapper(translateSlot(i)) correct.
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

    // Suspension is deliberately NOT checked here. It is a per-DRAWER attribute, and asking the
    // GROUP for it is wrong twice over: a controller or controller IO never has the
    // DRAWER_ATTRIBUTES capability registered at all (see PlatformCapabilities), so the answer was
    // always false for exactly the setups where automation matters most; and resolving a block
    // capability on every call put a getBlockState + getBlockEntity + provider lookup on the hopper
    // hot path. The guard now lives per drawer in DrawerWrapper.insert/extract, matching Fabric.

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

            return group.getDrawer(slot).getMaxCapacity(resource.toStack());
        }

        /**
         * Reported capacity has to be the VOID-aware figure, or a full void drawer reads as full and
         * NeoForge's hopper never even asks: VanillaInventoryCodeHooks.insertHook opens with
         * ResourceHandlerUtil.isFull(handler) and bails, so the void branch in insert() below is
         * never reached. Fabric has no such precheck, which is why void drawers work there.
         *
         * This overrides the reported capacity only. getCapacity() above stays the real figure,
         * because ItemStackResourceHandler.insert clamps the inserted amount to it -- widening that
         * one instead would let the clamp swallow the overflow and make the void branch dead code.
         */
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
            // Suspension pauses automation IO, per drawer. This is the door vanilla hoppers and
            // NeoForge transfer mods come through; getAttributes() is a cached field read.
            if (group.getDrawer(slot).getAttributes().isSuspended())
                return 0;
            if (!group.getDrawer(slot).canItemBeStored(resource.toStack()))
                return 0;

            int inserted = super.insert(index, resource, amount, transaction);

            if (inserted < amount) {
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
}
