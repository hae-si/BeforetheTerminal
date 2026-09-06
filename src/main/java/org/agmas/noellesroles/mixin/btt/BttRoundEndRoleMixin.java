package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoleAnnouncements;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * BTT 结局数据（NRS 协议，照抄 TrainMurderMystery）：setRoundEndData 后把每玩家
 * RoundEndData.role 重写为其**真实身份**的 per-role 宣告条目
 * （{@link BttRoleAnnouncements} 于 mod init 注册进 wathe ROLE_ANNOUNCEMENT_TEXTS，
 * name=noellesroles.&lt;path&gt;，读写两侧时机恒定 → NBT index 恒定有效，读档越界结构性消除）。
 * didWin（胜负音效）以服务端下发的 game_state.winners 为准（音效与结局分组一致，含独胜）。
 */
@Mixin(GameRoundEndComponent.class)
public abstract class BttRoundEndRoleMixin {

    @Shadow @Final private World world;
    @Shadow @Final @Mutable private List<GameRoundEndComponent.RoundEndData> players;

    @Inject(method = "setRoundEndData", at = @At("TAIL"))
    private void bttPerRoleData(List<ServerPlayerEntity> playersArg, GameFunctions.WinStatus winStatus, CallbackInfo ci) {
        if (!BttIdentity.isBttMode(world)) return;
        List<GameRoundEndComponent.RoundEndData> rebuilt = new ArrayList<>();
        boolean changed = false;
        for (GameRoundEndComponent.RoundEndData d : this.players) {
            Role real = GameWorldComponent.KEY.get(world).getRole(d.player().getId());
            var entry = real == null ? null : BttRoleAnnouncements.get(real.identifier().getPath());
            if (entry != null && entry != d.role()) {
                rebuilt.add(new GameRoundEndComponent.RoundEndData(d.player(), entry, d.wasDead()));
                changed = true;
            } else {
                rebuilt.add(d);
            }
        }
        if (changed) {
            this.players.clear();
            this.players.addAll(rebuilt);
            ((GameRoundEndComponent) (Object) this).sync();
        }
    }

    @Inject(method = "didWin(Ljava/util/UUID;)Z", at = @At("HEAD"), cancellable = true)
    private void bttDidWin(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        if (!BttIdentity.isBttMode(world)) return;
        String winners = org.agmas.noellesroles.btt.BttGameWorldComponent.KEY.get(world).winners;
        cir.setReturnValue(Arrays.stream(winners.split(",")).anyMatch(uuid.toString()::equals));
    }
}
