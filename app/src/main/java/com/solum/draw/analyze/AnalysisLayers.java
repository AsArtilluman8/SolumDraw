package com.solum.draw.analyze;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AnalysisLayers {
    public final int width;
    public final int height;
    public final float[] gray;
    public final float[] blur;
    public final float[] edges;
    public final float[] saliency;
    public final int[] histogram;
    public final List<ColorCluster> clusters;
    public final List<Region> attentionRegions;
    public final List<Component> components;
    public final RectF foregroundBox;
    public final float centralObjectRatio;
    public final float edgeDensity;
    public final float saliencyDensity;
    public final float symmetryVertical;
    public final float logoScore;
    public final float glyphScore;
    public final float realTextScore;
    public final int componentCount;
    public final int textComponentCount;
    public final int textLineCount;
    public final int largeComponentCount;
    public final float largestComponentRatio;
    public final float textRowScore;
    public final float componentTextScore;

    private AnalysisLayers(int width, int height, float[] gray, float[] blur, float[] edges, float[] saliency,
            int[] histogram, List<ColorCluster> clusters, List<Region> attentionRegions, List<Component> components,
            RectF foregroundBox, float centralObjectRatio, float edgeDensity, float saliencyDensity,
            float symmetryVertical, float logoScore, float glyphScore, float realTextScore,
            int textComponentCount, int textLineCount, int largeComponentCount,
            float largestComponentRatio, float textRowScore, float componentTextScore) {
        this.width = width;
        this.height = height;
        this.gray = gray;
        this.blur = blur;
        this.edges = edges;
        this.saliency = saliency;
        this.histogram = histogram;
        this.clusters = clusters;
        this.attentionRegions = attentionRegions;
        this.components = components;
        this.foregroundBox = foregroundBox;
        this.centralObjectRatio = centralObjectRatio;
        this.edgeDensity = edgeDensity;
        this.saliencyDensity = saliencyDensity;
        this.symmetryVertical = symmetryVertical;
        this.logoScore = logoScore;
        this.glyphScore = glyphScore;
        this.realTextScore = realTextScore;
        this.componentCount = components.size();
        this.textComponentCount = textComponentCount;
        this.textLineCount = textLineCount;
        this.largeComponentCount = largeComponentCount;
        this.largestComponentRatio = largestComponentRatio;
        this.textRowScore = textRowScore;
        this.componentTextScore = componentTextScore;
    }

    public static AnalysisLayers build(Bitmap source) {
        Bitmap bmp = scaleMax(source, 192);
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        float[] gray = grayLinear(bmp);
        float[] blur = gaussian(gray, w, h, 2);
        float[] edges = sobel(blur, w, h);
        float[] saliency = saliency(gray, w, h);
        int[] histogram = histogram(gray);
        List<ColorCluster> clusters = kmeans(bmp, 6, 10);
        float edgeDensity = density(edges, 0.12f);
        float saliencyDensity = density(saliency, 0.35f);
        float symmetry = verticalSymmetry(edges, w, h);
        List<Component> components = connectedComponents(edges, w, h, 0.19f);
        ComponentStats cs = componentStats(components, w, h);
        RectF fg = cs.largest == null ? foregroundBox(bmp, clusters) : pad(cs.largest.rect, 4, w, h);
        float central = centralRatio(fg, w, h);
        float rowText = rawRowTextScore(edges, w, h);
        float componentText = cs.textLineScore;
        float realText = clamp01(componentText * 0.85f + Math.min(0.18f, rowText * 0.18f));
        float glyph = glyphScore(fg, central, edgeDensity, realText, symmetry, cs, w, h);
        float compact = clusters.isEmpty() ? 0f : clusters.get(0).ratio;
        float logo = clamp01(glyph * 0.72f + symmetry * 0.18f + compact * 0.22f + cs.largestComponentRatio * 0.35f - realText * 0.75f - Math.min(0.25f, cs.textComponentCount / 80f));
        List<Region> attention = attentionRegions(saliency, edges, components, w, h, 8);
        return new AnalysisLayers(w, h, gray, blur, edges, saliency, histogram, clusters, attention, components,
                fg, central, edgeDensity, saliencyDensity, symmetry, logo, glyph, realText,
                cs.textComponentCount, cs.textLineCount, cs.largeComponentCount,
                cs.largestComponentRatio, rowText, componentText);
    }

    public Bitmap makeAttentionBitmap(Bitmap source) {
        Bitmap base = Bitmap.createScaledBitmap(source, width, height, true).copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(base);
        Paint dim = new Paint();
        dim.setColor(0x99000000);
        c.drawRect(0, 0, width, height, dim);
        Paint heat = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int y = 0; y < height; y += 2) for (int x = 0; x < width; x += 2) {
            float v = Math.max(saliency[y * width + x], edges[y * width + x] * 0.75f);
            if (v < 0.18f) continue;
            heat.setColor(Color.argb(Math.min(190, (int)(v * 220)), 0, 255, 136));
            c.drawRect(x, y, x + 2, y + 2, heat);
        }
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2f);
        ring.setColor(0xFFFFCC00);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.WHITE);
        label.setTextSize(11f);
        label.setFakeBoldText(true);
        for (int i = 0; i < attentionRegions.size(); i++) {
            Region r = attentionRegions.get(i);
            c.drawOval(r.rect, ring);
            c.drawText(String.valueOf(i + 1), r.rect.centerX(), r.rect.centerY(), label);
        }
        return base;
    }

    public Bitmap makeEdgeBitmap() {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            float e = edges[y * width + x];
            int a = e > 0.10f ? Math.min(255, (int)(e * 255)) : 0;
            out.setPixel(x, y, Color.argb(a, 0, 255, 136));
        }
        return out;
    }

    public Bitmap makeComponentsBitmap() {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF0B0D12);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f);
        int i = 0;
        for (Component comp : components) {
            if (i >= 80) break;
            if (comp.isTextCandidate) p.setColor(0xFFFF4D6D);
            else if (comp.isLarge) p.setColor(0xFF00E5FF);
            else p.setColor(0xFF69F0AE);
            c.drawRect(comp.rect, p);
            i++;
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setTextSize(10f);
        c.drawText("components=" + components.size() + " textCand=" + textComponentCount + " lines=" + textLineCount, 5, 14, p);
        return out;
    }

    public Bitmap makePriorityBitmap(Bitmap source) {
        Bitmap out = Bitmap.createScaledBitmap(source, width, height, true).copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(0xFF00E5FF);
        c.drawRect(foregroundBox, p);
        p.setColor(0xFFFFCC00);
        for (Region r : attentionRegions) c.drawOval(r.rect, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(10f);
        p.setFakeBoldText(true);
        p.setColor(Color.WHITE);
        c.drawText("1 BG", 6, 14, p);
        c.drawText("2 MAIN", foregroundBox.left + 4, Math.max(28, foregroundBox.top + 14), p);
        c.drawText("3 EDGES", Math.max(6, foregroundBox.right - 56), Math.min(height - 8, foregroundBox.bottom - 8), p);
        return out;
    }

    public String metricsJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        kv(b, "layerWidth", width, true); kv(b, "layerHeight", height, true);
        kv(b, "edgeDensity", edgeDensity, true); kv(b, "saliencyDensity", saliencyDensity, true);
        kv(b, "centralObjectRatio", centralObjectRatio, true); kv(b, "symmetryVertical", symmetryVertical, true);
        kv(b, "logoScore", logoScore, true); kv(b, "glyphScore", glyphScore, true); kv(b, "realTextScore", realTextScore, true);
        kv(b, "componentCount", componentCount, true); kv(b, "textComponentCount", textComponentCount, true);
        kv(b, "textLineCount", textLineCount, true); kv(b, "largeComponentCount", largeComponentCount, true);
        kv(b, "largestComponentRatio", largestComponentRatio, true); kv(b, "textRowScore", textRowScore, true);
        kv(b, "componentTextScore", componentTextScore, false);
        b.append("\n}");
        return b.toString();
    }

    private static List<Component> connectedComponents(float[] edges, int w, int h, float th) {
        boolean[] seen = new boolean[w * h];
        int[] qx = new int[w * h];
        int[] qy = new int[w * h];
        ArrayList<Component> out = new ArrayList<>();
        for (int sy = 1; sy < h - 1; sy++) for (int sx = 1; sx < w - 1; sx++) {
            int si = sy * w + sx;
            if (seen[si] || edges[si] <= th) continue;
            int head = 0, tail = 0, minX = sx, maxX = sx, minY = sy, maxY = sy, area = 0;
            seen[si] = true; qx[tail] = sx; qy[tail] = sy; tail++;
            while (head < tail) {
                int x = qx[head], y = qy[head]; head++; area++;
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (Math.abs(dx) + Math.abs(dy) != 1) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx <= 0 || ny <= 0 || nx >= w - 1 || ny >= h - 1) continue;
                    int ni = ny * w + nx;
                    if (!seen[ni] && edges[ni] > th) { seen[ni] = true; qx[tail] = nx; qy[tail] = ny; tail++; }
                }
            }
            int cw = maxX - minX + 1, ch = maxY - minY + 1;
            if (area >= 3 && cw >= 2 && ch >= 2) out.add(new Component(new RectF(minX, minY, maxX + 1, maxY + 1), area, w, h));
        }
        Collections.sort(out, new Comparator<Component>() { @Override public int compare(Component a, Component b) { return b.area - a.area; } });
        return out;
    }

    private static ComponentStats componentStats(List<Component> comps, int w, int h) {
        ComponentStats s = new ComponentStats();
        s.largest = comps.isEmpty() ? null : comps.get(0);
        if (s.largest != null) s.largestComponentRatio = s.largest.area / (float)(w * h);
        int[] rowBins = new int[16];
        int[] rowSpanMin = new int[16];
        int[] rowSpanMax = new int[16];
        for (int i = 0; i < rowBins.length; i++) { rowSpanMin[i] = w; rowSpanMax[i] = 0; }
        for (Component c : comps) {
            float cw = c.rect.width(), ch = c.rect.height();
            float ar = cw / Math.max(1f, ch);
            c.isLarge = c.area > w * h * 0.018f || cw > w * 0.28f || ch > h * 0.28f;
            c.isTextCandidate = c.area >= 3 && c.area <= w * h * 0.010f && ch >= 3 && ch <= h * 0.12f && cw >= 2 && cw <= w * 0.22f && ar >= 0.12f && ar <= 12f;
            if (c.isLarge) s.largeComponentCount++;
            if (c.isTextCandidate) {
                s.textComponentCount++;
                int bin = clamp((int)((c.rect.centerY() / h) * rowBins.length), 0, rowBins.length - 1);
                rowBins[bin]++;
                rowSpanMin[bin] = Math.min(rowSpanMin[bin], (int)c.rect.left);
                rowSpanMax[bin] = Math.max(rowSpanMax[bin], (int)c.rect.right);
            }
        }
        for (int i = 0; i < rowBins.length; i++) {
            int span = rowSpanMax[i] - rowSpanMin[i];
            if (rowBins[i] >= 5 && span > w * 0.22f) s.textLineCount++;
        }
        float compPart = clamp01(s.textComponentCount / 70f);
        float linePart = clamp01(s.textLineCount / 5f);
        s.textLineScore = clamp01(linePart * 0.72f + compPart * 0.28f);
        return s;
    }

    private static float rawRowTextScore(float[] edges, int w, int h) {
        int rows = 0;
        for (int y = 0; y < h; y += 4) {
            int hits = 0;
            for (int x = 0; x < w; x += 2) if (edges[y * w + x] > 0.22f) hits++;
            if (hits > w / 14 && hits < w / 2) rows++;
        }
        return clamp01(rows / (float)Math.max(1, h / 4));
    }

    private static float glyphScore(RectF fg, float central, float edgeDensity, float realText, float symmetry, ComponentStats cs, int w, int h) {
        float areaBox = (fg.width() * fg.height()) / Math.max(1f, w * h);
        float largeBonus = clamp01(cs.largeComponentCount / 3f) * 0.18f;
        float fewTextBonus = 1f - clamp01(cs.textLineCount / 4f);
        return clamp01(central * 0.35f + areaBox * 0.20f + edgeDensity * 0.55f + symmetry * 0.16f + cs.largestComponentRatio * 1.3f + largeBonus + fewTextBonus * 0.20f - realText * 0.85f);
    }

    private static float[] grayLinear(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight(); float[] g = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { int c = bmp.getPixel(x, y); float r = gamma(Color.red(c)/255f), gr = gamma(Color.green(c)/255f), b = gamma(Color.blue(c)/255f); g[y*w+x] = 0.2126f*r + 0.7152f*gr + 0.0722f*b; }
        return g;
    }
    private static float gamma(float v) { return v <= 0.04045f ? v / 12.92f : (float)Math.pow((v + 0.055f) / 1.055f, 2.4); }
    private static float[] gaussian(float[] src, int w, int h, int r) {
        float[] tmp = new float[w*h], out = new float[w*h];
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) { float sum=0, wt=0; for (int dx=-r;dx<=r;dx++){int nx=clamp(x+dx,0,w-1); float gw=(float)Math.exp(-(dx*dx)/(2f*r*r)); sum+=src[y*w+nx]*gw; wt+=gw;} tmp[y*w+x]=sum/Math.max(.0001f,wt); }
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) { float sum=0, wt=0; for (int dy=-r;dy<=r;dy++){int ny=clamp(y+dy,0,h-1); float gw=(float)Math.exp(-(dy*dy)/(2f*r*r)); sum+=tmp[ny*w+x]*gw; wt+=gw;} out[y*w+x]=sum/Math.max(.0001f,wt); }
        return out;
    }
    private static float[] sobel(float[] g, int w, int h) {
        float[] e = new float[w*h]; float max=0f;
        for(int y=1;y<h-1;y++) for(int x=1;x<w-1;x++){ float gx=-g[(y-1)*w+x-1]+g[(y-1)*w+x+1]-2*g[y*w+x-1]+2*g[y*w+x+1]-g[(y+1)*w+x-1]+g[(y+1)*w+x+1]; float gy=-g[(y-1)*w+x-1]-2*g[(y-1)*w+x]-g[(y-1)*w+x+1]+g[(y+1)*w+x-1]+2*g[(y+1)*w+x]+g[(y+1)*w+x+1]; float v=(float)Math.sqrt(gx*gx+gy*gy); e[y*w+x]=v; if(v>max)max=v; }
        if(max>0) for(int i=0;i<e.length;i++) e[i]/=max; return e;
    }
    private static float[] saliency(float[] g, int w, int h) {
        int bs=Math.max(6,Math.min(w,h)/14); float[] sal=new float[w*h]; float max=0;
        for(int by=0;by<h;by+=bs) for(int bx=0;bx<w;bx+=bs){ float sum=0,sum2=0; int cnt=0; for(int y=by;y<Math.min(h,by+bs);y++) for(int x=bx;x<Math.min(w,bx+bs);x++){float v=g[y*w+x]; sum+=v; sum2+=v*v; cnt++;} float mean=sum/Math.max(1,cnt); float var=Math.max(0,sum2/Math.max(1,cnt)-mean*mean); if(var>max)max=var; for(int y=by;y<Math.min(h,by+bs);y++) for(int x=bx;x<Math.min(w,bx+bs);x++) sal[y*w+x]=var; }
        if(max>0) for(int i=0;i<sal.length;i++) sal[i]/=max; return sal;
    }
    private static int[] histogram(float[] gray){int[] h=new int[64]; for(float v:gray) h[clamp((int)(v*63),0,63)]++; return h;}
    private static List<ColorCluster> kmeans(Bitmap bmp, int k, int iterations) {
        int w=bmp.getWidth(),h=bmp.getHeight(),step=Math.max(1,Math.min(w,h)/72); ArrayList<int[]> samples=new ArrayList<>();
        for(int y=0;y<h;y+=step) for(int x=0;x<w;x+=step){int c=bmp.getPixel(x,y); samples.add(new int[]{Color.red(c),Color.green(c),Color.blue(c)});} if(samples.isEmpty()) samples.add(new int[]{0,0,0});
        ArrayList<float[]> centers=new ArrayList<>(); for(int i=0;i<k;i++){int[] s=samples.get((i*samples.size())/k); centers.add(new float[]{s[0],s[1],s[2]});} int[] assign=new int[samples.size()];
        for(int it=0;it<iterations;it++){float[][] sums=new float[k][3]; int[] counts=new int[k]; for(int i=0;i<samples.size();i++){int[] s=samples.get(i); int best=0; float bd=Float.MAX_VALUE; for(int c=0;c<k;c++){float d=dist2(s[0],s[1],s[2],centers.get(c)); if(d<bd){bd=d;best=c;}} assign[i]=best; counts[best]++; sums[best][0]+=s[0]; sums[best][1]+=s[1]; sums[best][2]+=s[2];} for(int c=0;c<k;c++) if(counts[c]>0) centers.set(c,new float[]{sums[c][0]/counts[c],sums[c][1]/counts[c],sums[c][2]/counts[c]});}
        int[] counts=new int[k]; for(int a:assign) counts[a]++; ArrayList<ColorCluster> out=new ArrayList<>(); for(int c=0;c<k;c++){float[] cc=centers.get(c); out.add(new ColorCluster(Color.rgb(clamp((int)cc[0],0,255),clamp((int)cc[1],0,255),clamp((int)cc[2],0,255)), counts[c]/(float)Math.max(1,samples.size())));} Collections.sort(out,new Comparator<ColorCluster>(){@Override public int compare(ColorCluster a,ColorCluster b){return Float.compare(b.ratio,a.ratio);}}); return out;
    }
    private static RectF foregroundBox(Bitmap bmp,List<ColorCluster> clusters){int w=bmp.getWidth(),h=bmp.getHeight();int bg=clusters.isEmpty()?bmp.getPixel(0,0):clusters.get(0).color;int minX=w,minY=h,maxX=0,maxY=0,count=0; for(int y=0;y<h;y+=2) for(int x=0;x<w;x+=2){int c=bmp.getPixel(x,y); if(colorDist(c,bg)>70){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);count++;}} if(count<30)return new RectF(w*.2f,h*.2f,w*.8f,h*.8f); return new RectF(Math.max(0,minX-4),Math.max(0,minY-4),Math.min(w,maxX+4),Math.min(h,maxY+4));}
    private static List<Region> attentionRegions(float[] sal,float[] edges,List<Component> comps,int w,int h,int maxRegions){ArrayList<Region> list=new ArrayList<>(); for(Component c:comps){float score=c.area/(float)(w*h)+centerScore(c.rect,w,h)*0.35f; list.add(new Region(c.rect,score));} int cellsX=8,cellsY=8; for(int cy=0;cy<cellsY;cy++)for(int cx=0;cx<cellsX;cx++){int x0=cx*w/cellsX,x1=(cx+1)*w/cellsX,y0=cy*h/cellsY,y1=(cy+1)*h/cellsY;float s=0;int n=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){int i=y*w+x;s+=sal[i]*.65f+edges[i]*.35f;n++;}list.add(new Region(new RectF(x0,y0,x1,y1),s/Math.max(1,n)));} Collections.sort(list,new Comparator<Region>(){@Override public int compare(Region a,Region b){return Float.compare(b.score,a.score);}}); return new ArrayList<>(list.subList(0,Math.min(maxRegions,list.size())));}
    private static float verticalSymmetry(float[] e,int w,int h){float diff=0,sum=0;for(int y=0;y<h;y+=2)for(int x=0;x<w/2;x+=2){float a=e[y*w+x],b=e[y*w+(w-1-x)];diff+=Math.abs(a-b);sum+=Math.max(a,b);}return clamp01(1f-diff/Math.max(.0001f,sum));}
    private static float centralRatio(RectF r,int w,int h){RectF c=new RectF(w*.25f,h*.25f,w*.75f,h*.75f);RectF in=new RectF(Math.max(r.left,c.left),Math.max(r.top,c.top),Math.min(r.right,c.right),Math.min(r.bottom,c.bottom));if(in.left>=in.right||in.top>=in.bottom)return 0;return clamp01((in.width()*in.height())/Math.max(1f,r.width()*r.height()));}
    private static RectF pad(RectF r,int p,int w,int h){return new RectF(Math.max(0,r.left-p),Math.max(0,r.top-p),Math.min(w,r.right+p),Math.min(h,r.bottom+p));}
    private static float centerScore(RectF r,int w,int h){float dx=Math.abs(r.centerX()-w*.5f)/(w*.5f);float dy=Math.abs(r.centerY()-h*.5f)/(h*.5f);return clamp01(1f-(dx+dy)*.5f);} private static float density(float[] a,float th){int n=0;for(float v:a)if(v>th)n++;return n/(float)Math.max(1,a.length);} private static float dist2(int r,int g,int b,float[] c){float dr=r-c[0],dg=g-c[1],db=b-c[2];return dr*dr+dg*dg+db*db;} private static int colorDist(int a,int b){return Math.abs(Color.red(a)-Color.red(b))+Math.abs(Color.green(a)-Color.green(b))+Math.abs(Color.blue(a)-Color.blue(b));}
    private static Bitmap scaleMax(Bitmap source,int maxSide){float s=Math.min(1f,maxSide/(float)Math.max(source.getWidth(),source.getHeight()));return Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*s)),Math.max(1,Math.round(source.getHeight()*s)),true);} private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));} private static float clamp01(float v){return Math.max(0f,Math.min(1f,v));}
    private static void kv(StringBuilder b,String k,int v,boolean comma){b.append("  \"").append(k).append("\": ").append(v); if(comma)b.append(","); b.append("\n");} private static void kv(StringBuilder b,String k,float v,boolean comma){b.append("  \"").append(k).append("\": ").append(String.format(java.util.Locale.US,"%.4f",v)); if(comma)b.append(","); b.append("\n");}
    private static final class ComponentStats { Component largest; int textComponentCount; int textLineCount; int largeComponentCount; float largestComponentRatio; float textLineScore; }
    public static final class Region { public final RectF rect; public final float score; Region(RectF rect,float score){this.rect=rect;this.score=score;} }
    public static final class Component { public final RectF rect; public final int area; public boolean isTextCandidate; public boolean isLarge; Component(RectF rect,int area,int w,int h){this.rect=rect;this.area=area;} public float ratio(int w,int h){return area/(float)Math.max(1,w*h);} }
    public static final class ColorCluster { public final int color; public final float ratio; ColorCluster(int color,float ratio){this.color=color;this.ratio=ratio;} public String hex(){return String.format("#%06X",0xFFFFFF&color);} }
}
