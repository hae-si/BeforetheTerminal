package org.agmas.noellesroles.btt;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * BTT 回合级世界状态（BT-ARCH-001 后为**纯数据组件**）：
 * active 标志 + lastEnding（驱动客户端 BttEndTextMixin）。
 * 原.serverTick 的身份行为（小丑球棒/精神病人护盾）已迁至 {@link BttRoleDefs} 的 onTick 声明。
 */
public class BttGameWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<BttGameWorldComponent> KEY =
            ComponentRegistry.getOrCreate(Identifier.of(Noellesroles.MOD_ID, "game_state"), BttGameWorldComponent.class);

    private final World world;
    /** 当前是否处于 BTT 回合（STARTING/ACTIVE/STOPPING 视为回合内） */
    public boolean active = false;
    /** 本局 doc 结局（BttEndings.Ending 名称；NONE=无）。同步给客户端驱动结束覆盖层文本。 */
    public String lastEnding = "NONE";
    /**
     * 独胜结局的赢家 uuid 列表（逗号分隔；THIEF_WIN/NOVELIST_WIN/MAJO_WIN 用；常规三结局留空=客户端按阵营算）。
     * fork（TrainMurderMystery）把 isWinner 在服务端算好下发，wathe 的 RoundEndData 无此槽位 → 用本字段承载。
     */
    public String winners = "";
    /** 活跃尾声（BT-SYS-EPILOGUE 最小实现："MAJO"=魔女尾声进行中，空=无）。同步给客户端驱动活人雷达。 */
    public String epilogue = "";


    public BttGameWorldComponent(World world) {
        this.world = world;
    }

    public void sync() {
        KEY.sync(world);
    }

    public boolean isBttRoundRunning() {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        return game.getGameMode() instanceof BeforeTheTerminalGameMode && game.isRunning();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, @NotNull RegistryWrapper.WrapperLookup registryLookup) {
        tag.putBoolean("active", this.active);
        tag.putString("lastEnding", this.lastEnding);
        tag.putString("winners", this.winners);
        tag.putString("epilogue", this.epilogue);

    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, @NotNull RegistryWrapper.WrapperLookup registryLookup) {
        if (tag.contains("active")) this.active = tag.getBoolean("active");
        if (tag.contains("lastEnding")) this.lastEnding = tag.getString("lastEnding");
        if (tag.contains("winners")) this.winners = tag.getString("winners");
        if (tag.contains("epilogue")) this.epilogue = tag.getString("epilogue");

    }
}
