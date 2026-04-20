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
    private final float moveX; // for DODGE: client's xxa input at dodge moment
    private final float moveZ; // for DODGE: client's zza input at dodge moment

    public CombatStatePacket(CombatState state) {
        this(state, 0, 0f, 0f);
    }

    public CombatStatePacket(CombatState state, int extra) {
        this(state, extra, 0f, 0f);
    }

    public CombatStatePacket(CombatState state, int extra, float moveX, float moveZ) {
        this.stateOrdinal = (byte) state.ordinal();
        this.extra = extra;
        this.moveX = moveX;
        this.moveZ = moveZ;
    }

    private CombatStatePacket(byte stateOrdinal, int extra, float moveX, float moveZ) {
        this.stateOrdinal = stateOrdinal;
        this.extra = extra;
        this.moveX = moveX;
        this.moveZ = moveZ;
    }

    public static void encode(CombatStatePacket msg, RegistryFriendlyByteBuf buf) {
        buf.writeByte(msg.stateOrdinal);
        buf.writeVarInt(msg.extra);
        buf.writeFloat(msg.moveX);
        buf.writeFloat(msg.moveZ);
    }

    public static CombatStatePacket decode(RegistryFriendlyByteBuf buf) {
        return new CombatStatePacket(buf.readByte(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
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

            // Dash attack: 服务端独立检测 sprint 状态(客户端检测后无法把 combo=99 传到服务端)
            // 只在玩家最近 2s 没攻击过的情况下触发,否则继续走正常 combo 1/2/3
            if (requested == CombatState.ATTACK_LIGHT
                    && cap.getWeaponType() == WeaponType.SWORD
                    && player.isSprinting()
                    && cap.getComboCount() <= 0
                    && cap.getState() == CombatState.IDLE) {
                long lastAttack = cap.getLastAttackTime();
                long gameTime = player.level().getGameTime();
                boolean recentlyAttacked = lastAttack > 0 && (gameTime - lastAttack) < 40;
                if (!recentlyAttacked) {
                    cap.setComboCount(99);
                }
            }

            CombatState prevState = cap.getState();
            CombatStateMachine.requestTransition(cap, requested);

            // DODGE: 立即按客户端报告的方向施力（避免依赖服务端不可靠的 player.xxa）
            if (cap.getState() == CombatState.DODGE && prevState != CombatState.DODGE) {
                CombatCapabilityEvents.applyDodgeImpulse(player, msg.moveX, msg.moveZ);
            }

            // Play sound on successful state transition
            if (cap.getState() != prevState || cap.getState() == CombatState.ATTACK_LIGHT) {
                CombatSoundPlayer.playStateSound(player, cap.getState(), cap.getWeaponType(), cap.getComboCount());
            }

            CombatCapabilityEvents.broadcastCombatState(player, cap);
        });
    }
}
