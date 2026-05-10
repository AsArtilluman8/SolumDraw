#!/usr/bin/env python3
import json, re, shutil, base64, mimetypes
from pathlib import Path

ROOT = Path('/storage/emulated/0/Download/SolumDrawDatasets/meigen_v2')
NOISE = ['Translate Explore More Prompts','Explore More Prompts','View on X']
TEMPLATE = ['[subject]','[outfit]','[doing action]','uploaded image']
GENRE_WORDS = {
 'sketch_lineart':['black-and-white','line art','monochrome','notion-style','clean line','illustration'],
 'vector_flat':['vector','flat','minimal','simple shapes','geometric'],
 'product_photo':['product','soda','can','commercial','packshot','studio','brand','drink','beverage','cola'],
 'storyboard':['story','4-part','creatures','treehouse','consistent identity'],
 'anime_character':['anime','manga','character','chibi','waifu'],
 'portrait_photo':['portrait','face','headshot','woman','man','person'],
 'nature_scene':['forest','tree','nature','landscape','mountain','river'],
 'cinematic_scene':['cinematic','dramatic','movie','fantasy','scene'],
 'ui_design':[' ui ','interface','dashboard','app screen','button','card','toolbar'],
 'logo_icon':['logo','icon','symbol','emblem']
}

def load_jsonl(p):
    if not p.exists(): return []
    out=[]
    for line in p.read_text('utf-8',errors='replace').splitlines():
        if line.strip():
            try: out.append(json.loads(line))
            except Exception: pass
    return out

def clean_prompt(s):
    s=s or ''
    for n in NOISE: s=s.replace(n,'')
    return re.sub(r'\s+',' ',s).strip()

def infer(prompt):
    p=' '+clean_prompt(prompt).lower()+' '
    best=('unknown',0)
    for g,ks in GENRE_WORDS.items():
        score=sum(1 for k in ks if k in p)
        if score>best[1]: best=(g,score)
    return best

def uri(path):
    p=Path(path)
    if not p.exists(): return ''
    mt=mimetypes.guess_type(str(p))[0] or 'image/jpeg'
    return 'data:%s;base64,%s'%(mt,base64.b64encode(p.read_bytes()).decode())

def decide(r):
    prompt=clean_prompt(r.get('prompt',''))
    p=prompt.lower()
    old=r.get('genre','unknown')
    fixed,score=infer(prompt)
    if fixed=='unknown': fixed=old
    flags=[]
    if len(prompt)<40: flags.append('weak_prompt')
    if any(x.lower() in p for x in TEMPLATE): flags.append('template_prompt')
    if fixed!=old: flags.append('genre_repair:%s->%s'%(old,fixed))
    if old=='ui_design' and fixed!='ui_design': flags.append('false_ui')
    if not Path(r.get('file','')).exists(): flags.append('missing_file')
    action='KEEP'
    if 'missing_file' in flags or 'false_ui' in flags: action='REJECT'
    elif flags: action='REVIEW'
    r['prompt_clean']=prompt
    r['clean']={'action':action,'fixed_genre':fixed,'flags':flags,'score':score}
    return r

def copy_rows(root, rows):
    for d in ['accepted','review','rejected']:
        out=root/d
        if out.exists(): shutil.rmtree(out)
        out.mkdir(exist_ok=True)
    for r in rows:
        f=Path(r.get('file',''))
        if f.exists():
            dst={'KEEP':'accepted','REVIEW':'review','REJECT':'rejected'}[r['clean']['action']]
            shutil.copy2(f, root/dst/f.name)

def write_html(root, rows):
    css='body{font-family:monospace;background:#080808;color:#eee;padding:12px}table{border-collapse:collapse;width:100%;font-size:12px}td,th{border:1px solid #333;padding:6px;vertical-align:top}.KEEP{background:#102414}.REVIEW{background:#302810}.REJECT{background:#361010}img{width:220px;max-height:180px;object-fit:contain}.prompt{max-width:520px;white-space:pre-wrap}'
    html=['<html><meta charset=utf-8><style>'+css+'</style><h1>Clean report v2</h1><table><tr><th>action</th><th>old</th><th>fixed</th><th>flags</th><th>image</th><th>prompt</th></tr>']
    for r in rows:
        c=r['clean']; img=uri(r.get('file',''))
        html.append('<tr class="%s"><td><b>%s</b></td><td>%s</td><td>%s</td><td>%s</td><td><img src="%s"></td><td class=prompt>%s</td></tr>'%(c['action'],c['action'],r.get('genre'),c['fixed_genre'],', '.join(c['flags']),img,r.get('prompt_clean','')[:900]))
    html.append('</table></html>')
    (root/'clean_report.html').write_text('\n'.join(html),'utf-8')

def main():
    import sys
    root=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT
    rows=[decide(r) for r in load_jsonl(root/'metadata.jsonl')]
    copy_rows(root, rows)
    (root/'clean_report.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),'utf-8')
    write_html(root, rows)
    print('DONE',root)
    for a in ['KEEP','REVIEW','REJECT']:
        print(a, sum(1 for r in rows if r['clean']['action']==a))
    print('REPORT',root/'clean_report.html')
if __name__=='__main__': main()
