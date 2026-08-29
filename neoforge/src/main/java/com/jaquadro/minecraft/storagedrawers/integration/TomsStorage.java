package com.jaquadro.minecraft.storagedrawers.integration;

import com.jaquadro.minecraft.storagedrawers.api.storage.IControlGroup;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityController;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityControllerIO;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.tom.storagemod.api.MultiblockInventoryAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.function.Consumer;

public class TomsStorage
{
    public static void init () {
        MultiblockInventoryAPI.EVENT.register(TomsStorage::detectNetwork);
    }

    private static void detectNetwork (Level level, BlockPos pos, BlockState state, Consumer<BlockPos> consumer) {
        if (level == null || level.isClientSide())
            return;

        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityController controller) {
            Set<BlockPos> members = controller.getNetworkBlockPositions(false);
            members.forEach(consumer);
            for (BlockPos member : members) {
                if (!level.isLoaded(member))
                    continue;
                if (level.getBlockEntity(member) instanceof BlockEntityDrawers memberDrawers) {
                    for (IControlGroup group : memberDrawers.getSoftBoundControlGroups()) {
                        if (group instanceof BlockEntityController sibling && sibling != controller)
                            consumer.accept(sibling.getBlockPos());
                    }
                }
            }
            return;
        }

        if (entity instanceof BlockEntityControllerIO io) {
            BlockEntityController controller = io.getController();
            if (controller == null || !controller.isValidIO(pos))
                return;

            consumer.accept(controller.getBlockPos());
            for (BlockPos member : controller.getNetworkBlockPositions(false)) {
                if (!member.equals(pos))
                    consumer.accept(member);
            }
            return;
        }

        if (entity instanceof BlockEntityDrawers drawers) {
            emitControlGroup(drawers.getBoundControlGroup(), consumer);
            for (IControlGroup group : drawers.getSoftBoundControlGroups())
                emitControlGroup(group, consumer);
        }
    }

    private static void emitControlGroup (IControlGroup group, Consumer<BlockPos> consumer) {
        if (!(group instanceof BlockEntityController controller) || controller.isRemoved())
            return;

        consumer.accept(controller.getBlockPos());
        controller.getNetworkBlockPositions(true).forEach(consumer);
    }
}
