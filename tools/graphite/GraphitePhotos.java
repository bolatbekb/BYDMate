import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Crops the Graphite gallery photos to 920x440 PNG ("cover" scale + CSS object-position).
 * Usage: java GraphitePhotos.java SRC_DIR OUT_DIR   (SRC_DIR holds leopard_<name>.jpg)
 * Writes OUT_DIR/gr_photo_<name>.png; photos.sh turns them into WebP.
 */
public class GraphitePhotos {
    static final int W = 920, H = 440;
    // name, object-position x%, y% (SPEC.md)
    static final Object[][] PHOTOS = {
        {"hill", 52, 62}, {"desert", 68, 50}, {"front", 50, 48}, {"side_night", 50, 46}, {"lights", 50, 50},
    };

    static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    static BufferedImage scaleCover(BufferedImage src, int sw, int sh) {
        BufferedImage cur = src;
        // halve while the remaining shrink is more than 2x, so bicubic does not alias
        while (cur.getWidth() / 2 >= sw && cur.getHeight() / 2 >= sh) {
            cur = resize(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        }
        return resize(cur, sw, sh);
    }

    public static void main(String[] a) throws Exception {
        File srcDir = new File(a[0]), outDir = new File(a[1]);
        outDir.mkdirs();
        for (Object[] p : PHOTOS) {
            String name = (String) p[0];
            int px = (Integer) p[1], py = (Integer) p[2];
            BufferedImage src = ImageIO.read(new File(srcDir, "leopard_" + name + ".jpg"));
            double k = Math.max((double) W / src.getWidth(), (double) H / src.getHeight());
            int sw = Math.max(W, (int) Math.round(src.getWidth() * k));
            int sh = Math.max(H, (int) Math.round(src.getHeight() * k));
            BufferedImage scaled = scaleCover(src, sw, sh);
            int ox = (int) Math.round((sw - W) * px / 100.0);
            int oy = (int) Math.round((sh - H) * py / 100.0);
            BufferedImage crop = scaled.getSubimage(ox, oy, W, H);
            ImageIO.write(crop, "png", new File(outDir, "gr_photo_" + name + ".png"));
            System.out.printf("%s: %dx%d -> %dx%d offset %d,%d%n", name, src.getWidth(), src.getHeight(), sw, sh, ox, oy);
        }
    }
}
