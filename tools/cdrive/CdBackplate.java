// Run (helpers live in tools/common/Backplate.java, so compile both):
//   javac -d /tmp/bp tools/common/Backplate.java tools/cdrive/CdBackplate.java
//   java -cp /tmp/bp CdBackplate tools/cdrive/cdrive_mockup_2560x1208.png app/src/main/res/drawable-nodpi
// then tools/webp.sh. Coordinates are mockup pixels; tools/cdrive/LAYOUT.md lists the live areas left for the app.
import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.File;

/**
 * Builds the C-Drive stack's backplate from the owner's mockup. Order of work:
 * 1. cut-outs, taken from the untouched mockup (lit key faces, ECO leaf, lamps);
 * 2. erasures of everything the app draws live (they use mockup coordinates);
 * 3. the owner's edits (logo, labels, arrows, badge);
 * 4. moving the deck/data frames to one height, last, so it carries already-clean frames.
 */
public class CdBackplate extends Backplate {
  // what is printed (icon and word) on the lit bottom key Машина, and on the lit period key День
  static final int[] TAB_CONTENT={896,1016,1114,1114};      // x0,y0,x1,y1 on the Машина key
  static final int[] DAY_CONTENT={80,646,232,688};          // on the День key
  static final int PERIOD_SHIFT=156;                         // Месяц sits this far below День

  public static void main(String[] a) throws Exception {
    BufferedImage img=copy(ImageIO.read(new File(a[0]))); String out=a[1];
    BufferedImage orig=copy(img);

    // ================= 1. cut-outs from the lit originals
    BufferedImage leaf=new BufferedImage(45,41,BufferedImage.TYPE_INT_ARGB);   // ECO's leaf, alpha from its brightness
    for(int y=0;y<41;y++)for(int x=0;x<45;x++){ int c=orig.getRGB(2158+x,44+y); int al=cl((lum(c)-55)*255/130.0); leaf.setRGB(x,y,(al<<24)|(c&0xFFFFFF)); }
    save(leaf,out+"/cd_leaf.png");
    save(crop(orig,953,978,1054,1005),out+"/cd_led_green.png");   // Машина's lamp, centred on x 1003 (101 x 27)
    save(crop(orig,1219,978,1320,1005),out+"/cd_led_amber.png");  // Поездки's lamp, centred on x 1269
    BufferedImage litFaces=copy(orig);                              // lit faces with their icon and word removed
    fill(litFaces,TAB_CONTENT[0],TAB_CONTENT[1],TAB_CONTENT[2],TAB_CONTENT[3],null,0);
    fill(litFaces,DAY_CONTENT[0],DAY_CONTENT[1],DAY_CONTENT[2],DAY_CONTENT[3],null,0);

    // ================= 2. erase what the app draws
    // ---- header: mode word and leaf, gear, READY
    fill(img,2156,40,2270,88,null,0);
    fill(img,2320,40,2368,88,null,0);
    fill(img,2404,40,2512,88,null,0);
    // ---- deck display: title line, big time, PLAYING, segmented progress bar (the thin rule above it stays)
    fill(img,470,225,930,280,null,0);
    fill(img,495,281,672,342,null,0);
    fill(img,730,293,890,332,null,0);
    fill(img,338,358,1030,391,null,0);
    // ---- car data screen: values and their bars (labels and icons stay)
    int[][] info={{1226,214,1312,250},{1152,255,1356,279},{1482,214,1568,250},{1410,255,1612,279},{1672,228,1810,272},
      {1957,244,2045,276},{1237,332,1440,374},{1603,333,1735,373},{1918,332,2012,373},
      {1262,437,1458,482},{1745,437,1916,482}};
    for(int[] r:info) fillMed(img,r[0],r[1],r[2],r[3]);
    fillMed(img,1957,203,2026,218);            // the EV range: short of the (i) and its glow,
    fillMed(img,1957,219,2040,233);            // wider below it
    // ---- volume knob: the marker, refilled along the knob's own circles
    angFill(img,2315,329,46,105,10,34);
    // ---- meters: needles with their glow, hubs, big values
    meterNeedle(img,1583,723,1683.5,801.5);
    meterNeedle(img,2122,692,2183.5,801.5);
    fill(img,1600,829,1803,870,null,0);        // 1 760 об/мин (RPM stays)
    fill(img,2126,835,2252,877,null,0);        // 1.2 кВт
    // ---- chart: grid, curves, fill, axis numbers and times; legend and unit titles stay
    fill(img,296,704,1373,900,null,0);
    // ---- period keys: День becomes a plain key like Месяц, its own icon and word kept
    blit(orig,44,783,263,863,img,44,783-PERIOD_SHIFT,263,863-PERIOD_SHIFT);
    softenRecess(orig,img);
    fill(img,DAY_CONTENT[0],DAY_CONTENT[1],DAY_CONTENT[2],DAY_CONTENT[3],null,0);
    BufferedImage plainDay=copy(img);
    paintGlyphs(orig,img,DAY_CONTENT);
    degreen(img,40,606,266,626); degreen(img,40,707,266,716);   // the green glow around the old lit key
    // ---- bottom keys: Машина becomes a plain key like Поездки; all five lamps amber, the app lights the current one
    blit(orig,1134,1004,1400,1134,img,876,1004,1134,1134);
    fill(img,TAB_CONTENT[0],TAB_CONTENT[1],TAB_CONTENT[2],TAB_CONTENT[3],null,0);
    BufferedImage plainTab=copy(img);
    paintGlyphs(orig,img,TAB_CONTENT);
    blit(orig,1219,978,1320,1005,img,953,978,1054,1005);    // Машина's lamp
    blit(orig,1219,978,1320,1005,img,2018,978,2119,1005);   // Настройки's lamp
    // lit faces as overlays: drawn over any plain key of their row they give it the lit look (clear where nothing changes)
    save(overlay(litFaces,plainTab,872,1002,1137,1134),out+"/cd_key_lit.png");
    save(overlay(litFaces,plainDay,46,614,261,710),out+"/cd_period_lit.png");
    // the printed icons and words alone, to lay back over a lit face so they stay white
    // icons and words apart: on the lit key the app tints the icon (green calendar, pale-green car) and keeps the word white
    int[][] tabX={{896,1114},{1154,1380},{1420,1646},{1686,1917},{1958,2169}};
    save(glyphs(img,rows(tabX,1016,1071),882,1016,2183,1114),out+"/cd_tab_icons.png");
    save(glyphs(img,rows(tabX,1072,1114),882,1016,2183,1114),out+"/cd_tab_words.png");
    int[][] perY={{640,694},{720,772},{797,849}};
    save(glyphs(img,cols(perY,80,137),80,640,232,849),out+"/cd_period_icons.png");
    save(glyphs(img,cols(perY,138,232),80,640,232,849),out+"/cd_period_words.png");

    // ================= 3. owner's edits (Task 2)
    // ================= 4. deck and data frames to one height (Task 2)

    save(img,out+"/cd_base.png");
  }

  /** A needle from [tx,ty] into its hub at [px,py]: the blade, its green glow on the lower side, and the hub,
   *  repainted with the meter screen's own background nearby. White printed numbers next to the blade are kept. */
  static void meterNeedle(BufferedImage im,double tx,double ty,double px,double py){
    int x0=(int)Math.min(tx,px)-34,x1=(int)Math.max(tx,px)+30,y0=(int)Math.min(ty,py)-30,y1=(int)Math.max(ty,py)+30;
    double dx=px-tx,dy=py-ty,len2=dx*dx+dy*dy; boolean[][] m=new boolean[y1-y0+1][x1-x0+1];
    for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
      double t=((x-tx)*dx+(y-ty)*dy)/len2, tc=Math.max(0,Math.min(1,t)); double d=Math.hypot(tx+dx*tc-x,ty+dy*tc-y);
      int c=im.getRGB(x,y); boolean tint=g(c)-Math.max(r(c),b(c))>=3;
      boolean blade=d<=6.5, glow=t>0.04&&t<1.05&&d<=30&&tint, hub=Math.hypot(x-px,(y-py)*0.85-3)<=27;
      m[y-y0][x-x0]=blade||glow||hub; }
    m=dil(m,1);
    for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
      double t=Math.max(0,Math.min(1,((x-tx)*dx+(y-ty)*dy)/len2)); boolean core=Math.hypot(tx+dx*t-x,ty+dy*t-y)<=4||Math.hypot(x-px,y-py)<=30;
      int c=im.getRGB(x,y); if(!core&&lum(c)>90&&sat(c)<25) m[y-y0][x-x0]=false; }
    bgFill(im,x0,y0,m,22);
  }
  /** Like fill(): each row of the rect redrawn between its two sides, each side the median of the three pixels beyond it. */
  static void fillMed(BufferedImage im,int x0,int y0,int x1,int y1){
    for(int y=y0;y<=y1;y++){ int L=med(im,x0-3,y),R=med(im,x1+1,y); int n=x1-x0+2;
      for(int x=x0;x<=x1;x++){ double t=(x-x0+1)/(double)n; im.setRGB(x,y,rgb(r(L)+(r(R)-r(L))*t,g(L)+(g(R)-g(L))*t,b(L)+(b(R)-b(L))*t)); } } }
  static int med(BufferedImage im,int x,int y){ int[] c={im.getRGB(x,y),im.getRGB(x+1,y),im.getRGB(x+2,y)}; return rgb(m3(r(c[0]),r(c[1]),r(c[2])),m3(g(c[0]),g(c[1]),g(c[2])),m3(b(c[0]),b(c[1]),b(c[2]))); }
  static int m3(int a,int b,int c){return Math.max(Math.min(a,b),Math.min(Math.max(a,b),c));}
  static int sat(int c){return Math.max(r(c),Math.max(g(c),b(c)))-Math.min(r(c),Math.min(g(c),b(c)));}
  /** Masked pixels take the mean of the plain screen background (dark, untinted, unmasked) within [rad] of them. */
  static void bgFill(BufferedImage im,int x0,int y0,boolean[][] m,int rad){
    BufferedImage src=copy(im); int h=m.length,w=m[0].length;
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){ if(!m[y][x])continue; double sr=0,sg=0,sb=0; int n=0;
      for(int yy=y-rad;yy<=y+rad;yy++)for(int xx=x-rad;xx<=x+rad;xx++){ boolean in=yy>=0&&yy<h&&xx>=0&&xx<w; if(in&&m[yy][xx])continue;
        int c=src.getRGB(x0+xx,y0+yy); int l=lum(c); if(l<8||l>35||Math.abs(g(c)-Math.max(r(c),b(c)))>=3)continue; sr+=r(c);sg+=g(c);sb+=b(c);n++; }
      if(n>0) im.setRGB(x0+x,y0+y,rgb(sr/n,sg/n,sb/n)); } }
  /** The bright strokes and dark outlines of [src]'s printed icon and word, laid over the clean face in [dst]. */
  static void paintGlyphs(BufferedImage src,BufferedImage dst,int[] r){
    for(int y=r[1];y<=r[3];y++)for(int x=r[0];x<=r[2];x++){ int c=src.getRGB(x,y),f=dst.getRGB(x,y);
      double s=Math.max(0,Math.min(1,(Math.max(r(c),Math.max(g(c),b(c)))-90)/140.0)), k=Math.max(0,Math.min(1,(22-lum(c))/22.0))*0.85;
      double R=r(f)*(1-k),G=g(f)*(1-k),B=b(f)*(1-k);
      dst.setRGB(x,y,rgb(R+(250-R)*s,G+(250-G)*s,B+(247-B)*s)); } }
  /** Green-tinted pixels (a glow over grey metal) back to grey. */
  static void degreen(BufferedImage im,int x0,int y0,int x1,int y1){
    for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){ int c=im.getRGB(x,y); if(g(c)-Math.max(r(c),b(c))<3)continue; int v=Math.min(r(c),Math.min(g(c),b(c))); im.setRGB(x,y,rgb(v,v,v)); } }
  /** The dark recess copied above the new День key (from the gap over Месяц) starts square at y 627: fade it in from the
   *  metal above with a vertical ramp and rounded corners, the metal taken 12 rows higher and cleared of the old green glow. */
  static void softenRecess(BufferedImage orig,BufferedImage im){
    double rx0=63,rx1=243,ry0=629,rad=7;
    for(int y=627;y<=636;y++)for(int x=44;x<=263;x++){
      double cx=Math.max(rx0+rad,Math.min(rx1-rad,x)), cy=Math.max(ry0+rad,y);            // nearest centre of the rounded top
      double d=(x>=rx0+rad&&x<=rx1-rad)? ry0-y : (y>=ry0+rad? Math.max(rx0-x,x-rx1) : Math.hypot(x-cx,y-cy)-rad);
      double w=Math.max(0,Math.min(1,0.5-d/2.5));                                          // 1 inside, soft 2.5 px edge
      int t=orig.getRGB(x,y-12); int v=Math.min(r(t),Math.min(g(t),b(t))); int c=im.getRGB(x,y);
      double k=w; im.setRGB(x,y,rgb(r(c)*k+v*(1-k),g(c)*k+v*(1-k),b(c)*k+v*(1-k))); } }
  static int[][] rows(int[][] xs,int y0,int y1){ int[][] o=new int[xs.length][]; for(int i=0;i<xs.length;i++) o[i]=new int[]{xs[i][0],y0,xs[i][1],y1}; return o; }
  static int[][] cols(int[][] ys,int x0,int x1){ int[][] o=new int[ys.length][]; for(int i=0;i<ys.length;i++) o[i]=new int[]{x0,ys[i][0],x1,ys[i][1]}; return o; }
  /** The bright strokes inside [rects] (and their dark outlines) as an ARGB layer over [x0,y0]-[x1,y1]; clear elsewhere. */
  static BufferedImage glyphs(BufferedImage im,int[][] rects,int x0,int y0,int x1,int y1){
    int w=x1-x0+1,h=y1-y0+1; boolean[][] br=new boolean[h][w];
    for(int[] r:rects)for(int y=r[1];y<=r[3];y++)for(int x=r[0];x<=r[2];x++) br[y-y0][x-x0]=lum(im.getRGB(x,y))>110;
    boolean[][] near=dil(br,3); BufferedImage o=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){ if(!near[y][x])continue; int c=im.getRGB(x0+x,y0+y),l=lum(c);
      if(l>110) o.setRGB(x,y,(cl((l-110)*255/90.0)<<24)|(c&0xFFFFFF));
      else if(l<20) o.setRGB(x,y,cl((20-l)*0.9*255/20.0)<<24); }
    return o; }
  /** The least-opaque layer that turns [plain] into [lit] over the rect (ARGB): where they match it is clear. */
  static BufferedImage overlay(BufferedImage lit,BufferedImage plain,int x0,int y0,int x1,int y1){
    BufferedImage o=new BufferedImage(x1-x0+1,y1-y0+1,BufferedImage.TYPE_INT_ARGB);
    for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){ int L=lit.getRGB(x,y),U=plain.getRGB(x,y); int[] l={r(L),g(L),b(L)},u={r(U),g(U),b(U)};
      double al=0; for(int i=0;i<3;i++){ double need= l[i]>u[i] ? (l[i]-u[i])/(255.0-u[i]) : l[i]<u[i] ? (u[i]-l[i])/(double)u[i] : 0; al=Math.max(al,need); }
      int A=(int)Math.ceil(al*255); if(A<=0){o.setRGB(x-x0,y-y0,0);continue;} double aa=A/255.0;
      o.setRGB(x-x0,y-y0,(A<<24)|(cl(u[0]+(l[0]-u[0])/aa)<<16)|(cl(u[1]+(l[1]-u[1])/aa)<<8)|cl(u[2]+(l[2]-u[2])/aa)); }
    return o; }
}
