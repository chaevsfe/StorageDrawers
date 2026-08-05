package com.jaquadro.minecraft.storagedrawers.core;

import com.jaquadro.minecraft.storagedrawers.api.storage.attribute.IPortable;
import com.jaquadro.minecraft.storagedrawers.config.ModCommonConfig;
import com.jaquadro.minecraft.storagedrawers.item.ItemUpgradeRemote;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Punishes players holding filled drawers, if enabled in config */
public class PlayerEventListener
{

	private void applyDebuff(Player plr)
	{
		// slowness IV for 5 seconds
		plr.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3, true, true));
	}

	@SubscribeEvent
	public void onPlayerPickup(ItemEntityPickupEvent.Post event) {
		if (!ModCommonConfig.INSTANCE.DRAWERS.anyHeavyDrawers())
			return;

		// getOriginalStack(), not getItemEntity().getItem(): Post fires AFTER
		// Inventory.add() has drained the entity's stack down to the remainder, so the live
		// stack is empty on any complete pickup and nothing would ever be recognised as heavy.
		// NeoForge keeps the pre-pickup copy on the event for exactly this reason.
		checkItemDebuf(event.getOriginalStack(), event.getPlayer());
	}

	@SubscribeEvent
	public void onPlayerTick(PlayerTickEvent.Post event) {
		// every 3 seconds, in the END phase
		if(event.getEntity().tickCount % 60 != 0)
			return;

		// PlayerTickEvent fires on BOTH logical sides. Fabric drives this from
		// ServerTickEvents.END_SERVER_TICK over the player list, so it is server-only there;
		// without this guard the client would scan its own inventory and apply a duplicate
		// client-side slowness on top of the one the server already sends.
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;

		ItemUpgradeRemote.validateInventory(player.getInventory(), player.level());

		if (!ModCommonConfig.INSTANCE.DRAWERS.anyHeavyDrawers())
			return;

		// TODO: What is getAllSlots
		//for(var s : player.getAllSlots()) {
		//	if (checkItemDebuf(s, player))
		//		return;
		//}

		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (checkItemDebuf(inv.getItem(i), player))
				return;
		}
	}

	private boolean checkItemDebuf (ItemStack stack, Player player) {
		Item item = stack.getItem();
		if (item instanceof IPortable ip) {
			if (ip.isHeavy(player.level().registryAccess(), stack)) {
				applyDebuff(player);
				return true;
			}
		}

		return false;
	}
}
