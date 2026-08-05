package com.jaquadro.minecraft.storagedrawers.block.tile.tiledata;

import com.jaquadro.minecraft.storagedrawers.ModServices;
import com.jaquadro.minecraft.storagedrawers.api.storage.IControlGroup;
import com.jaquadro.minecraft.storagedrawers.api.storage.INetworked;
import com.jaquadro.minecraft.storagedrawers.config.ModCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.*;
import java.util.stream.Stream;

public class ControllerHostData extends BlockEntityDataShim
{
    private Map<BlockPos, INetworked> nodeMap = new HashMap<>();

    @Override
    public void read (ValueInput input) {
        nodeMap.clear();

        input.childrenList("RemoteNodes").ifPresent(list -> {
            for (var t : list)
                nodeMap.put(new BlockPos(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0)), null);
        });
    }

    @Override
    public void write (ValueOutput output) {
        var list = output.childrenList("RemoteNodes");
        for (BlockPos pos : nodeMap.keySet()) {
            var ctag = list.addChild();
            ctag.putInt("x", pos.getX());
            ctag.putInt("y", pos.getY());
            ctag.putInt("z", pos.getZ());
        }
    }

    public void validateRemoteNodes (IControlGroup host, Level level) {
        if (ModCommonConfig.INSTANCE.GENERAL.debugTrace.get())
            ModServices.log.info("controllerHostData [{}, size={}] validate remote notes for host [{}]", this, nodeMap.size(), host);

        // Use iterator directly so that entries can be removed during iteration.
        Iterator<Map.Entry<BlockPos, INetworked>> iterator = nodeMap.entrySet().iterator();

        while (iterator.hasNext()) {
            BlockPos pos = iterator.next().getKey();

            if (!level.isLoaded(pos))
                continue;

            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof INetworked networked) {
                if (networked.getBoundControlGroup() == host) {
                    nodeMap.put(pos, networked);
                    if (ModCommonConfig.INSTANCE.GENERAL.debugTrace.get())
                        ModServices.log.info("  put node [{} = {}]", pos, entity);
                    continue;
                }
            }

            iterator.remove();
            if (ModCommonConfig.INSTANCE.GENERAL.debugTrace.get())
                ModServices.log.info("  remove node [{} = {}]", pos, entity);
        }
    }

    public void validateRemoteNode (IControlGroup host, INetworked node) {
        if (node == null)
            return;

        if (node instanceof BlockEntity blockEntity) {
            BlockPos pos = blockEntity.getBlockPos();
            if (node.getBoundControlGroup() == host) {
                nodeMap.put(pos, node);
                if (ModCommonConfig.INSTANCE.GENERAL.debugTrace.get())
                    ModServices.log.info("  put node [{} = {}]", pos, node);
                return;
            }

            nodeMap.remove(pos);
        }
    }

    public boolean addRemoteNode (IControlGroup host, INetworked node) {
        if (ModCommonConfig.INSTANCE.GENERAL.debugTrace.get())
            ModServices.log.info("ControllerHostData [{}] add remote node [{}] for host [{}]", this, node, host);

        if (node == null)
            return false;

        if (node instanceof BlockEntity blockEntity) {
            BlockPos pos = blockEntity.getBlockPos();
            if (node.getBoundControlGroup() == host) {
                nodeMap.put(pos, node);
                return true;
            }

            nodeMap.put(pos, null);
        }

        return false;
    }

    public boolean removeRemoteNode (IControlGroup host, INetworked node) {
        if (ModCommonConfig.INSTANCE.GENERAL.debugTrace.get())
            ModServices.log.info("ControllerHostData [{}] remove node [{}] for host [{}]", this, node, host);

        if (node == null)
            return false;

        if (node instanceof BlockEntity blockEntity) {
            BlockPos pos = blockEntity.getBlockPos();
            if (nodeMap.containsKey(pos)) {
                nodeMap.remove(pos);
                return true;
            }
        }

        return false;
    }

    public Stream<INetworked> getRemoteNodes () {
        return nodeMap.values().stream().filter(Objects::nonNull);
    }
}