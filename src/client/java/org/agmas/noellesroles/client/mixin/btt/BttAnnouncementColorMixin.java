package org.agmas.noellesroles.client.mixin.btt;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 「通知栏」（回合开始的身份通知覆盖层 `announcement.welcome`，即 wathe `AnnounceWelcomePayload` → 迎新覆盖层）
 * 的文字色 = 身份色（C-098，用户指令「通知栏为身份颜色」）。
 * <p>
 * wathe {@code RoleAnnouncementText} 构造器把 welcomeText 的底色写死 {@code 0xF0F0F0}（只有内嵌的角色名带身份色），
 * 这里把该常量替换为该条目自己的 {@link #colour}——BTT 的 per-role 条目由 `BttRoleAnnouncements` 以
 * {@code Role.color()} 注册，故「欢迎登车 ＜身份＞」整行随身份色。只改文本样式，不动布局/时序。
 */
@Mixin(RoleAnnouncementTexts.RoleAnnouncementText.class)
public abstract class BttAnnouncementColorMixin {
    @Shadow @Final public int colour;

    /** 0xF0F0F0 = 15790320（welcomeText 的硬编码底色）→ 身份色 */
    @ModifyExpressionValue(method = "<init>", at = @At(value = "CONSTANT", args = "intValue=15790320"))
    private int bttWelcomeRoleColor(int original) {
        return this.colour;
    }
}
