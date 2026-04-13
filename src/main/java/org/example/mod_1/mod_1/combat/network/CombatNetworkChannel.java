package org.example.mod_1.mod_1.combat.network;

import net.minecraft.resources.Identifier;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;
import org.example.mod_1.mod_1.Mod_1;

public class CombatNetworkChannel {

    private static final int PROTOCOL_VERSION = 1;

    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath(Mod_1.MODID, "combat"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .optional()
            .simpleChannel();

    public static void init() {
        CHANNEL.messageBuilder(CombatStatePacket.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CombatStatePacket::encode)
                .decoder(CombatStatePacket::decode)
                .consumerMainThread(CombatStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(CombatSyncPacket.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CombatSyncPacket::encode)
                .decoder(CombatSyncPacket::decode)
                .consumerMainThread(CombatSyncPacket::handle)
                .add();
    }
}
