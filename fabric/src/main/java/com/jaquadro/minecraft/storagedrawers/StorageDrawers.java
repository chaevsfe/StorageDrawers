package com.jaquadro.minecraft.storagedrawers;

import com.jaquadro.minecraft.storagedrawers.capabilities.PlatformCapabilities;
import com.jaquadro.minecraft.storagedrawers.config.*;
import com.jaquadro.minecraft.storagedrawers.core.*;
import com.jaquadro.minecraft.storagedrawers.core.ModCreativeTabs;
import com.jaquadro.minecraft.storagedrawers.integration.LocalIntegrationRegistry;
import com.texelsaurus.minecraft.chameleon.api.ChameleonInit;
import com.texelsaurus.minecraft.chameleon.service.ChameleonConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class StorageDrawers implements ModInitializer
{
    public static final Api api = new Api();

    @Override
    public void onInitialize () {
        ModCommonConfig.INSTANCE.context().init(ModConstants.MOD_ID, ChameleonConfig.Type.COMMON);
        ModClientConfig.INSTANCE.context().init(ModConstants.MOD_ID, ChameleonConfig.Type.CLIENT);

        ChameleonInit.InitContext context = new ChameleonInit.InitContext();

        ModBlocks.init(context);
        ModItems.init(context);
        ModCreativeTabs.init(context);
        ModBlockEntities.init(context);
        ModContainers.init(context);
        ModDataComponents.init(context);
        ModRecipes.init(context);

        ModNetworking.INSTANCE.init(context);
        CommonEvents.init();

        PlatformCapabilities.initHandlers();

        // Per-player settings arrive via PlayerBoolConfigMessage; prune on disconnect so
        // the map does not grow for the lifetime of the server.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            PlayerConfig.serverPlayerConfigSettings.remove(handler.getPlayer().getUUID()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers())
                PlayerEventListener.onPlayerTick(player);
        });

        // These three resolve config entries into ItemStacks, which cannot happen here. On
        // 26.2 an item's data components are data-driven and bound during datapack load, and
        // the ItemStack constructor reads them eagerly -- building one at mod init throws
        // "Components not bound yet". TAGS_LOADED fires at the tail of
        // ReloadableServerResources.updateComponentsAndStaticRegistryTags, which is exactly
        // the point components become available, and again on every reload and client login.
        // The three registries rebuild themselves from scratch on each call.
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            CompTierRegistry.INSTANCE.initialize();
            StorageBlacklist.INSTANCE.initialize();
            MaterialBlacklist.INSTANCE.initialize();
        });

        // Stays here: this one only builds TagKeys and parses strings, never an ItemStack.
        ConversionRegistry.INSTANCE.initialize();

        LocalIntegrationRegistry.initialize();
        LocalIntegrationRegistry.instance().init();
        LocalIntegrationRegistry.instance().postInit();
    }
}
