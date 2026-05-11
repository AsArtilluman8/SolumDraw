#!/usr/bin/env python3
import argparse, base64, html, json, os, time
from pathlib import Path

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}

def guess_label(path: Path):
    parts = [p.lower() for p in path.parts]
    name = path.name.lower()
    text = " ".join(parts + [name])
    if any(x in text for x in ["ui", "interface", "screen", "button", "app"]): return "ui"
    if any(x in text for x in ["anime", "character", "person", "girl", "boy", "face", "human", "портрет", "персонаж"]): return "character"
    if any(x in text for x in ["landscape", "forest", "nature", "sky", "tree", "mountain", "пейзаж", "лес"]): return "landscape"
    if any(x in text for x in ["logo", "icon", "symbol", "логотип"]): return "logo"
    if any(x in text for x in ["sketch", "lineart", "drawing", "скетч", "контур"]): return "sketch"
    return "unknown"

def read_sidecar(path: Path):
    candidates = [path.with_suffix(".json"), path.parent / (path.stem + ".meta.json")]
    for c in candidates:
        if c.exists():
            try:
                return json.loads(c.read_text(encoding="utf-8"))
            except Exception as e:
                return {"json_error": str(e), "file": str(c)}
    return {}

def image_data_url(path: Path):
    ext = path.suffix.lower()
    mime = "image/png" if ext == ".png" else "image/webp" if ext == ".webp" else "image/jpeg"
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:{mime};base64,{data}"

def collect_images(dataset: Path, limit: int):
    out = []
    for p in dataset.rglob("*"):
        if p.is_file() and p.suffix.lower() in IMAGE_EXTS:
            out.append(p)
    out.sort()
    return out[:limit]

def make_html(entries, dataset, generated_at):
    cards = []
    for i, e in enumerate(entries, 1):
        meta = html.escape(json.dumps(e["meta"], ensure_ascii=False, indent=2))
        cards.append(f"""
<section class="card" data-index="{i}" data-expected="{html.escape(e['expected'])}">
  <div class="head">
    <div>
      <h2>{i:02d}. {html.escape(e['name'])}</h2>
      <div class="muted">Ожидание по папке/json: <b>{html.escape(e['expected'])}</b></div>
    </div>
    <div class="badge" id="badge-{i}">анализ...</div>
  </div>

  <div class="grid">
    <div class="panel">
      <h3>Исходник</h3>
      <img id="img-{i}" src="{e['data_url']}" />
    </div>
    <div class="panel">
      <h3>Как видит контуры</h3>
      <canvas id="edge-{i}"></canvas>
    </div>
    <div class="panel">
      <h3>Острова / области</h3>
      <canvas id="region-{i}"></canvas>
    </div>
    <div class="panel">
      <h3>Маршрут рисования</h3>
      <canvas id="route-{i}"></canvas>
    </div>
  </div>

  <div class="reason" id="reason-{i}">Ждём анализ...</div>
  <details>
    <summary>JSON / детали датасета</summary>
    <pre>{meta}</pre>
  </details>
</section>
""")
    body_cards = "\n".join(cards)

    return f"""<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>SolumDraw Benchmark Report</title>
<style>
:root {{
  --bg:#050914; --card:#0b1322; --panel:#101a2c; --text:#eaf6ff; --muted:#93a8ba;
  --cyan:#22e6f2; --gold:#ffc448; --violet:#9b6bff; --bad:#ff5b7c; --ok:#56ff9a;
}}
* {{ box-sizing:border-box; }}
body {{ margin:0; background:linear-gradient(180deg,#040713,#0a1020); color:var(--text); font-family:Arial,system-ui,sans-serif; }}
header {{ padding:18px 16px; border-bottom:1px solid #1f3048; position:sticky; top:0; background:#050914ee; backdrop-filter:blur(8px); z-index:10; }}
h1 {{ margin:0 0 6px; font-size:22px; }}
h2 {{ margin:0; font-size:17px; }}
h3 {{ margin:0 0 8px; font-size:14px; color:#d7f8ff; }}
.muted {{ color:var(--muted); font-size:13px; }}
.wrap {{ padding:14px; max-width:1200px; margin:auto; }}
.summary {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:10px; margin-bottom:14px; }}
.stat {{ background:#0b1322; border:1px solid #1d2c42; border-radius:14px; padding:12px; }}
.stat b {{ display:block; font-size:22px; color:var(--cyan); }}
.card {{ background:var(--card); border:1px solid #1d2c42; border-radius:18px; padding:12px; margin:0 0 14px; box-shadow:0 12px 32px #0008; }}
.head {{ display:flex; align-items:flex-start; justify-content:space-between; gap:12px; margin-bottom:10px; }}
.badge {{ padding:7px 10px; border-radius:999px; background:#15243b; color:var(--cyan); font-size:13px; white-space:nowrap; }}
.grid {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(230px,1fr)); gap:10px; }}
.panel {{ background:var(--panel); border:1px solid #21344f; border-radius:14px; padding:10px; min-height:180px; }}
img, canvas {{ width:100%; max-height:360px; object-fit:contain; border-radius:10px; background:#04070d; }}
.reason {{ margin-top:10px; background:#07101d; border-left:3px solid var(--cyan); padding:10px; border-radius:10px; line-height:1.45; color:#dbefff; }}
pre {{ overflow:auto; color:#cce9ff; background:#040913; padding:10px; border-radius:10px; }}
</style>
</head>
<body>
<header>
  <h1>SolumDraw — Benchmark / отчёт анализа</h1>
  <div class="muted">Датасет: {html.escape(str(dataset))} · Создано: {html.escape(generated_at)} · Java-only CV направление, без OpenCV/NDK</div>
</header>
<div class="wrap">
  <div class="summary">
    <div class="stat"><b id="total">0</b>картинок</div>
    <div class="stat"><b id="matched">0</b>угадано грубо</div>
    <div class="stat"><b id="noisy">0</b>шумных</div>
    <div class="stat"><b id="weak">0</b>слабый контур</div>
  </div>
  {body_cards}
</div>

<script>
const TOTAL = {len(entries)};
let matched = 0, noisy = 0, weak = 0, done = 0;

function clamp(v,a,b) {{ return Math.max(a, Math.min(b, v)); }}

function analyzeImage(img, edgeCanvas, regionCanvas, routeCanvas) {{
  const W = 128;
  const H = Math.max(48, Math.round(W * img.naturalHeight / Math.max(1,img.naturalWidth)));
  const work = document.createElement('canvas');
  work.width = W; work.height = H;
  const ctx = work.getContext('2d', {{ willReadFrequently:true }});
  ctx.drawImage(img, 0, 0, W, H);
  const data = ctx.getImageData(0,0,W,H).data;

  const lum = new Float32Array(W*H);
  const sat = new Float32Array(W*H);
  let avgL=0, avgS=0, topBlue=0, bottomGreen=0, skinish=0;

  for (let y=0;y<H;y++) for (let x=0;x<W;x++) {{
    const i=(y*W+x)*4, id=y*W+x;
    const r=data[i], g=data[i+1], b=data[i+2];
    const mx=Math.max(r,g,b), mn=Math.min(r,g,b);
    const l=(r*30+g*59+b*11)/100;
    lum[id]=l; sat[id]=mx-mn; avgL+=l; avgS+=sat[id];
    if (y < H*0.35 && b > r*0.9 && b > g*0.8) topBlue++;
    if (y > H*0.45 && g > r*0.85 && g > b*0.85) bottomGreen++;
    if (r>95 && g>55 && b>35 && r>g && g>b && (r-b)>25) skinish++;
  }}
  avgL/=W*H; avgS/=W*H;

  const grad = new Float32Array(W*H);
  let gavg=0, gmax=0;
  for (let y=1;y<H-1;y++) for (let x=1;x<W-1;x++) {{
    const id=y*W+x;
    const gx = -lum[id-W-1]+lum[id-W+1]-2*lum[id-1]+2*lum[id+1]-lum[id+W-1]+lum[id+W+1];
    const gy = -lum[id-W-1]-2*lum[id-W]-lum[id-W+1]+lum[id+W-1]+2*lum[id+W]+lum[id+W+1];
    const m=Math.abs(gx)+Math.abs(gy);
    grad[id]=m; gavg+=m; if(m>gmax)gmax=m;
  }}
  gavg/=W*H;

  const edge = new Uint8Array(W*H);
  const strong = Math.max(70, gavg*2.05);
  const weakT = Math.max(38, gavg*1.15);
  let edgeCount=0, strongCount=0;

  for (let y=1;y<H-1;y++) for (let x=1;x<W-1;x++) {{
    const id=y*W+x;
    let v = grad[id] > strong ? 2 : grad[id] > weakT ? 1 : 0;
    if (v === 1) {{
      let nearStrong=false;
      for(let yy=-1;yy<=1;yy++) for(let xx=-1;xx<=1;xx++) {{
        if(grad[id+yy*W+xx] > strong) nearStrong=true;
      }}
      if(!nearStrong) v=0;
    }}
    edge[id]=v;
    if(v) edgeCount++;
    if(v===2) strongCount++;
  }}

  const sal = new Uint8Array(W*H);
  for (let y=1;y<H-1;y++) for (let x=1;x<W-1;x++) {{
    const id=y*W+x;
    const dL=Math.abs(lum[id]-avgL), dS=Math.abs(sat[id]-avgS);
    sal[id] = (edge[id] || dL>32 || dS>42) ? 1 : 0;
  }}

  const regions = [];
  const seen = new Uint8Array(W*H);
  const qx = [], qy = [];
  for (let y=1;y<H-1;y++) for (let x=1;x<W-1;x++) {{
    const start=y*W+x;
    if(!sal[start] || seen[start]) continue;
    let minX=x,maxX=x,minY=y,maxY=y,area=0,edges=0;
    qx.length=0; qy.length=0; qx.push(x); qy.push(y); seen[start]=1;
    while(qx.length) {{
      const px=qx.pop(), py=qy.pop(), id=py*W+px;
      area++; if(edge[id]) edges++;
      if(px<minX)minX=px; if(px>maxX)maxX=px; if(py<minY)minY=py; if(py>maxY)maxY=py;
      const ns=[[1,0],[-1,0],[0,1],[0,-1]];
      for(const n of ns) {{
        const nx=px+n[0], ny=py+n[1], nid=ny*W+nx;
        if(nx<=0||ny<=0||nx>=W-1||ny>=H-1) continue;
        if(sal[nid] && !seen[nid]) {{ seen[nid]=1; qx.push(nx); qy.push(ny); }}
      }}
    }}
    if(area>=10) {{
      const bw=maxX-minX+1, bh=maxY-minY+1;
      const cover=(bw*bh)/(W*H);
      if(cover < 0.72) regions.push({{minX,maxX,minY,maxY,area,edges,cover}});
    }}
  }}

  regions.sort((a,b)=>b.area-a.area);
  const main = regions[0] || {{minX:W*.35,maxX:W*.65,minY:H*.3,maxY:H*.75,area:0,edges:0,cover:.1}};

  const edgeDensity = edgeCount/(W*H);
  const strongDensity = strongCount/(W*H);
  const regionCount = regions.length;
  const mainAspect = (main.maxY-main.minY+1)/Math.max(1,(main.maxX-main.minX+1));
  const landscapeScore = (topBlue/(W*H))*1.5 + (bottomGreen/(W*H))*1.2 + (main.cover>.45?0.2:0);
  const characterScore = (skinish/(W*H))*4.0 + (mainAspect>1.25?0.25:0) + (main.cover>.08 && main.cover<.55?0.2:0);
  const uiScore = (edgeDensity>0.11?0.25:0) + (regionCount>12?0.25:0) + (avgS<45?0.1:0);
  const sketchScore = avgS<35 && edgeDensity>0.04 ? 0.45 : 0.0;
  const logoScore = regionCount<=5 && main.cover<0.35 && edgeDensity<0.12 ? 0.25 : 0;

  const scores = [
    ['character', characterScore],
    ['landscape', landscapeScore],
    ['ui', uiScore],
    ['sketch', sketchScore],
    ['logo', logoScore]
  ].sort((a,b)=>b[1]-a[1]);

  // draw edge
  for (const c of [edgeCanvas, regionCanvas, routeCanvas]) {{
    c.width = img.naturalWidth; c.height = img.naturalHeight;
    const cctx=c.getContext('2d');
    cctx.drawImage(img,0,0,c.width,c.height);
    cctx.fillStyle='rgba(0,0,0,.42)';
    cctx.fillRect(0,0,c.width,c.height);
  }}

  const ectx=edgeCanvas.getContext('2d');
  ectx.lineCap='round'; ectx.lineJoin='round';
  ectx.strokeStyle='rgba(34,230,242,.92)';
  ectx.lineWidth=Math.max(1.1, edgeCanvas.width/W*0.55);
  ectx.beginPath();
  for (let y=1;y<H-1;y++) for (let x=1;x<W-1;x++) {{
    const id=y*W+x;
    if(!edge[id]) continue;
    let n=edge[id-1]+edge[id+1]+edge[id-W]+edge[id+W]+edge[id-W-1]+edge[id-W+1]+edge[id+W-1]+edge[id+W+1];
    if(n<2) continue;
    const cx=(x+.5)*edgeCanvas.width/W, cy=(y+.5)*edgeCanvas.height/H;
    const sx=edgeCanvas.width/W*.50, sy=edgeCanvas.height/H*.50;
    if(edge[id-1]||edge[id+1]) {{ ectx.moveTo(cx-sx,cy); ectx.lineTo(cx+sx,cy); }}
    if(edge[id-W]||edge[id+W]) {{ ectx.moveTo(cx,cy-sy); ectx.lineTo(cx,cy+sy); }}
  }}
  ectx.stroke();

  // draw regions
  const rctx=regionCanvas.getContext('2d');
  const colors=['rgba(34,230,242,.95)','rgba(255,196,72,.9)','rgba(155,107,255,.85)','rgba(80,255,150,.8)','rgba(255,90,140,.8)'];
  regions.slice(0,8).forEach((r,i)=>{{
    const x=r.minX*regionCanvas.width/W, y=r.minY*regionCanvas.height/H;
    const w=(r.maxX-r.minX+1)*regionCanvas.width/W, h=(r.maxY-r.minY+1)*regionCanvas.height/H;
    rctx.strokeStyle=colors[i%colors.length]; rctx.lineWidth=i===0?3:2;
    rctx.strokeRect(x,y,w,h);
    rctx.fillStyle=colors[i%colors.length].replace('.95','.15').replace('.9','.14').replace('.85','.13').replace('.8','.12');
    rctx.fillRect(x,y,w,h);
    rctx.fillStyle='#020812'; rctx.beginPath(); rctx.arc(x+15,y+15,13,0,Math.PI*2); rctx.fill();
    rctx.fillStyle='#22e6f2'; rctx.font='bold 16px Arial'; rctx.textAlign='center'; rctx.fillText(String(i+1),x+15,y+21);
  }});

  // draw route
  const rt=routeCanvas.getContext('2d');
  const pts = [
    [0.5,0.14,'1','фон'],
    [((main.minX+main.maxX)/2)/W, (main.minY+(main.maxY-main.minY)*.25)/H, '2','масса'],
    [((main.minX+main.maxX)/2)/W, ((main.minY+main.maxY)/2)/H, '3','форма'],
    [clamp(main.minX/W+.18,0.05,.95), clamp(main.maxY/H-.1,0.05,.95), '4','тени'],
    [clamp(main.maxX/W-.12,0.05,.95), clamp(main.minY/H+.18,0.05,.95), '5','детали']
  ];
  pts.forEach(p=>{{
    const x=p[0]*routeCanvas.width, y=p[1]*routeCanvas.height;
    rt.fillStyle='rgba(2,8,14,.92)'; rt.beginPath(); rt.arc(x,y,25,0,Math.PI*2); rt.fill();
    rt.fillStyle='#22e6f2'; rt.beginPath(); rt.arc(x,y,20,0,Math.PI*2); rt.fill();
    rt.fillStyle='#03080c'; rt.font='bold 20px Arial'; rt.textAlign='center'; rt.fillText(p[2],x,y+7);
    rt.fillStyle='#fff'; rt.font='16px Arial'; rt.textAlign='left'; rt.fillText(p[3],x+28,y+6);
  }});

  let warnings=[];
  if(edgeDensity>0.18) warnings.push('слишком много шума в контурах');
  if(edgeDensity<0.025) warnings.push('контур слишком слабый');
  if(regionCount<2) warnings.push('мало найденных областей');
  if(main.cover>.55) warnings.push('главный остров слишком большой — фон слипся с объектом');

  return {{
    label:scores[0][0],
    confidence:Math.round(clamp(scores[0][1],0,1)*100),
    edgeDensity, strongDensity, regionCount, mainCover:main.cover,
    warnings,
    reason:`Жанр: ${{scores[0][0]}} (${{Math.round(clamp(scores[0][1],0,1)*100)}}%). Контуры: ${{Math.round(edgeDensity*1000)/10}}%. Областей: ${{regionCount}}. Главный остров: ${{Math.round(main.cover*100)}}%. ` + (warnings.length ? 'Проблемы: '+warnings.join('; ') : 'Критичных проблем не найдено.')
  }};
}}

function runOne(i) {{
  const img=document.getElementById('img-'+i);
  const edge=document.getElementById('edge-'+i);
  const region=document.getElementById('region-'+i);
  const route=document.getElementById('route-'+i);
  const badge=document.getElementById('badge-'+i);
  const reason=document.getElementById('reason-'+i);
  const expected=document.querySelector(`[data-index="${{i}}"]`).dataset.expected;

  try {{
    const r=analyzeImage(img,edge,region,route);
    badge.textContent=r.label+' '+r.confidence+'%';
    badge.style.color = expected !== 'unknown' && expected === r.label ? 'var(--ok)' : 'var(--cyan)';
    reason.textContent=r.reason;
    if(expected !== 'unknown' && expected === r.label) matched++;
    if(r.edgeDensity>0.18) noisy++;
    if(r.edgeDensity<0.025) weak++;
  }} catch(e) {{
    badge.textContent='ошибка';
    badge.style.color='var(--bad)';
    reason.textContent='Ошибка анализа: '+e;
  }}
  done++;
  document.getElementById('total').textContent=done+'/'+TOTAL;
  document.getElementById('matched').textContent=matched;
  document.getElementById('noisy').textContent=noisy;
  document.getElementById('weak').textContent=weak;
}}

window.addEventListener('load',()=>{{
  document.getElementById('total').textContent='0/'+TOTAL;
  for(let i=1;i<=TOTAL;i++) {{
    const img=document.getElementById('img-'+i);
    if(img.complete) runOne(i); else img.onload=()=>runOne(i);
  }}
}});
</script>
</body>
</html>"""

def main():
    ap = argparse.ArgumentParser(description="SolumDraw benchmark HTML report generator")
    ap.add_argument("--dataset", required=True, help="Path to image dataset folder")
    ap.add_argument("--out", default=str(Path.home() / "storage/downloads/SolumDrawReports/benchmark_report.html"))
    ap.add_argument("--limit", type=int, default=60)
    args = ap.parse_args()

    dataset = Path(args.dataset).expanduser()
    if not dataset.exists():
        raise SystemExit(f"DATASET_NOT_FOUND: {dataset}")

    out = Path(args.out).expanduser()
    out.parent.mkdir(parents=True, exist_ok=True)

    images = collect_images(dataset, args.limit)
    if not images:
        raise SystemExit(f"NO_IMAGES_FOUND_IN: {dataset}")

    entries = []
    for p in images:
        meta = read_sidecar(p)
        expected = str(meta.get("genre") or meta.get("type") or meta.get("label") or guess_label(p))
        entries.append({
            "name": str(p.relative_to(dataset)),
            "expected": expected,
            "meta": meta,
            "data_url": image_data_url(p),
        })

    generated_at = time.strftime("%Y-%m-%d %H:%M:%S")
    out.write_text(make_html(entries, dataset, generated_at), encoding="utf-8")
    print("SOLUMDRAW_BENCHMARK_REPORT_READY")
    print(f"IMAGES={len(entries)}")
    print(f"REPORT={out}")

if __name__ == "__main__":
    main()
