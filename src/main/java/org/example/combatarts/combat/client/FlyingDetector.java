package org.example.combatarts.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class FlyingDetector {
    private FlyingDetector() {}

    public static boolean isFlying(Player player) {
        // 鞘翅: isFallFlying 所有玩家都同步
        if (player.isFallFlying()) return true;

        // 创造飞行: abilities.flying 只有本地玩家准确(服务端不广播给其他客户端)
        // 远端玩家创造飞行 → 蒙皮网格正常渲染(站/走姿态浮在空中, 和原版表现一致)
        if (player == Minecraft.getInstance().player) {
            return player.getAbilities().flying;
        }

        return false;
    }
}
