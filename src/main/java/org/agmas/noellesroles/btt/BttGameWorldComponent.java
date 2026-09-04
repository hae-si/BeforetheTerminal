package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * BTT 回合级世界状态。active 标志 + 服务端 tick（小丑疯魔结束回收球棒）。
 * 席位快照/结局数据等在后续任务扩展。
 */
public class BttGameWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<BttGameWorldComponent> KEY =
            ComponentRegistry.getOrCreate(Identifier.of(Noellesroles.MOD_ID, "btt_game"), BttGameWorldComponent.class);

    private final World world;
    /** 当前是否处于 BTT 回合（STARTING/ACTIVE/STOPPING 视为回合内） */
    public boolean active = false;
    /** 本局 doc 结局（BttEndings.Ending 名称；NONE=无）。同步给客户端驱动结束覆盖层文本。 */
    public String lastEnding = "NONE";

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
    public void serverTick() {
        // 小丑疯魔结束 → 回收疯魔球棒（doc 疯魔模式限定）
        if (!(GameWorldComponent.KEY.get(world).getGameMode() instanceof BeforeTheTerminalGameMode)) return;
        if (!(world instanceof net.minecraft.server.world.ServerWorld serverWorld)) return;
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            if (GameWorldComponent.KEY.get(world).isRole(player, BttRoles.JESTER)) {
                PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(player);
                if (psycho.getPsychoTicks() <= 0 && hasItem(player, WatheItems.BAT)) {
                    removeOne(player, WatheItems.BAT);
                }
            }
        }
    }

    private static boolean hasItem(ServerPlayerEntity player, net.minecraft.item.Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(item)) return true;
        }
        return false;
    }

    private static void removeOne(ServerPlayerEntity player, net.minecraft.item.Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                stack.setCount(0);
                player.getInventory().markDirty();
                return;
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, @NotNull RegistryWrapper.WrapperLookup registryLookup) {
        tag.putBoolean("active", this.active);
        tag.putString("lastEnding", this.lastEnding);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, @NotNull RegistryWrapper.WrapperLookup registryLookup) {
        if (tag.contains("active")) this.active = tag.getBoolean("active");
        if (tag.contains("lastEnding")) this.lastEnding = tag.getString("lastEnding");
    }
}
