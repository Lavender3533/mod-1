package org.example.combatarts.combat.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.network.CustomPayloadEvent;
import org.example.combatarts.combat.CombatState;
import org.example.combatarts.combat.WeaponType;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;

public class CombatSyncPacket {

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(CombatSyncPacket::encode, CombatSyncPacket::decode);

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

        CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
            // 本地玩家:state/stateTimer/combo/drawn 都由客户端预测 own (避免服务端 tick 比客户端慢半 tick
            // 时, 同步把 client 已经 queue→fire 后的 combo=2 snap 回 combo=1, 造成 3 段动画 1→2→1 闪烁;
            // drawn 同理 — 服务端慢半拍的 drawn=false 会让本地刚拔完的剑短暂回背上 → "回背→攻击"闪烁)。
            // 但 weaponType 必须同步: 这是从手持物推导, 客户端无 swap 检测, 不同步会停在按 R 时的值。
            // PARRY 例外: 服务端被动触发(收到攻击瞬间), 客户端无法预测, 必须从服务端拉, 否则 HUD 一直显示 BLOCK。
            boolean isLocal = player == Minecraft.getInstance().player;
            CombatState srvState = CombatState.fromOrdinal(msg.stateOrdinal);
            if (!isLocal) {
                cap.setState(srvState);
                cap.setStateTimer(msg.stateTimer);
                cap.setComboCount(msg.comboCount);
                cap.setWeaponDrawn(msg.weaponDrawn);
            } else if (srvState == CombatState.PARRY && cap.getState() != CombatState.PARRY) {
                cap.setState(CombatState.PARRY);
                cap.setStateTimer(msg.stateTimer);
            }
            cap.setWeaponType(WeaponType.fromOrdinal(msg.weaponTypeOrdinal));
        });
    }
}
