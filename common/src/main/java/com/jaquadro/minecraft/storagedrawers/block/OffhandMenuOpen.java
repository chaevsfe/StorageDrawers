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

// vanilla skips the block entirely when a sneaking player holds anything in either hand, so an
// offhand item silently blocks shift-clicking into the drawer UI; the loader hooks run this first.
// The client never claims the click: vanilla only sends the use packet from inside its own
// prediction, so cancelling there would stop the server from ever hearing about it
public final class OffhandMenuOpen
{
    private OffhandMenuOpen () { }

    public static InteractionResult tryOpen (Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isSecondaryUseActive() || player.isSpectator())
            return InteractionResult.PASS;

        // vanilla already reaches the block for every other hand combination
        if (!player.getMainHandItem().isEmpty() || player.getOffhandItem().isEmpty())
            return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDrawers))
            return InteractionResult.PASS;

        if (level.isClientSide())
            return InteractionResult.PASS;

        // the same gates as BlockDrawers.openUI
        if (!ModCommonConfig.INSTANCE.GENERAL.enableUI.get())
            return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof BlockEntityDrawers blockEntity) || !SecurityManager.hasAccess(player, blockEntity))
            return InteractionResult.PASS;

        // SUCCESS_SERVER so the swing is broadcast from here, since the client predicted nothing
        if (state.getMenuProvider(level, pos) instanceof ContentMenuProvider<?> menu && player instanceof ServerPlayer serverPlayer) {
            menu.openMenu(serverPlayer);
            return InteractionResult.SUCCESS_SERVER;
        }

        return InteractionResult.PASS;
    }
}
