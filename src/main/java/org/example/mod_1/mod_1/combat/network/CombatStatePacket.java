package org.example.mod_1.mod_1.combat.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.CombatStateMachine;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;
import org.slf4j.Logger;

public class CombatStatePacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final byte stateOrdinal;

    public CombatStatePacket(CombatState state) {
        this.stateOrdinal = (byte) state.ordinal();
    }

    private CombatStatePacket(byte stateOrdinal) {
        this.stateOrdinal = stateOrdinal;
    }

    public static void encode(CombatStatePacket msg, RegistryFriendlyByteBuf buf) {
        buf.writeByte(msg.stateOrdinal);
    }

    public static CombatStatePacket decode(RegistryFriendlyByteBuf buf) {
        return new CombatStatePacket(buf.readByte());
    }

    public static void handle(CombatStatePacket msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        CombatState requested = CombatState.fromOrdinal(msg.stateOrdinal);

        CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
            CombatStateMachine.requestTransition(cap, requested);

            // Broadcast to all tracking players + self
            CombatSyncPacket sync = new CombatSyncPacket(
                    player.getId(),
                    cap.getState(),
                    cap.getWeaponType(),
                    cap.isWeaponDrawn(),
                    cap.getComboCount()
            );
            CombatNetworkChannel.CHANNEL.send(sync,
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player));
        });
    }
}
