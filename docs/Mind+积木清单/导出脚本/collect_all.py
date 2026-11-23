import sys, json
from playwright.sync_api import sync_playwright
sys.stdout.reconfigure(encoding='utf-8')
S=sys.argv[1]
js=open(S+'/collect.js',encoding='utf-8').read()
with sync_playwright() as p:
    b=p.chromium.connect_over_cdp('http://127.0.0.1:9333')
    pg=[x for c in b.contexts for x in c.pages if x.url.startswith('http://localhost')][-1]
    menu=pg.locator('.scratchCategoryMenuItem')
    names=[menu.nth(i).inner_text().strip() for i in range(menu.count())]
    print(names)
    allitems=[]; seen=set()
    for i,n in enumerate(names):
        if n in ('搜索',): continue
        menu.nth(i).click(); pg.wait_for_timeout(1200)
        r=pg.evaluate(js)
        new=[x for x in r['items'] if x['type'] not in seen or x['type'].startswith('data_') or x['type'].startswith('procedures')]
        for x in new: x['cat']=n; seen.add(x['type'])
        allitems+=new
        print(n, len(r['items']), 'labels', [l['text'] for l in r['labels']])
    json.dump(allitems, open(S+'/toolbox.json','w',encoding='utf-8'), ensure_ascii=False, indent=1)
    print('total', len(allitems))
