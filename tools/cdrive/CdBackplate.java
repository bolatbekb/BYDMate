// Run (helpers live in tools/common/Backplate.java, so compile both):
//   javac -d /tmp/bp tools/common/Backplate.java tools/cdrive/CdBackplate.java
//   java -cp /tmp/bp CdBackplate tools/cdrive/cdrive_mockup_2560x1208.png app/src/main/res/drawable-nodpi
// then tools/webp.sh. Coordinates are mockup pixels; tools/cdrive/LAYOUT.md lists the live areas left for the app.
// Inputs next to the mockup: fonts/ (Noticia Text Bold for BYDMATE, Open Sans SemiBold for the labels; both SIL OFL,
// licences beside them) and fangchengbao_logo.png (https://upload.wikimedia.org/wikipedia/en/a/a5/Fangchengbao_logo.png).
import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.File;
import java.awt.*; import java.awt.font.*; import java.awt.geom.*; import java.util.Map;

/**
 * Builds the C-Drive stack's backplate from the owner's mockup. Order of work:
 * 1. cut-outs, taken from the untouched mockup (lit key faces, ECO leaf, lamps);
 * 2. erasures of everything the app draws live (they use mockup coordinates);
 * 3. the owner's edits (BYDMATE, Russian labels, the meters' arrows, the plain ▶ key, the badge);
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

    // ================= 3. owner's edits
    DIR=new File(a[0]).getAbsoluteFile().getParentFile();
    // ---- BYDMATE in gold for «Technics», top left and by the chart's title: same cap height, baseline and width
    texFill(img,236,17,490,63,188,552);
    logo(img,242,24.5,55,237);
    texFill(img,62,571,250,609,25,284);
    logo(img,68,579,602,172);
    // ---- «Stereo Car Deck RS-C900» over the deck
    texFill(img,273,148,583,175,250,1080);
    // ---- left keys: CAR / TRIPS / SETTINGS become Статистика (bars) / Поездки (route) / Система (the gear stays)
    texFill(img,112,190,170,227,78,201); texFill(img,116,232,166,256,78,201);   // CAR's car and word
    texFill(img,116,306,166,350,78,201); texFill(img,108,354,172,380,78,201);   // TRIPS's bars and word
    texFill(img,92,475,192,499,78,201);                                          // SETTINGS's word
    paintIcon(orig,new int[]{116,306,166,350},img,140.5,208.5,1);                 // TRIPS's bars onto the first key
    paintIcon(orig,new int[]{1243,1019,1305,1067},img,140.5,327.5,0.8);           // the bottom Поездки key's route
    Font keys=fit(capFont(SANS,16),"Статистика",106);                              // one size for all three, the longest fitting
    label(img,keys,"Статистика",140.5,252);
    label(img,keys,"Поездки",140.5,375);
    label(img,keys,"Система",140.5,495);
    // ---- «громкость» and «phones» capitalised in Russian
    texFill(img,2259,145,2373,171,2130,2520);
    label(img,xFont(SANS,12),"Громкость",2315.5,163);
    texFill(img,89,978,169,1008,30,220);
    label(img,xFont(SANS,13),"Наушники",129,1000);
    // ---- the meters' ◀ ▶ keys, back to the screen's glass
    fillMed(img,1404,719,1472,806); fillMed(img,2399,719,2467,806);   // to 806: the keys' faint drop shadow too
    // ---- the deck's green ▶ becomes a plain key like its neighbours (the app lays cd_play_lit over it while playing)
    playKey(orig,img);
    save(crop(orig,693,433,824,499),out+"/cd_play_lit.png");      // 131 x 66, lands at 693, 423
    // ---- «class AA» gives way to the Fangchengbao badge, the plate kept as its base
    texFill(img,2313,1029,2466,1076,2305,2478);
    badge(img,ImageIO.read(new File(DIR,"fangchengbao_logo.png")),2391.5,1052.5,128);

    // ================= 4. deck and data frames to one height
    alignDeck(img);
    System.out.printf("frame edges (brightest rows): deck top %d bottom %d, data top %d bottom %d%n",
      peak(img,700,160,200),peak(img,700,490,530),peak(img,1600,160,200),peak(img,1600,490,530));

    save(img,out+"/cd_base.png");
  }

  // ================= the owner's edits, step by step

  static final String SERIF="NoticiaText-Bold.ttf", SANS="OpenSans-SemiBold.ttf";
  static final double BOLDER=0.4;                     // the logo's strokes thickened by this much each side, as heavy as «Technics»
  /** «BYDMATE» in gold, its ink from [x] and [width] wide, capitals from [top] to the baseline [base]. */
  static void logo(BufferedImage im,double x,double top,double base,double width)throws Exception{
    Font f=capFont(SERIF,base-top); Rectangle2D b=inkOf(f,"BYDMATE"); double tr=(width-b.getWidth())/6;
    goldText(im,grow(text(f,"BYDMATE",x-b.getX(),base,tr),BOLDER),top,base); }
  /** A font sized so its x is [xh] pixels tall. */
  static Font xFont(String file,double xh)throws Exception{ Font f=font(file,100); return f.deriveFont((float)(100*xh/inkOf(f,"x").getHeight())); }
  /** [f], made smaller if [s] would be wider than [max] pixels. */
  static Font fit(Font f,String s,double max){ double w=inkOf(f,s).getWidth(); return w<=max? f : f.deriveFont((float)(f.getSize2D()*max/w)); }
  static void label(BufferedImage im,Font f,String s,double cx,double base){ label(im,centred(f,s,cx,base,0)); }
  /** A printed key icon from [src] (rect x0,y0,x1,y1) laid on [dst] scaled by [sc], its rect's centre at cx,cy: the bright
   *  strokes and the dark outline carried over (as paintGlyphs does), the key face beneath kept. */
  static void paintIcon(BufferedImage src,int[] r,BufferedImage dst,double cx,double cy,double sc){
    double scx=(r[0]+r[2])/2.0, scy=(r[1]+r[3])/2.0, hw=(r[2]-r[0])/2.0*sc, hh=(r[3]-r[1])/2.0*sc;
    for(int y=(int)Math.floor(cy-hh);y<=(int)Math.ceil(cy+hh);y++)for(int x=(int)Math.floor(cx-hw);x<=(int)Math.ceil(cx+hw);x++){
      double sx=scx+(x-cx)/sc, sy=scy+(y-cy)/sc; if(sx<r[0]||sx>r[2]||sy<r[1]||sy>r[3])continue;
      int c=bil(src,sx,sy),f=dst.getRGB(x,y);
      double s=Math.max(0,Math.min(1,(Math.max(r(c),Math.max(g(c),b(c)))-90)/140.0)), k=Math.max(0,Math.min(1,(22-lum(c))/22.0))*0.85;
      double R=r(f)*(1-k),G=g(f)*(1-k),B=b(f)*(1-k);
      dst.setRGB(x,y,rgb(R+(250-R)*s,G+(250-G)*s,B+(247-B)*s)); } }

  /** The ▶ transport key, printed lit green, made plain: the ⏸ key's face copied over it (left half aligned on the left
   *  edges, right half on the right ones, the seam under the glyph), the ⏸ bars wiped, a ▶ drawn as its neighbours' glyphs. */
  static void playKey(BufferedImage orig,BufferedImage im){
    for(int y=434;y<=497;y++)for(int x=694;x<=822;x++) im.setRGB(x,y,orig.getRGB(x<758? x-132 : x-130,y));
    texFill(im,743,446,777,489,700,815);
    Shape tri=new Path2D.Double(); ((Path2D)tri).moveTo(748,452.5); ((Path2D)tri).lineTo(773.5,465.5); ((Path2D)tri).lineTo(748,478.5); ((Path2D)tri).closePath();
    Rectangle bx=box(tri,6); shadowAndOutline(im,tri,bx,1.9,0.35,1.2);
    emboss(im,bx.x,bx.y,bx.width,bx.height,cover(tri,bx.x,bx.y,bx.width,bx.height,SS),(x,y)->{ double t=Math.max(0,Math.min(1,(y-453)/25.0));
      return new double[]{217-14*t,210-14*t,200-14*t}; },new double[]{255,255,250},new double[]{255,255,255},new double[]{122,118,111},1.3); }

  /** The Fangchengbao logo (transparent around it) as a dark-metal badge, [width] px wide, centred on cx,cy: its chrome
   *  turned graphite with cool highlights, raised (bright top edges, dark lower edges) and casting a short shadow. */
  static void badge(BufferedImage im,BufferedImage logo,double cx,double cy,double width){
    int lw=logo.getWidth(),lh=logo.getHeight(),x0=lw,y0=lh,x1=0,y1=0;
    for(int y=0;y<lh;y++)for(int x=0;x<lw;x++) if((logo.getRGB(x,y)>>>24)>8){x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y);}
    double sc=width/(x1-x0+1), height=(y1-y0+1)*sc, left=cx-width/2, topY=cy-height/2;
    Rectangle bx=new Rectangle((int)left-6,(int)topY-6,(int)width+12,(int)height+12); int w=bx.width,h=bx.height,W=w*SS,H=h*SS;
    float[] m=new float[W*H],L=new float[W*H];
    for(int y=0;y<H;y++)for(int x=0;x<W;x++){ double px=bx.x+(x+0.5)/SS, py=bx.y+(y+0.5)/SS;
      double lx=x0+(px-left)/sc-0.5, ly=y0+(py-topY)/sc-0.5; if(lx<0||ly<0||lx>=lw-1||ly>=lh-1)continue;
      double[] s=bilA(logo,lx,ly); m[y*W+x]=(float)s[0]; L[y*W+x]=(float)s[1]; }
    float[] a=down(m,w,h,SS);
    over(im,bx.x,bx.y,w,h,blur(shift(a,w,h,0,2),w,h,1,2),0x000000,0.8);           // shadow below, light from above
    over(im,bx.x,bx.y,w,h,blur(a,w,h,1,1),0x000000,0.55);                          // a dark seam around it
    emboss(im,bx.x,bx.y,w,h,m,(px,py)->{ int x=(int)((px-bx.x)*SS),y=(int)((py-bx.y)*SS); double t=L[Math.min(H-1,y)*W+Math.min(W-1,x)]/255.0;
        t=Math.pow(Math.max(0,Math.min(1,(t-0.3)/0.68)),1.4);                       // the chrome's range, spread
        double[] c={26+(96-26)*t,28+(102-28)*t,33+(116-33)*t};
        double hl=Math.max(0,(t-0.82)/0.18); return new double[]{c[0]+(150-c[0])*hl*0.8,c[1]+(162-c[1])*hl*0.8,c[2]+(182-c[2])*hl*0.8}; },
      null,new double[]{178,190,208},new double[]{8,9,12},1.1); }
  /** Premultiplied bilinear sample of an ARGB image: {alpha 0..1, luminance of the colour}. */
  static double[] bilA(BufferedImage im,double x,double y){ int ix=(int)x,iy=(int)y; double fx=x-ix,fy=y-iy,A=0,Lm=0;
    for(int j=0;j<2;j++)for(int i=0;i<2;i++){ int c=im.getRGB(ix+i,iy+j); double wgt=(i==0?1-fx:fx)*(j==0?1-fy:fy), al=(c>>>24)/255.0; A+=wgt*al; Lm+=wgt*al*lum(c); }
    return new double[]{A, A>0? Lm/A : 0}; }
  static float[] shift(float[] a,int w,int h,int dx,int dy){ float[] o=new float[w*h];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){ int sx=x-dx,sy=y-dy; if(sx>=0&&sy>=0&&sx<w&&sy<h) o[y*w+x]=a[sy*w+sx]; } return o; }

  /** Deck block columns (inside the plate's borders) and where its rows come from. The data screen's frame is the
   *  reference: the deck's top edge moves up 12 rows, its bottom edge 10, the 2 rows taken up in the metal between the
   *  display and the keys. 10 of the 12 metal rows cut above the deck (the erased title's) go back under it. */
  static final int DECK_X0=247, DECK_X1=1110, DECK_FADE=13;
  static int deckSrc(int y){
    if(y<150||y>522) return y;
    if(y<404) return y+12;                                   // title metal, display: up 12
    if(y<419) return 416+(int)Math.round((y-404)*12/14.0);   // metal between display and keys: 13 rows over 15
    if(y<=512) return y+10;                                  // keys, frame bottom, shadow: up 10
    return 150+(y-513);                                      // the cut metal rows, under the frame
  }
  static void alignDeck(BufferedImage im){
    BufferedImage src=copy(im);
    for(int y=150;y<=522;y++){ int sy=deckSrc(y);
      for(int x=DECK_X0;x<=DECK_X1;x++){ double w=Math.min(1,Math.min(x-DECK_X0+1,DECK_X1-x+1)/(double)DECK_FADE);
        int n=src.getRGB(x,sy),o=src.getRGB(x,y);
        im.setRGB(x,y,rgb(r(o)+(r(n)-r(o))*w,g(o)+(g(n)-g(o))*w,b(o)+(b(n)-b(o))*w)); } } }
  /** The brightest row of column x between y0 and y1 (a frame's top or bottom highlight). */
  static int peak(BufferedImage im,int x,int y0,int y1){ int best=y0; for(int y=y0;y<=y1;y++) if(lum(im.getRGB(x,y))>lum(im.getRGB(x,best))) best=y; return best; }

  // ================= drawing on the plate: text, gold, textured erasing

  static File DIR;                                    // tools/cdrive (the mockup's folder): fonts/, the badge source
  static final int SS=4;                              // supersampling for text and the badge
  static Font font(String file,double px)throws Exception{
    Font f=Font.createFont(Font.TRUETYPE_FONT,new File(DIR,"fonts/"+file));
    return f.deriveFont(Map.of(TextAttribute.KERNING,TextAttribute.KERNING_ON)).deriveFont((float)px); }
  /** A font sized so its capital H is [cap] pixels tall. */
  static Font capFont(String file,double cap)throws Exception{ Font f=font(file,100); return f.deriveFont((float)(100*cap/inkOf(f,"H").getHeight())); }
  static Rectangle2D inkOf(Font f,String s){ return text(f,s,0,0,0).getBounds2D(); }
  /** The outline of [s]: pen at x, baseline at y, [track] pixels added after each letter. */
  static Shape text(Font f,String s,double x,double y,double track){
    char[] ch=s.toCharArray(); GlyphVector gv=f.layoutGlyphVector(new FontRenderContext(null,true,true),ch,0,ch.length,Font.LAYOUT_LEFT_TO_RIGHT);
    for(int i=0;i<gv.getNumGlyphs();i++){ Point2D p=gv.getGlyphPosition(i); gv.setGlyphPosition(i,new Point2D.Double(p.getX()+i*track,p.getY())); }
    return AffineTransform.getTranslateInstance(x,y).createTransformedShape(gv.getOutline()); }
  /** [s] with its ink centred on cx, baseline at y. */
  static Shape centred(Font f,String s,double cx,double y,double track){
    Shape t=text(f,s,0,y,track); Rectangle2D b=t.getBounds2D(); return AffineTransform.getTranslateInstance(cx-b.getCenterX(),0).createTransformedShape(t); }
  /** Coverage of [s] over the w x h box at bx,by, sampled [ss] times finer (row-major, ss*w wide). */
  static float[] cover(Shape s,int bx,int by,int w,int h,int ss){
    BufferedImage m=new BufferedImage(w*ss,h*ss,BufferedImage.TYPE_BYTE_GRAY); Graphics2D g=m.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
    g.scale(ss,ss); g.translate(-bx,-by); g.setColor(Color.WHITE); g.fill(s); g.dispose();
    float[] o=new float[w*ss*h*ss]; java.awt.image.Raster r=m.getRaster();
    for(int y=0;y<h*ss;y++)for(int x=0;x<w*ss;x++) o[y*w*ss+x]=r.getSample(x,y,0)/255f; return o; }
  static float[] down(float[] a,int w,int h,int ss){ float[] o=new float[w*h]; int W=w*ss;
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){ float s=0; for(int j=0;j<ss;j++)for(int i=0;i<ss;i++) s+=a[(y*ss+j)*W+x*ss+i]; o[y*w+x]=s/(ss*ss);} return o; }
  /** Box blur of radius [r], [n] passes. */
  static float[] blur(float[] a,int w,int h,int r,int n){ float[] o=a.clone(),t=new float[w*h];
    for(int k=0;k<n;k++){
      for(int y=0;y<h;y++)for(int x=0;x<w;x++){ float s=0;int c=0; for(int i=-r;i<=r;i++){int xx=x+i; if(xx>=0&&xx<w){s+=o[y*w+xx];c++;}} t[y*w+x]=s/c; }
      for(int y=0;y<h;y++)for(int x=0;x<w;x++){ float s=0;int c=0; for(int i=-r;i<=r;i++){int yy=y+i; if(yy>=0&&yy<h){s+=t[yy*w+x];c++;}} o[y*w+x]=s/c; } }
    return o; }
  /** Colour [c] (0xRRGGBB) laid over the box with alpha [a]·[op]. */
  static void over(BufferedImage im,int bx,int by,int w,int h,float[] a,int c,double op){
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){ double al=a[y*w+x]*op; if(al<=0.002)continue; int f=im.getRGB(bx+x,by+y);
      im.setRGB(bx+x,by+y,rgb(r(f)+(r(c)-r(f))*al,g(f)+(g(c)-g(f))*al,b(f)+(b(c)-b(f))*al)); } }
  /** The pen and its outline grown by [d] pixels (round joins). */
  static Shape grow(Shape s,double d){ Area a=new Area(s); a.add(new Area(new BasicStroke((float)(2*d),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(s))); return a; }
  static Rectangle box(Shape s,int m){ Rectangle r=s.getBounds(); return new Rectangle(r.x-m,r.y-m,r.width+2*m,r.height+2*m); }

  /** A printed label as on the mockup: white strokes in a hard dark outline. */
  static void label(BufferedImage im,Shape s){
    Rectangle bx=box(s,5); int w=bx.width,h=bx.height;
    over(im,bx.x,bx.y,w,h,down(cover(grow(s,1.7),bx.x,bx.y,w,h,SS),w,h,SS),0x050505,0.93);
    over(im,bx.x,bx.y,w,h,down(cover(s,bx.x,bx.y,w,h,SS),w,h,SS),0xFFFFFF,1); }

  /** Gold lettering in the manner of the mockup's logo: a vertical gold ramp, bright rounded edges (near white on top,
   *  dark brown underneath), a hard black outline and a soft shadow below. [top]..[base] is the cap height. */
  static void goldText(BufferedImage im,Shape s,double top,double base){
    Rectangle bx=box(s,8); int w=bx.width,h=bx.height;
    shadowAndOutline(im,s,bx,1.6,0.75,2.4);
    emboss(im,bx.x,bx.y,w,h,cover(s,bx.x,bx.y,w,h,SS),(x,y)->ramp((y-top)/(base-top)),new double[]{254,214,148},new double[]{255,255,232},new double[]{96,56,14},2.0); }
  /** A soft shadow [drop] px below the pen grown by [line], then a hard black outline [line] px wide. */
  static void shadowAndOutline(BufferedImage im,Shape s,Rectangle bx,double line,double shadow,double drop){
    int w=bx.width,h=bx.height; Shape g=grow(s,line);
    if(shadow>0) over(im,bx.x,bx.y,w,h,blur(down(cover(AffineTransform.getTranslateInstance(drop/4,drop).createTransformedShape(g),bx.x,bx.y,w,h,SS),w,h,SS),w,h,1,2),0x000000,shadow);
    over(im,bx.x,bx.y,w,h,down(cover(g,bx.x,bx.y,w,h,SS),w,h,SS),0x000000,1); }
  interface Tone { double[] at(double x,double y); }
  /** A raised pen laid on the plate, lit from above: [m] is its coverage ([SS] times finer over the w x h box at bx,by),
   *  [face] its colour at a plate point. Within [rim] px of its edge the colour turns to [side] (null: no side rim), to [hi]
   *  where the edge faces up and to [lo] where it faces down. */
  static void emboss(BufferedImage im,int bx,int by,int w,int h,float[] m,Tone face,double[] side,double[] hi,double[] lo,double rim){
    int W=w*SS,H=h*SS; float[] ds=blur(distIn(m,W,H),W,H,1,1);
    float[] R=new float[W*H],G=new float[W*H],Bl=new float[W*H];
    for(int y=0;y<H;y++)for(int x=0;x<W;x++){ int i=y*W+x; if(m[i]<=0)continue;
      double[] c=face.at(bx+(x+0.5)/SS,by+(y+0.5)/SS).clone();
      double dp=ds[i]/SS, e=Math.max(0,Math.min(1,(rim-dp)/(rim*0.47)));                      // 1 near the edge, 0 from [rim] px in
      double gx=ds[Math.min(W*H-1,i+1)]-ds[Math.max(0,i-1)], gy=(y<H-1&&y>0)?(ds[i+W]-ds[i-W]):0, gl=Math.hypot(gx,gy);
      double oy= gl>1e-3? -gy/gl:0;                                                          // outward normal, y down
      if(side!=null) for(int k=0;k<3;k++) c[k]+=(side[k]-c[k])*e;
      if(oy<0){ double u=e*Math.min(1,-oy*1.3); for(int k=0;k<3;k++) c[k]+=(hi[k]-c[k])*u; }
      else { double u=e*Math.min(1,oy*1.2)*0.85; for(int k=0;k<3;k++) c[k]+=(lo[k]-c[k])*u; }
      R[i]=(float)(c[0]*m[i]);G[i]=(float)(c[1]*m[i]);Bl[i]=(float)(c[2]*m[i]); }
    float[] a=down(m,w,h,SS),rr=down(R,w,h,SS),gg=down(G,w,h,SS),bb=down(Bl,w,h,SS);
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){ int i=y*w+x; double al=a[i]; if(al<=0.002)continue; int f=im.getRGB(bx+x,by+y);
      im.setRGB(bx+x,by+y,rgb(r(f)*(1-al)+rr[i],g(f)*(1-al)+gg[i],b(f)*(1-al)+bb[i])); } }
  /** The logo's gold down the cap height (t 0 top, 1 baseline): pale on top, darker low down, a lighter band at the foot. */
  static double[] ramp(double t){
    double[][] st={{-0.2,240,214,160},{0.1,212,168,108},{0.3,190,148,88},{0.5,154,118,68},{0.7,134,100,52},{0.86,110,82,40},{0.94,150,114,54},{1.2,128,98,48}};
    for(int i=1;i<st.length;i++) if(t<=st[i][0]||i==st.length-1){ double u=Math.max(0,Math.min(1,(t-st[i-1][0])/(st[i][0]-st[i-1][0])));
      return new double[]{st[i-1][1]+(st[i][1]-st[i-1][1])*u,st[i-1][2]+(st[i][2]-st[i-1][2])*u,st[i-1][3]+(st[i][3]-st[i-1][3])*u}; }
    return null; }
  /** Distance from each inside pixel (coverage > 0.5) to the outside, in pixels of the array (8-neighbour chamfer). */
  static float[] distIn(float[] m,int w,int h){ float[] d=new float[w*h]; float INF=1e6f, D=1.4142f;
    for(int i=0;i<w*h;i++) d[i]= m[i]>0.5? INF:0;
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){ int i=y*w+x; if(d[i]==0)continue; float v=d[i];
      if(x>0)v=Math.min(v,d[i-1]+1); if(y>0){ v=Math.min(v,d[i-w]+1); if(x>0)v=Math.min(v,d[i-w-1]+D); if(x<w-1)v=Math.min(v,d[i-w+1]+D);} d[i]=v; }
    for(int y=h-1;y>=0;y--)for(int x=w-1;x>=0;x--){ int i=y*w+x; if(d[i]==0)continue; float v=d[i];
      if(x<w-1)v=Math.min(v,d[i+1]+1); if(y<h-1){ v=Math.min(v,d[i+w]+1); if(x<w-1)v=Math.min(v,d[i+w+1]+D); if(x>0)v=Math.min(v,d[i+w-1]+D);} d[i]=v; }
    return d; }

  /** Erases a rect on brushed metal: each row is redrawn as a ramp between the medians just outside it, carrying the
   *  fine streaks of the same row from both sides (mirrored outward from the rect, cross-faded), so no smooth patch is left.
   *  The streaks come from [sx0, x0) and (x1, sx1]. */
  static void texFill(BufferedImage im,int x0,int y0,int x1,int y1,int sx0,int sx1){
    for(int y=y0;y<=y1;y++){
      int nl=x0-sx0, nr=sx1-x1; double[] dl=detail(im,sx0,x0-1,y), dr=detail(im,x1+1,sx1,y);
      int L=med(im,x0-3,y),R=med(im,x1+1,y); int n=x1-x0+2;
      for(int x=x0;x<=x1;x++){ double t=(x-x0+1)/(double)n;
        double a= nl>0? dl[pp(x0-1-x+nl, nl)] : 0, b2= nr>0? dr[pp(x1-x, nr)] : 0;    // mirrored into each side
        double det= nl>0&&nr>0? a*Math.cos(t*Math.PI/2)+b2*Math.sin(t*Math.PI/2) : a+b2;
        im.setRGB(x,y,rgb(r(L)+(r(R)-r(L))*t+det,g(L)+(g(R)-g(L))*t+det,b(L)+(b(R)-b(L))*t+det)); } } }
  /** Index k folded back and forth into 0..n-1. */
  static int pp(int k,int n){ if(n==1)return 0; int p=2*n-2; k=((k%p)+p)%p; return k<n? k : p-k; }
  /** The fine streaks of row y between xa and xb: lum minus its running mean (radius 6). */
  static double[] detail(BufferedImage im,int xa,int xb,int y){ int n=xb-xa+1; if(n<=0)return new double[0]; double[] l=new double[n],o=new double[n];
    for(int i=0;i<n;i++) l[i]=lum(im.getRGB(xa+i,y));
    for(int i=0;i<n;i++){ double s=0;int c=0; for(int j=Math.max(0,i-6);j<=Math.min(n-1,i+6);j++){s+=l[j];c++;} o[i]=l[i]-s/c; } return o; }

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
