package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 关系系统（C-061，改造 HML 修饰词/Modifier 机制——每玩家 Modifier 旗标 + BttState 搭档存储）：
 * 恋人（任意两人，知晓名字；殉情；异阵营共同存活→并入胜者组）、
 * 宿敌（1 平民+1 从犯，互知身份；平民被击毙→凶手方 +2 护盾）、
 * 双子（2 平民，互不知晓；身份随机统一为其中一人，发 kit 前生效）。
 * 对数 = N//8，每人至多一对；对类型按可行性随机（不可行则降级为恋人）。
 */
public final class BttRelationships {
    private BttRelationships() {}

    public static final Modifier MOD_LOVERS = HMLModifiers.registerModifier(
            new Modifier(Identifier.of(Noellesroles.MOD_ID, "lovers"), 0xFF69B4, null, null, false, false));
    public static final Modifier MOD_ARCHENEMY = HMLModifiers.registerModifier(
            new Modifier(Identifier.of(Noellesroles.MOD_ID, "archenemy"), 0xFF4500, null, null, false, false));
    public static final Modifier MOD_TWINS = HMLModifiers.registerModifier(
            new Modifier(Identifier.of(Noellesroles.MOD_ID, "twins"), 0x00CED1, null, null, false, false));

    /** 开局分配关系并应用双子统一（须在 addRole/发 kit 前调用）；notify=开局知晓提示 */
    public static void assign(ServerWorld world, Map<UUID, Role> seats,
                              java.util.function.BiConsumer<UUID, Text> notify) {
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(world);
        int pairs = seats.size() / 8;
        if (pairs <= 0) return;

        List<UUID> pool = new ArrayList<>(seats.keySet());
        Collections.shuffle(pool);
        List<String> types = new ArrayList<>(List.of("LOVER", "ARCHENEMY", "TWINS"));

        for (int p = 0; p < pairs && pool.size() >= 2; p++) {
            Collections.shuffle(types);
            UUID a = null, b = null;
            String chosen = null;
            for (String type : new String[]{types.get(0), types.get(1), types.get(2), "LOVER"}) {
                switch (type) {
                    case "ARCHENEMY" -> {
                        var civ = pool.stream().filter(u -> BttRoles.factionOf(seats.get(u)) == BttRoles.Faction.CIVILIAN).findFirst();
                        var acc = pool.stream().filter(u -> BttRoles.factionOf(seats.get(u)) == BttRoles.Faction.ACCOMPLICE).findFirst();
                        if (civ.isPresent() && acc.isPresent()) { a = civ.get(); b = acc.get(); chosen = "ARCHENEMY"; }
                    }
                    case "TWINS" -> {
                        var cives = pool.stream().filter(u -> BttRoles.factionOf(seats.get(u)) == BttRoles.Faction.CIVILIAN).toList();
                        if (cives.size() >= 2) { a = cives.get(0); b = cives.get(1); chosen = "TWINS"; }
                    }
                    default -> {
                        a = pool.get(0);
                        b = pool.get(1);
                        chosen = "LOVER";
                    }
                }
                if (chosen != null) break;
            }
            if (a == null || b == null) break;

            pool.remove(a);
            pool.remove(b);
            Modifier mod = switch (chosen) {
                case "ARCHENEMY" -> MOD_ARCHENEMY;
                case "TWINS" -> MOD_TWINS;
                default -> MOD_LOVERS;
            };
            modifiers.addModifier(a, mod);
            modifiers.addModifier(b, mod);
            BttState.setString(a, "partner", b.toString());
            BttState.setString(b, "partner", a.toString());
            BttState.setString(a, "relType", chosen);
            BttState.setString(b, "relType", chosen);

            if (notify != null) {
                ServerPlayerEntity pa = (ServerPlayerEntity) world.getPlayerByUuid(a);
                ServerPlayerEntity pb = (ServerPlayerEntity) world.getPlayerByUuid(b);
                switch (chosen) {
                    case "LOVER" -> {
                        if (pa != null) notify.accept(a, Text.literal("你的恋人是 " + nameOf(pb) + "。").formatted(Formatting.LIGHT_PURPLE));
                        if (pb != null) notify.accept(b, Text.literal("你的恋人是 " + nameOf(pa) + "。").formatted(Formatting.LIGHT_PURPLE));
                    }
                    case "ARCHENEMY" -> {
                        if (pa != null) notify.accept(a, Text.literal("你的宿敌是 " + nameOf(pb)
                                + "（" + BttIdentity.displayName(seats.get(b)).getString() + "）。").formatted(Formatting.RED));
                        if (pb != null) notify.accept(b, Text.literal("你的宿敌是 " + nameOf(pa)
                                + "（" + BttIdentity.displayName(seats.get(a)).getString() + "）。").formatted(Formatting.RED));
                    }
                    // 双子：互不知晓 ✓
                }
            }

            // 双子：身份随机统一为其中一人（发 kit 前 → 物品/技能/宣告一致）
            if (chosen.equals("TWINS")) {
                Role unified = world.getRandom().nextBoolean() ? seats.get(a) : seats.get(b);
                seats.put(a, unified);
                seats.put(b, unified);
            }
        }
    }

    private static String nameOf(ServerPlayerEntity p) {
        return p == null ? "？" : p.getGameProfile().getName();
    }

    // ===== 查询 =====

    public static UUID partnerOf(UUID player) {
        String s = BttState.getString(player, "partner");
        try {
            return s == null || s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isLover(UUID player) {
        return "LOVER".equals(typeOf(player));
    }

    public static String typeOf(UUID player) {
        return BttState.getString(player, "relType");
    }

    /** 宿敌护盾余量（凶手方） */
    public static int archenemyShields(UUID player) {
        return BttState.getInt(player, "archenemyShields");
    }

    public static void grantArchenemyShields(UUID player, int layers) {
        BttState.setInt(player, "archenemyShields", archenemyShields(player) + layers);
    }

    /** 消耗一层护盾；无余量返回 false */
    public static boolean consumeArchenemyShield(UUID player) {
        int v = archenemyShields(player);
        if (v <= 0) return false;
        BttState.setInt(player, "archenemyShields", v - 1);
        return true;
    }
}
