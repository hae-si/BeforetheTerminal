package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;

/**
 * BTT 事件挂接：
 * - ModdedRoleAssigned：给 bt_* 身份发初始道具（教父=刀+查验镜；义警=左轮）。
 * - AllowPlayerDeath：小丑被乘客枪击 → 取消死亡并进入 2 分钟疯魔（护盾 2 层 + 球棒）。
 * - 过渡期守卫：bt_* 身份写入 HML disabled，避免泄漏进 HML 谋杀局（沿用 NR shitpost 模式）。
 */
public final class BttEvents {
    private BttEvents() {}

    public static void init() {
        ensureWatheClientConfig();
        registerRoleKits();
        registerJesterPsycho();
        guardHmlPool();
    }

    /**
     * 兼容垫片（wathe 1.3.2 全新客户端实例 NPE 规避）：
     * MC 1.21.1 的 client 入口点在 MinecraftClient.options 赋值前执行；
     * wathe 首次初始化配置时 MidnightConfig.write → WatheConfig.writeChanges 读取 options → NPE，
     * 且 write 先于文件落盘 → 每次启动必崩。官方整合包靠自带 config/wathe.json 规避。
     * 本垫片在 main 阶段（先于所有 client 入口点）预创建缺失的 wathe.json（字段均为 wathe 默认值）。
     */
    private static void ensureWatheClientConfig() {
        try {
            var cfg = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("wathe.json");
            if (java.nio.file.Files.notExists(cfg)) {
                java.nio.file.Files.writeString(cfg,
                        "{\"ultraPerfMode\": false, \"disableScreenShake\": false}");
                BeforeTheTerminalGameMode.LOGGER.info("[BTT] pre-created config/wathe.json (wathe 1.3.2 fresh-client NPE workaround)");
            }
        } catch (Exception e) {
            BeforeTheTerminalGameMode.LOGGER.warn("[BTT] could not pre-create wathe config: {}", e.toString());
        }
    }

    private static void registerRoleKits() {
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            if (role == BttRoles.GODFATHER) {
                player.giveItemStack(new ItemStack(WatheItems.KNIFE));
                player.giveItemStack(new ItemStack(BttItems.INSPECT));
            }
            if (role == BttRoles.VIGILANTE) {
                player.giveItemStack(new ItemStack(WatheItems.REVOLVER));
            }
        });
    }

    private static void registerJesterPsycho() {
        AllowPlayerDeath.EVENT.register((victim, killer, reason) -> {
            if (!BttIdentity.isBttMode(victim.getWorld())) return true;
            if (reason != GameConstants.DeathReasons.GUN) return true;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(victim.getWorld());
            if (!gwc.isRole(victim, BttRoles.JESTER)) return true;
            if (killer == null || !gwc.isInnocent(killer)) return true; // 仅“被乘客枪击”
            PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(victim);
            if (psycho.getPsychoTicks() > 0) return true; // 已在疯魔中：交给 wathe 护盾/死亡结算
            // 进入 2 分钟疯魔，护盾 2 层（“+先前被误杀人数”与幕间胜负 = BT-ROLE-JESTER-001 TODO）
            psycho.startPsycho();
            psycho.setPsychoTicks(GameConstants.getInTicks(2, 0));
            psycho.setArmour(2);
            victim.giveItemStack(new ItemStack(WatheItems.BAT)); // doc 疯魔=球棒杀人
            return false; // 取消本次死亡
        });
    }

    private static void guardHmlPool() {
        HarpyModLoaderConfig.HANDLER.load();
        boolean changed = false;
        for (Identifier id : BttRoles.NEW_BTT_ROLE_IDS) {
            if (!HarpyModLoaderConfig.HANDLER.instance().disabled.contains(id.toString())) {
                HarpyModLoaderConfig.HANDLER.instance().disabled.add(id.toString());
                changed = true;
            }
        }
        if (changed) HarpyModLoaderConfig.HANDLER.save();
    }
}
