// Run: java RsBackplate.java <BYDMATE_Hybrid_2560x1208.png> <app/src/main/res/drawable-nodpi>
import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.File; import java.util.function.IntPredicate;

/** Builds the RS stack's backplate from the mockup: erases what the app draws live, extracts lit faces and reels. */
public class RsBackplate {
  static BufferedImage img;
  static int r(int c){return (c>>16)&255;} static int g(int c){return (c>>8)&255;} static int b(int c){return c&255;}
  static int lum(int c){return (r(c)*30+g(c)*59+b(c)*11)/100;}
  static int rgb(double r,double g,double b){return 0xFF000000|(cl(r)<<16)|(cl(g)<<8)|cl(b);}
  static int cl(double v){return (int)Math.max(0,Math.min(255,Math.round(v)));}

  /** Masked pixels in the rect (pred null: all) are refilled row by row, linearly between the nearest clean neighbours. */
  static void fill(BufferedImage im,int x0,int y0,int x1,int y1,IntPredicate pred,int dilate){
    int w=x1-x0+1,h=y1-y0+1; boolean[][] m=new boolean[h][w];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++) m[y][x]= pred==null || pred.test(im.getRGB(x0+x,y0+y));
    m=dil(m,dilate); rowFill(im,x0,y0,m);
  }
  static boolean[][] dil(boolean[][] m,int d){ if(d<=0)return m; int h=m.length,w=m[0].length; boolean[][] o=new boolean[h][w];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++) if(m[y][x]) for(int dy=-d;dy<=d;dy++)for(int dx=-d;dx<=d;dx++){int yy=y+dy,xx=x+dx; if(yy>=0&&yy<h&&xx>=0&&xx<w&&dx*dx+dy*dy<=d*d) o[yy][xx]=true;} return o; }
  static void rowFill(BufferedImage im,int x0,int y0,boolean[][] m){
    for(int y=0;y<m.length;y++){ int x=0; while(x<m[0].length){ if(!m[y][x]){x++;continue;} int a=x; while(x<m[0].length&&m[y][x])x++; int bb=x-1;
      int L=im.getRGB(x0+a-1,y0+y), R=im.getRGB(x0+bb+1,y0+y); int n=bb-a+2;
      for(int i=a;i<=bb;i++){ double t=(i-a+1)/(double)n; im.setRGB(x0+i,y0+y,rgb(r(L)+(r(R)-r(L))*t,g(L)+(g(R)-g(L))*t,b(L)+(b(R)-b(L))*t)); } } } }
  /** Pixels within [w] of segment a-b (extended by [ext] at both ends). */
  static void needle(BufferedImage im,double ax,double ay,double bx,double by,double w,double ext){
    double dx=bx-ax,dy=by-ay,len=Math.hypot(dx,dy); ax-=dx/len*ext; ay-=dy/len*ext; bx+=dx/len*ext; by+=dy/len*ext; dx=bx-ax; dy=by-ay; len=Math.hypot(dx,dy);
    int x0=(int)(Math.min(ax,bx)-w-2),x1=(int)(Math.max(ax,bx)+w+2),y0=(int)(Math.min(ay,by)-w-2),y1=(int)(Math.max(ay,by)+w+2);
    boolean[][] m=new boolean[y1-y0+1][x1-x0+1];
    for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){ double t=((x-ax)*dx+(y-ay)*dy)/(len*len); t=Math.max(0,Math.min(1,t)); double px=ax+dx*t-x,py=ay+dy*t-y; m[y-y0][x-x0]=Math.hypot(px,py)<=w; }
    rowFill(im,x0,y0,m);
  }
  /** A needle crossing an arc scale: row-filled, then the arc band (|r-R|<band) redrawn from the scale [deg] further round. */
  static void needleOnArc(BufferedImage orig,BufferedImage im,double ax,double ay,double bx,double by,double w,double ext,double cx,double cy,double R,double band,double deg){
    needle(im,ax,ay,bx,by,w,ext);
    double dx=bx-ax,dy=by-ay,len=Math.hypot(dx,dy); double a2x=ax-dx/len*ext,a2y=ay-dy/len*ext,b2x=bx+dx/len*ext,b2y=by+dy/len*ext; dx=b2x-a2x; dy=b2y-a2y; len=Math.hypot(dx,dy);
    double a=Math.toRadians(deg),c=Math.cos(a),s=Math.sin(a);
    int x0=(int)(Math.min(a2x,b2x)-w-2),x1=(int)(Math.max(a2x,b2x)+w+2),y0=(int)(Math.min(a2y,b2y)-w-2),y1=(int)(Math.max(a2y,b2y)+w+2);
    for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){ double t=((x-a2x)*dx+(y-a2y)*dy)/(len*len); t=Math.max(0,Math.min(1,t)); if(Math.hypot(a2x+dx*t-x,a2y+dy*t-y)>w)continue;
      double px=x-cx,py=y-cy; if(Math.abs(Math.hypot(px,py)-R)>band)continue; double sx=cx+px*c-py*s, sy=cy+px*s+py*c; im.setRGB(x,y,bil(orig,sx,sy)); }
  }
  /** An annular sector (screen degrees, clockwise from +x) refilled along each circle between its two edges. */
  static void angFill(BufferedImage im,double cx,double cy,double r0,double r1,double a0,double a1){
    BufferedImage src=copy(im);
    for(int y=(int)(cy-r1-1);y<=cy+r1+1;y++)for(int x=(int)(cx-r1-1);x<=cx+r1+1;x++){
      double dx=x-cx,dy=y-cy,r=Math.hypot(dx,dy); if(r<r0||r>r1)continue; double a=Math.toDegrees(Math.atan2(dy,dx)); if(a<a0||a>a1)continue;
      double t=(a-a0)/(a1-a0); double ra=Math.toRadians(a0),rb=Math.toRadians(a1);
      int L=bil(src,cx+r*Math.cos(ra),cy+r*Math.sin(ra)),R=bil(src,cx+r*Math.cos(rb),cy+r*Math.sin(rb));
      im.setRGB(x,y,rgb(r(L)+(r(R)-r(L))*t,g(L)+(g(R)-g(L))*t,b(L)+(b(R)-b(L))*t)); }
  }
  /** Masked pixels around a knob refilled from the same radius, [deg] further round. */
  static void rotFill(BufferedImage im,double cx,double cy,int x0,int y0,int x1,int y1,IntPredicate pred,int dilate,double deg){
    int w=x1-x0+1,h=y1-y0+1; boolean[][] m=new boolean[h][w];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++) m[y][x]=pred.test(im.getRGB(x0+x,y0+y)); m=dil(m,dilate);
    BufferedImage src=copy(im); double a=Math.toRadians(deg),c=Math.cos(a),s=Math.sin(a);
    for(int y=0;y<h;y++)for(int x=0;x<w;x++) if(m[y][x]){ double px=x0+x-cx,py=y0+y-cy; double sx=cx+px*c-py*s, sy=cy+px*s+py*c; im.setRGB(x0+x,y0+y,bil(src,sx,sy)); }
  }
  static int bil(BufferedImage im,double x,double y){ int ix=(int)Math.floor(x),iy=(int)Math.floor(y); double fx=x-ix,fy=y-iy;
    int a=im.getRGB(ix,iy),b2=im.getRGB(ix+1,iy),c=im.getRGB(ix,iy+1),d=im.getRGB(ix+1,iy+1);
    return rgb(mix(r(a),r(b2),r(c),r(d),fx,fy),mix(g(a),g(b2),g(c),g(d),fx,fy),mix(b(a),b(b2),b(c),b(d),fx,fy)); }
  static double mix(int a,int b,int c,int d,double fx,double fy){return (a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy;}
  static BufferedImage copy(BufferedImage im){BufferedImage o=new BufferedImage(im.getWidth(),im.getHeight(),BufferedImage.TYPE_INT_RGB); o.getGraphics().drawImage(im,0,0,null); return o;}
  /** Source rect stretched onto the destination rect (bilinear). */
  static void blit(BufferedImage src,int sx0,int sy0,int sx1,int sy1,BufferedImage dst,int dx0,int dy0,int dx1,int dy1){
    for(int y=dy0;y<=dy1;y++)for(int x=dx0;x<=dx1;x++){ double u=sx0+(x-dx0)*(double)(sx1-sx0)/(dx1-dx0), v=sy0+(y-dy0)*(double)(sy1-sy0)/Math.max(1,dy1-dy0); dst.setRGB(x,y,bil(src,Math.min(u,sx1-1.001),Math.min(v,sy1-1.001))); } }
  static BufferedImage crop(BufferedImage im,int x0,int y0,int x1,int y1){ return copy(im).getSubimage(x0,y0,x1-x0,y1-y0); }
  static void save(BufferedImage im,String path)throws Exception{ ImageIO.write(im,"png",new File(path)); }
  /** A round reel cut out with a soft alpha edge. */
  static void reel(BufferedImage im,int cx,int cy,int rad,String path)throws Exception{
    BufferedImage o=new BufferedImage(rad*2,rad*2,BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<rad*2;y++)for(int x=0;x<rad*2;x++){ double d=Math.hypot(x+0.5-rad,y+0.5-rad); double al=Math.max(0,Math.min(1,rad-d)); int c=im.getRGB(cx-rad+x,cy-rad+y)&0xFFFFFF; o.setRGB(x,y,((int)(al*255)<<24)|c);} save(o,path); }

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
    reel(orig,537,343,42,out+"/rs_reel_l.png");
    reel(orig,826,343,42,out+"/rs_reel_r.png");

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
    needleOnArc(orig,img,606,676,736,856,15,8,805,1133,452,40,3.2);
    needleOnArc(orig,img,1949,670,1970,838,12,8,1992,1132,442,40,3.4);
    needle(img,626,730,726,848,17,0);
    needle(img,1947,708,1966,836,14,0);
    fill(img,712,794,940,871,null,0);
    fill(img,1912,820,2108,897,null,0);
    fill(img,1296,710,1434,754,null,0);
    fill(img,1298,870,1430,912,null,0);
    int[][] kw={{1596,764,1647,801},{1659,704,1721,748},{1752,660,1814,706},{1856,640,1918,686},{1982,628,2044,676},{2092,640,2151,686},{2184,664,2246,712},{2274,707,2336,753},{2342,754,2403,802},{2372,810,2433,858}};
    for(int[] r:kw) fill(img,r[0],r[1],r[2],r[3],null,0);
    // ---- bottom: the lit car key becomes a plain key with the same icon and word
    blit(orig,1136,1017,1392,1165,img,866,1017,1129,1165);
    fill(img,890,1033,1110,1152,null,0);
    for(int y=1033;y<=1152;y++)for(int x=890;x<=1110;x++){ int c=orig.getRGB(x,y); double s=Math.max(0,Math.min(1,(r(c)-70)/150.0)); if(s<=0)continue;
      int f=img.getRGB(x,y); img.setRGB(x,y,rgb(r(f)+(236-r(f))*s,g(f)+(232-g(f))*s,b(f)+(222-b(f))*s)); }
    save(img,out+"/rs_base.png");
  }
}
