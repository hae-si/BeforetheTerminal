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
    /** 纵火犯：是否已被浇汽油（客户端可见——纵火犯按本能键透视浇湿者，C-092） */
    public boolean doused = false;

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
    /** 猎人是否已用狙击 */
    public int hunterShot = 0;
    /** 醉酒剩余 tick */
    public int drunkTicks = 0;
    /** 关系搭档 UUID 字符串 */
    public String partner = "";
    /** 关系类型 LOVER/ARCHENEMY/TWINS */
    public String relType = "";
    /** 永久醉酒来源（走私犯/哲人 UUID；施加者死亡后解除） */
    public String drunkSource = "";
    /** 虐待狂 <缄默> 剩余 tick（C-086：聋哑——语音禁言 + 听不到他人，由 NoellesrolesVoiceChatPlugin 读取） */
    public int muteTicks = 0;
    /** 派对主已变声次数（2 次 → 氦气自爆） */
    public int partyUses = 0;
    /** C-093 延时技能：待生效标记的类型（comedian，空 = 无） */
    public String delayedKind = "";
    /** C-093 延时技能：待生效标记的目标 UUID 字符串 */
    public String delayedTarget = "";
    /** C-093 延时技能：生效倒计时 tick（>0 时 BttDelayed 每 tick 递减；C-097 起取值 200–600 = 10–30 秒随机） */
    public int delayedTicks = 0;
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
    /** 工程师：<扫描> 透视剩余 tick（>0 时 BttEvents 逐 tick 递减并 sync，客户端本机自绘全车描边，C-089） */
    public int engineerScanTicks = 0;
    /** 窃贼：<搜刮> 后全员透视剩余 tick（>0 时客户端本机自绘全车描边，C-095；同 engineerScanTicks 口径） */
    public int thiefRevealTicks = 0;
    /** 教授 <使用药剂>：目标身上挂着的护盾层（1 层，免疫下一次致命伤；服务端状态，C-104） */
    public boolean professorShield = false;
    /** 演员 <装死>：是否处于躺倒态（**客户端可见** —— 本机把该玩家按尸体姿态渲染；C-106，开关式无时长） */
    public boolean fakeDead = false;
    /** 饕餮 <绑架>：吞下本玩家者（UUID 字符串，空 = 不在腹中；**客户端可见** —— 本机据此冻结输入/隐藏名牌；C-109） */
    public String swallowedBy = "";
    /** 第二身份（C-110）：**借来的技能身份** identifier 字符串（空 = 无；**客户端可见** —— 客户端据此切技能 UI）；不改阵营 */
    public String borrowedRole = "";
    /** 借来的技能剩余 tick（服务端；0 = 永久到本局结束） */
    public int borrowedTicks = 0;
    /** 饕餮：入腹时被吞者理智是否 >0（决定"理智归零"能否作为释放条件；服务端，C-113） */
    public boolean swallowedMoodOk = false;
    /** 小恶魔 <印记> 的目标 UUID 字符串（空 = 未印记；服务端回合状态，C-111） */
    public String impMark = "";
    /** 保镖 <守护>：被守护者 UUID 字符串（空 = 未守护；服务端，C-117） */
    public String guardTarget = "";
    /** 保镖 <守护>：守护者 UUID 字符串（空 = 未被守护；服务端，C-117） */
    public String guardedBy = "";
    /** 保镖 <守护>：守护剩余 tick（>0 时"只有守护者死亡才会死亡"；服务端，C-117） */
    public int guardedTicks = 0;
    /** 寄生者 <寄生>：宿主 UUID 字符串（空 = 未寄生；宿主存活时寄生者不会死亡；服务端，C-117） */
    public String parasiteHost = "";
    /** 贵族「族人」UUID 列表（逗号分隔；客户端可见——本人注视显示「族人」；C-121） */
    public String kins = "";
    /** 飞行家「阵营代表」UUID 列表（逗号分隔；客户端可见；C-121） */
    public String balloonReps = "";
    /** 乘务员 <广播> 开关（docx 2026-09-12：随时切换正常讲话 ⇄ 全车广播；C-123） */
    public boolean broadcastOn = false;
    /** 异教领袖 <救赎>：阵营覆盖（""|PASSENGER|KILLER|LONE|OUTSIDER；本人加入被救赎者阵营；C-124） */
    public String campOverride = "";
    /** 异教领袖：当前计数窗口（两次救赎之间，含首轮）内被处决的次数（C-124） */
    public int executionsInWindow = 0;
    /** 异教领袖：审判已触发（本局只触发一次；C-124） */
    public boolean judgmentFired = false;
    /** 梦游病 <入梦>：灵魂出窍中（客户端据此切换假相机，C-087） */
    public boolean projecting = false;
    /** 纵火犯：被浇后「闻到汽油味」延迟提示剩余 tick（服务端；C-092，C-097 与延时技能统一 10–30 秒随机） */
    public int gasolineHintTicks = 0;
    /** 出窍时留下的躯体坐标（客户端画本体 + 30 格半径限制的参考点） */
    public double bodyX = 0;
    public double bodyY = 0;
    public double bodyZ = 0;

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

    /** 永久醉酒（走私犯/哲人）：施加者死亡后由 kill hook 解除 */
    public void applyPermanentDrunk(java.util.UUID source) {
        this.drunkTicks = Integer.MAX_VALUE / 4;
        this.drunkSource = source.toString();
    }

    /** 解除醉酒（含永久醉） */
    public void clearDrunk() {
        this.drunkTicks = 0;
        this.drunkSource = "";
    }

    // ===== 虐待狂 <缄默>（C-086，参照 NRS Silencer/SilencedPlayerComponent）=====
    public boolean isMuted() {
        return muteTicks > 0;
    }

    /** 施加/延长缄默（取较大值）；客户端可见（HUD/提示） */
    public void applyMute(int ticks) {
        this.muteTicks = Math.max(ticks, this.muteTicks);
        this.sync();
    }

    /** 每 tick 递减（BttEvents tick 循环调用） */
    public void decrementMute() {
        if (this.muteTicks > 0) this.muteTicks--;
    }

    // ===== 梦游病 <入梦>（C-087；参照 NRS SpiritPlayerComponent，去掉 fork 依赖）=====

    // ===== 纵火犯（C-092）=====

    public boolean isDoused() {
        return this.doused;
    }

    /** 浇汽油：置位并同步（客户端本能键透视用） */
    public void setDoused() {
        this.doused = true;
        this.sync();
    }

    /** 排定延迟提示（本人十几秒后闻到汽油味；不同步，纯服务端计时） */
    public void scheduleGasolineHint(int ticks) {
        this.gasolineHintTicks = ticks;
    }

    // ===== 教授 <使用药剂>：护盾（C-104）=====

    public boolean hasProfessorShield() {
        return this.professorShield;
    }

    /** 挂上 1 层护盾；已有则返回 false（调用方据此**不扣冷却**，同虐待狂待生效标记口径） */
    public boolean applyProfessorShield() {
        if (this.professorShield) return false;
        this.professorShield = true;
        return true;
    }

    /** 护盾挡下一次致命伤：消耗（服务端状态，无客户端可见字段） */
    public void consumeProfessorShield() {
        this.professorShield = false;
    }

    // ===== 第二身份：借技能（C-110）=====

    public boolean isBorrowing() {
        return !this.borrowedRole.isEmpty();
    }

    public void setBorrowedRole(String roleId, int ticks) {
        this.borrowedRole = roleId;
        this.borrowedTicks = ticks;
        this.sync();
    }

    public void clearBorrowedRole() {
        this.borrowedRole = "";
        this.borrowedTicks = 0;
        this.sync();
    }

    // ===== 饕餮 <绑架>（C-109）=====

    public boolean isSwallowed() {
        return !this.swallowedBy.isEmpty();
    }

    /** 被吞：记下饕餮 uuid 并同步（客户端据此冻结输入、隐藏名牌） */
    public void setSwallowedBy(String kidnapperUuid) {
        this.swallowedBy = kidnapperUuid;
        this.sync();
    }

    /** 出腹 / 回合清理：清态并同步 */
    public void clearSwallowed() {
        this.swallowedBy = "";
        this.sync();
    }

    // ===== 演员 <装死>（C-106）=====

    public boolean isFakeDead() {
        return this.fakeDead;
    }

    /** 切换躺倒态（客户端据此渲染尸体姿态 / 恢复站姿） */
    public void setFakeDead(boolean value) {
        this.fakeDead = value;
        this.sync();
    }

    // ===== 窃贼 <搜刮> 后全员透视（C-095）=====

    /** 搜刮成功：置全员透视读秒并同步（BttEvents 逐 tick 递减） */
    public void setThiefReveal(int ticks) {
        this.thiefRevealTicks = ticks;
        this.sync();
    }

    public boolean isProjecting() {
        return this.projecting;
    }

    /** 入梦：记录躯体坐标并置出窍态（客户端可见） */
    public void startProjecting(double x, double y, double z) {
        this.bodyX = x;
        this.bodyY = y;
        this.bodyZ = z;
        this.projecting = true;
        this.sync();
    }

    /** 回归躯体 / 强制收回 */
    public void stopProjecting() {
        this.projecting = false;
        this.sync();
    }

    /** 开局清空全部回合状态 */
    public void reset() {
        cult = false;
        doused = false;
        veteranUses = 0;
        witchUses = 0;
        hasKilled = 0;
        novelistHits = 0;
        hunterShot = 0;
        drunkTicks = 0;
        muteTicks = 0;
        partner = "";
        relType = "";
        drunkSource = "";
        partyUses = 0;
        delayedKind = "";
        delayedTarget = "";
        delayedTicks = 0;
        markedTarget = "";
        bombPlaced = false;
        bombBeeping = false;
        bombTimer = 0;
        beepTimer = 0;
        bombTransferCd = 0;
        bombLastSec = -1;
        bombSource = "";
        engineerScanTicks = 0;
        thiefRevealTicks = 0;
        professorShield = false;
        fakeDead = false;
        swallowedBy = "";
        borrowedRole = "";
        borrowedTicks = 0;
        impMark = "";
        guardTarget = "";
        guardedBy = "";
        guardedTicks = 0;
        parasiteHost = "";
        kins = "";
        balloonReps = "";
        broadcastOn = false;
        campOverride = "";
        executionsInWindow = 0;
        judgmentFired = false;
        swallowedMoodOk = false;
        projecting = false;
        gasolineHintTicks = 0;
        bodyX = 0;
        bodyY = 0;
        bodyZ = 0;
        this.sync();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putBoolean("cult", cult);
        tag.putBoolean("doused", doused);
        tag.putInt("veteranUses", veteranUses);
        tag.putInt("witchUses", witchUses);
        tag.putInt("hasKilled", hasKilled);
        tag.putInt("novelistHits", novelistHits);
        tag.putInt("hunterShot", hunterShot);
        tag.putInt("drunkTicks", drunkTicks);
        tag.putInt("muteTicks", muteTicks);
        tag.putString("partner", partner);
        tag.putString("relType", relType);
        tag.putString("drunkSource", drunkSource);
        tag.putInt("partyUses", partyUses);
        tag.putString("delayedKind", delayedKind);
        tag.putString("delayedTarget", delayedTarget);
        tag.putInt("delayedTicks", delayedTicks);
        tag.putString("markedTarget", markedTarget);
        tag.putBoolean("bombPlaced", bombPlaced);
        tag.putBoolean("bombBeeping", bombBeeping);
        tag.putInt("bombTimer", bombTimer);
        tag.putInt("beepTimer", beepTimer);
        tag.putInt("bombTransferCd", bombTransferCd);
        tag.putInt("bombLastSec", bombLastSec);
        tag.putString("bombSource", bombSource);
        tag.putInt("engineerScanTicks", engineerScanTicks);
        tag.putInt("thiefRevealTicks", thiefRevealTicks);
        tag.putBoolean("professorShield", professorShield);
        tag.putBoolean("fakeDead", fakeDead);
        tag.putString("swallowedBy", swallowedBy);
        tag.putString("borrowedRole", borrowedRole);
        tag.putInt("borrowedTicks", borrowedTicks);
        tag.putString("impMark", impMark);
        tag.putString("guardTarget", guardTarget);
        tag.putString("guardedBy", guardedBy);
        tag.putInt("guardedTicks", guardedTicks);
        tag.putString("parasiteHost", parasiteHost);
        tag.putString("kins", kins);
        tag.putString("balloonReps", balloonReps);
        tag.putBoolean("broadcastOn", broadcastOn);
        tag.putString("campOverride", campOverride);
        tag.putInt("executionsInWindow", executionsInWindow);
        tag.putBoolean("judgmentFired", judgmentFired);
        tag.putBoolean("swallowedMoodOk", swallowedMoodOk);
        tag.putBoolean("projecting", projecting);
        tag.putInt("gasolineHintTicks", gasolineHintTicks);
        tag.putDouble("bodyX", bodyX);
        tag.putDouble("bodyY", bodyY);
        tag.putDouble("bodyZ", bodyZ);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        this.cult = tag.contains("cult") && tag.getBoolean("cult");
        this.doused = tag.contains("doused") && tag.getBoolean("doused");
        this.veteranUses = tag.getInt("veteranUses");
        this.witchUses = tag.getInt("witchUses");
        this.hasKilled = tag.getInt("hasKilled");
        this.novelistHits = tag.getInt("novelistHits");
        this.hunterShot = tag.getInt("hunterShot");
        this.drunkTicks = tag.getInt("drunkTicks");
        this.muteTicks = tag.getInt("muteTicks");
        this.partner = tag.contains("partner") ? tag.getString("partner") : "";
        this.relType = tag.contains("relType") ? tag.getString("relType") : "";
        this.drunkSource = tag.contains("drunkSource") ? tag.getString("drunkSource") : "";
        this.partyUses = tag.getInt("partyUses");
        this.delayedKind = tag.contains("delayedKind") ? tag.getString("delayedKind") : "";
        this.delayedTarget = tag.contains("delayedTarget") ? tag.getString("delayedTarget") : "";
        this.delayedTicks = tag.getInt("delayedTicks");
        this.markedTarget = tag.contains("markedTarget") ? tag.getString("markedTarget") : "";
        this.bombPlaced = tag.contains("bombPlaced") && tag.getBoolean("bombPlaced");
        this.bombBeeping = tag.contains("bombBeeping") && tag.getBoolean("bombBeeping");
        this.bombTimer = tag.getInt("bombTimer");
        this.beepTimer = tag.getInt("beepTimer");
        this.bombTransferCd = tag.getInt("bombTransferCd");
        this.bombLastSec = tag.getInt("bombLastSec");
        this.bombSource = tag.contains("bombSource") ? tag.getString("bombSource") : "";
        this.engineerScanTicks = tag.getInt("engineerScanTicks");
        this.thiefRevealTicks = tag.getInt("thiefRevealTicks");
        this.professorShield = tag.contains("professorShield") && tag.getBoolean("professorShield");
        this.fakeDead = tag.contains("fakeDead") && tag.getBoolean("fakeDead");
        this.swallowedBy = tag.contains("swallowedBy") ? tag.getString("swallowedBy") : "";
        this.borrowedRole = tag.contains("borrowedRole") ? tag.getString("borrowedRole") : "";
        this.borrowedTicks = tag.getInt("borrowedTicks");
        this.impMark = tag.contains("impMark") ? tag.getString("impMark") : "";
        this.guardTarget = tag.contains("guardTarget") ? tag.getString("guardTarget") : "";
        this.guardedBy = tag.contains("guardedBy") ? tag.getString("guardedBy") : "";
        this.guardedTicks = tag.getInt("guardedTicks");
        this.parasiteHost = tag.contains("parasiteHost") ? tag.getString("parasiteHost") : "";
        this.kins = tag.contains("kins") ? tag.getString("kins") : "";
        this.balloonReps = tag.contains("balloonReps") ? tag.getString("balloonReps") : "";
        this.broadcastOn = tag.contains("broadcastOn") && tag.getBoolean("broadcastOn");
        this.campOverride = tag.contains("campOverride") ? tag.getString("campOverride") : "";
        this.executionsInWindow = tag.getInt("executionsInWindow");
        this.judgmentFired = tag.contains("judgmentFired") && tag.getBoolean("judgmentFired");
        this.swallowedMoodOk = tag.contains("swallowedMoodOk") && tag.getBoolean("swallowedMoodOk");
        this.projecting = tag.contains("projecting") && tag.getBoolean("projecting");
        this.gasolineHintTicks = tag.getInt("gasolineHintTicks");
        this.bodyX = tag.contains("bodyX") ? tag.getDouble("bodyX") : 0;
        this.bodyY = tag.contains("bodyY") ? tag.getDouble("bodyY") : 0;
        this.bodyZ = tag.contains("bodyZ") ? tag.getDouble("bodyZ") : 0;
    }
}
