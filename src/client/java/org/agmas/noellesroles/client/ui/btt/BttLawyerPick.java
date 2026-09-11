package org.agmas.noellesroles.client.ui.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import org.agmas.noellesroles.btt.BttRoles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 律师 &lt;起诉&gt; 的客户端多指名状态（C-103）：在选人屏里逐个点头像指名，凑齐后**单包**提交
 * （照 swapper 的分段选择口径；用户 2026-09-11「有几个凶手就展示几次」）。
 * 需要指名的人数 = 场上**存活**的凶手席位玩家数（与服务端 `BttRules.isKillerSeat` 同算）。
 */
public final class BttLawyerPick {
    private BttLawyerPick() {}

    private static final List<UUID> PICKS = new ArrayList<>();

    public static List<UUID> picks() {
        return PICKS;
    }

    public static boolean isPicked(UUID uuid) {
        return PICKS.contains(uuid);
    }

    public static void reset() {
        PICKS.clear();
    }

    /**
     * 需要的指名数（0 = 当前无法起诉）= **在场凶手席位玩家数**（**含已死亡席位**；用户 2026-09-11 裁定
     * 「就算有的凶手（席位）已经死亡也要点出」）——与服务端同一判据（不做存活过滤）。
     */
    public static int needed(ClientPlayerEntity self) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return 0;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(client.world);
        int n = 0;
        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
            if (BttRoles.isKillerSeat(gwc.getRole(p))) n++;
        }
        return n;
    }
}