package org.agmas.noellesroles;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import dev.doctor4t.wathe.item.RevolverItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.item.BombItem;
import org.agmas.noellesroles.item.DaggerItem;
import org.agmas.noellesroles.item.FakeKnifeItem;
import org.agmas.noellesroles.item.PoisonGasGrenadeItem;
import org.agmas.noellesroles.item.PoisonNeedleItem;
import org.agmas.noellesroles.item.RoleMineItem;

public class ModItems {
    public static void init() {
        GameConstants.ITEM_COOLDOWNS.put(FAKE_REVOLVER, GameConstants.getInTicks(0,8));
        // ===== C-084：BTT 新物品冷却（doc 口径；物品自带冷却写入本表）=====
        GameConstants.ITEM_COOLDOWNS.put(DAGGER, GameConstants.getInTicks(1, 0));            // 清道夫匕首 1 分钟
        GameConstants.ITEM_COOLDOWNS.put(POISON_NEEDLE, GameConstants.getInTicks(0, 30));    // 毒针 30 秒
        GameConstants.ITEM_COOLDOWNS.put(BOMB, GameConstants.getInTicks(0, 30));             // 炸弹箱安放 30 秒
    }

    public static final Item FAKE_KNIFE = register(
            new FakeKnifeItem(new Item.Settings().maxCount(1)),
            "fake_knife"
    );
    public static final Item FAKE_REVOLVER = register(
            new RevolverItem(new Item.Settings().maxCount(1)),
            "fake_revolver"
    );
    public static final Item MASTER_KEY = register(
            new Item(new Item.Settings().maxCount(1)),
            "master_key"
    );
    public static final Item DELUSION_VIAL = register(
            new Item(new Item.Settings().maxCount(1)),
            "delusion_vial"
    );
    public static final Item DEFENSE_VIAL = register(
            new Item(new Item.Settings().maxCount(1)),
            "defense_vial"
    );
    public static final Item ROLE_MINE = register(
            new RoleMineItem(new Item.Settings().maxCount(1)),
            "role_mine"
    );
    // ===== C-084：BTT 新物品（模型/纹理先以现成资源顶替）=====
    /** 匕首：清道夫初始武器（即时·无声·1 分钟冷却） */
    public static final Item DAGGER = register(
            new DaggerItem(new Item.Settings().maxCount(1)),
            "dagger"
    );
    /** 毒针：炼金术士初始武器（右键身边者注入毒药，30 秒冷却） */
    public static final Item POISON_NEEDLE = register(
            new PoisonNeedleItem(new Item.Settings().maxCount(1)),
            "poison_needle"
    );
    /** 炸弹箱：恐怖分子初始道具（右键安放；身上有炸弹时可右键传递） */
    public static final Item BOMB = register(
            new BombItem(new Item.Settings().maxCount(1)),
            "bomb"
    );
    /** 毒气弹：炼金术士商店专属商品（投掷 → 毒气云充满房间） */
    public static final Item POISON_GAS_GRENADE = register(
            new PoisonGasGrenadeItem(new Item.Settings().maxCount(16)),
            "poison_gas_grenade"
    );
    public static Item register(Item item, String id) {
        // Create the identifier for the item.
        Identifier itemID = Identifier.of(Noellesroles.MOD_ID, id);

        // Register the item.
        Item registeredItem = Registry.register(Registries.ITEM, itemID, item);

        // Return the registered item!
        return registeredItem;
    }

}
