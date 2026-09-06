package com.jaquadro.minecraft.storagedrawers.block;

import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.config.ModCommonConfig;
import com.jaquadro.minecraft.storagedrawers.security.SecurityManager;
import com.texelsaurus.minecraft.chameleon.inventory.ContentMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// offhand handling the block cannot do itself; the loader hooks run this on every use pass.
// Both loaders still send the use packet when the client consumes the click, and consuming is
// what stops vanilla from going on to use whatever is in the offhand
public final class OffhandMenuOpen
{
    private OffhandMenuOpen () { }

    public static InteractionResult tryOpen (Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator())
            return InteractionResult.PASS;

        return player.isSecondaryUseActive()
            ? sneakOpen(player, level, hand, hit)
            : offhandDeposit(player, level, hand, hit);
    }

    // vanilla reaches the offhand pass only after the main hand did nothing with the click, so
    // acting here is exactly "shift-click opens the menu unless the item in the main hand acted":
    // a block or firework in the main hand still does its thing, a tool, totem or ingot does not
    private static InteractionResult sneakOpen (Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.OFF_HAND)
            return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDrawers))
            return InteractionResult.PASS;

        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        // the same gates as BlockDrawers.openUI
        if (!ModCommonConfig.INSTANCE.GENERAL.enableUI.get())
            return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof BlockEntityDrawers blockEntity) || !SecurityManager.hasAccess(player, blockEntity))
            return InteractionResult.PASS;

        if (state.getMenuProvider(level, pos) instanceof ContentMenuProvider<?> menu && player instanceof ServerPlayer serverPlayer) {
            menu.openMenu(serverPlayer);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // a plain click with an empty main hand puts the offhand stack into the clicked slot, but only
    // when that slot already holds (or is locked to) the same item; the block itself consumes every
    // main-hand click on the client, so this has to run ahead of it
    private static InteractionResult offhandDeposit (Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;

        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty())
            return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDrawers block) || !(level.getBlockEntity(pos) instanceof BlockEntityDrawers blockEntity))
            return InteractionResult.PASS;

        int slot = block.getFaceSlot(state, hit);
        if (slot < 0)
            return InteractionResult.PASS;

        IDrawer drawer = blockEntity.getDrawer(slot);
        if (!drawer.isEnabled() || drawer.getStoredItemPrototype().isEmpty() || !drawer.canItemBeStored(offhand))
            return InteractionResult.PASS;

        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        if (!SecurityManager.hasAccess(player, blockEntity))
            return InteractionResult.PASS;

        blockEntity.putItemsIntoSlot(slot, offhand, offhand.getCount(), player);
        return InteractionResult.SUCCESS;
    }
}
