package org.miau.particleeffects.exparticle.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * 图片读取与缩放缓存（移植自 ExParticle 的 ImageUtil）。图片目录为 {@code ./particleImages}。
 */
public final class ParticleExImageUtil {

    private static final Map<File, Map<Double, BufferedImage>> IMAGE_BUF = new HashMap<>();
    private static final File IMAGE_DIR = new File("./particleImages");

    static {
        if (IMAGE_DIR.exists() && IMAGE_DIR.isFile()) {
            IMAGE_DIR.delete();
        }
        if (!IMAGE_DIR.exists()) {
            IMAGE_DIR.mkdirs();
        }
    }

    private ParticleExImageUtil() {
    }

    public static BufferedImage readImage(String path, double scaling, boolean buffered) throws IOException {
        File imageFile = new File(IMAGE_DIR, path);
        BufferedImage result;
        if (buffered) {
            Map<Double, BufferedImage> cache = IMAGE_BUF.get(imageFile);
            if (cache == null) {
                cache = new HashMap<>();
                BufferedImage original = ImageIO.read(imageFile);
                cache.put(1.0, original);
                if (scaling != 1.0) {
                    cache.put(scaling, resize(original, scaling));
                }
                IMAGE_BUF.put(imageFile, cache);
            }
            result = cache.get(scaling);
            if (result == null) {
                result = resize(cache.get(1.0), scaling);
                cache.put(scaling, result);
            }
        } else {
            result = resize(ImageIO.read(imageFile), scaling);
        }
        return result;
    }

    private static BufferedImage resize(BufferedImage source, double scaling) {
        int width = source.getWidth();
        int height = source.getHeight();
        int targetWidth = (int) (width * scaling);
        int targetHeight = (int) (height * scaling);
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, source.getType());
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, 0, 0, width, height, null);
        graphics.dispose();
        return result;
    }

    /** 列出图片目录下的所有文件名，供命令补全使用。 */
    public static String[] getImages() {
        File[] list = IMAGE_DIR.listFiles(file -> file.isFile());
        if (list == null) {
            return new String[0];
        }
        String[] names = new String[list.length];
        for (int i = 0; i < list.length; i++) {
            names[i] = list[i].getName();
        }
        return names;
    }

    public static void clear() {
        IMAGE_BUF.clear();
    }
}
