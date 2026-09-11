package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.Component;

/**
 * BTT 尸体状态（CCA on {@link PlayerBodyEntity}）——取代原 {@code BttState} 中按尸体 UUID 存的
 * {@code corpseUsed}（失忆患者每具尸体只能取一次）。服务端私有，不 sync。
 */
public class BttBodyComponent implements Component {
    public static final ComponentKey<BttBodyComponent> KEY =
            ComponentRegistry.getOrCreate(Identifier.of(Noellesroles.MOD_ID, "btt_body"), BttBodyComponent.class);

    private final PlayerBodyEntity body;

    /** 失忆患者是否已从此尸体取过遗物 */
    public boolean amnesiacUsed = false;


    public BttBodyComponent(PlayerBodyEntity body) {
        this.body = body;
    }

    public boolean isAmnesiacUsed() {
        return amnesiacUsed;
    }

    public void markAmnesiacUsed() {
        this.amnesiacUsed = true;
    }


    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putBoolean("amnesiacUsed", amnesiacUsed);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        this.amnesiacUsed = tag.contains("amnesiacUsed") && tag.getBoolean("amnesiacUsed");
    }
}
