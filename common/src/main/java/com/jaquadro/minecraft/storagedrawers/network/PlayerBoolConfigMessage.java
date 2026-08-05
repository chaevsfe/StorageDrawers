package com.jaquadro.minecraft.storagedrawers.network;

import com.google.common.collect.Maps;
import com.jaquadro.minecraft.storagedrawers.ModConstants;
import com.jaquadro.minecraft.storagedrawers.config.PlayerConfig;
import com.jaquadro.minecraft.storagedrawers.config.PlayerConfigSetting;
import com.texelsaurus.minecraft.chameleon.network.ChameleonPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public record PlayerBoolConfigMessage(String uuid, String key, boolean value) implements ChameleonPacket
{
    public static final Type<PlayerBoolConfigMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ModConstants.MOD_ID, "player_bool_config"));

    public static final StreamCodec<FriendlyByteBuf, PlayerBoolConfigMessage> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        PlayerBoolConfigMessage::uuid,
        ByteBufCodecs.STRING_UTF8,
        PlayerBoolConfigMessage::key,
        ByteBufCodecs.BOOL,
        PlayerBoolConfigMessage::value,
        PlayerBoolConfigMessage::new
    );

    @Override
    public Type<? extends PlayerBoolConfigMessage> type () {
        return TYPE;
    }

    // The only two settings this message exists to carry. The handler writes the client-supplied
    // key straight into a static server-side map, and STRING_UTF8 accepts up to 32767 chars, so
    // without a whitelist a modified client can push unbounded distinct keys and grow the server's
    // heap for as long as it stays connected. Nothing else is ever sent or read -- see
    // PlayerConfig.getInvertShift/getInvertClick and the two send sites on each loader's client.
    private static final Set<String> KNOWN_KEYS = Set.of("invertShift", "invertClick");

    @Override
    public void handleMessage (Player player, Consumer<Runnable> workQueue) {
        if (!KNOWN_KEYS.contains(key))
            return;

        if (player instanceof ServerPlayer serverPlayer) {
            workQueue.accept(() -> {
                // Key on the AUTHENTICATED sender, never the client-supplied uuid string:
                // any client could otherwise overwrite another player's settings.
                UUID playerUniqueId = serverPlayer.getUUID();

                Map<String, PlayerConfigSetting<?>> clientMap = PlayerConfig.serverPlayerConfigSettings.get(playerUniqueId);
                if (clientMap == null) {
                    clientMap = Maps.newHashMap();
                }

                clientMap.put(key, new PlayerConfigSetting<>(key, value, playerUniqueId));
                PlayerConfig.serverPlayerConfigSettings.put(playerUniqueId, clientMap);
            });
        }
    }
}
