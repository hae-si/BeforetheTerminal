package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.WatheGameModes;

/**
 * BTT 内容注册入口。在 Noellesroles.onInitialize 调用（早于 HML SERVER_STARTED refreshRoles）。
 */
public final class BttGameModes {
    private BttGameModes() {}

    public static final BeforeTheTerminalGameMode BEFORE_THE_TERMINAL = new BeforeTheTerminalGameMode();

    /** BTT 统一启动入口（Noellesroles.onInitialize 调用；早于 HML SERVER_STARTED refreshRoles） */
    public static void register() {
        BttRoles.register();
        BttRoleDefs.init();
        BttRoleAnnouncements.init();
        WatheGameModes.registerGameMode(BeforeTheTerminalGameMode.ID, BEFORE_THE_TERMINAL);
        BttEvents.init();
        BttEvents.registerTickHandlers();
        BeforeTheTerminalGameMode.LOGGER.info("[BTT] game mode registered: {} ({} identities, {} defs)",
                BeforeTheTerminalGameMode.ID, BttRoles.newRoleIds().size(), org.agmas.noellesroles.btt.BttRoleDefs.defCount());
    }
}
