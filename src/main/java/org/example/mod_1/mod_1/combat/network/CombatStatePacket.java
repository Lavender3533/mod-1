package org.example.mod_1.mod_1.combat.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import org.example.mod_1.mod_1.Config;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.CombatStateMachine;
import org.example.mod_1.mod_1.combat.CombatSoundPlayer;
import org.example.mod_1.mod_1.combat.WeaponDetector;
import org.example.mod_1.mod_1.combat.WeaponType;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;
import org.slf4j.Logger;

public class CombatStatePacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final byte stateOrdinal;
    private final int extra; // chargeTicks when target is ATTACK_HEAVY; 0 otherwise

    public CombatStatePacket(CombatState state) {
        this(state, 0);
    }

    public CombatStatePacket(CombatState state, int extra) {
        this.stateOrdinal = (byte) state.ordinal();
        this.extra = extra;
    }

    private CombatStatePacket(byte stateOrdinal, int extra) {
        this.stateOrdinal = stateOrdinal;
        this.extra = extra;
    }

    public static void encode(CombatStatePacket msg, RegistryFriendlyByteBuf buf) {
        buf.writeByte(msg.stateOrdinal);
        buf.writeVarInt(msg.extra);
    }

    public static CombatStatePacket decode(RegistryFriendlyByteBuf buf) {
        return new CombatStatePacket(buf.readByte(), buf.readVarInt());
    }

    public static void handle(CombatStatePacket msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        CombatState requested = CombatState.fromOrdinal(msg.stateOrdinal);

        CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
            // Refresh weapon type from actual held item on server side
            if (requested == CombatState.DRAW_WEAPON) {
                WeaponType detected = WeaponDetector.detect(player);
                if (detected != WeaponType.UNARMED) {
                    cap.setWeaponType(detected);
                }
            }

            // Heavy attack: server computes charge multiplier from reported hold ticks
            if (requested == CombatState.ATTACK_HEAVY) {
                cap.setHeavyChargeMultiplier(CombatStateMachine.computeHeavyChargeMultiplier(msg.extra));
            }

            CombatState prevState = cap.getState();
            CombatStateMachine.requestTransition(cap, requested);

            // Play sound on successful state transition
            if (cap.getState() != prevState || cap.getState() == CombatState.ATTACK_LIGHT) {
                CombatSoundPlayer.playStateSound(player, cap.getState(), cap.getWeaponType(), cap.getComboCount());
            }

            CombatCapabilityEvents.broadcastCombatState(player, cap);
        });
    }
}
