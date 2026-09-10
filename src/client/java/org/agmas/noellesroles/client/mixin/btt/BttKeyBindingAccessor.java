package org.agmas.noellesroles.client.mixin.btt;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读 KeyBinding 的**原始**按下状态（C-087 梦游病上浮键）。
 * <p>
 * wathe 1.3.2 的 {@code KeyBindingMixin} 会在存活玩家身上把 jumpKey/dropKey 等一律读成 false
 * （游戏内禁跳），而 wathe **没有** fork 版 WatheSpark 的 {@code ShouldAllowSuppressedKey} 事件，
 * 故灵魂出窍的上浮只能直接读字段。
 */
@Mixin(KeyBinding.class)
public interface BttKeyBindingAccessor {
    @Accessor("pressed")
    boolean noellesroles$isPressedRaw();
}
