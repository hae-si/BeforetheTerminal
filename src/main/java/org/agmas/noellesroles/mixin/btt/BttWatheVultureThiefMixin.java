package org.agmas.noellesroles.mixin.btt;

import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.coroner.BodyDeathReasonComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import org.agmas.noellesroles.vulture.VulturePlayerComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.btt.BttGameWorldComponent;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 窃贼胜利（C-039，用户指令"复用秃鹫但吃尸体=胜利"）：窃贼=接管 NR VULTURE，
 * G 键吃尸走 NR 原生 packet（lambda$registerPackets$12）；BTT 局内拦 HEAD cancellable——
 * 复刻吃尸效果（计数/burp/缓慢/vultured）但不转杀手；计数过半 → THIEF_WIN 结局。
 */
@Mixin(Noellesroles.class)
public abstract class BttWatheVultureThiefMixin {

    @Inject(method = "lambda$registerPackets$12", at = @At("HEAD"), cancellable = true)
    private static void bttThiefEat(org.agmas.noellesroles.packet.VultureEatC2SPacket payload, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context, CallbackInfo ci) {
        var eat = payload; var ctx = context;
        ServerPlayerEntity user = ctx.player();
        if (!BttIdentity.isBttMode(user.getWorld())) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(user.getWorld());
        if (!gwc.isRunning()) return;
        if (!gwc.isRole(user, org.agmas.noellesroles.btt.BttRoles.THIEF)) return; // 非窃贼（真秃鹫）走原逻辑
        if (!(user.getServerWorld().getEntity(eat.playerBody()) instanceof PlayerBodyEntity body)) return;
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) { ci.cancel(); return; }
        BodyDeathReasonComponent death = BodyDeathReasonComponent.KEY.get(body);
        if (death.vultured) { ci.cancel(); return; }

        // 复刻 NR 吃尸效果；C-099：docx 未给 <搜刮> 冷却 → 按「大多数冷却统一 1 分钟」取 1 分钟（原 NR 原生 20 秒）
        ability.cooldown = GameConstants.getInTicks(0, 10); // docx 2026-09-12：搜刮 CD 10 秒（原 1 分钟）
        ability.sync();
        VulturePlayerComponent vulture = VulturePlayerComponent.KEY.get(user);
        vulture.bodiesEaten++;
        vulture.sync();
        // C-113：窃贼是**搜刮**不是"吃尸"（用户裁定，参照 NRS）——去掉 burp 音与缓慢
        death.vultured = true;
        // C-128：搜刮音
        user.getServerWorld().playSound(null, user.getBlockPos(),
                org.agmas.noellesroles.btt.BttSounds.THIEF_SCAVENGE,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        // C-095：搜刮成功后短暂透视全员 10 秒（NRS vulture setHighlightTicks 同值；BTT 走本机描边，不吃 fork 的 GetInstinctHighlight）
        BttPlayerComponent.KEY.get(user).setThiefReveal(GameConstants.getInTicks(0, 10));

        // 过半 → 窃贼独胜
        long players = user.getWorld().getPlayers().size();
        if (vulture.bodiesEaten * 3 >= players) {
            BttGameWorldComponent btt = BttGameWorldComponent.KEY.get(user.getWorld());
            btt.lastEnding = "THIEF_WIN";
            btt.winners = user.getUuid().toString();
            btt.sync();
            // 与小说家/GameMode 终局同构：per-role 结局数据 + stopGame（独胜 WinStatus=NONE）
            dev.doctor4t.wathe.cca.GameRoundEndComponent.KEY.get(user.getServerWorld())
                    .setRoundEndData(user.getServerWorld().getPlayers().stream()
                                    .filter(p -> gwc.getRole(p) != null).collect(java.util.stream.Collectors.toList()),
                            dev.doctor4t.wathe.game.GameFunctions.WinStatus.NONE);
            dev.doctor4t.wathe.game.GameFunctions.stopGame(user.getServerWorld());
        } else {
            // C-098：技能反馈（动作栏）用盗窃者身份色
            user.sendMessage(net.minecraft.text.Text.translatable("noellesroles.btt.action.thief.scavenge", vulture.bodiesEaten, vulture.bodiesRequired)
                    .withColor(org.agmas.noellesroles.btt.BttRoles.THIEF.color()), true);
        }
        ci.cancel();
    }
}
