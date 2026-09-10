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

    // ===== 平民乘客·生死类 =====

    public static final Role PROFESSOR = register("professor", 0xCCFF00, Faction.CIVILIAN);
    public static final Role PHARMACIST = register("pharmacist", 0xCCFF00, Faction.CIVILIAN);
    public static final Role STAR = register("star", 0xFFC0CB, Faction.CIVILIAN);
        /** 吟游诗人（2026-09-06 替换水手——策划案修订；<歌唱>=群体醉酒 1min，依赖 BT-SYS-DRUNK） */
    public static final Role MINSTREL = register("minstrel", 0xF400A1, Faction.CIVILIAN);
    /** 罂粟农（docx 2026-09-09 新增）：凶手本能透视全员绿（醉/死后恢复） */
    public static final Role POPPY_GROWER = register("poppy_grower", 0x228B22, Faction.CIVILIAN);

    // ===== 平民乘客·辅助类 =====

    public static final Role ATTENDANT = register("attendant", 0x66FF00, Faction.CIVILIAN);
    public static final Role ARCHITECT = register("architect", 0x50C878, Faction.CIVILIAN);
    public static final Role RIGGER = register("rigger", 0x50C878, Faction.CIVILIAN);
    public static final Role SHOUJO = register("shoujo", 0x00FF80, Faction.CIVILIAN);
    public static final Role DRIVER = register("driver", 0x00FF80, Faction.CIVILIAN,
            true, false, Role.MoodType.REAL, 200, true);
    public static final Role MAID = register("maid", 0x9ACD32, Faction.CIVILIAN);
    public static final Role POSTMAN = register("postman", 0x9ACD32, Faction.CIVILIAN);
    public static final Role CANNIBAL = register("cannibal", 0x6B8E23, Faction.CIVILIAN);
    public static final Role PHILOSOPHER = register("philosopher", 0x6B8E23, Faction.CIVILIAN);
    /** 卧底：接管 NR mimic 键（KillerSidedTextsMixin 原生=凶手视角显示凶手阵营；显示名 lang→卧底） */
    public static final Role UNDERCOVER = takeover(Noellesroles.MIMIC, Faction.CIVILIAN);
    public static final Role EX_UNDERCOVER = register("ex_undercover", 0x40E0D0, Faction.CIVILIAN);

    // ===== 主犯凶手（全局 1 席） =====

    /** 刺客：接管 guesser（NR 识破 UI/packet 原生；assassin 键删，GUESSER Role 由 C-043 恢复） */
    public static final Role ASSASSIN = takeover(Noellesroles.GUESSER_ROLE, Faction.PRINCIPAL);
    public static final Role IMPOSTOR = register("impostor", 0x800000, Faction.PRINCIPAL);
    /** 演员：接管 NR morphling 键（MorphlingRendererMixin 易容原生；显示名 lang→演员） */
    public static final Role ACTOR = takeover(Noellesroles.MORPHLING, Faction.PRINCIPAL);
    /** 偷渡客：接管 NR phantom 键（隐身能力原生=NR 幽灵 G 键；stowaway 键删除——用户指令 2026-09-05；假人/指纹延后） */
    public static final Role STOWAWAY = takeover(Noellesroles.PHANTOM, Faction.PRINCIPAL);
    /** 魔术师：接管 NR swapper（换位 UI/能力原生；2026-09-06 用户指令，magician 键删） */
    public static final Role MAGICIAN = takeover(Noellesroles.SWAPPER, Faction.PRINCIPAL);
    public static final Role SMUGGLER = register("smuggler", 0x556B2F, Faction.PRINCIPAL);
    /** 特工（docx 2026-09-09 新增）：<查看> 本局身份列表 */
    public static final Role AGENT = register("agent", 0x8B008B, Faction.PRINCIPAL);
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
    public static final Role ABUSER = register("abuser", 0xE34234, Faction.ACCOMPLICE);
    public static final Role PARTYHOST = register("partyhost", 0xE34234, Faction.ACCOMPLICE);
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
    public static final Role GOON = register("goon", 0xADD8E6, Faction.MAD);
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
            BANDIT, PSYCHOPATH, CLEANER, STAR, DRIVER, DETECTIVE,
            PHARMACIST, RIGGER, AMNESIAC, THIEF, MAID, SWORDSMAN, STOWAWAY,
            PROPHET, ASSASSIN, MAGICIAN, NOVELIST, SNAKE_CHARMER, MAJO, MESSIAH,
            BARTENDER, MINSTREL, SMUGGLER, POPPY_GROWER, AGENT, RIOT, VORTOX,
            LAWYER, JOURNALIST, ENGINEER, CABALLERO, RANGER, TRAITOR, EX_TRAITOR,
            IMPOSTOR, TERRORIST, PARTYHOST, GARDENER, EX_UNDERCOVER, BLACKDEATH,
            SHOUJO, HERETIC, ARCHITECT);
    /** 独行中立（2026-09-06 策划修订）：被杀加钱、活着不影响凶手胜利 */
    public static final java.util.Set<Role> LONE_NEUTRALS = java.util.Set.of(NOVELIST, JESTER, THIEF, ARSONIST);

    /** BTT 席位池排除表：空（酒保随 C-060 醉酒实装解除搁置；NR 泄漏点已门控） */
    private static final java.util.Set<Role> EXCLUDED = java.util.Set.of();

    /**
     * 同色身份对（docx 2026-09-07 勘误：同色 = **各阵营内相邻的奇偶编号对**，非 RGB 相等——
     * 如 arsonist 0xFF4500 与 folklorist/meyuubyou 色近但分属不同对，不构成互斥）。
     * 键=身份，值=其同色搭档；互斥语义：一对中至多一人入场（assignSeats 消费）。
     */
    private static final Map<Role, Role> SAME_COLOR_PARTNER = new HashMap<>();
    static {
        Role[][] pairs = {
                // 执法 1-8
                {VIGILANTE, HUNTER}, {RAILWAY_POLICE, LAWYER}, {CABALLERO, RANGER}, {WITCH, VETERAN},
                // 信息 1-8
                {DETECTIVE, PROPHET}, {DOCTOR, MORTICIAN}, {JOURNALIST, ENGINEER}, {FOLKLORIST, MEYUUBYOU},
                // 生死 1-8
                {PROFESSOR, PHARMACIST}, {VIRGIN, STAR}, {BARTENDER, MINSTREL}, {SNAKE_CHARMER, POPPY_GROWER},
                // 辅助 1-12
                {CONDUCTOR, ATTENDANT}, {ARCHITECT, RIGGER}, {SHOUJO, DRIVER}, {MAID, POSTMAN},
                {CANNIBAL, PHILOSOPHER}, {UNDERCOVER, EX_UNDERCOVER},
                // 主犯 1-10
                {ASSASSIN, IMPOSTOR}, {ACTOR, STOWAWAY}, {MAGICIAN, SMUGGLER}, {AGENT, GODFATHER}, {RIOT, VORTOX},
                // 从犯 1-8
                {SWORDSMAN, PSYCHOPATH}, {ALCHEMIST, TERRORIST}, {CLEANER, BANDIT}, {ABUSER, PARTYHOST},
                // 独行 1-4
                {NOVELIST, JESTER}, {THIEF, ARSONIST},
                // 外人 1-4
                {MAJO, MESSIAH}, {KIDNAPPER, GARDENER},
                // 狂人 1-8
                {BLACKDEATH, HERETIC}, {AMNESIAC, GOON}, {DRUNK, LUNATIC}, {TRAITOR, EX_TRAITOR},
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
