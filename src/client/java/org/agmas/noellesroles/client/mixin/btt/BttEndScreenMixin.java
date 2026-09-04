package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.agmas.noellesroles.btt.BeforeTheTerminalGameMode;
import org.agmas.noellesroles.btt.BttEndings;
import org.agmas.noellesroles.btt.BttGameWorldComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DEMO-008（表现层）：BTT 局内用 doc 结局文本直接替换 wathe 的回合结束覆盖层
 * （标题=结局名 / 引语 / 阵营胜负 / 观看者个人胜负），不再向聊天框输出。
 * 开局的身份迎新覆盖层（welcomeTime 阶段）不受影响。
 */
@Mixin(RoundTextRenderer.class)
public abstract class BttEndScreenMixin {

    @Shadow private static int endTime;

    @Inject(method = "renderHud", at = @At("HEAD"), cancellable = true)
    private static void bttReplaceEndText(TextRenderer renderer, ClientPlayerEntity player, DrawContext context, CallbackInfo ci) {
        if (endTime <= 0) return; // 结束覆盖层未激活（迎新屏走原版）
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(client.world);
        if (!(gwc.getGameMode() instanceof BeforeTheTerminalGameMode)) return;

        BttEndings.Ending ending;
        try {
            ending = BttEndings.Ending.valueOf(BttGameWorldComponent.KEY.get(client.world).lastEnding);
        } catch (IllegalArgumentException e) {
            return; // 未知值 → 回落原版文本
        }
        if (ending == BttEndings.Ending.NONE) return;

        ci.cancel();

        int cx = context.getScaledWindowWidth() / 2;
        int y0 = (int) (context.getScaledWindowHeight() * 0.30f);
        int color = BttEndings.titleColor(ending);

        // 标题（2x）
        Text title = Text.translatable("btt.ending." + keyOf(ending) + ".title");
        context.getMatrices().push();
        context.getMatrices().translate(cx, y0, 0);
        context.getMatrices().scale(2.0f, 2.0f, 1.0f);
        context.drawCenteredTextWithShadow(renderer, title, 0, 0, color);
        context.getMatrices().pop();

        // 引语
        Text quote = Text.translatable("btt.ending." + keyOf(ending) + ".quote");
        context.drawCenteredTextWithShadow(renderer, quote, cx, y0 + 40, 0xFFAAAAAA);

        // 阵营胜负
        Text result = Text.translatable("btt.ending." + keyOf(ending) + ".result");
        context.drawCenteredTextWithShadow(renderer, result, cx, y0 + 60, color);

        // 观看者个人胜负
        BttEndings.Personal personal = BttEndings.personalOutcome(ending, gwc.getRole(player));
        Text personalText = switch (personal) {
            case WIN -> Text.translatable("btt.ending.personal.win");
            case LOSE -> Text.translatable("btt.ending.personal.lose");
            case NEUTRAL -> Text.translatable("btt.ending.personal.neutral");
            default -> null;
        };
        if (personalText != null) {
            int pc = switch (personal) {
                case WIN -> 0xFF55FF55;
                case LOSE -> 0xFFFF5555;
                default -> 0xFF888888;
            };
            context.drawCenteredTextWithShadow(renderer, personalText, cx, y0 + 84, pc);
        }
    }

    private static String keyOf(BttEndings.Ending ending) {
        return switch (ending) {
            case TRIAL_COMPLETE -> "trial_complete";
            case ENFORCEMENT_END -> "enforcement_end";
            case JOURNEY_END -> "journey_end";
            case BLOOD_EXPRESS -> "blood_express";
            default -> "none";
        };
    }
}
