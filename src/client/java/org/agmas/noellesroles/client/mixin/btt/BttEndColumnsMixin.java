package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.btt.BttGameWorldComponent;
import org.agmas.noellesroles.btt.BttIdentity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * BTT 结局覆盖层（C-026 二轮：对齐 fork TrainMurderMystery 真实现）。
 * <p>
 * fork 结构（references/TrainMurderMystery RoundTextRenderer）：
 * <ul>
 *   <li>WinStatus==NONE 仍渲染（BTT 独胜结局 THIEF_WIN/NOVELIST_WIN 不再整屏空白）；</li>
 *   <li>玩家卡片按**胜/负两组**网格（winners 无标题、losers 有 announcement.result.losers 标题）；</li>
 *   <li>宣言=结局配套键 noellesroles.ending.quote.*（docx 结局章斜体句；**胜/败双方同文**，
 *       仅 didWin 音效区分——2026-09-06 用户裁定，原 per-role winText 分歧口径废除）；</li>
 *   <li>卡片 = 头像（脸+帽）+ 死亡 X + **角色名原尺寸在头像下方 y+18**（带角色色）；</li>
 *   <li>布局参数：cardWidth 36 / cardHeight 28 / 每行 ≥6 / 最多 4 行 / 行内居中。</li>
 * </ul>
 * isWinner 判定：常规三结局按阵营（客户端 gwc 同步 roles 即可算，同 fork 服务端 Role.getFaction）；
 * 独胜结局读 {@code game_state.winners}（服务端下发 uuid 列表，fork 无此需求因 RoundEndData 内嵌 isWinner）。
 * 注入点 = renderHud HEAD cancellable 全自绘（原 winStatus==NONE early-return 会拦住独胜）；
 * 原 BttEndTextMixin（标题/引语改写）并入本类后删除。
 */
@Mixin(RoundTextRenderer.class)
public abstract class BttEndColumnsMixin {

    @Shadow private static int endTime;

    private static final int CARD_WIDTH = 36;
    private static final int CARD_HEIGHT = 28;
    private static final int MAX_ROWS = 4;
    private static final int MIN_PER_ROW = 6;

    @Inject(method = "renderHud", at = @At("HEAD"), cancellable = true)
    private static void bttEndScreen(TextRenderer renderer, ClientPlayerEntity player, DrawContext context, CallbackInfo ci) {
        if (!BttIdentity.isBttMode(player.getWorld())) return;
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (!(endTime > 0 && endTime < 200 - (GameConstants.FADE_TIME * 2) && !game.isRunning())) return;

        GameRoundEndComponent roundEnd = GameRoundEndComponent.KEY.get(player.getWorld());
        GameFunctions.WinStatus status = roundEnd.getWinStatus();
        String lastEnding = BttGameWorldComponent.KEY.get(player.getWorld()).lastEnding;
        boolean soloWin = "THIEF_WIN".equals(lastEnding) || "NOVELIST_WIN".equals(lastEnding) || "MAJO_WIN".equals(lastEnding);
        if (status == GameFunctions.WinStatus.NONE && !soloWin) return; // 无人结局

        List<GameRoundEndComponent.RoundEndData> entries = roundEnd.getPlayers();
        List<GameRoundEndComponent.RoundEndData> winners = new ArrayList<>();
        List<GameRoundEndComponent.RoundEndData> losers = new ArrayList<>();

        // fork 口径：isWinner 服务端算好下发（game_state.winners csv，覆盖全部结局）——客户端不再自行判阵营
        BttGameWorldComponent btt = BttGameWorldComponent.KEY.get(player.getWorld());
        List<String> winnerIds = Arrays.stream(btt.winners.split(","))
                .filter(s -> !s.isEmpty()).toList();
        for (GameRoundEndComponent.RoundEndData entry : entries) {
            (winnerIds.contains(entry.player().getId().toString()) ? winners : losers).add(entry);
        }

        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2f, context.getScaledWindowHeight() / 2f - 40, 0);

        // ===== 标题（结局名） =====
        String key = endingKey(lastEnding);
        if (key != null) {
            int color = switch (key) {
                case "trial" -> 0xFF55FF55;
                case "journey" -> 0xFF55FFFF;
                case "blood" -> 0xFFFF5555;
                case "thief" -> 0xFFAA00AA;
                case "novelist" -> 0xFF00FFFF; // 小说家=角色青色（用户 2026-09-06；原 0xFFAF7ADB 紫）
                case "majo" -> 0xFFFF00FF; // 魔女=角色色（洋红）
                case "naku" -> 0xFF8B0000;
                case "heretic_killer", "heretic_passenger" -> 0xFF800000; // 异端特殊结局=角色色
                default -> 0xFFFFFFFF;
            };
            Text endText = Text.translatable("noellesroles.ending." + key).withColor(color);
            context.getMatrices().push();
            context.getMatrices().scale(2.6f, 2.6f, 1f);
            context.drawTextWithShadow(renderer, endText, -renderer.getWidth(endText) / 2, -12, 0xFFFFFF);
            context.getMatrices().pop();
        }

        // ===== 宣言（结局配套，胜/败双方同文；仅 didWin 音效区分——用户裁定 2026-09-06）=====
        // 异端特殊结局宣言 = docx 特殊胜利宣言（noellesroles.special.heretic.*，C-033 预存键）
        if (key != null) {
            String quoteKey = switch (key) {
                case "heretic_killer" -> "noellesroles.special.heretic.killer_win";
                case "heretic_passenger" -> "noellesroles.special.heretic.passenger_win";
                default -> "noellesroles.ending.quote." + key;
            };
            Text declaration = Text.translatable(quoteKey);
            context.getMatrices().push();
            context.getMatrices().scale(1.2f, 1.2f, 1f);
            context.drawTextWithShadow(renderer, declaration, -renderer.getWidth(declaration) / 2, -4, 0xFFFFFF);
            context.getMatrices().pop();
        }

        // ===== 胜者组（无标题） =====
        int winnerPerRow = perRow(winners.size());
        for (int i = 0; i < winners.size(); i++) {
            int row = i / winnerPerRow;
            int col = i % winnerPerRow;
            int itemsInRow = (row == rowsFor(winners.size(), winnerPerRow) - 1)
                    ? (winners.size() - 1) % winnerPerRow + 1 : winnerPerRow;
            int x = -(itemsInRow * CARD_WIDTH) / 2 + col * CARD_WIDTH + CARD_WIDTH / 2 - 8;
            int y = 16 + row * CARD_HEIGHT;
            renderCard(context, renderer, winners.get(i), x, y);
        }

        // ===== 败者组（标题 + 网格） =====
        int losersStartY = 16 + Math.max(1, rowsFor(winners.size(), winnerPerRow)) * CARD_HEIGHT + 8;
        if (!losers.isEmpty()) {
            Text losersTitle = Text.translatable("noellesroles.ending.result.losers");
            context.drawTextWithShadow(renderer, losersTitle, -renderer.getWidth(losersTitle) / 2, losersStartY, 0xFF5555);
            int loserPerRow = perRow(losers.size());
            int gap = 14;
            for (int i = 0; i < losers.size(); i++) {
                int row = i / loserPerRow;
                int col = i % loserPerRow;
                int itemsInRow = (row == rowsFor(losers.size(), loserPerRow) - 1)
                        ? (losers.size() - 1) % loserPerRow + 1 : loserPerRow;
                int x = -(itemsInRow * CARD_WIDTH) / 2 + col * CARD_WIDTH + CARD_WIDTH / 2 - 8;
                int y = losersStartY + gap + row * CARD_HEIGHT;
                renderCard(context, renderer, losers.get(i), x, y);
            }
        }

        context.getMatrices().pop();
        ci.cancel();
    }

    private static int perRow(int size) {
        return Math.max(MIN_PER_ROW, (size + MAX_ROWS - 1) / MAX_ROWS);
    }

    private static int rowsFor(int size, int perRow) {
        return size == 0 ? 0 : Math.min(MAX_ROWS, (size + perRow - 1) / perRow);
    }

    /** fork renderPlayerCard 同构：头像（脸+帽）+ 死亡 X + 角色名 y+18 原尺寸带角色色（NRS：role=per-role 条目） */
    private static void renderCard(DrawContext context, TextRenderer renderer, GameRoundEndComponent.RoundEndData entry, int x, int y) {
        PlayerListEntry ple = WatheClient.PLAYER_ENTRIES_CACHE.get(entry.player().getId());
        Identifier texture = ple == null ? null : ple.getSkinTextures().texture();
        boolean dead = entry.wasDead();
        if (texture != null) {
            context.getMatrices().push();
            context.getMatrices().scale(2f, 2f, 1f);
            context.getMatrices().translate(x / 2f, y / 2f, 0);
            // 1.21.1 Yarn 的 drawTexturedQuad 14 参为包私有 → 公共 drawTexture + 死亡 fill 压暗近似 fork r,g,b(1.0,0.4,0.4)
            context.drawTexture(texture, 0, 0, 8f, 8f, 8, 8, 64, 64);
            context.getMatrices().push();
            context.getMatrices().translate(-0.5f, -0.5f, 0);
            context.getMatrices().scale(1.125f, 1.125f, 1f);
            context.drawTexture(texture, 0, 0, 40f, 8f, 8, 8, 64, 64);
            context.getMatrices().pop();
            context.getMatrices().pop();
        }
        if (dead) {
            context.getMatrices().push();
            context.getMatrices().scale(2f, 1f, 1f);
            context.getMatrices().translate(x / 2f + 5, y + 8f, 0);   // 修正：y 不再除以 2
            context.drawText(renderer, "x", -renderer.getWidth("x") / 2, 0, 0xE10000, false);
            context.drawText(renderer, "x", -renderer.getWidth("x") / 2, 1, 0x550000, false);
            context.getMatrices().pop();
        }
        // NRS：roleText 自带角色色（per-role 条目由 BttRoleAnnouncements 注册、BttRoundEndRoleMixin 写入）
        Text roleName = entry.role().roleText;
        context.drawTextWithShadow(renderer, roleName, x + 8 - renderer.getWidth(roleName) / 2, y + 18, entry.role().colour);
    }

    private static String endingKey(String last) {
        return switch (last == null ? "NONE" : last) {
            case "TRIAL_COMPLETE" -> "trial";
            case "JOURNEY_END" -> "journey";
            case "BLOOD_EXPRESS" -> "blood";
            case "THIEF_WIN" -> "thief";
            case "NOVELIST_WIN" -> "novelist";
            case "NAKU_KORO" -> "naku";
            case "HERETIC_KILLER" -> "heretic_killer";
            case "HERETIC_PASSENGER" -> "heretic_passenger";
            case "MAJO_WIN" -> "majo";
            default -> null;
        };
    }
}
