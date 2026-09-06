package com.jaquadro.minecraft.storagedrawers.block;

import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.config.ModCommonConfig;
import com.jaquadro.minecraft.storagedrawers.security.SecurityManager;
import com.texelsaurus.minecraft.chameleon.inventory.ContentMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// sneaking with anything in either hand makes vanilla skip the block entirely, so the block
// cannot handle this itself; the loader hooks run this on every use pass
// Both loaders still send the use packet when the client consumes the click, and consuming is
// what stops vanilla from going on to use whatever is in the offhand
public final class OffhandMenuOpen
{
    private OffhandMenuOpen () { }

    public static InteractionResult tryOpen (Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        // vanilla reaches the offhand pass only after the main hand did nothing with the click, so
        // acting here is exactly "shift-click opens the menu unless the item in the main hand acted":
        // a block or firework in the main hand still does its thing, a tool, totem or ingot does not
        if (hand != InteractionHand.OFF_HAND || !player.isSecondaryUseActive() || player.isSpectator())
            return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDrawers))
            return InteractionResult.PASS;

        // consume on the client too: a non-consuming result lets vanilla go on to use the offhand item
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
}
