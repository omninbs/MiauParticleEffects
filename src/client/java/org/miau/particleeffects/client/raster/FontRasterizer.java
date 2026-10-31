package org.miau.particleeffects.client.raster;

import org.miau.particleeffects.config.ConfigManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 文字栅格化器：把文字（含 § 颜色码）渲染到离屏位图，然后按密度采样为粒子坐标列表。
 *
 * <p>每个 {@link ParticlePixel} 的局部坐标 {@code (x, y)} 单位为方块（世界空间），
 * 以文字中心为原点，X 水平向右，Y 垂直向上；同时记录该像素属于第几个字符，
 * 供「逐字打字」类动画使用。</p>
 *
 * <p>采样点数量不设死上限：默认上限取自配置 {@code maxTextPixels}，
 * 设为 0 表示完全按密度采样、不做任何截断。</p>
 */
public final class FontRasterizer {

    private static final int PAD = 8;
    /** 栅格结果缓存条数上限：同一条歌词反复播放/多客户端复用，避免每次重新绘制位图。 */
    private static final int CACHE_SIZE = 48;

    private static final int[] MC_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA,
            0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private record CacheKey(String text, int baseColor, double density, float scale, int maxPixels) {
    }

    private static final Map<CacheKey, RasterResult> CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, RasterResult> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    private static Font font;

    static {
        try {
            font = findFont();
        } catch (Exception e) {
            font = new Font(Font.SANS_SERIF, Font.BOLD, 72);
        }
    }

    private FontRasterizer() {
    }

    private static Font findFont() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Font[] all = ge.getAllFonts();
        for (Font f : all) {
            if (f.canDisplayUpTo("歌词粒子测试MiauParticleEffects") == -1) {
                return f.deriveFont(Font.BOLD, 72f);
            }
        }
        for (Font f : all) {
            String name = f.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT);
            if (name.contains("sans") || name.contains("noto") || name.contains("cjk") || name.contains("hei")) {
                return f.deriveFont(Font.BOLD, 72f);
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, 72);
    }

    /**
     * 栅格化文字并采样（带缓存）。
     *
     * @param text      原始文字（含 § 颜色码）
     * @param baseColor 整体默认颜色 ARGB
     * @param density   粒子采样间距（方块），越小越清晰、粒子越多
     * @param scale     字形高度（方块）
     */
    public static RasterResult rasterize(String text, int baseColor, double density, float scale) {
        if (text == null || text.isBlank()) {
            return RasterResult.EMPTY;
        }
        int maxPixels = ConfigManager.get().maxTextPixels;
        CacheKey key = new CacheKey(text, baseColor, density, scale, maxPixels);
        synchronized (CACHE) {
            RasterResult cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        RasterResult result = rasterizeUncached(text, baseColor, density, scale, maxPixels);
        synchronized (CACHE) {
            CACHE.put(key, result);
        }
        return result;
    }

    private static RasterResult rasterizeUncached(String text, int baseColor, double density,
                                                  float scale, int maxPixels) {
        List<Segment> segments = parseSegments(text, baseColor);
        if (segments.isEmpty()) {
            return RasterResult.EMPTY;
        }

        // 逐字符测量：同时得到每个字符的像素区间，逐字动画要用。
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontMetrics pfm = pg.getFontMetrics();
        int ascent = pfm.getAscent();
        int textHeight = ascent + pfm.getDescent();
        List<int[]> charRanges = new ArrayList<>();
        int cursor = 0;
        for (Segment seg : segments) {
            int i = 0;
            while (i < seg.text.length()) {
                int cp = seg.text.codePointAt(i);
                String glyph = new String(Character.toChars(cp));
                int w = pfm.stringWidth(glyph);
                charRanges.add(new int[]{cursor, cursor + w});
                cursor += w;
                i += Character.charCount(cp);
            }
        }
        pg.dispose();
        int textWidth = Math.max(1, cursor);
        if (textHeight <= 0 || charRanges.isEmpty()) {
            return RasterResult.EMPTY;
        }

        int imgWidth = textWidth + PAD * 2;
        int imgHeight = textHeight + PAD * 2;
        BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        int baseline = PAD + ascent;
        int x = PAD;
        for (Segment seg : segments) {
            g.setColor(new Color(seg.color, true));
            g.drawString(seg.text, x, baseline);
            x += fm.stringWidth(seg.text);
        }
        g.dispose();

        float worldPerPx = scale / textHeight;
        double densityClamped = Math.max(0.01, Math.min(1.0, density));
        float quadSize = (float) densityClamped;
        int step = Math.max(1, (int) Math.round(densityClamped / worldPerPx));

        // 自适应 LOD：超过（可配置的）像素上限时自动增大采样间距，保证整段文字均匀采样，
        // 而不是行优先采样在超限后直接截断下半部分。maxPixels <= 0 表示不做任何限制。
        if (maxPixels > 0) {
            long cols = (textWidth + step - 1) / step;
            long rows = (textHeight + step - 1) / step;
            while (cols * rows > maxPixels && step < 128) {
                step++;
                cols = (textWidth + step - 1) / step;
                rows = (textHeight + step - 1) / step;
            }
        }

        float halfW = textWidth / 2f;
        float halfH = textHeight / 2f;
        List<ParticlePixel> pixels = new ArrayList<>();

        // 采样点取每个格子的中心，并把 PAD 偏移补偿掉，使文字几何中心正好落在 pos 上。
        int sampleOffset = step / 2;
        for (int py = 0; py < textHeight; py += step) {
            int iy = PAD + py + sampleOffset;
            if (iy >= imgHeight) {
                break;
            }
            int charCursor = 0;
            for (int px = 0; px < textWidth; px += step) {
                int ix = PAD + px + sampleOffset;
                if (ix >= imgWidth) {
                    break;
                }
                int argb = img.getRGB(ix, iy);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha < 40) {
                    continue;
                }
                // 像素所属字符：字符区间按 x 递增，顺着列前进即可（列是递增的）。
                while (charCursor + 1 < charRanges.size()
                        && px >= charRanges.get(charCursor)[1]) {
                    charCursor++;
                }
                float localX = (px + sampleOffset - halfW) * worldPerPx;
                float localY = (halfH - (py + sampleOffset)) * worldPerPx;
                pixels.add(new ParticlePixel(localX, localY, argb, charCursor));
                if (maxPixels > 0 && pixels.size() >= maxPixels) {
                    return new RasterResult(List.copyOf(pixels), quadSize, charRanges.size());
                }
            }
        }

        return new RasterResult(List.copyOf(pixels), quadSize, charRanges.size());
    }

    private static List<Segment> parseSegments(String text, int baseColor) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentColor = baseColor;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'r') {
                    if (!current.isEmpty()) {
                        segments.add(new Segment(current.toString(), currentColor));
                        current.setLength(0);
                    }
                    currentColor = baseColor;
                    i++;
                } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    if (!current.isEmpty()) {
                        segments.add(new Segment(current.toString(), currentColor));
                        current.setLength(0);
                    }
                    int idx = code >= 'a' ? (code - 'a' + 10) : (code - '0');
                    currentColor = 0xFF000000 | MC_COLORS[idx];
                    i++;
                } else {
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            segments.add(new Segment(current.toString(), currentColor));
        }
        return segments;
    }

    private record Segment(String text, int color) {
    }

    /**
     * 一个采样点。
     *
     * @param localX    相对文字中心的水平偏移（方块）
     * @param localY    相对文字中心的垂直偏移（方块）
     * @param colorArgb 该像素的颜色（含 § 颜色码解析结果与抗锯齿 alpha）
     * @param charIndex 该像素属于第几个字符（从 0 开始），逐字动画用
     */
    public record ParticlePixel(float localX, float localY, int colorArgb, int charIndex) {
    }

    public record RasterResult(List<ParticlePixel> pixels, float quadSize, int charCount) {

        public static final RasterResult EMPTY = new RasterResult(List.of(), 0f, 0);
    }
}
