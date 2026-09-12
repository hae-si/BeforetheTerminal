package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BTT（终点站抵达之前）身份目录——策划案全量 70 身份（2026-09-04 目录轮）。
 *
 * 键名规则（渐进式替换，D7/D8）：
 * - 新身份 = 新键 `noellesroles:<英文身份名 snake_case>`；
 * - NR 已有同身份键 = **接管既有 Role 对象**（coroner=医生 / noisemaker=处子 / jester=小丑 /
 *   conductor=列车长 / voodoo=巫觋），不重复创建；
 * - 仅教父/义警为本轮前新增键。
 *
 * 元数据：阵营分类（{@link Faction}，含 isInnocent/canUseKiller/mood/体力/倒计时可见性的
 * 阵营默认值，取 wathe 原生数值）。**不含任何技能/被动/道具/交互/胜负逻辑**——
 * 每身份的策划特例（小女孩/司机可见倒计时、乘警无限制等）随其实现轮落地。
 *
 * 隔离：全部新键由 {@link #newRoleIds()} 提供、BttEvents 写入 HML disabled，
 * 不进 HML 谋杀局池；接管键维持 NR 池参与不变。
 * 注册须早于 HML SERVER_STARTED 的 refreshRoles（onInitialize 链）。
 */
public final class BttRoles {
    private BttRoles() {}

    /** 身份阵营分类（基础元数据；数值取 wathe 原生默认） */
    public enum Faction {
        /** 执法乘客：乘客阵营，有理智/体力，不可见倒计时 */
        ENFORCER(true, false, Role.MoodType.REAL, 200, false),
        /** 平民乘客：同执法（分类用于目录统计与后续实现） */
        CIVILIAN(true, false, Role.MoodType.REAL, 200, false),
        /** 主犯凶手（全局仅 1）：无理智/体力限制，可见倒计时 */
        PRINCIPAL(false, true, Role.MoodType.FAKE, -1, true),
        /** 从犯凶手 */
        ACCOMPLICE(false, true, Role.MoodType.FAKE, -1, true),
        /** 中立 · 独行（C-037 三分类）：个人条件独胜、不阻塞任何主结局、有限体力（蓝旗 mood_ghost） */
        LONE(false, false, Role.MoodType.FAKE, 200, false),
        /** 中立 · 外人（C-037 三分类）：自成阵营、无限体力、全员尾声；可见倒计时（docx 2026-09-07 恢复外人可见，品红旗 mood_jester） */
        OUTSIDER(false, false, Role.MoodType.FAKE, -1, true),
        /** 中立 · 狂人（C-037 三分类）：阵营归属乘客（结局计数随乘客）、正常需求/理智、无独立胜利（绿旗） */
        MAD(true, false, Role.MoodType.REAL, 200, false);

        public final boolean innocent;
        public final boolean canUseKiller;
        public final Role.MoodType mood;
        public final int maxSprintTime;
        public final boolean canSeeTime;

        Faction(boolean innocent, boolean canUseKiller, Role.MoodType mood, int maxSprintTime, boolean canSeeTime) {
            this.innocent = innocent;
            this.canUseKiller = canUseKiller;
            this.mood = mood;
            this.maxSprintTime = maxSprintTime;
            this.canSeeTime = canSeeTime;
        }
    }

    private static final Map<Role, Faction> FACTIONS = new HashMap<>();
    private static final List<Identifier> NEW_ROLE_IDS = new ArrayList<>();

    /** 新键注册（阵营默认元数据） */
    private static Role register(String path, int color, Faction f) {
        Role r = WatheRoles.registerRole(new Role(Identifier.of(Noellesroles.MOD_ID, path), color,
                f.innocent, f.canUseKiller, f.mood, f.maxSprintTime, f.canSeeTime));
        FACTIONS.put(r, f);
        NEW_ROLE_IDS.add(r.identifier());
        return r;
    }

    /** 特例注册（覆盖阵营默认旗标；用于策划明确例外的身份，如异端分子属乘客阵营、酒鬼/疯子为乘客） */
    private static Role register(String path, int color, Faction f, boolean innocent, boolean canUseKiller,
                                 Role.MoodType mood, int maxSprintTime, boolean canSeeTime) {
        Role r = WatheRoles.registerRole(new Role(Identifier.of(Noellesroles.MOD_ID, path), color,
                innocent, canUseKiller, mood, maxSprintTime, canSeeTime));
        FACTIONS.put(r, f);
        NEW_ROLE_IDS.add(r.identifier());
        return r;
    }

    /** 接管 NR 既有键（不重复创建；仅登记阵营分类） */
    private static Role takeover(Role existing, Faction f) {
        FACTIONS.put(existing, f);
        return existing;
    }

    // ===== Demo 六身份（Phase 1 可席位，实现见各 DEMO 任务） =====

    public static final Role GODFATHER = register("godfather", 0x8B008B, Faction.PRINCIPAL);
    public static final Role VIGILANTE = register("vigilante", 0x0000FF, Faction.ENFORCER);
    /** 医生：接管 NR coroner 键（验尸 HUD 原生；显示名 lang→医生） */
    public static final Role DOCTOR = takeover(Noellesroles.CORONER, Faction.CIVILIAN);
    /** 处子：接管 NR noisemaker 键（死亡发光原生；显示名 lang→处子） */
    public static final Role VIRGIN = takeover(Noellesroles.NOISEMAKER, Faction.CIVILIAN);
    /** 小丑：接管 NR jester 键（NR 旧行为已在 BTT 门控） */
    public static final Role JESTER = takeover(Noellesroles.JESTER, Faction.LONE);
    /** 列车长：复用 NR CONDUCTOR 键（万能钥匙链路原样） */
    public static final Role CONDUCTOR = takeover(Noellesroles.CONDUCTOR, Faction.CIVILIAN);
    /** 酒保：接管 NR bartender 键（doc 酒保=灌酒，与 NR 防御药水机制不同——行为差异随实现轮门控） */
    public static final Role BARTENDER = takeover(Noellesroles.BARTENDER, Faction.CIVILIAN);

    /** Demo 座位次序（Phase 1 固定 6 席历史；席位解锁后仅作存档参考，不再用于分配） */
    public static final List<Role> DEMO_SEATS = List.of(GODFATHER, VIGILANTE, JESTER, DOCTOR, VIRGIN, CONDUCTOR);

    // ===== 执法乘客（槽位 = N//6） =====

    public static final Role HUNTER = register("hunter", 0xCCCCFF, Faction.ENFORCER);
    public static final Role RAILWAY_POLICE = register("railway_police", 0x0000FF, Faction.ENFORCER,
            true, false, Role.MoodType.REAL, -1, false);
    /** 律师（docx 2026-09-09，取代守夜人）：[枪] <起诉> 场上所有凶手（不含叛徒/黑死病），正确则全部死亡；CD 60s */
    public static final Role LAWYER = register("lawyer", 0xCCCCFF, Faction.ENFORCER);
    /** 骑士（2026-09-10 B1：原 `ranger` 键改名）：[枪] 造成误杀时自裁 */
    public static final Role CABALLERO = register("caballero", 0x8000FF, Faction.ENFORCER);
    /** 游侠（2026-09-10 B1：原 `golem` 键改名）：[枪] 不造成误杀时自裁 */
    public static final Role RANGER = register("ranger", 0x8000FF, Faction.ENFORCER);
    /** 巫觋：接管 NR voodoo 键（死亡带走=doc 诅咒核心；显示名 lang→巫觋） */
    public static final Role WITCH = takeover(Noellesroles.VOODOO, Faction.ENFORCER);
    public static final Role VETERAN = register("veteran", 0x800080, Faction.ENFORCER);

    // ===== 平民乘客·信息类 =====

    public static final Role DETECTIVE = register("detective", 0xFFFF00, Faction.CIVILIAN);
    public static final Role PROPHET = register("prophet", 0xFFFF00, Faction.CIVILIAN);
    public static final Role MORTICIAN = register("mortician", 0xFF6600, Faction.CIVILIAN);
    public static final Role JOURNALIST = register("journalist", 0xF5B7B1, Faction.CIVILIAN);
    public static final Role ENGINEER = register("engineer", 0xFFE5B4, Faction.CIVILIAN);
    public static final Role FOLKLORIST = register("folklorist", 0xCD7F32, Faction.CIVILIAN);
    public static final Role MEYUUBYOU = register("meyuubyou", 0xCD7F32, Faction.CIVILIAN);
    /** 贵族（docx 2026-09-12 新增）：四个「族人」，注视可辨，其中有且只有一个是**从犯凶手**；与飞行家同色 */
    public static final Role NOBLE = register("noble", 0xC8A2C8, Faction.CIVILIAN);
    /** 飞行家（docx 2026-09-12 新增）：四个「阵营代表」，注视可辨，阵营依次为 乘客/独行/凶手/外人 */
    public static final Role BALLOONIST = register("balloonist", 0xC8A2C8, Faction.CIVILIAN);

    // ===== 平民乘客·生死类 =====

    public static final Role PROFESSOR = register("professor", 0xCCFF00, Faction.CIVILIAN);
    /** 保镖（docx 2026-09-12 新增）：<守护> 身边者 30 秒内"只有你死亡他才会死亡"；与教授同色 */
    public static final Role BODYGUARD = register("bodyguard", 0xCCFF00, Faction.CIVILIAN);
    public static final Role PHARMACIST = register("pharmacist", 0xCCFF00, Faction.CIVILIAN);
    public static final Role STAR = register("star", 0xFFC0CB, Faction.CIVILIAN);
    /** 罂粟农（docx 2026-09-09 新增）：凶手本能透视全员绿（醉/死后恢复） */
    public static final Role POPPY_GROWER = register("poppy_grower", 0x228B22, Faction.CIVILIAN);

    // ===== 平民乘客·辅助类 =====

    /** 乘务员：docx 2026-09-12 = 可见到站倒计时 + 全车 <广播>（广播【待办】；可见倒计时=canSeeTime） */
    public static final Role ATTENDANT = register("attendant", 0x66FF00, Faction.CIVILIAN,
            true, false, Role.MoodType.REAL, 200, true);
    public static final Role ARCHITECT = register("architect", 0x50C878, Faction.CIVILIAN);
    /** 锁匠（docx 2026-09-12 新增）：<上锁> 一扇门 → 坚不可摧直到本人开锁；与建筑师同色 */
    public static final Role LOCKSMITH = register("locksmith", 0x50C878, Faction.CIVILIAN);
    /** 绳艺师（docx 2026-09-12：**由辅助类移入主犯类**，色值对齐同色搭档魔术师） */
    public static final Role RIGGER = register("rigger", 0x8B008B, Faction.PRINCIPAL);
    /** 小女孩：不可被凶手本能透视（docx 2026-09-12 删去"可见到站倒计时"→ 撤销 C-094 的 canSeeTime） */
    public static final Role SHOUJO = register("shoujo", 0x00FF80, Faction.CIVILIAN);
    public static final Role MAID = register("maid", 0x9ACD32, Faction.CIVILIAN);
    public static final Role POSTMAN = register("postman", 0x9ACD32, Faction.CIVILIAN);
    public static final Role CANNIBAL = register("cannibal", 0x6B8E23, Faction.CIVILIAN);
    /** 卧底：接管 NR mimic 键（KillerSidedTextsMixin 原生=凶手视角显示凶手阵营；显示名 lang→卧底） */
    public static final Role UNDERCOVER = takeover(Noellesroles.MIMIC, Faction.CIVILIAN);
    public static final Role EX_UNDERCOVER = register("ex_undercover", 0x40E0D0, Faction.CIVILIAN);

    // ===== 主犯凶手（全局 1 席） =====

    /** 刺客：接管 guesser（NR 识破 UI/packet 原生；assassin 键删，GUESSER Role 由 C-043 恢复） */
    public static final Role ASSASSIN = takeover(Noellesroles.GUESSER_ROLE, Faction.PRINCIPAL);
    /** 小恶魔（用户 2026-09-11 新增，取代冒牌货；与刺客同色 = 同阵营同色对）：[刀] + <印记>，被印记的独行/外人中立在其死亡后成为小恶魔 */
    public static final Role IMP = register("imp", ASSASSIN.color(), Faction.PRINCIPAL);
    /** 寄生者（docx 2026-09-12 新增）：仅限一次 <寄生> 身边者——只有他死亡你才会死亡；与小恶魔同色 */
    public static final Role LEECH = register("leech", ASSASSIN.color(), Faction.PRINCIPAL);
    /** 演员：接管 NR morphling 键（MorphlingRendererMixin 易容原生；显示名 lang→演员） */
    public static final Role ACTOR = takeover(Noellesroles.MORPHLING, Faction.PRINCIPAL);
    /** 偷渡客：接管 NR phantom 键（隐身能力原生=NR 幽灵 G 键；stowaway 键删除——用户指令 2026-09-05；假人/指纹延后） */
    public static final Role STOWAWAY = takeover(Noellesroles.PHANTOM, Faction.PRINCIPAL);
    /** 魔术师：接管 NR swapper（换位 UI/能力原生；2026-09-06 用户指令，magician 键删） */
    public static final Role MAGICIAN = takeover(Noellesroles.SWAPPER, Faction.PRINCIPAL);
    /**
     * 魅魔 Succubus（用户 2026-09-12：原「走私犯 `smuggler`」改键名与显示名即为魅魔）：
     * 从犯凶手席位、与[笑匠]同色；[刀] + G 键 &lt;魅惑&gt; 身边者 → 醉酒 1 分钟，冷却 1 分钟（docx 2026-09-12）。
     */
    public static final Role SUCCUBUS = register("succubus", 0xE34234, Faction.ACCOMPLICE);
    /** 暴乱（docx 2026-09-09 新增）：存活时乘客误杀自裁/非误杀全凶 +100 */
    public static final Role RIOT = register("riot", 0xB8860B, Faction.PRINCIPAL);
    /** 涡流（docx 2026-09-09 新增）：存活时所有乘客持续醉酒 */
    public static final Role VORTOX = register("vortox", 0xB8860B, Faction.PRINCIPAL);

    // ===== 从犯凶手（槽位 = N//6 − 1） =====

    /** 剑客（docx 2026-09-07 由"杀手"改名）：[剑] 飞剑为 GAP，暂以刀代 kit */
    public static final Role SWORDSMAN = register("swordsman", 0xC41E3A, Faction.ACCOMPLICE);
    public static final Role PSYCHOPATH = register("psychopath", 0xC41E3A, Faction.ACCOMPLICE);
    public static final Role ALCHEMIST = register("alchemist", 0x8B008B, Faction.ACCOMPLICE);
    public static final Role TERRORIST = register("terrorist", 0x8B008B, Faction.ACCOMPLICE);
    public static final Role CLEANER = register("cleaner", 0x8B4513, Faction.ACCOMPLICE);
    public static final Role BANDIT = register("bandit", 0x8B4513, Faction.ACCOMPLICE);
    public static final Role COMEDIAN = register("comedian", 0xE34234, Faction.ACCOMPLICE);
    /** 叛徒（docx 2026-09-09：狂人中立席位+凶手阵营，本能绿色，刀+商店） */
    public static final Role TRAITOR = register("traitor", 0x2F4F4F, Faction.MAD,
            false, true, Role.MoodType.FAKE, -1, true);
    /** 前任叛徒（docx 2026-09-09：狂人中立席位+凶手阵营，继承不在场平民） */
    public static final Role EX_TRAITOR = register("ex_traitor", 0x2F4F4F, Faction.MAD,
            false, true, Role.MoodType.FAKE, -1, true);

    // ===== 中立 · 独行（C-037 三分类；合计中立槽位 = N//6） =====

    public static final Role NOVELIST = register("novelist", 0x00FFFF, Faction.LONE);
    /** 窃贼：接管 NR vulture（透视尸体/G 键吃尸原生；胜利=吃尸体过半而非变杀手，C-039） */
    public static final Role THIEF = takeover(Noellesroles.VULTURE, Faction.LONE);
    public static final Role ARSONIST = register("arsonist", 0xFF4500, Faction.LONE);
    public static final Role AMNESIAC = register("amnesiac", 0xADD8E6, Faction.MAD);
    /**
     * 异教领袖（用户 2026-09-12 二次裁定：删莽夫 `goon`、复活 `cult_leader`）：乘客阵营·狂人席。
     * [G 键]&lt;救赎&gt; 身边者 → **本人加入被救赎者的阵营**（{@link BttPlayerComponent#campOverride}）；
     * 被枪处决免伤且计数；窗口内被处决两次 → 对立阵营全员死于「审判」（见 {@link BttCultLeader}）。
     */
    public static final Role CULT_LEADER = register("cult_leader", 0xADD8E6, Faction.MAD);
    public static final Role SNAKE_CHARMER = register("snake_charmer", 0x228B22, Faction.CIVILIAN);
    /** 酒鬼：狂人中立（乘客旗标、永久醉酒设计） */
    public static final Role DRUNK = register("drunk", 0xDB7093, Faction.MAD);
    /** 疯子：狂人中立（同上） */
    public static final Role LUNATIC = register("lunatic", 0xDB7093, Faction.MAD);

    // ===== 中立 · 外人（自成阵营；无限体力、可见倒计时、全员尾声） =====

    public static final Role MAJO = register("majo", 0xFF00FF, Faction.OUTSIDER);
    public static final Role MESSIAH = register("messiah", 0xFF00FF, Faction.OUTSIDER);
    public static final Role KIDNAPPER = register("kidnapper", 0x7FFFD4, Faction.OUTSIDER);
    public static final Role GARDENER = register("gardener", 0x7FFFD4, Faction.OUTSIDER);
    /** 黑死病：狂人中立席位但**阵营归属凶手**（docx 2026-09-07），不作乘客侧计数；个人胜负=特殊结局【待作者：映射细节】 */
    public static final Role BLACKDEATH = register("blackdeath", 0x800000, Faction.MAD,
            false, false, Role.MoodType.REAL, 200, false);
    /** 异端分子：狂人中立——乘客旗标；胜负对调为实现期逻辑（特殊结局） */
    public static final Role HERETIC = register("heretic", 0x800000, Faction.MAD);

    // ===== 查询 =====

    /** 已实装身份（有真实行为；席位抽取优先层）。注意：须置于全部角色声明之后（静态初始化顺序）。 */
    private static final java.util.Set<Role> IMPLEMENTED = java.util.Set.of(
            GODFATHER, VIGILANTE, DOCTOR, VIRGIN, JESTER, CONDUCTOR,
            ACTOR, UNDERCOVER, WITCH, RAILWAY_POLICE, VETERAN, HUNTER,
            BANDIT, PSYCHOPATH, CLEANER, STAR, DETECTIVE,
            PHARMACIST, RIGGER, AMNESIAC, THIEF, MAID, SWORDSMAN, STOWAWAY,
            PROPHET, ASSASSIN, MAGICIAN, NOVELIST, SNAKE_CHARMER, MAJO, MESSIAH,
            BARTENDER, SUCCUBUS, POPPY_GROWER, RIOT, VORTOX,
            LAWYER, JOURNALIST, ENGINEER, CABALLERO, RANGER, TRAITOR, EX_TRAITOR,
            IMP, TERRORIST, COMEDIAN, GARDENER, EX_UNDERCOVER, BLACKDEATH,
            SHOUJO, HERETIC, ARCHITECT, ALCHEMIST, MEYUUBYOU, ARSONIST, PROFESSOR, KIDNAPPER,
            CANNIBAL, BODYGUARD, LEECH, LOCKSMITH, NOBLE, BALLOONIST, CULT_LEADER);
    /** 独行中立（2026-09-06 策划修订）：被杀加钱、活着不影响凶手胜利 */
    public static final java.util.Set<Role> LONE_NEUTRALS = java.util.Set.of(NOVELIST, JESTER, THIEF, ARSONIST);

    /** BTT 席位池排除表：空（酒保随 C-060 醉酒实装解除搁置；NR 泄漏点已门控） */
    /** 排除表（不参与席位分配）；当前为空（冒牌货已按用户 2026-09-11 裁定**整体删除**，由小恶魔取代） */
    private static final java.util.Set<Role> EXCLUDED = java.util.Set.of();

    /**
     * 同色身份对（docx 2026-09-07 勘误：同色 = **各阵营内相邻的奇偶编号对**，非 RGB 相等——
     * 如 arsonist 0xFF4500 与 folklorist/meyuubyou 色近但分属不同对，不构成互斥）。
     * 键=身份，值=其同色搭档；互斥语义：一对中至多一人入场（assignSeats 消费）。
     */
    private static final Map<Role, Role> SAME_COLOR_PARTNER = new HashMap<>();
    static {
        // docx 2026-09-12 编号（35 对）：随目录删/增重排；尚未注册的 4 对见下方注释
        Role[][] pairs = {
                // 执法 1-8
                {VIGILANTE, HUNTER}, {RAILWAY_POLICE, LAWYER}, {CABALLERO, RANGER}, {WITCH, VETERAN},
                // 信息 1-10
                {DETECTIVE, PROPHET}, {DOCTOR, MORTICIAN}, {JOURNALIST, ENGINEER}, {FOLKLORIST, MEYUUBYOU},
                {NOBLE, BALLOONIST},
                // 生死 1-8
                {PROFESSOR, BODYGUARD}, {VIRGIN, STAR}, {SNAKE_CHARMER, CANNIBAL}, {UNDERCOVER, EX_UNDERCOVER},
                // 辅助 1-10
                {CONDUCTOR, ATTENDANT}, {ARCHITECT, LOCKSMITH}, {PHARMACIST, BARTENDER}, {SHOUJO, POPPY_GROWER}, {MAID, POSTMAN},
                // 主犯 1-10
                {ASSASSIN, GODFATHER}, {ACTOR, STOWAWAY}, {MAGICIAN, RIGGER}, {IMP, LEECH}, {RIOT, VORTOX},
                // 从犯 1-8
                {SWORDSMAN, PSYCHOPATH}, {ALCHEMIST, TERRORIST}, {CLEANER, BANDIT}, {COMEDIAN, SUCCUBUS},
                // 独行 1-4
                {NOVELIST, JESTER}, {THIEF, ARSONIST},
                // 外人 1-4
                {MAJO, MESSIAH}, {KIDNAPPER, GARDENER},
                // 狂人 1-8
                {BLACKDEATH, HERETIC}, {AMNESIAC, CULT_LEADER}, {DRUNK, LUNATIC}, {TRAITOR, EX_TRAITOR},
        };
        for (Role[] pair : pairs) {
            SAME_COLOR_PARTNER.put(pair[0], pair[1]);
            SAME_COLOR_PARTNER.put(pair[1], pair[0]);
        }
    }

    /** 同色搭档（无则 null）；互斥语义见 {@link #SAME_COLOR_PARTNER} */
    public static Role sameColorPartner(Role role) {
        return SAME_COLOR_PARTNER.get(role);
    }

    public static boolean isImplemented(Role role) {
        return IMPLEMENTED.contains(role);
    }

    /** 按阵营分组席位池（排除表生效；wathe 原生角色不在 FACTIONS 中自然排除） */
    public static java.util.Map<Faction, List<Role>> factionPools() {
        java.util.Map<Faction, List<Role>> pools = new java.util.EnumMap<>(Faction.class);
        for (var e : FACTIONS.entrySet()) {
            if (EXCLUDED.contains(e.getKey())) continue;
            pools.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }
        return pools;
    }

    /** 身份阵营分类（未登记的原版/NR 角色返回 null） */
    public static Faction factionOf(Role role) {
        return FACTIONS.get(role);
    }

    /**
     * 阵营=凶手（与席位可不同，2026-09-10 B2 裁定）：主犯/从犯 + 叛徒系 + 黑死病
     * （叛徒系/黑死病占 MAD 席位但归属凶手；叛徒仅在凶手本能透视中显示为乘客）。
     */
    public static boolean isKillerCamp(Role role) {
        Faction f = factionOf(role);
        return f == Faction.PRINCIPAL || f == Faction.ACCOMPLICE
                || role == TRAITOR || role == EX_TRAITOR || role == BLACKDEATH;
    }

    // ===== 阵营覆盖（异教领袖 <救赎>，用户 2026-09-12）=====

    /** 阵营覆盖取值："" = 无覆盖（按身份）/ PASSENGER / KILLER / LONE / OUTSIDER */
    public static final String CAMP_PASSENGER = "PASSENGER";
    public static final String CAMP_KILLER = "KILLER";
    public static final String CAMP_LONE = "LONE";
    public static final String CAMP_OUTSIDER = "OUTSIDER";

    /** 身份本身的阵营名（用于 <救赎> 记录被救赎者的阵营） */
    public static String campNameOf(Role role) {
        if (role == null) return "";
        if (isKillerCamp(role)) return CAMP_KILLER;
        if (isPassengerCamp(role)) return CAMP_PASSENGER;
        Faction f = factionOf(role);
        if (f == Faction.LONE) return CAMP_LONE;
        if (f == Faction.OUTSIDER) return CAMP_OUTSIDER;
        return "";
    }

    /** 阵营覆盖（空 = 未覆盖）。混血规则：**阵营变换影响胜负计数与死后影响**，不影响理智/体力/倒计时。 */
    public static String campOverride(net.minecraft.entity.player.PlayerEntity player) {
        return BttPlayerComponent.KEY.get(player).campOverride;
    }

    /** 玩家当前是否**乘客侧**（覆盖优先）。用于胜负计数/死后影响等"随阵营"的判定。 */
    public static boolean isPassengerCampFor(dev.doctor4t.wathe.cca.GameWorldComponent gwc,
                                             net.minecraft.entity.player.PlayerEntity player) {
        String override = campOverride(player);
        if (CAMP_PASSENGER.equals(override)) return true;
        if (!override.isEmpty()) return false;
        return isPassengerCamp(gwc.getRole(player));
    }

    /** 玩家当前是否**凶手侧**（覆盖优先） */
    public static boolean isKillerCampFor(dev.doctor4t.wathe.cca.GameWorldComponent gwc,
                                          net.minecraft.entity.player.PlayerEntity player) {
        String override = campOverride(player);
        if (CAMP_KILLER.equals(override)) return true;
        if (!override.isEmpty()) return false;
        return isKillerCamp(gwc.getRole(player));
    }

    /**
     * **凶手席位**（= 主犯/从犯；C-103 律师 <起诉> 口径：「场上的所有凶手，**不包括**加入凶手阵营的其他身份」——
     * 叛徒/前任叛徒/黑死病占 MAD 席位、虽属凶手阵营但**不算**凶手席位）。
     */
    public static boolean isKillerSeat(Role role) {
        Faction f = factionOf(role);
        return f == Faction.PRINCIPAL || f == Faction.ACCOMPLICE;
    }

    // ===== 第二身份（C-110）=====

    /** 按 identifier 字符串还原 Role（在本局注册表 `WatheRoles.ROLES` 内扫描；BTT 新键亦注册其中） */
    public static Role byId(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Role r : WatheRoles.ROLES) {
            if (r.identifier().toString().equals(id)) return r;
        }
        return null;
    }

    /** 当前**实际扮演**的身份：借来的技能身份优先（食人族/前任系），否则自身身份 */
    public static Role effectiveRole(dev.doctor4t.wathe.cca.GameWorldComponent gwc, net.minecraft.entity.player.PlayerEntity p) {
        Role borrowed = byId(BttPlayerComponent.KEY.get(p).borrowedRole);
        return borrowed != null ? borrowed : gwc.getRole(p);
    }

    /**
     * 主动技能判定（C-110）：自身身份**或**借来的技能身份。仅用于**技能键**分派（<技能> = 主动，docx 口径），
     * 不用于被动天赋/免死等（那些仍走 {@code gwc.isRole}）。
     */
    public static boolean isPlayingAs(dev.doctor4t.wathe.cca.GameWorldComponent gwc, net.minecraft.entity.player.PlayerEntity p, Role role) {
        if (gwc.isRole(p, role)) return true;
        Role borrowed = byId(BttPlayerComponent.KEY.get(p).borrowedRole);
        return borrowed != null && borrowed == role;
    }

    /** 阵营=乘客侧（执法/平民/狂人；叛徒系/黑死病/凶手除外） */
    public static boolean isPassengerCamp(Role role) {
        if (role == null) return false;
        Faction f = factionOf(role);
        if (f == Faction.PRINCIPAL || f == Faction.ACCOMPLICE) return false;
        if (role == TRAITOR || role == EX_TRAITOR || role == BLACKDEATH) return false;
        return f == Faction.ENFORCER || f == Faction.CIVILIAN || f == Faction.MAD;
    }

    /** 全部已注册身份（btt:forceRole 子命令枚举用） */
    public static java.util.List<Role> allRoles() {
        return List.copyOf(FACTIONS.keySet());
    }

    /** 按 path 查身份（含接管键；btt:forceRole 用） */
    public static Role byPath(String path) {
        for (Role r : FACTIONS.keySet()) {
            if (r.identifier().getPath().equalsIgnoreCase(path)) return r;
        }
        return null;
    }

    /** 本轮全部**新增键**（HML 池隔离用；接管键不在其中） */
    public static List<Identifier> newRoleIds() {
        return List.copyOf(NEW_ROLE_IDS);
    }

    public static void register() {
        // 静态字段初始化即完成注册；此方法仅固化“在 onInitialize 被调用”的时序。
    }
}
