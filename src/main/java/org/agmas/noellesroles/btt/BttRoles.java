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
 *   conductor=列车长 / bartender=酒保 / voodoo=巫觋），不重复创建；
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
        /** 中立：无理智/体力限制可见性按中立处理（小丑先例=FAKE） */
        NEUTRAL(false, false, Role.MoodType.FAKE, 200, false),
        /** 外人：自成阵营，无理智/体力限制，可见倒计时 */
        OUTSIDER(false, false, Role.MoodType.FAKE, -1, true);

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

    public static final Role GODFATHER = register("godfather", 0xB2182B, Faction.PRINCIPAL);
    public static final Role VIGILANTE = register("vigilante", 0x1B8AE5, Faction.ENFORCER);
    /** 医生：接管 NR coroner 键（验尸 HUD 原生；显示名 lang→医生） */
    public static final Role DOCTOR = takeover(Noellesroles.CORONER, Faction.CIVILIAN);
    /** 处子：接管 NR noisemaker 键（死亡发光原生；显示名 lang→处子） */
    public static final Role VIRGIN = takeover(Noellesroles.NOISEMAKER, Faction.CIVILIAN);
    /** 小丑：接管 NR jester 键（NR 旧行为已在 BTT 门控） */
    public static final Role JESTER = takeover(Noellesroles.JESTER, Faction.NEUTRAL);
    /** 列车长：复用 NR CONDUCTOR 键（万能钥匙链路原样） */
    public static final Role CONDUCTOR = takeover(Noellesroles.CONDUCTOR, Faction.CIVILIAN);
    /** 酒保：接管 NR bartender 键（doc 酒保=灌酒，与 NR 防御药水机制不同——行为差异随实现轮门控） */
    public static final Role BARTENDER = takeover(Noellesroles.BARTENDER, Faction.CIVILIAN);

    /** Demo 座位次序（Phase 1 固定 6 席历史；席位解锁后仅作存档参考，不再用于分配） */
    public static final List<Role> DEMO_SEATS = List.of(GODFATHER, VIGILANTE, JESTER, DOCTOR, VIRGIN, CONDUCTOR);

    // ===== 执法乘客（槽位 = N//6） =====

    public static final Role HUNTER = register("hunter", 0x2E86C1, Faction.ENFORCER);
    public static final Role RAILWAYPOLICE = register("railwaypolice", 0x1F618D, Faction.ENFORCER,
            true, false, Role.MoodType.REAL, -1, false);
    public static final Role NIGHT_WATCHMAN = register("night_watchman", 0x5DADE2, Faction.ENFORCER);
    public static final Role RANGER = register("ranger", 0x48C9B0, Faction.ENFORCER);
    public static final Role GOLEM = register("golem", 0x85929E, Faction.ENFORCER);
    /** 巫觋：接管 NR voodoo 键（死亡带走=doc 诅咒核心；显示名 lang→巫觋） */
    public static final Role WITCH = takeover(Noellesroles.VOODOO, Faction.ENFORCER);
    public static final Role VETERAN = register("veteran", 0x34495E, Faction.ENFORCER);

    // ===== 平民乘客·信息类 =====

    public static final Role DETECTIVE = register("detective", 0xF7DC6F, Faction.CIVILIAN);
    public static final Role PROPHET = register("prophet", 0xABEBC6, Faction.CIVILIAN);
    public static final Role MORTICIAN = register("mortician", 0xD7BDE2, Faction.CIVILIAN);
    public static final Role JOURNALIST = register("journalist", 0xF5B7B1, Faction.CIVILIAN);
    public static final Role ENGINEER = register("engineer", 0xAED6F1, Faction.CIVILIAN);
    public static final Role FOLKLORIST = register("folklorist", 0x76D7C4, Faction.CIVILIAN);
    public static final Role MEYUUBYOU = register("meyuubyou", 0xBB8FCE, Faction.CIVILIAN);

    // ===== 平民乘客·生死类 =====

    public static final Role PROFESSOR = register("professor", 0xF9E79F, Faction.CIVILIAN);
    public static final Role CANDY_SELLER = register("candy_seller", 0xF1948A, Faction.CIVILIAN);
    public static final Role STAR = register("star", 0xFAD7A0, Faction.CIVILIAN);
    public static final Role SAILOR = register("sailor", 0x85C1E9, Faction.CIVILIAN);

    // ===== 平民乘客·辅助类 =====

    public static final Role ATTENDANT = register("attendant", 0xA3E4D7, Faction.CIVILIAN);
    public static final Role ARCHITECT = register("architect", 0xD5DBDB, Faction.CIVILIAN);
    public static final Role RIGGER = register("rigger", 0xF0B27A, Faction.CIVILIAN);
    public static final Role SHOUJO = register("shoujo", 0xFADBD8, Faction.CIVILIAN);
    public static final Role DRIVER = register("driver", 0xA9CCE3, Faction.CIVILIAN,
            true, false, Role.MoodType.REAL, 200, true);
    public static final Role MAID = register("maid", 0xE8DAEF, Faction.CIVILIAN);
    public static final Role POSTMAN = register("postman", 0xFDEBD0, Faction.CIVILIAN);
    public static final Role CANNIBAL = register("cannibal", 0xCD6155, Faction.CIVILIAN);
    public static final Role PHILOSOPHER = register("philosopher", 0xD0ECE7, Faction.CIVILIAN);
    /** 卧底：接管 NR mimic 键（KillerSidedTextsMixin 原生=凶手视角显示凶手阵营；显示名 lang→卧底） */
    public static final Role UNDERCOVER = takeover(Noellesroles.MIMIC, Faction.CIVILIAN);
    public static final Role EX_UNDERCOVER = register("ex_undercover", 0xAAB7B8, Faction.CIVILIAN);

    // ===== 主犯凶手（全局 1 席） =====

    public static final Role ASSASSIN = register("assassin", 0xC0392B, Faction.PRINCIPAL);
    public static final Role IMPOSTOR = register("impostor", 0x922B21, Faction.PRINCIPAL);
    /** 演员：接管 NR morphling 键（MorphlingRendererMixin 易容原生；显示名 lang→演员） */
    public static final Role ACTOR = takeover(Noellesroles.MORPHLING, Faction.PRINCIPAL);
    /** 偷渡客：接管 NR phantom 键（隐身能力原生=NR 幽灵 G 键；stowaway 键删除——用户指令 2026-09-05；假人/指纹延后） */
    public static final Role STOWAWAY = takeover(Noellesroles.PHANTOM, Faction.PRINCIPAL);
    public static final Role MAGICIAN = register("magician", 0xD35400, Faction.PRINCIPAL);
    public static final Role SMUGGLER = register("smuggler", 0xA93226, Faction.PRINCIPAL);
    public static final Role DRUG_MAKER = register("drug_maker", 0x7B241C, Faction.PRINCIPAL);

    // ===== 从犯凶手（槽位 = N//6 − 1） =====

    public static final Role KILLER = register("killer", 0xCA6F1E, Faction.ACCOMPLICE);
    public static final Role PSYCHOPATH = register("psychopath", 0xBA4A00, Faction.ACCOMPLICE);
    public static final Role ALCHEMIST = register("alchemist", 0xD68910, Faction.ACCOMPLICE);
    public static final Role TERRORIST = register("terrorist", 0xE67E22, Faction.ACCOMPLICE);
    public static final Role CLEANER = register("cleaner", 0xB9770E, Faction.ACCOMPLICE);
    public static final Role BANDIT = register("bandit", 0xAF601C, Faction.ACCOMPLICE);
    public static final Role ABUSER = register("abuser", 0x9C640C, Faction.ACCOMPLICE);
    public static final Role PARTYHOST = register("partyhost", 0xD4AC0D, Faction.ACCOMPLICE);
    public static final Role DEMON = register("demon", 0x873600, Faction.ACCOMPLICE);
    public static final Role SERIALKILLER = register("serialkiller", 0x6E2C00, Faction.ACCOMPLICE);
    public static final Role TRAITOR = register("traitor", 0x935116, Faction.ACCOMPLICE);
    public static final Role EX_TRAITOR = register("ex_traitor", 0x8E7060, Faction.ACCOMPLICE);

    // ===== 中立（槽位 = 1 + N//12） =====

    public static final Role NOVELIST = register("novelist", 0x7D3C98, Faction.NEUTRAL);
    public static final Role THIEF = register("thief", 0x884EA0, Faction.NEUTRAL);
    public static final Role PYROMANIAC = register("pyromaniac", 0xA569BD, Faction.NEUTRAL);
    public static final Role AMNESIAC = register("amnesiac", 0xD2B4DE, Faction.NEUTRAL);
    public static final Role GOON = register("goon", 0x5B2C6F, Faction.NEUTRAL);
    public static final Role SNAKE_CHARMER = register("snake_charmer", 0x6C3483, Faction.NEUTRAL);
    public static final Role CULT_LEADER = register("cult_leader", 0x4A235A, Faction.NEUTRAL);
    /** 酒鬼：占据中立坑位的**乘客**（doc 原文）——旗标按乘客（innocent），分类归中立 */
    public static final Role DRUNK = register("drunk", 0xBFC9CA, Faction.NEUTRAL, true, false, Role.MoodType.REAL, 200, false);
    /** 疯子：占据中立坑位的**乘客**（doc 原文）——同上 */
    public static final Role LUNATIC = register("lunatic", 0x99A3A4, Faction.NEUTRAL, true, false, Role.MoodType.REAL, 200, false);

    // ===== 外人（槽位 = N//12；全员有尾声） =====

    public static final Role MAJO = register("majo", 0x1C2833, Faction.OUTSIDER);
    public static final Role MESSIAH = register("messiah", 0x212F3D, Faction.OUTSIDER);
    public static final Role KIDNAPPER = register("kidnapper", 0x2E4053, Faction.OUTSIDER);
    public static final Role GARDENER = register("gardener", 0x283747, Faction.OUTSIDER);
    public static final Role BLACKDEATH = register("blackdeath", 0x17202A, Faction.OUTSIDER);
    /** 异端分子：doc“属于乘客阵营”——旗标按乘客；胜负对调为实现期逻辑（分类仍归外人） */
    public static final Role HERETIC = register("heretic", 0x2F4F4F, Faction.OUTSIDER, true, false, Role.MoodType.REAL, 200, false);

    // ===== 查询 =====

    /** 已实装身份（有真实行为；席位抽取优先层）。注意：须置于全部角色声明之后（静态初始化顺序）。 */
    private static final java.util.Set<Role> IMPLEMENTED = java.util.Set.of(
            GODFATHER, VIGILANTE, DOCTOR, VIRGIN, JESTER, CONDUCTOR,
            ACTOR, UNDERCOVER, WITCH, RAILWAYPOLICE, VETERAN, HUNTER,
            BANDIT, PSYCHOPATH, CLEANER, STAR, DRIVER, DETECTIVE,
            CANDY_SELLER, RIGGER, DEMON, SERIALKILLER, AMNESIAC, THIEF, NIGHT_WATCHMAN, STOWAWAY,
            PROPHET, ASSASSIN, MAGICIAN, NOVELIST, SNAKE_CHARMER);
    /** BTT 席位池排除表（BARTENDER 搁置：NR 原生行为会泄漏进 BTT 局，用户裁定 2026-09-05） */
    private static final java.util.Set<Role> EXCLUDED = java.util.Set.of(BARTENDER);

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
