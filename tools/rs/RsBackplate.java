// Run (helpers live in tools/common/Backplate.java, so compile both):
//   javac -d /tmp/bp tools/common/Backplate.java tools/rs/RsBackplate.java
//   java -cp /tmp/bp RsBackplate tools/rs/BYDMATE_Hybrid_2560x1208.png app/src/main/res/drawable-nodpi
// then tools/webp.sh
import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.File;

/** Builds the RS stack's backplate from the mockup: erases what the app draws live, extracts lit faces and reels. */
public class RsBackplate extends Backplate {
  static BufferedImage img;
  public static void main(String[] a) throws Exception {
    img = ImageIO.read(new File(a[0])); img=copy(img); String out=a[1];
    BufferedImage orig=copy(img);
    // ---- assets taken from the lit originals
    BufferedImage lit=copy(orig);
    fill(lit,2140,26,2298,74,null,0);            // NORMAL text off the amber face
    save(crop(lit,2100,4,2331,97),out+"/rs_lit_mode.png");
    fill(lit,734,454,790,506,null,0);            // play triangle off the green face
    save(crop(lit,692,442,823,519),out+"/rs_lit_play.png");
    BufferedImage leaf=new BufferedImage(71,57,BufferedImage.TYPE_INT_ARGB);   // ECO's leaf, alpha from its brightness
    for(int y=0;y<57;y++)for(int x=0;x<71;x++){ int c=orig.getRGB(1895+x,22+y); int al=cl((lum(c)-55)*255/130.0); leaf.setRGB(x,y,(al<<24)|(c&0xFFFFFF)); }
    save(leaf,out+"/rs_leaf.png");
    reel(orig,536,343,42,out+"/rs_reel_l.png");
    reel(orig,829,343,42,out+"/rs_reel_r.png");

    // ---- header: all three mode keys unlit, their words drawn by the app
    blit(orig,2322,0,2541,104,img,2098,0,2331,104);     // SPORT key (and its plate) over the lit NORMAL
    fill(img,1982,28,2064,72,null,0);          // ECO
    fill(img,2140,28,2298,72,null,0);          // the copied SPORT word
    fill(img,2380,28,2500,72,null,0);          // SPORT
    // ---- deck: track display, play key
    fill(img,412,186,1033,262,null,0);
    blit(orig,560,436,691,528,img,692,436,823,528);     // pause key over the lit play key
    fill(img,736,456,788,504,null,0);          // its pause glyph
    // ---- car data screen
    int[][] info={{1222,182,1318,230},{1143,233,1368,263},{1505,186,1600,230},{1430,233,1638,263},{1698,198,1888,266},
      {2006,186,2098,222},{2006,226,2114,263},{1241,320,1474,374},{1638,320,1788,374},{1978,320,2097,374},{1286,446,1514,504},{1766,446,1966,504}};
    for(int[] r:info) fill(img,r[0],r[1],r[2],r[3],null,0);
    // ---- volume marker
    angFill(img,2367,306,20,104,4,46);
    // ---- meters: needles, values, statuses, compressor numbers
    needle(img,706,742,789,849,15,8);                    // the wide lower blade, below the scale
    needleOnArc(orig,img,664,670,712,742,8,3,855,1080,402,70,2.8);  // its thin tip across the scale
    needleOnArc(orig,img,1957,680,1978,836,12,8,2000,1128,438,40,3.4);
    needle(img,691,728,782,841,17,0);
    needle(img,1955,718,1975,834,14,0);
    fill(img,775,792,985,868,null,0);
    fill(img,1922,822,2118,897,null,0);
    fill(img,1310,706,1452,752,null,0);
    fill(img,1310,864,1454,910,null,0);
    int[][] kw={{1604,758,1663,793},{1670,703,1730,746},{1760,660,1820,703},{1862,638,1922,681},{1992,628,2052,671},{2100,638,2160,681},{2190,665,2250,708},{2280,708,2340,751},{2350,755,2410,798},{2385,811,2445,854}};
    for(int[] r:kw) fill(img,r[0],r[1],r[2],r[3],null,0);
    fill(img,1604,793,1655,799,null,0);                 // the foot of "0.0", short of the end tick
    // ---- bottom: the lit car key becomes a plain key with the same icon and word
    blit(orig,1136,1017,1392,1165,img,866,1017,1129,1165);
    fill(img,890,1033,1110,1152,null,0);
    for(int y=1033;y<=1152;y++)for(int x=890;x<=1110;x++){ int c=orig.getRGB(x,y); double s=Math.max(0,Math.min(1,(r(c)-70)/150.0)); if(s<=0)continue;
      int f=img.getRGB(x,y); img.setRGB(x,y,rgb(r(f)+(236-r(f))*s,g(f)+(232-g(f))*s,b(f)+(222-b(f))*s)); }
    save(img,out+"/rs_base.png");
  }
}
