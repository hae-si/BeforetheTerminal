package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import org.agmas.noellesroles.btt.BttArchitect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * C-083 建筑师感知挂点：官方 wathe 1.3.2 没有门状态事件（NRS 用的 DoorStateChanged 属 WatheSpark fork-only），
 * 故在 {@link DoorBlockEntity#jam()}/{@link DoorBlockEntity#blast()} 末尾自行派发。
 * 全车只有 LockpickItem（撬锁=卡门）与 CrowbarItem（撬棍=撬门）调用这两个方法；serverTick 的 jammedTime 递减走 setJammed，不触发本挂点。
 */
@Mixin(DoorBlockEntity.class)
public abstract class BttDoorStateMixin {

    @Inject(method = "jam", at = @At("TAIL"))
    private void bttOnDoorJammed(CallbackInfo ci) {
        BttArchitect.onDoorTampered((DoorBlockEntity) (Object) this);
    }

    @Inject(method = "blast", at = @At("TAIL"))
    private void bttOnDoorBlasted(CallbackInfo ci) {
        BttArchitect.onDoorTampered((DoorBlockEntity) (Object) this);
    }
}
