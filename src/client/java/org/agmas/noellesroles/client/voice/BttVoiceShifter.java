package org.agmas.noellesroles.client.voice;

/**
 * 嗓音变调器（C-139，笑匠 &lt;变声&gt;）。**自研实现**（参考项目只用于确认技法，不复制其代码）。
 * <p>
 * 口径：一帧进一帧出（**时长不变、音高 ×ratio**），供 simple-voice-chat 的接收端在
 * "解码完成、空间化之前"改写 PCM。做法 = 经典两段式：
 * <ol>
 *   <li><b>时域拉长</b> ×ratio：汉宁窗 50% 交叠相加（OLA），分析位置 = 输出位置 / ratio，
 *       并用窗权重归一化，避免帧首尾幅度塌陷；</li>
 *   <li><b>线性插值重采样</b> ÷ratio：把拉长后的波形按 ratio 采样回原始长度（这一步决定音高）。</li>
 * </ol>
 * 跨帧连续性用**尾部交叉淡化**（保留上一帧末尾 hop 个样点，与本帧开头线性混合）——比 NRS 的 WSOLA
 * （相位搜索 + Hermite 插值）简单，听感上是"氦气音"可用级；若要更干净可后续升级为相位对齐版本。
 * <p>
 * 每个说话人一个实例（非线程安全，调用方保证串行）。
 */
public final class BttVoiceShifter {
    /** 分析窗上限（≈20ms @48kHz） */
    private static final int MAX_WINDOW = 960;
    /** 交叉淡化长度（hop = 窗长一半） */
    private static final int FADE = MAX_WINDOW / 2;

    private final float[] prevTail = new float[FADE];
    private boolean hasPrev;

    /** 帧内时域拉长：输出长度 ≈ in.length × ratio（汉宁窗 50% 交叠相加 + 权重归一化） */
    private static float[] stretch(float[] in, float ratio) {
        int n = in.length;
        int len = Math.min(MAX_WINDOW, n);
        if (len < 8) return in.clone();
        int hop = len / 2;
        int outLen = Math.round(n * ratio);
        float[] out = new float[outLen + len];
        float[] weight = new float[outLen + len];
        for (int outPos = 0; outPos < outLen; outPos += hop) {
            int src = Math.min(n - len, Math.round(outPos / ratio));
            if (src < 0) src = 0;
            for (int i = 0; i < len; i++) {
                float w = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (len - 1))); // 汉宁窗
                out[outPos + i] += in[src + i] * w;
                weight[outPos + i] += w;
            }
        }
        for (int i = 0; i < outLen; i++) {
            if (weight[i] > 1.0e-4f) out[i] /= weight[i];
        }
        float[] trimmed = new float[outLen];
        System.arraycopy(out, 0, trimmed, 0, outLen);
        return trimmed;
    }

    /** 线性插值重采样：把 src 按 ratio 采样成 outLen 个样点 */
    private static float[] resample(float[] src, float ratio, int outLen) {
        float[] out = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            float pos = i * ratio;
            int i0 = (int) pos;
            if (i0 >= src.length - 1) {
                out[i] = src[src.length - 1];
                continue;
            }
            float frac = pos - i0;
            out[i] = src[i0] * (1.0f - frac) + src[i0 + 1] * frac;
        }
        return out;
    }

    /**
     * 处理一帧 PCM（16bit，单声道短数组）。ratio &gt; 1 = 音高变高。
     * 返回**与输入等长**的新数组（输入不被修改）。
     */
    public short[] process(short[] in, float ratio) {
        int n = in.length;
        if (n == 0 || ratio <= 1.001f) return in;
        float[] src = new float[n];
        for (int i = 0; i < n; i++) src[i] = in[i];
        float[] stretched = stretch(src, ratio);
        float[] shifted = resample(stretched, ratio, n);
        // 跨帧交叉淡化：本帧开头与上一帧尾巴线性混合（消除帧边界爆音）
        if (hasPrev) {
            int fade = Math.min(FADE, n);
            for (int i = 0; i < fade; i++) {
                float t = (float) i / fade;
                shifted[i] = prevTail[i] * (1.0f - t) + shifted[i] * t;
            }
        }
        int fade = Math.min(FADE, n);
        System.arraycopy(shifted, n - fade, prevTail, 0, fade);
        hasPrev = true;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(shifted[i])));
        }
        return out;
    }
}
