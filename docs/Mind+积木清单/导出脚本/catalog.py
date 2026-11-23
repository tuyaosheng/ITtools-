# -*- coding: utf-8 -*-
"""把导出的 Mind+ 积木（toolbox.json + codes.json + img/）整理成清单 HTML 和 JSON。"""
import json, sys, re, html, collections
sys.stdout.reconfigure(encoding='utf-8')
S, OUT = sys.argv[1], sys.argv[2]
tb = json.load(open(S + '/toolbox.json', encoding='utf-8'))
cd = {r['i']: r for r in json.load(open(S + '/codes.json', encoding='utf-8'))}
BASE = {'// 主程序开始', 'void setup() {', '}', 'void loop() {', '', '// 事件回调函数', '// 动态变量'}


def snippet(c):
    c = re.sub(r'^/\*!.*?\*/\s*', '', c, flags=re.S)
    keep = [l for l in c.split('\n') if l.strip() not in BASE and not l.startswith('String mind_s_my_string_variable')]
    return '\n'.join(keep).strip()


# ITtools 掌控板编程页（web/public/mpython/mpl/blocks.js + Blockly 标准积木）里大致对应的积木
HAVE = {
    'control_wait': 'mpl_sleep', 'control_repeat': 'controls_repeat_ext', 'control_forever': 'mpl_forever',
    'control_if': 'controls_if', 'control_if_else': 'controls_if（带否则）',
    'operator_add': 'math_arithmetic', 'operator_subtract': 'math_arithmetic', 'operator_multiply': 'math_arithmetic',
    'operator_divide': 'math_arithmetic', 'operator_random': 'math_random_int',
    'operator_gt': 'logic_compare', 'operator_lt': 'logic_compare', 'operator_equals': 'logic_compare',
    'operator_gt_equals': 'logic_compare', 'operator_lt_equals': 'logic_compare',
    'operator_and': 'logic_operation', 'operator_or': 'logic_operation', 'operator_not': 'logic_negate',
    'operator_join': 'text_join', 'operator_length': 'text_length', 'operator_mod': 'math_modulo',
    'operator_round': 'math_round', 'operator_mathop': 'math_single（部分）', 'operator_map': 'mpl_map',
    'data_variable': '变量（Blockly 标准）', 'data_setvariableto': '变量（Blockly 标准）', 'data_changevariableby': '变量（Blockly 标准）',
    'start': 'mpl_main', 'esp32_buttonPress': 'mpl_on_button', 'esp32_padConnected': 'mpl_on_touch',
    'esp32_btnIsPressed': 'mpl_button', 'esp32_pinIsConnected': 'mpl_touch', 'esp32_readTouchValue': 'mpl_touch_value',
    'esp32_getSoundIntensity': 'mpl_sound', 'esp32_readBrightness': 'mpl_light', 'esp32_acceleration': 'mpl_accel',
    'esp32_displayInLine': 'mpl_gui_text', 'esp32_displayInXY': 'mpl_gui_text_xy', 'esp32_fillScreen': 'mpl_gui_bg / mpl_gui_clear',
    'esp32_lineDrawing': 'mpl_gui_line', 'esp32_circleDrawing': 'mpl_gui_circle', 'esp32_rectangleDrawing': 'mpl_gui_rect',
    'esp32_setLightsColor': 'mpl_rgb_all / mpl_rgb_one', 'esp32_rgbDisable': 'mpl_rgb_off', 'esp32_rgbToColor': 'mpl_rgb_mix（部分）',
    'esp32_playMusic': 'mpl_music_melody', 'esp32_setBuzzerFreq': 'mpl_music_note', 'esp32_serialSend': 'mpl_print（部分）',
    'esp32_timer': 'mpl_ticks', 'wifiConnectByPwd': 'mpl_wifi', 'isGesture': 'mpl_shaken / mpl_angle（部分）',
}


def short(t):
    return re.sub(r'^(dev|ext)-DFRobot-[^_]+_', '', t)


def shape(it, r):
    st = [i for i in it['inputs'] if i['type'] == 3]
    if r.get('how') == 'hat':
        return '帽子（事件）'
    if it['output']:
        return '六边形（真/假）' if it['outputShape'] == 1 else '圆角（数值/文字）'
    tail = '，下面不能接' if not it['next'] else ''
    if len(st) >= 2:
        return 'E 形（两个口）' + tail
    if st:
        return 'C 形（包住积木）' + tail
    return '语句' + tail


def args(it):
    out = []
    for inp in it['inputs']:
        for f in inp['fields']:
            if f['name'] and f.get('options'):
                ops = [o[0] for o in f['options'] if not str(o[1]).endswith('_ID')]
                out.append(f"{f['name']}：下拉 {'/'.join(ops[:12])}{'…' if len(ops) > 12 else ''}")
            elif f['name']:
                out.append(f"{f['name']}：默认 {f['text']}")
        if inp['shadow']:
            out.append(f"{inp['name']}：可放积木，默认 {inp['shadow']['text']}")
    return out


cats = collections.OrderedDict()
rows = []
for i, it in enumerate(tb):
    r = cd[i]
    row = {'分类': it['cat'].lstrip('-'), 'opcode': it['type'], '文字': it['text'].replace('?', '▢'),
           '形状': shape(it, r), '参数': args(it), 'Mind+代码': snippet(r.get('code', '')),
           '平台现有': HAVE.get(short(it['type']), ''), '图': r.get('img', '')}
    rows.append(row)
    cats.setdefault(row['分类'], []).append(row)

# 函数（自定义模块）要弹窗新建，按 Mind+ 的样子手工补录
for e in [
    {'opcode': 'procedures_definition', '文字': '定义 模块名(参数…)', '形状': '帽子（定义）', '参数': ['参数：数字 / 文字 / 布尔'], 'Mind+代码': 'void 模块名(参数) { … }'},
    {'opcode': 'procedures_call', '文字': '模块名(参数…)', '形状': '语句', '参数': [], 'Mind+代码': '模块名(参数);'},
    {'opcode': 'argument_reporter', '文字': '参数名', '形状': '圆角 / 六边形', '参数': [], 'Mind+代码': '参数名'},
]:
    e.update({'分类': '函数', '平台现有': '函数（Blockly 标准）', '图': ''})
    rows.append(e)
    cats.setdefault('函数', []).append(e)

json.dump(rows, open(OUT + '/积木清单.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

tot = len(rows)
have = sum(1 for r in rows if r['平台现有'])
h = ['<!doctype html><meta charset="utf-8"><title>Mind+ 掌控板积木清单</title>',
     '<style>body{font-family:"Microsoft YaHei",sans-serif;margin:16px;background:#fafafa}'
     'table{border-collapse:collapse;width:100%;background:#fff}td,th{border:1px solid #ddd;padding:6px;vertical-align:top;font-size:13px}'
     'th{background:#f0f0f0;position:sticky;top:0}pre{margin:0;white-space:pre-wrap;font-size:12px;max-width:420px}'
     'img{max-width:360px}.miss{color:#c0392b}.have{color:#27ae60}h2{margin-top:28px}</style>',
     '<h1>Mind+ 2.0.7 上传模式 · 掌控板积木清单</h1>',
     f'<p>来源：本机 Mind+ 2.0.7（E:\\mind+），设备“掌控板”0.0.6，扩展 Wi-Fi 0.0.5、MQTT 0.0.4。2026-11-21 用隔离副本逐个放积木，导出外形截图和 Mind+ 生成的代码。'
     f'共 {tot} 个积木，ITtools 掌控板编程页现在大致有对应的 {have} 个。</p>',
     '<p>注意：Mind+ 上传模式生成的是 Arduino C（要编译后上传，会覆盖板子上的 MicroPython 固件）；ITtools 用 MicroPython（直接写进板子）。'
     '“Mind+ 代码”一栏用来对照每个积木做什么，平台里要另写 MicroPython 版本。</p>',
     '<p>各分类数量：' + '，'.join(f'{k} {len(v)}' for k, v in cats.items()) + '</p>']
for k, v in cats.items():
    h.append(f'<h2>{html.escape(k)}（{len(v)}）</h2><table><tr><th>外形</th><th>文字 / opcode</th><th>形状</th><th>参数</th><th>Mind+ 生成代码（Arduino C）</th><th>平台现有</th></tr>')
    for r in v:
        img = f'<img src="img/{r["图"]}">' if r['图'] else ''
        hv = f'<span class="have">{html.escape(r["平台现有"])}</span>' if r['平台现有'] else '<span class="miss">缺</span>'
        ar = '<br>'.join(html.escape(a) for a in r['参数'])
        h.append(f'<tr><td>{img}</td><td>{html.escape(r["文字"])}<br><small>{html.escape(r["opcode"])}</small></td>'
                 f'<td>{r["形状"]}</td><td>{ar}</td><td><pre>{html.escape(r["Mind+代码"])}</pre></td><td>{hv}</td></tr>')
    h.append('</table>')
open(OUT + '/积木清单.html', 'w', encoding='utf-8').write('\n'.join(h))
print(tot, have, {k: len(v) for k, v in cats.items()})
