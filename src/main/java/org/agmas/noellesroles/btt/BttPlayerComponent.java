package org.agmas.noellesroles.btt;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * BTT 每玩家回合状态（AutoSynced CCA）——**取代原 {@code BttState} 静态 Map**（R1 架构重构）。
 * <p>
 * 约定：
 * <ul>
 *   <li>客户端可见字段（当前 {@link #cult}）变更时 {@link #sync()}；</li>
 *   <li>服务端回合状态（老兵/巫觋次数、杀人历史、醉酒、关系搭档、护盾等）只持久化、不主动 sync；</li>
 *   <li>开局 {@code initializeGame} 调 {@link #reset()} 清空。</li>
 * </ul>
 * 教训：服务端静态 Map 无法被客户端读取（曾致教团信徒互透视失效）；需要客户端可见的状态一律走 CCA。
 */
public class BttPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<BttPlayerComponent> KEY =
            ComponentRegistry.getOrCreate(Identifier.of(Noellesroles.MOD_ID, "btt_player"), BttPlayerComponent.class);

    private final PlayerEntity player;

    // ===== 客户端可见（变更即 sync）=====
    /** 教团信徒（救世主 &lt;预知&gt; 命中） */
    public boolean cult = false;

    // ===== 服务端回合状态（不主动 sync）=====
    /** 老兵刀剩余次数 */
    public int veteranUses = 0;
    /** 巫觋刀剩余次数 */
    public int witchUses = 0;
    /** 是否杀过人（侦探 &lt;调查&gt;） */
    public int hasKilled = 0;
    /** 小说家猜对次数 */
    public int novelistHits = 0;
    /** 女仆取餐次数 */
    public int maidPickups = 0;
    /** 猎人是否已用狙击 */
    public int hunterShot = 0;
    /** 宿敌护盾余量（B3 后未使用，保留字段兼容） */
    public int archenemyShields = 0;
    /** 醉酒剩余 tick */
    public int drunkTicks = 0;
    /** 关系搭档 UUID 字符串 */
    public String partner = "";
    /** 关系类型 LOVER/ARCHENEMY/TWINS */
    public String relType = "";
    /** 永久醉酒来源（走私犯/冒牌货 UUID；施加者死亡后解除） */
    public String drunkSource = "";
    /** 派对主已变声次数（2 次 → 氦气自爆） */
    public int partyUses = 0;
    /** 记者：<跟踪> 标记的目标 UUID 字符串（空=未标记） */
    public String markedTarget = "";
    /** 恐怖分子炸弹：是否放置在本玩家身上 */
    public boolean bombPlaced = false;
    /** 炸弹是否已进入倒计时阶段（5 秒静默后） */
    public boolean bombBeeping = false;
    /** 静默期剩余 tick（5 秒） */
    public int bombTimer = 0;
    /** 倒计时剩余 tick（15 秒） */
    public int beepTimer = 0;
    /** 传递冷却剩余 tick（3 秒） */
    public int bombTransferCd = 0;
    /** 上次显示的倒计时秒（避免刷屏） */
    public int bombLastSec = -1;
    /** 放置炸弹的恐怖分子 UUID 字符串 */
    public String bombSource = "";
    /** 工程师：<扫描> 透视剩余 tick（>0 时 BttEvents 令全车存活者发光） */
    public int engineerScanTicks = 0;

    public BttPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ===== 教团 =====
    public boolean isCult() {
        return cult;
    }

    public void setCult(boolean value) {
        this.cult = value;
        this.sync();
    }

    // ===== 醉酒（BT-SYS-DRUNK）=====
    public boolean isDrunk() {
        return drunkTicks > 0;
    }

    /** 施加/延长醉酒（取较大值，避免叠加覆盖短醉） */
    public void applyDrunk(int ticks) {
        this.drunkTicks = Math.max(ticks, this.drunkTicks);
    }

    /** 每 tick 递减（BttEvents tick 循环调用） */
    public void decrementDrunk() {
        if (this.drunkTicks > 0) this.drunkTicks--;
    }

    /** 永久醉酒（走私犯/冒牌货）：施加者死亡后由 kill hook 解除 */
    public void applyPermanentDrunk(java.util.UUID source) {
        this.drunkTicks = Integer.MAX_VALUE / 4;
        this.drunkSource = source.toString();
    }

    /** 解除醉酒（含永久醉） */
    public void clearDrunk() {
        this.drunkTicks = 0;
        this.drunkSource = "";
    }

    /** 开局清空全部回合状态 */
    public void reset() {
        cult = false;
        veteranUses = 0;
        witchUses = 0;
        hasKilled = 0;
        novelistHits = 0;
        maidPickups = 0;
        hunterShot = 0;
        archenemyShields = 0;
        drunkTicks = 0;
        partner = "";
        relType = "";
        drunkSource = "";
        partyUses = 0;
        markedTarget = "";
        bombPlaced = false;
        bombBeeping = false;
        bombTimer = 0;
        beepTimer = 0;
        bombTransferCd = 0;
        bombLastSec = -1;
        bombSource = "";
        engineerScanTicks = 0;
        this.sync();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putBoolean("cult", cult);
        tag.putInt("veteranUses", veteranUses);
        tag.putInt("witchUses", witchUses);
        tag.putInt("hasKilled", hasKilled);
        tag.putInt("novelistHits", novelistHits);
        tag.putInt("maidPickups", maidPickups);
        tag.putInt("hunterShot", hunterShot);
        tag.putInt("archenemyShields", archenemyShields);
        tag.putInt("drunkTicks", drunkTicks);
        tag.putString("partner", partner);
        tag.putString("relType", relType);
        tag.putString("drunkSource", drunkSource);
        tag.putInt("partyUses", partyUses);
        tag.putString("markedTarget", markedTarget);
        tag.putBoolean("bombPlaced", bombPlaced);
        tag.putBoolean("bombBeeping", bombBeeping);
        tag.putInt("bombTimer", bombTimer);
        tag.putInt("beepTimer", beepTimer);
        tag.putInt("bombTransferCd", bombTransferCd);
        tag.putInt("bombLastSec", bombLastSec);
        tag.putString("bombSource", bombSource);
        tag.putInt("engineerScanTicks", engineerScanTicks);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        this.cult = tag.contains("cult") && tag.getBoolean("cult");
        this.veteranUses = tag.getInt("veteranUses");
        this.witchUses = tag.getInt("witchUses");
        this.hasKilled = tag.getInt("hasKilled");
        this.novelistHits = tag.getInt("novelistHits");
        this.maidPickups = tag.getInt("maidPickups");
        this.hunterShot = tag.getInt("hunterShot");
        this.archenemyShields = tag.getInt("archenemyShields");
        this.drunkTicks = tag.getInt("drunkTicks");
        this.partner = tag.contains("partner") ? tag.getString("partner") : "";
        this.relType = tag.contains("relType") ? tag.getString("relType") : "";
        this.drunkSource = tag.contains("drunkSource") ? tag.getString("drunkSource") : "";
        this.partyUses = tag.getInt("partyUses");
        this.markedTarget = tag.contains("markedTarget") ? tag.getString("markedTarget") : "";
        this.bombPlaced = tag.contains("bombPlaced") && tag.getBoolean("bombPlaced");
        this.bombBeeping = tag.contains("bombBeeping") && tag.getBoolean("bombBeeping");
        this.bombTimer = tag.getInt("bombTimer");
        this.beepTimer = tag.getInt("beepTimer");
        this.bombTransferCd = tag.getInt("bombTransferCd");
        this.bombLastSec = tag.getInt("bombLastSec");
        this.bombSource = tag.contains("bombSource") ? tag.getString("bombSource") : "";
        this.engineerScanTicks = tag.getInt("engineerScanTicks");
    }
}
