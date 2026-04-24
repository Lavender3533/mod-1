package org.example.combatarts.combat.network;

import net.minecraft.resources.Identifier;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
import org.example.combatarts.CombatArts;

public class CombatNetworkChannel {

    private static final int PROTOCOL_VERSION = 1;
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath(CombatArts.MODID, "combat"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .optional()
            .simpleChannel();

    public static void init() {
        var play = CHANNEL.play();
        play.serverbound().addMain(CombatStatePacket.class, CombatStatePacket.STREAM_CODEC, CombatStatePacket::handle);
        play.clientbound().addMain(CombatSyncPacket.class, CombatSyncPacket.STREAM_CODEC, CombatSyncPacket::handle);
        CHANNEL.build();
    }
}
