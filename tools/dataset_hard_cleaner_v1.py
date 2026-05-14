#!/usr/bin/env python3
import json, re, shutil, base64, mimetypes
from pathlib import Path

ROOT = Path('/storage/emulated/0/Download/SolumDrawDatasets/meigen_v2')
BAD_PROMPT = ['translate explore more prompts','view on x','[subject]','[outfit]','[doing action]']
GENRE_WORDS = {
 'anime_character':['anime','manga','character','chibi','creature'],
 'portrait_photo':['portrait','face','person','woman','man','subject'],
 'nature_scene':['forest','tree','nature','landscape','mountain','river'],
 'cinematic_scene':['cinematic','dramatic','movie','fantasy','scene'],
 'ui_design':[' ui ','interface','dashboard','app screen','button','card'],
 'logo_icon':['logo','icon','symbol','emblem'],
 'vector_flat':['vector','flat','minimal','line art','illustration'],
 'product_photo':['product','soda','can','commercial','packshot','studio'],
 'storyboard':['story','4-part','creatures','treehouse']
}

def load_jsonl(p):
    if not p.exists(): return []
    out=[]
    for line in p.read_text('utf-8',errors='replace').splitlines():
        if line.strip():
            try: out.append(json.loads(line))
            except Exception: pass
    return out

def infer(prompt):
    p=' '+(prompt or '').lower()+' '
    best=('unknown',0)
    for g,ks in GENRE_WORDS.items():
        s=sum(1 for k in ks if k in p)
        if s>best[1]: best=(g,s)
    return best

def uri(path):
    p=Path(path)
    if not p.exists(): return ''
    mt=mimetypes.guess_type(str(p))[0] or 'image/jpeg'
    return 'data:%s;base64,%s'%(mt,base64.b64encode(p.read_bytes()).decode())

def decide(r):
    prompt=r.get('prompt','')
    p=prompt.lower()
    flags=[]
    if len(prompt)<40: flags.append('weak_prompt')
    if any(x in p for x in BAD_PROMPT): flags.append('template_or_site_noise')
    g,score=infer(prompt)
    old=r.get('genre','unknown')
    if g!='unknown' and g!=old: flags.append('genre_mismatch:%s->%s'%(old,g))
    if 'ui_design'==old and g!='ui_design': flags.append('false_ui_risk')
    if not Path(r.get('file','')).exists(): flags.append('missing_file')
    action='KEEP'
    if 'missing_file' in flags or len(flags)>=2: action='REJECT'
    elif flags: action='REVIEW'
    r['clean']={'action':action,'fixed_genre':g if g!='unknown' else old,'flags':flags}
    return r

def main():
    root=Path(__import__('sys').argv[1]) if len(__import__('sys').argv)>1 else ROOT
    rows=[decide(r) for r in load_jsonl(root/'metadata.jsonl')]
    for d in ['accepted','review','rejected']:
        (root/d).mkdir(exist_ok=True)
    for r in rows:
        f=Path(r.get('file',''))
        if f.exists():
            dst={'KEEP':'accepted','REVIEW':'review','REJECT':'rejected'}[r['clean']['action']]
            shutil.copy2(f, root/dst/f.name)
    (root/'clean_report.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),'utf-8')
    html=['<html><meta charset=utf-8><body style="background:#080808;color:#eee;font-family:monospace"><h1>Clean report</h1><table border=1>']
    for r in rows:
        html.append('<tr><td>%s</td><td>%s</td><td>%s</td><td><img width=180 src="%s"></td><td>%s</td></tr>'%(r['clean']['action'],r.get('genre'),','.join(r['clean']['flags']),uri(r.get('file','')),r.get('prompt','')[:300]))
    html.append('</table></body></html>')
    (root/'clean_report.html').write_text('\n'.join(html),'utf-8')
    print('DONE',root)
    print('KEEP',sum(1 for r in rows if r['clean']['action']=='KEEP'),'REVIEW',sum(1 for r in rows if r['clean']['action']=='REVIEW'),'REJECT',sum(1 for r in rows if r['clean']['action']=='REJECT'))
    print('REPORT',root/'clean_report.html')
if __name__=='__main__': main()
