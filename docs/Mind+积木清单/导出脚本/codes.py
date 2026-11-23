import sys, json
from playwright.sync_api import sync_playwright
sys.stdout.reconfigure(encoding='utf-8')
S=sys.argv[1]; only=sys.argv[2] if len(sys.argv)>2 else None
items=json.load(open(S+'/toolbox.json',encoding='utf-8'))
one=open(S+'/one.js',encoding='utf-8').read()
with sync_playwright() as p:
    b=p.chromium.connect_over_cdp('http://127.0.0.1:9333')
    pg=[x for c in b.contexts for x in c.pages if x.url.startswith('http://localhost')][-1]
    out=[]
    for i,it in enumerate(items):
        if only and only not in it['type']: continue
        r={'i':i,'type':it['type']}
        try:
            res=pg.evaluate(one,it); r.update(res)
            rc=res['rect']
            if rc['w']>0:
                pg.screenshot(path=f"{S}/img/{i:03d}.png", clip={'x':rc['x']-2,'y':rc['y']-2,'width':rc['w']+4,'height':rc['h']+4}); r['img']=f"{i:03d}.png"
        except Exception as e:
            r['err']=str(e)[:300]
        out.append(r); print(i, it['type'], r.get('how'), r.get('err','')[:100])
    json.dump(out, open(S+('/codes_%s.json'%only if only else '/codes.json'),'w',encoding='utf-8'), ensure_ascii=False, indent=1)
