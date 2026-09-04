package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;

import java.awt.*;
import java.util.List;

/**
 * BTT（终点站抵达之前）身份目录。Demo 六身份（渐进式替换：新身份直接占用最终键名）：
 * 主犯=教父 godfather(新) / 执法=义警 vigilante(新,发左轮) / 中立=小丑(接管 NR jester 键)
 * 平民=医生 doctor(新,复用 NR coroner 尸体读取 HUD)/处子 virgin(新,死亡发光)/列车长(复用 NR CONDUCTOR 万能钥匙)
 * 注册须早于 HML SERVER_STARTED 的 refreshRoles（onInitialize 中调用）。
 *
 * 小丑（渐进式替换说明）：doc-小丑接管 noellesroles:jester（与 NR 旧小丑同键同对象）；
 * NR 旧小丑行为（假刀假枪/禁拾取/45s疯魔/杀手侧HUD/击杀疯魔免疫）已在 BTT 局内门控，
 * 由 BttEvents 提供 doc 口径（2 分钟疯魔+2 层护盾+球棒）；NR 谋杀局行为不变。
 */
public final class BttRoles {
    private BttRoles() {}

    public static final Identifier GODFATHER_ID = Identifier.of(Noellesroles.MOD_ID, "godfather");
    public static final Identifier VIGILANTE_ID = Identifier.of(Noellesroles.MOD_ID, "vigilante");
    public static final Identifier DOCTOR_ID = Identifier.of(Noellesroles.MOD_ID, "doctor");
    public static final Identifier VIRGIN_ID = Identifier.of(Noellesroles.MOD_ID, "virgin");

    /** 主犯凶手：刀 + 查验；无限体力；可见倒计时 */
    public static final Role GODFATHER = WatheRoles.registerRole(new Role(GODFATHER_ID,
            new Color(178, 24, 43).getRGB(), false, true, Role.MoodType.FAKE, -1, true));
    /** 执法乘客：左轮 10s CD；乘客体感 */
    public static final Role VIGILANTE = WatheRoles.registerRole(new Role(VIGILANTE_ID,
            new Color(27, 138, 229).getRGB(), true, false, Role.MoodType.REAL, WatheRoles.CIVILIAN.getMaxSprintTime(), false));
    /** 平民乘客：验尸（复用 NR coroner 尸体读取 HUD，CoronerHudMixin 已扩展识别本身份） */
    public static final Role DOCTOR = WatheRoles.registerRole(new Role(DOCTOR_ID,
            new Color(240, 240, 240).getRGB(), true, false, Role.MoodType.REAL, WatheRoles.CIVILIAN.getMaxSprintTime(), false));
    /** 平民乘客：死亡时全体透视其尸体一分钟 */
    public static final Role VIRGIN = WatheRoles.registerRole(new Role(VIRGIN_ID,
            new Color(255, 183, 197).getRGB(), true, false, Role.MoodType.REAL, WatheRoles.CIVILIAN.getMaxSprintTime(), false));

    /** 中立：doc-小丑接管 NR jester 键（NR 旧行为已在 BTT 门控）；doc 完整幕间胜负 = BT-ROLE-JESTER-001 */
    public static final Role JESTER = Noellesroles.JESTER;

    /** 列车长 = 复用 NR CONDUCTOR（万能钥匙链路 + 掉落 + 名字“列车长”与策划一致） */
    public static final Role CONDUCTOR = Noellesroles.CONDUCTOR;

    /** Demo 座位次序（分配时随机洗人后按下标落座），恰好 6 人 */
    public static final List<Role> DEMO_SEATS = List.of(GODFATHER, VIGILANTE, JESTER, DOCTOR, VIRGIN, CONDUCTOR);

    /** 新增（非接管）的 BTT 身份 id：HML 谋杀局池隔离用（jester/conductor 属接管/复用，不入此列） */
    public static final List<Identifier> NEW_BTT_ROLE_IDS = List.of(GODFATHER_ID, VIGILANTE_ID, DOCTOR_ID, VIRGIN_ID);

    public static void register() {
        // 静态字段初始化即完成注册；此方法仅固化“在 onInitialize 被调用”的时序。
    }
}
