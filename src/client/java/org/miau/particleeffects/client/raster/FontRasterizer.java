package org.miau.particleeffects.client.raster;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文字栅格化器：把文字（含 § 颜色码）渲染到离屏位图，然后按密度采样为粒子坐标列表。 *
 * 每个 ParticlePixel 的局部坐。(x, y) 单位为方块（世界空间），
 * 以文字中心为原点，X 水平向右，Y 垂直向上。 */
public final class FontRasterizer {

    private static final int MAX_PIXELS = 8000;
    private static final int[] MC_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA,
            0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
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
     * 栅格化文字并采样。     *
     * @param text      原始文字（含 § 代码。     * @param baseColor 整体默认颜色 ARGB
     * @param density   粒子采样间距（方块），越小越清晰
     * @param scale     字形高度（方块）
     * @return 采样结果（局部坐标，单位方块。     */
    public static RasterResult rasterize(String text, int baseColor, double density, float scale) {
        if (text == null || text.isBlank()) {
            return new RasterResult(List.of(), 0f);
        }
        List<Segment> segments = parseSegments(text, baseColor);
        if (segments.isEmpty()) {
            return new RasterResult(List.of(), 0f);
        }

        // 先测量总宽度，再按实际尺寸建图，避免长文字溢出画布导致越界异常。
        int pad = 8;
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontMetrics pfm = pg.getFontMetrics();
        int ascent = pfm.getAscent();
        int textHeight = ascent + pfm.getDescent();
        int measuredWidth = 4;
        for (Segment seg : segments) {
            measuredWidth += pfm.stringWidth(seg.text);
        }
        pg.dispose();
        if (textHeight <= 0) {
            return new RasterResult(List.of(), 0f);
        }

        int textWidth = Math.max(1, measuredWidth);
        int imgWidth = textWidth + pad;
        int imgHeight = textHeight + pad;
        BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        int baseline = ascent + pad / 2;
        int x = 4;
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

        // 自适应 LOD：当整段文字按当前间距采样会超过像素上限时，自动增大采样间距。
        // 保证整段文字都被均匀采样（而不是行优先采样在超限后直接截断下半部分）。
        long cols = (textWidth + step - 1) / step;
        long rows = (textHeight + step - 1) / step;
        while (cols * rows > MAX_PIXELS && step < 64) {
            step++;
            cols = (textWidth + step - 1) / step;
            rows = (textHeight + step - 1) / step;
        }

        int halfW = textWidth / 2;
        int halfH = textHeight / 2;
        List<ParticlePixel> pixels = new ArrayList<>();

        for (int py = 0; py < textHeight && pixels.size() < MAX_PIXELS; py += step) {
            for (int px = 0; px < textWidth; px += step) {
                int argb = img.getRGB(px, py);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha < 40) {
                    continue;
                }
                float localX = (px - halfW) * worldPerPx;
                float localY = (halfH - py) * worldPerPx;
                pixels.add(new ParticlePixel(localX, localY, argb));
                if (pixels.size() >= MAX_PIXELS) {
                    break;
                }
            }
        }

        return new RasterResult(pixels, quadSize);
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

    public record ParticlePixel(float localX, float localY, int colorArgb) {
    }

    public record RasterResult(List<ParticlePixel> pixels, float quadSize) {
    }
}