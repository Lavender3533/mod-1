package org.example.mod_1.mod_1.combat.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.network.CustomPayloadEvent;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.WeaponType;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;

public class CombatSyncPacket {

    private final int entityId;
    private final byte stateOrdinal;
    private final byte weaponTypeOrdinal;
    private final boolean weaponDrawn;
    private final byte comboCount;
    private final byte stateTimer;

    public CombatSyncPacket(int entityId, CombatState state, WeaponType weaponType, boolean weaponDrawn, int comboCount, int stateTimer) {
        this.entityId = entityId;
        this.stateOrdinal = (byte) state.ordinal();
        this.weaponTypeOrdinal = (byte) weaponType.ordinal();
        this.weaponDrawn = weaponDrawn;
        this.comboCount = (byte) comboCount;
        this.stateTimer = (byte) Math.min(stateTimer, 127);
    }

    public static void encode(CombatSyncPacket msg, RegistryFriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeByte(msg.stateOrdinal);
        buf.writeByte(msg.weaponTypeOrdinal);
        buf.writeBoolean(msg.weaponDrawn);
        buf.writeByte(msg.comboCount);
        buf.writeByte(msg.stateTimer);
    }

    public static CombatSyncPacket decode(RegistryFriendlyByteBuf buf) {
        return new CombatSyncPacket(
                buf.readInt(),
                CombatState.fromOrdinal(buf.readByte()),
                WeaponType.fromOrdinal(buf.readByte()),
                buf.readBoolean(),
                buf.readByte(),
                buf.readByte()
        );
    }

    public static void handle(CombatSyncPacket msg, CustomPayloadEvent.Context ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(msg.entityId);
        if (!(entity instanceof Player player)) return;

        // Don't override local player's client prediction — only sync other players
        if (player == mc.player) return;

        CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
            cap.setState(CombatState.fromOrdinal(msg.stateOrdinal));
            cap.setWeaponType(WeaponType.fromOrdinal(msg.weaponTypeOrdinal));
            cap.setWeaponDrawn(msg.weaponDrawn);
            cap.setComboCount(msg.comboCount);
            cap.setStateTimer(msg.stateTimer);
        });
    }
}
