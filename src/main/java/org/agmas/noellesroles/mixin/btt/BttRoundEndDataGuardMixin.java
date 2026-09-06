package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

/**
 * 读档防御（C-030）：旧版曾把 per-role 宣告条目 index（70+）写进世界存档的 RoundEndData，
 * 重进世界时 HML 注册未发生（列表仅 5 条）→ IndexOutOfBoundsException 读档崩。
 * 对 NBT 构造器的列表取值 clamp：越界一律回退 BLANK。
 */
@Mixin(GameRoundEndComponent.RoundEndData.class)
public abstract class BttRoundEndDataGuardMixin {

    @Redirect(method = "<init>(Lnet/minecraft/nbt/NbtCompound;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;get(I)Ljava/lang/Object;"))
    private static Object bttClampRoleIndex(ArrayList list, int index) {
        if (index < 0 || index >= list.size()) return RoleAnnouncementTexts.BLANK;
        return list.get(index);
    }
}
