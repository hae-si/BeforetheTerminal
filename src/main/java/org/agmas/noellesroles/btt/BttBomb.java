package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.noellesroles.ModItems;

/**
 * 恐怖分子炸弹状态机（C-084）：**安放**改由物品 [炸弹箱] 右键目标触发（不再是 G 键技能），
 * **传递**保留 G 键 + 物品右键双入口。持有状态在 {@link BttPlayerComponent}，倒计时爆炸在 BttEvents。
 * 门控：调用方负责 BTT 模式/running/身份判定；本类只管状态。
 */
public final class BttBomb {
    private BttBomb() {}

    /** 静默期：5 秒（doc：5 秒后可见） */
    public static final int QUIET_TICKS = 100;
    /** 倒计时：15 秒（doc） */
    public static final int COUNTDOWN_TICKS = 300;
    /** 传递冷却：3 秒（doc：到手 3 秒才能传递） */
    public static final int TRANSFER_CD_TICKS = 60;
    /** 安放冷却：30 秒（doc：[炸弹箱] 冷却 30 秒） */
    public static final int PLACE_CD_TICKS = 600;

    /** 安放炸弹（目标已有炸弹 → false，调用方不应消耗冷却） */
    public static boolean place(ServerPlayerEntity user, ServerPlayerEntity target) {
        if (target == user) return false;
        BttPlayerComponent tc = BttPlayerComponent.KEY.get(target);
        if (tc.bombPlaced) {
            user.sendMessage(Text.literal("目标身上已有炸弹。").withColor(BttRoles.TERRORIST.color()), true);
            return false;
        }
        tc.bombPlaced = true;
        tc.bombBeeping = false;
        tc.bombTimer = QUIET_TICKS;
        tc.bombSource = user.getUuid().toString();
        tc.bombLastSec = -1;
        tc.sync(); // 客户端需要同步（HUD/G 键传递门控读本组件）
        user.sendMessage(Text.literal("炸弹已放置。").withColor(BttRoles.TERRORIST.color()), true);
        target.sendMessage(Text.literal("你听到了一声轻响……").withColor(BttRoles.TERRORIST.color()), true);
        return true;
    }

    /** 传递炸弹（仅倒计时阶段可传出；3 秒传递冷却；静默期不可传递） */
    public static boolean transfer(ServerPlayerEntity holder, ServerPlayerEntity target) {
        BttPlayerComponent hc = BttPlayerComponent.KEY.get(holder);
        if (!hc.bombPlaced || !hc.bombBeeping || hc.bombTransferCd > 0) return false;
        if (target == holder || !GameFunctions.isPlayerAliveAndSurvival(target)) return false;
        BttPlayerComponent tc = BttPlayerComponent.KEY.get(target);
        if (tc.bombPlaced) return false;
        tc.bombPlaced = true;
        tc.bombBeeping = true;
        tc.beepTimer = hc.beepTimer;
        tc.bombSource = hc.bombSource;
        tc.bombLastSec = -1;
        tc.bombTransferCd = TRANSFER_CD_TICKS;
        hc.bombPlaced = false;
        hc.bombBeeping = false;
        removeBombItem(holder);
        giveBombItem(target);
        // 恐怖分子的 [炸弹箱] 是常驻 kit（安放走物品 CD、不消耗）→ 脱手后补回，否则其失去再安放能力
        if (GameWorldComponent.KEY.get(holder.getWorld()).isRole(holder, BttRoles.TERRORIST)) giveBombItem(holder);
        hc.sync();
        tc.sync();
        holder.getWorld().playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        holder.sendMessage(Text.literal("炸弹已脱手。").withColor(BttRoles.TERRORIST.color()), true);
        target.sendMessage(Text.literal("有人把炸弹塞给了你！快传出去！")
                .formatted(Formatting.BOLD).withColor(BttRoles.TERRORIST.color()), true);
        return true;
    }

    // ===== [炸弹箱] 物品（省纹理：安放用的炸弹箱 = 携带在身上的炸弹，同一物品）=====

    /** doc「5 秒后可见」：倒计时开始时把 [炸弹箱] 发到持有者身上 */
    public static void reveal(ServerPlayerEntity carrier) {
        giveBombItem(carrier);
    }

    /** 炸弹离开该玩家（爆炸/持有者死亡）→ 收回 [炸弹箱]，避免尸体掉落/他人捡走 */
    public static void clear(ServerPlayerEntity carrier) {
        removeBombItem(carrier);
    }

    /** 玩家身上至多 1 个 [炸弹箱]（恐怖分子自己带来的那个不重复发） */
    private static void giveBombItem(ServerPlayerEntity player) {
        if (countBombItem(player) == 0) player.giveItemStack(new ItemStack(ModItems.BOMB));
    }

    private static void removeBombItem(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isOf(ModItems.BOMB)) {
                inventory.removeStack(i, 1);
                break;
            }
        }
    }

    private static int countBombItem(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isOf(ModItems.BOMB)) count++;
        }
        return count;
    }
}
