package org.agmas.noellesroles.btt;

import net.minecraft.item.Item;
import org.agmas.noellesroles.Noellesroles;

public final class BttItems {
    private BttItems() {}

    /** 教父 <查验> 道具（对活人/尸体用，30s CD） */
    public static final Item INSPECT = BttItems.register(
            new BttInspectItem(new Item.Settings().maxCount(1)),
            "inspect"
    );

    private static Item register(Item item, String id) {
        return net.minecraft.registry.Registry.register(
                net.minecraft.registry.Registries.ITEM,
                net.minecraft.util.Identifier.of(Noellesroles.MOD_ID, id),
                item
        );
    }

    /** 强制类加载完成物品注册（由 BttGameModes.register 调用） */
    public static void bootstrap() {
        if (INSPECT == null) throw new IllegalStateException("noellesroles:inspect failed to register");
    }
}
