package com.jaquadro.minecraft.storagedrawers.client;

import com.jaquadro.minecraft.storagedrawers.StorageDrawers;
import com.jaquadro.minecraft.storagedrawers.config.ModClientConfig;
import com.jaquadro.minecraft.storagedrawers.network.PlayerBoolConfigMessage;
import com.texelsaurus.minecraft.chameleon.ChameleonServices;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Client-only game bus listeners.
 *
 * The invertShift/invertClick settings live in the client config but are enforced server-side, so
 * the server keeps a per-player copy. Without this handshake the server falls back to the defaults
 * and executes the opposite of what the client predicted for shift/click interactions.
 *
 * This lives in a Dist.CLIENT class rather than the main mod class: the previous version keyed off
 * EntityJoinLevelEvent on the common event bus and reached for Minecraft.getInstance() from a class
 * loaded on both sides, which is a dedicated-server class-loading hazard.
 */
@EventBusSubscriber(modid = StorageDrawers.MOD_ID, value = Dist.CLIENT)
public class ClientEventBusSubscriber
{
    @SubscribeEvent
    public static void onClientLogin (ClientPlayerNetworkEvent.LoggingIn event) {
        LocalPlayer player = event.getPlayer();
        if (player == null)
            return;

        String uuid = player.getUUID().toString();
        ChameleonServices.NETWORK.sendToServer(new PlayerBoolConfigMessage(uuid, "invertShift", ModClientConfig.INSTANCE.GENERAL.invertShift.get()));
        ChameleonServices.NETWORK.sendToServer(new PlayerBoolConfigMessage(uuid, "invertClick", ModClientConfig.INSTANCE.GENERAL.invertClick.get()));
    }
}
