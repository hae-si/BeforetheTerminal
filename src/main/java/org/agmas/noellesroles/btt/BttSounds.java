package org.agmas.noellesroles.btt;

import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;

/**
 * BTT 音频（尾声 BGM）。
 * 音频文件占位：`assets/noellesroles/sounds/epilogue/{majo,cult,kidnapper,gardener,survival}.ogg`
 * （当前为空文件，待作者替换为正式 ogg；替换后无需改代码）。
 */
public final class BttSounds {
    private BttSounds() {}

    public static final SoundEvent EPILOGUE_MAJO = SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "epilogue_majo"));
    public static final SoundEvent EPILOGUE_CULT = SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "epilogue_cult"));
    public static final SoundEvent EPILOGUE_KIDNAPPER = SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "epilogue_kidnapper"));
    public static final SoundEvent EPILOGUE_GARDENER = SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "epilogue_gardener"));
    public static final SoundEvent EPILOGUE_SURVIVAL = SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "epilogue_survival"));

    public static final SoundEvent[] EPILOGUE_ALL = {
            EPILOGUE_MAJO, EPILOGUE_CULT, EPILOGUE_KIDNAPPER, EPILOGUE_GARDENER, EPILOGUE_SURVIVAL
    };

    /** 按尾声主持人翁类型取 BGM（"MAJO"/"CULT"/"KIDNAPPER"/"GARDENER"/其余=生还） */
    public static SoundEvent forEpilogue(String type) {
        return switch (type) {
            case "MAJO" -> EPILOGUE_MAJO;
            case "CULT" -> EPILOGUE_CULT;
            case "KIDNAPPER" -> EPILOGUE_KIDNAPPER;
            case "GARDENER" -> EPILOGUE_GARDENER;
            default -> EPILOGUE_SURVIVAL;
        };
    }
}
