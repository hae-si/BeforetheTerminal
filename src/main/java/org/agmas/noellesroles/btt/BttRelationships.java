package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
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
 * 关系系统（C-061，改造 HML 修饰词/Modifier 机制）：
 * 恋人（任意两人，知晓名字；殉情；异阵营共同存活→并入胜者组）、
 * 宿敌（1 平民+1 从犯，互知身份；平民被处决→凶手方单独胜利）、
 * 双子（2 平民，互不知晓；身份统一为其中一人，发 kit 前生效）。
 * 对数 = N//8，每人至多一对；对类型按可行性随机。
 * <p>
 * 两阶段（2026-09-10 修订）：{@link #assign}（身份阶段，决定对子并**统一双子身份**）→
 * addRole/发 kit → {@link #apply}（身份发放后再落 modifier/搭档/提示）。
 */
public final class BttRelationships {
    private BttRelationships() {}

    /**
     * 关系色（docx「关系」章，C-098「先前未明确的关系颜色见doc」）：
     * 恋人 {@code #FFC0CB}、宿敌 {@code #800080}、双子 {@code #66FF00}。此前为自拟值（FF69B4/FF4500/00CED1），一律回改。
     */
    public static final int COLOR_LOVERS = 0xFFC0CB;
    public static final int COLOR_ARCHENEMY = 0x800080;
    public static final int COLOR_TWINS = 0x66FF00;

    public static final Modifier MOD_LOVERS = HMLModifiers.registerModifier(
            new Modifier(Identifier.of(Noellesroles.MOD_ID, "lovers"), COLOR_LOVERS, null, null, false, false));
    public static final Modifier MOD_ARCHENEMY = HMLModifiers.registerModifier(
            new Modifier(Identifier.of(Noellesroles.MOD_ID, "archenemy"), COLOR_ARCHENEMY, null, null, false, false));
    public static final Modifier MOD_TWINS = HMLModifiers.registerModifier(
            new Modifier(Identifier.of(Noellesroles.MOD_ID, "twins"), COLOR_TWINS, null, null, false, false));

    /** 关系对（a,b=玩家 UUID；type=LOVER/ARCHENEMY/TWINS） */
    public record Pair(UUID a, UUID b, String type) {}

    /**
     * 身份阶段：决定 N//8 对关系，并在 {@code seats} 上**统一双子身份**（须在 addRole/发 kit 前）。
     * 返回关系对，供 {@link #apply} 在身份发放后落关系。
     */
    public static List<Pair> assign(ServerWorld world, Map<UUID, Role> seats) {
        int pairs = seats.size() / 8;
        List<Pair> result = new ArrayList<>();
        if (pairs <= 0) return result;

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
                        if (civ.isPresent() && acc.isPresent()) {
                            a = civ.get();
                            b = acc.get();
                            chosen = "ARCHENEMY";
                        }
                    }
                    case "TWINS" -> {
                        var cives = pool.stream().filter(u -> BttRoles.factionOf(seats.get(u)) == BttRoles.Faction.CIVILIAN).toList();
                        if (cives.size() >= 2) {
                            a = cives.get(0);
                            b = cives.get(1);
                            chosen = "TWINS";
                        }
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

            if ("TWINS".equals(chosen)) {
                // 统一身份为其中一人（addRole 前改 seats → 两人 kit/宣告一致）
                Role unified = world.getRandom().nextBoolean() ? seats.get(a) : seats.get(b);
                seats.put(a, unified);
                seats.put(b, unified);
            }
            result.add(new Pair(a, b, chosen));
        }
        return result;
    }

    /** 身份发放后：落 Modifier 旗标 + 搭档/类型存储 + 开局提示（双子不提示） */
    public static void apply(ServerWorld world, Map<UUID, Role> seats, List<Pair> pairs,
                             java.util.function.BiConsumer<UUID, Text> notify) {
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(world);
        for (Pair pair : pairs) {
            Modifier mod = switch (pair.type()) {
                case "ARCHENEMY" -> MOD_ARCHENEMY;
                case "TWINS" -> MOD_TWINS;
                default -> MOD_LOVERS;
            };
            modifiers.addModifier(pair.a(), mod);
            modifiers.addModifier(pair.b(), mod);

            ServerPlayerEntity pa = (ServerPlayerEntity) world.getPlayerByUuid(pair.a());
            ServerPlayerEntity pb = (ServerPlayerEntity) world.getPlayerByUuid(pair.b());
            if (pa != null) {
                BttPlayerComponent c = BttPlayerComponent.KEY.get(pa);
                c.partner = pair.b().toString();
                c.relType = pair.type();
            }
            if (pb != null) {
                BttPlayerComponent c = BttPlayerComponent.KEY.get(pb);
                c.partner = pair.a().toString();
                c.relType = pair.type();
            }

            if (notify == null) continue;
            switch (pair.type()) {
                case "LOVER" -> {
                    if (pa != null) notify.accept(pair.a(), Text.literal("你的恋人是 " + nameOf(pb) + "。").withColor(COLOR_LOVERS));
                    if (pb != null) notify.accept(pair.b(), Text.literal("你的恋人是 " + nameOf(pa) + "。").withColor(COLOR_LOVERS));
                }
                case "ARCHENEMY" -> {
                    if (pa != null) notify.accept(pair.a(), Text.literal("你的宿敌是 " + nameOf(pb)
                            + "（" + BttIdentity.displayName(seats.get(pair.b())).getString() + "）。").withColor(COLOR_ARCHENEMY));
                    if (pb != null) notify.accept(pair.b(), Text.literal("你的宿敌是 " + nameOf(pa)
                            + "（" + BttIdentity.displayName(seats.get(pair.a())).getString() + "）。").withColor(COLOR_ARCHENEMY));
                }
                // 双子：互不知晓 ✓
            }
        }
    }

    private static String nameOf(ServerPlayerEntity p) {
        return p == null ? "？" : p.getGameProfile().getName();
    }

    // ===== 查询 =====

    public static UUID partnerOf(PlayerEntity player) {
        String s = player == null ? null : BttPlayerComponent.KEY.get(player).partner;
        try {
            return s == null || s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isLover(PlayerEntity player) {
        return "LOVER".equals(typeOf(player));
    }

    public static String typeOf(PlayerEntity player) {
        return player == null ? null : BttPlayerComponent.KEY.get(player).relType;
    }
}
