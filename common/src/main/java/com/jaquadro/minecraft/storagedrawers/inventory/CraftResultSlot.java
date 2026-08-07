package com.jaquadro.minecraft.storagedrawers.inventory;

import com.jaquadro.minecraft.storagedrawers.api.framing.FrameMaterial;
import com.jaquadro.minecraft.storagedrawers.api.framing.IFramedBlock;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityFramingTable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class CraftResultSlot extends Slot
{
    private final Container inputInventory;
    private final int[] inputSlots;
    private final Player player;
    private int amountCrafted;

    public CraftResultSlot (Player player, Container inputInventory, Container inventory, int[] inputSlots, int slot, int x, int y) {
        super(inventory, slot, x, y);

        this.player = player;
        this.inputSlots = inputSlots;
        this.inputInventory = inputInventory;
    }

    @Override
    public boolean mayPlace (ItemStack stack) {
        return false;
    }

    @Override
    public @NotNull ItemStack remove (int amount) {
        if (hasItem())
            amountCrafted += Math.min(amount, getItem().getCount());

        return super.remove(amount);
    }

    @Override
    public void onTake (@NotNull Player player, @NotNull ItemStack stack) {
        consumeInputs(stack);
    }

    @Override
    protected void onQuickCraft (@NotNull ItemStack stack, int amount) {
        amountCrafted += amount;
        consumeInputs(stack);
        super.onQuickCraft(stack, amount);
    }

    @Override
    protected void onSwapCraft (int amount) {
        amountCrafted += amount;
    }

    private void consumeInputs (@NotNull ItemStack stack) {
        if (amountCrafted <= 0)
            return;

        for (int slot : inputSlots) {
            ItemStack itemTarget = inputInventory.getItem(slot);

            // Framing table don't consume unused material slots
            if (stack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock() instanceof IFramedBlock framedBlock) {
                    if (slot == BlockEntityFramingTable.SLOT_SIDE && !framedBlock.supportsFrameMaterial(FrameMaterial.SIDE))
                        continue;
                    if (slot == BlockEntityFramingTable.SLOT_TRIM && !framedBlock.supportsFrameMaterial(FrameMaterial.TRIM))
                        continue;
                    if (slot == BlockEntityFramingTable.SLOT_FRONT && !framedBlock.supportsFrameMaterial(FrameMaterial.FRONT))
                        continue;
                }
            }

            if (!itemTarget.isEmpty())
                inputInventory.removeItem(slot, amountCrafted);
        }

        amountCrafted = 0;
    }
}