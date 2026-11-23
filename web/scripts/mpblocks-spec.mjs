// 由 Mind+ 2.0.7 导出的积木数据（docs/Mind+积木清单/数据/）生成平台图形化编程用的积木规格 public/mpblocks/js/spec.js。
// 只取积木的文字、参数、下拉选项、默认值、形状、颜色；代码生成是平台自己写的 MicroPython（见 js/codegen.js）。
// 用法：node scripts/mpblocks-spec.mjs
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const dataDir = path.resolve(here, '../../docs/Mind+积木清单/数据')
const out = path.resolve(here, '../public/mpblocks/js/spec.js')
const read = (f) => JSON.parse(fs.readFileSync(path.join(dataDir, f), 'utf8'))

const toolbox = read('toolbox.json')
const exts = { 'dev-DFRobot-handpyEsp32@0.0.6': read('handpy.json'), 'ext-DFRobot-WIFI@0.0.5': read('wifi.json'), 'ext-DFRobot-MQTT@0.0.4': read('mqtt.json') }

const prefixOf = (type) => Object.keys(exts).find((p) => type.startsWith(p + '_'))
const extArgs = (type) => {
  const p = prefixOf(type)
  if (!p) return null
  const op = type.slice(p.length + 1)
  const b = exts[p].blocks.find((x) => x.opcode === op)
  return b ? b.arguments || {} : null
}

const CAT = { '控制': 'control', '运算符': 'operators', '变量': 'variables', '函数': 'myBlocks', '掌控': 'handpy', '-板载传感器': 'handpy_sensor', '-屏幕显示': 'handpy_screen', '-LED控制': 'handpy_led', '-音乐': 'handpy_music', 'MQTT': 'mqtt', 'Wi-Fi': 'wifi' }

const seen = new Set()
const blocks = []
const menus = {}
for (const it of toolbox) {
  if (seen.has(it.type)) continue
  seen.add(it.type)
  const meta = extArgs(it.type) || {}
  const lines = [{ text: [], args: [] }]
  let icon = false
  const cur = () => lines[lines.length - 1]
  const addArg = (a) => { cur().args.push(a); cur().text.push('%' + cur().args.length) }
  for (const inp of it.inputs) {
    if (inp.type === 3) {
      // 语句口：单独一行
      lines.push({ text: ['%1'], args: [{ type: 'input_statement', name: inp.name }] })
      lines.push({ text: [], args: [] })
      continue
    }
    for (const f of inp.fields) {
      if (f.name == null) {
        if (typeof f.value === 'string' && f.value.startsWith('data:')) { icon = true; continue }
        const t = (f.text || '').trim()
        if (t && t !== '?') cur().text.push(t)
        continue
      }
      if (f.options) {
        const varField = /^(VARIABLE|LIST\d?|STRING)$/.test(f.name) && f.options.some((o) => String(o[1]).endsWith('_ID'))
        if (varField) {
          addArg({ type: 'field_variable', name: f.name, variableTypes: [f.name.startsWith('LIST') ? 'list' : f.name === 'STRING' ? 'string' : ''], value: f.text })
        } else {
          addArg({ type: 'field_dropdown', name: f.name, options: f.options.filter((o) => !String(o[1]).endsWith('_ID')), value: f.value })
        }
      } else if (/^(VARIABLE|LIST\d?|STRING)$/.test(f.name)) {
        addArg({ type: 'field_variable', name: f.name, variableTypes: [f.name.startsWith('LIST') ? 'list' : f.name === 'STRING' ? 'string' : ''], value: f.text })
      } else {
        addArg({ type: 'field_input', name: f.name, value: f.value })
      }
    }
    if (inp.type === 1) {
      const a = { type: 'input_value', name: inp.name }
      const sh = inp.shadow
      if (sh) {
        const m = meta[inp.name] || {}
        const s = { type: sh.type, fields: {} }
        for (const f of sh.fields) {
          if (!f.name) continue
          s.fields[f.name] = f.value
          if (f.options && /menu_/.test(sh.type)) menus[sh.type] = { field: f.name, options: f.options }
        }
        if (m.inputParams) {
          if (m.inputParams.symbol) s.quote = true
          if (m.inputParams.rangeMin != null) { s.min = m.inputParams.rangeMin; s.max = m.inputParams.rangeMax }
        }
        if (sh.type === 'text' && (m.inputParams?.symbol || prefixOf(it.type))) s.quote = !!m.inputParams?.symbol
        // 比较积木（> ≥ < ≤ =）的框在 Mind+ 里不带引号，生成 C++ 时能当数字就写数字
        if (sh.type === 'text' && /^operator_(gt|gt_equals|lt|lt_equals|equals)$/.test(it.type)) s.quote = false
        if (/eventHeadInner/.test(sh.type)) s.label = sh.text
        a.shadow = s
      }
      addArg(a)
    }
  }
  const cleaned = lines.filter((l, i) => l.text.length || (i === 0))
  const stmts = it.inputs.filter((i) => i.type === 3).length
  blocks.push({
    type: it.type,
    cat: CAT[it.cat] || it.cat,
    colour: it.colour,
    icon,
    shape: it.hat ? 'hat' : it.output ? (it.outputShape === 1 ? 'boolean' : 'reporter') : stmts ? (it.next ? 'c' : 'c_end') : it.next ? 'stack' : 'end',
    lines: cleaned.map((l) => ({ text: l.text.join(' '), args: l.args })),
  })
}

const head = '// 自动生成，不要手改：node scripts/mpblocks-spec.mjs（数据来自本机 Mind+ 2.0.7 的掌控板、Wi-Fi、MQTT 积木）\n'
fs.mkdirSync(path.dirname(out), { recursive: true })
fs.writeFileSync(out, head + 'window.MP_SPEC = ' + JSON.stringify({ blocks, menus }, null, 1) + ';\n')
console.log('blocks', blocks.length, 'menus', Object.keys(menus).length, '→', out)
