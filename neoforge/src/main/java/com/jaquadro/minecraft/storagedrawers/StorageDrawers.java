package com.jaquadro.minecraft.storagedrawers;

import com.jaquadro.minecraft.storagedrawers.capabilities.PlatformCapabilities;
import com.jaquadro.minecraft.storagedrawers.config.*;
import com.jaquadro.minecraft.storagedrawers.core.*;
import com.jaquadro.minecraft.storagedrawers.integration.LocalIntegrationRegistry;
import com.texelsaurus.minecraft.chameleon.registry.NeoforgeRegistryContext;
import com.texelsaurus.minecraft.chameleon.service.ChameleonConfig;
import com.texelsaurus.minecraft.chameleon.service.NeoforgeConfig;
import com.texelsaurus.minecraft.chameleon.service.NeoforgeNetworking;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(StorageDrawers.MOD_ID)
public class StorageDrawers
{
    public static final String MOD_ID = "storagedrawers";
    public static final Api api = new Api();
    public static Logger log = LogManager.getLogger();
    //public static ConfigManager config;
    //public static CompTierRegistry compRegistry;
    //public static OreDictRegistry oreDictRegistry;

    //public static RenderRegistry renderRegistry;
    //public static WailaRegistry wailaRegistry;
    //public static SecurityRegistry securityRegistry;

    public StorageDrawers (ModContainer modContainer, IEventBus modEventBus) {
        ModCommonConfig.INSTANCE.context().init(ModConstants.MOD_ID, ChameleonConfig.Type.COMMON);
        ModClientConfig.INSTANCE.context().init(ModConstants.MOD_ID, ChameleonConfig.Type.CLIENT);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ((NeoforgeConfig)ModCommonConfig.INSTANCE.context()).neoSpec, "storagedrawers-common.v2.toml");
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, ((NeoforgeConfig)ModClientConfig.INSTANCE.context()).neoSpec);

        //modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.spec);
        //modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.spec);

        NeoforgeRegistryContext regContext = new NeoforgeRegistryContext(modEventBus);

        ModBlocks.init(regContext);
        ModItems.init(regContext);
        ModBlockEntities.init(regContext);
        ModContainers.init(regContext);
        ModDataComponents.init(regContext);
        ModRecipes.init(regContext);

        modEventBus.addListener(this::setup);
        //modEventBus.addListener(MessageHandler::register);
        //modEventBus.addListener(this::onModQueueEvent);
        modEventBus.addListener(this::onModConfigEvent);
        modEventBus.addListener(ModCreativeTabs::init);
        modEventBus.addListener(PlatformCapabilities::register);

        NeoforgeNetworking.init(MOD_ID, ModNetworking.INSTANCE, regContext);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new PlayerEventListener());
    }

    private void setup (final FMLCommonSetupEvent event) {
        ConversionRegistry.INSTANCE.initialize();

        LocalIntegrationRegistry.initialize();
        LocalIntegrationRegistry.instance().init();
        LocalIntegrationRegistry.instance().postInit();

        //oreDictRegistry = new OreDictRegistry();
        //renderRegistry = new RenderRegistry();
        //wailaRegistry = new WailaRegistry();
        //securityRegistry = new SecurityRegistry();

        //proxy.registerRenderers();

        //LocalIntegrationRegistry.instance().init();
        //compRegistry.initialize();

        //LocalIntegrationRegistry.instance().postInit();
    }

    @SuppressWarnings("Convert2MethodRef")  // otherwise the class loader gets upset if TheOneProbe is not loaded
    //private void onModQueueEvent(final InterModEnqueueEvent event) {
    //    InterModComms.sendTo("theoneprobe", "getTheOneProbe", () -> new TheOneProbe());
    //}
    private void onModConfigEvent(final ModConfigEvent event) {
        if (event.getConfig().getType() == ModConfig.Type.COMMON)
            ModCommonConfig.INSTANCE.setLoaded();
        if (event.getConfig().getType() == ModConfig.Type.CLIENT)
            ModClientConfig.INSTANCE.setLoaded();
    }

    @SubscribeEvent
    public void onDataComponentsBound(DefaultDataComponentsBoundEvent event) {
        if (!event.shouldUpdateStaticData())
            return;

        CompTierRegistry.INSTANCE.initialize();
        StorageBlacklist.INSTANCE.initialize();
        MaterialBlacklist.INSTANCE.initialize();
    }

    @SubscribeEvent
    public void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerConfig.serverPlayerConfigSettings.remove(event.getEntity().getUUID());
    }

    public static Identifier rl(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
