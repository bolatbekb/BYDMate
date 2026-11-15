import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.*;

/** Fails unless a PNG and a PAM (RGBA, as dwebp writes it) hold the same pixels, alpha included. */
public class SamePixels {
  public static void main(String[] a) throws Exception {
    BufferedImage png = ImageIO.read(new File(a[0]));
    DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(a[1])));
    int w = 0, h = 0, depth = 0; String line;
    while (!(line = readLine(in)).equals("ENDHDR")) {
      String[] t = line.split(" ");
      if (t[0].equals("WIDTH")) w = Integer.parseInt(t[1]);
      if (t[0].equals("HEIGHT")) h = Integer.parseInt(t[1]);
      if (t[0].equals("DEPTH")) depth = Integer.parseInt(t[1]);
    }
    if (w != png.getWidth() || h != png.getHeight()) throw new IllegalStateException(a[0] + ": size differs");
    boolean alpha = png.getColorModel().hasAlpha();
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
      int r = in.read(), g = in.read(), b = in.read(), al = depth == 4 ? in.read() : 255;
      int p = png.getRGB(x, y);
      int pa = alpha ? (p >>> 24) : 255;
      // a fully transparent pixel has no colour to keep
      boolean same = pa == al && (pa == 0 || (((p >> 16) & 255) == r && ((p >> 8) & 255) == g && (p & 255) == b));
      if (!same) throw new IllegalStateException(a[0] + ": pixel " + x + "," + y + " differs");
    }
  }
  static String readLine(DataInputStream in) throws IOException {
    StringBuilder sb = new StringBuilder(); int c;
    while ((c = in.read()) != '\n') sb.append((char) c);
    return sb.toString().trim();
  }
}
