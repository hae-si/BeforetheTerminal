package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BTT mood 旗帜按设计分类映射（2026-09-07 用户确认）：
 * 乘客绿旗（REAL→renderCivilian 原生）、凶手红旗（FAKE→renderKiller 原生）、
 * **独行中立蓝旗 hud/mood_ghost**、**外人中立品红旗 hud/mood_jester**（本 mixin 在 renderKiller 拦截改绘）。
 * 狂人中立已改 REAL（BttRoles）→ 自动走乘客绿旗。与 NR JesterMoodRenderer 对小丑的处理互不冲突
 * （双方对 BTT 小丑都画 mood_ghost，先到先 cancel，结果一致）。
 */
@Mixin(MoodRenderer.class)
public abstract class BttMoodFlagMixin {

    @Shadow public static float moodOffset;
    @Shadow public static float moodTextWidth;
    @Shadow public static float moodRender;
    @Shadow public static float moodAlpha;

    private static final Identifier GHOST = Identifier.of(Noellesroles.MOD_ID, "hud/mood_ghost");
    private static final Identifier JESTER = Identifier.of(Noellesroles.MOD_ID, "hud/mood_jester");

    @Inject(method = "renderKiller", at = @At("HEAD"), cancellable = true)
    private static void bttFactionFlag(TextRenderer textRenderer, DrawContext context, CallbackInfo ci) {
        var viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !BttIdentity.isBttMode(viewer.getWorld())) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(viewer.getWorld());
        Role role = gwc.getRole(viewer);
        if (role == null) return;
        Identifier tex = null;
        var faction = BttRoles.factionOf(role);
        if (faction == BttRoles.Faction.LONE) tex = GHOST;
        else if (faction == BttRoles.Faction.OUTSIDER_NEUTRAL) tex = JESTER;
        if (tex == null) return;
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 3.0F * moodOffset, 0.0F);
        context.drawGuiTexture(tex, 5, 6, 14, 17);
        context.getMatrices().pop();
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 10.0F * moodOffset, 0.0F);
        context.getMatrices().translate(26.0F, 8 + 9, 0.0F);
        context.getMatrices().scale((moodTextWidth - 8.0F) * moodRender, 1.0F, 1.0F);
        context.fill(0, 0, 1, 1, role.color() | (int) (moodAlpha * 255.0F) << 24);
        context.getMatrices().pop();
        ci.cancel();
    }
}
