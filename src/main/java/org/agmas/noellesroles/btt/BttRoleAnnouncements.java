package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;

import java.util.HashMap;
import java.util.Map;

/**
 * NRS 结局协议（照抄 TrainMurderMystery fork）：mod init 期为**全部 70 身份**注册
 * per-role {@link RoleAnnouncementTexts.RoleAnnouncementText}（name=noellesroles.&lt;path&gt;，
 * lang 键 announcement.role.noellesroles.&lt;path&gt;，色=Role.color()）。
 * 注册发生在任何世界读档之前 → RoundEndData NBT 的 role index 恒定有效（结构性消除读档越界）；
 * BttRoundEndRoleMixin 据此把 RoundEndData.role 写为 per-role 条目（替代 endRoles csv 旁路）。
 */
public final class BttRoleAnnouncements {
    private BttRoleAnnouncements() {}

    /** path → 本 mod 注册的 per-role 条目 */
    private static final Map<String, RoleAnnouncementTexts.RoleAnnouncementText> ENTRIES = new HashMap<>();

    public static void init() {
        for (var e : BttRoles.allRoles().stream().collect(java.util.stream.Collectors.toMap(
                r -> r.identifier().getPath(), r -> r)).entrySet()) {
            String path = e.getKey();
            Role role = e.getValue();
            RoleAnnouncementTexts.RoleAnnouncementText entry = RoleAnnouncementTexts.registerRoleAnnouncementText(
                    new RoleAnnouncementTexts.RoleAnnouncementText("noellesroles." + path, role.color()));
            ENTRIES.put(path, entry);
        }
        BeforeTheTerminalGameMode.LOGGER.info("[BTT] per-role announcement entries registered: {}", ENTRIES.size());
    }

    public static RoleAnnouncementTexts.RoleAnnouncementText get(String path) {
        return path == null ? null : ENTRIES.get(path);
    }
}
