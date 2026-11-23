// 积木程序 ⇄ Mind+ 的 project.json（scratch-vm 格式），⇄ .mpcode 文件（zip，里面是 project.json）。
// 用 Mind+ 一样的 opcode 和结构保存：平台已有的 Mind+ 自动评分规则可以直接判；Mind+ 做的 .mpcode 也能在这里打开。
(function () {
  // Mind+ 2.0.7 存的文件里固定带的舞台背景（文件内容就是 Mind+ 自己写进去的那段，原样照抄）和 config.json
  var BACKDROP = { name: '背景', dataFormat: 'svg', assetId: 'cd21514d0531fdffb22204e0ec5ed84a', md5ext: 'cd21514d0531fdffb22204e0ec5ed84a.svg', rotationCenterX: 240, rotationCenterY: 180 };
  var BACKDROP_SVG = '<!DOCTYPE html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n<title>Error</title>\n</head>\n<body>\n<pre>Cannot GET /static/sprite/cd21514d0531fdffb22204e0ec5ed84a.svg</pre>\n</body>\n</html>\n';
  var MP_CONFIG = '{"appInfo":{"id":"upload","version":"0.1.0"}}';

  'use strict';
  var H = 'dev-DFRobot-handpyEsp32@0.0.6';
  var PRIM = { math_number: 4, math_positive_number: 5, math_whole_number: 6, math_integer: 7, math_angle: 8, math_slider: 4, text: 10, mp_text_q: 10 };
  var PRIM_FIELD = { 4: 'NUM', 5: 'NUM', 6: 'NUM', 7: 'NUM', 8: 'NUM', 10: 'TEXT' };

  // ---------------- 工作区 → project.json ----------------
  function blockToJson(b, out) {
    var j = { opcode: b.type, next: null, parent: null, inputs: {}, fields: {}, shadow: b.isShadow(), topLevel: !b.getParent() };
    var parent = b.getParent();
    if (parent) j.parent = parent.id;
    var next = b.getNextBlock();
    if (next) j.next = next.id;
    if (j.topLevel) {
      var xy = b.getRelativeToSurfaceXY();
      j.x = Math.round(xy.x);
      j.y = Math.round(xy.y);
    }
    b.inputList.forEach(function (inp) {
      inp.fieldRow.forEach(function (f) {
        if (!f.name || f.SERIALIZABLE === false) return;
        if (f instanceof Blockly.FieldVariable) {
          var v = b.workspace.getVariableById(f.getValue());
          j.fields[f.name] = [v ? v.name : f.getText(), f.getValue()];
          if (v && v.type) j.fields[f.name].push(v.type);
        } else {
          j.fields[f.name] = [f.getValue(), null];
        }
      });
      if (!inp.connection || !inp.name) return;
      var target = inp.connection.targetBlock();
      var shadow = inp.connection.getShadowDom ? inp.connection.getShadowDom() : null;
      if (inp.type === Blockly.NEXT_STATEMENT) {
        if (target) { j.inputs[inp.name] = [2, target.id]; blockToJson(target, out); }
        return;
      }
      if (!target) return;
      if (target.isShadow() && PRIM[target.type] !== undefined) {
        var code = PRIM[target.type];
        j.inputs[inp.name] = [1, [code, String(target.getFieldValue(PRIM_FIELD[code]))]];
        return;
      }
      if (target.isShadow()) {
        j.inputs[inp.name] = [1, target.id];
        blockToJson(target, out);
        return;
      }
      // 积木盖住了参数框：[3, 积木, 原来的参数框]
      var sh = null;
      if (shadow) {
        var t = shadow.getAttribute('type');
        var fieldEl = shadow.querySelector('field');
        if (PRIM[t] !== undefined) sh = [PRIM[t], fieldEl ? fieldEl.textContent : ''];
      }
      j.inputs[inp.name] = sh ? [3, target.id, sh] : [2, target.id];
      blockToJson(target, out);
    });
    if (b.mutationToDom) {
      var m = b.mutationToDom();
      if (m) {
        var mj = { tagName: 'mutation', children: [] };
        for (var i = 0; i < m.attributes.length; i++) mj[m.attributes[i].name] = m.attributes[i].value;
        j.mutation = mj;
      }
    }
    out[b.id] = j;
    if (next) blockToJson(next, out);
  }

  function extensionsOf(blocks) {
    var ext = [H];
    var all = JSON.stringify(Object.keys(blocks).map(function (k) { return blocks[k].opcode; }));
    if (/ext-DFRobot-WIFI@0\.0\.5/.test(all)) ext.push('ext-DFRobot-WIFI@0.0.5');
    if (/ext-DFRobot-MQTT@0\.0\.4/.test(all)) ext.push('ext-DFRobot-MQTT@0.0.4');
    return ext;
  }

  function toProject(ws, extra) {
    var blocks = {};
    ws.getTopBlocks(true).forEach(function (b) { blockToJson(b, blocks); });
    var variables = {}, lists = {}, strings = {};
    ws.getAllVariables().forEach(function (v) {
      if (v.type === 'list') lists[v.getId()] = [v.name, []];
      else if (v.type === 'string') strings[v.getId()] = [v.name, ''];
      else if (v.type === '') variables[v.getId()] = [v.name, 0];
    });
    var p = {
      targets: [{
        isStage: true, name: 'Stage', variables: variables, strings: strings, lists: lists, broadcasts: {}, blocks: blocks, comments: {},
        currentCostume: 0, costumes: [BACKDROP], sounds: [], volume: 100, layerOrder: 0, tempo: 60, videoTransparency: 50, videoState: 'on',
        textToSpeechLanguage: null
      }],
      monitors: [], extensions: extensionsOf(blocks), code: '', assets: [],
      meta: { semver: '3.0.0', vm: '1.5.76', refactorVersion: true, agent: 'ITtools 图形化编程' }  // 没有 refactorVersion，Mind+ 打开 .mpcode 时会当旧版转换，报设备不支持
    };
    if (extra) p.ittools = extra;
    return p;
  }

  // ---------------- project.json → 工作区 ----------------
  function esc(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }

  // spec 里这个积木这个参数的影子是什么类型（带引号的文字框等）
  function specShadow(opcode, input) {
    var info = window.MP_BLOCK_INFO && window.MP_BLOCK_INFO[opcode];
    if (!info) return null;
    for (var i = 0; i < info.lines.length; i++) {
      for (var k = 0; k < info.lines[i].args.length; k++) {
        var a = info.lines[i].args[k];
        if (a.name === input && a.shadow) return a.shadow;
      }
    }
    return null;
  }

  function specStatement(opcode, input) {
    var info = window.MP_BLOCK_INFO && window.MP_BLOCK_INFO[opcode];
    if (!info) return false;
    return info.lines.some(function (l) { return l.args.some(function (a) { return a.type === 'input_statement' && a.name === input; }); });
  }

  function primShadowXml(opcode, input, prim) {
    var sp = specShadow(opcode, input);
    var code = prim[0], val = prim[1];
    var type;
    if (sp) type = sp.type === 'text' && sp.quote !== false ? 'mp_text_q' : sp.type;
    else type = code === 10 ? 'text' : code === 9 ? 'colour_picker' : 'math_number';
    if (type === 'colour_palette') return '<shadow type="colour_palette"><field name="COLOUR">' + esc(val) + '</field></shadow>';
    var field = type === 'text' || type === 'mp_text_q' ? 'TEXT' : type === 'piano' ? 'PIANO' : 'NUM';
    if (/_menu_/.test(type)) field = type.replace(/^.*_menu_/, '');
    return '<shadow type="' + esc(type) + '"><field name="' + field + '">' + esc(val) + '</field></shadow>';
  }

  function blockXml(id, blocks, isShadow) {
    var b = blocks[id];
    if (!b) return '';
    var tag = isShadow || b.shadow ? 'shadow' : 'block';
    var x = '<' + tag + ' type="' + esc(b.opcode) + '" id="' + esc(id) + '"' + (b.topLevel ? ' x="' + (b.x || 0) + '" y="' + (b.y || 0) + '"' : '') + '>';
    if (b.mutation) {
      x += '<mutation';
      Object.keys(b.mutation).forEach(function (k) {
        if (k === 'tagName' || k === 'children') return;
        x += ' ' + k + '="' + esc(b.mutation[k]) + '"';
      });
      x += '></mutation>';
    }
    Object.keys(b.fields || {}).forEach(function (k) {
      var f = b.fields[k];
      if (f[1] && /^(VARIABLE|LIST\d?|STRING)$/.test(k)) {
        var vtype = f[2] || (k.indexOf('LIST') === 0 ? 'list' : k === 'STRING' ? 'string' : '');
        x += '<field name="' + k + '" id="' + esc(f[1]) + '" variabletype="' + esc(vtype) + '">' + esc(f[0]) + '</field>';
      } else {
        x += '<field name="' + esc(k) + '">' + esc(f[0]) + '</field>';
      }
    });
    Object.keys(b.inputs || {}).forEach(function (k) {
      var inp = b.inputs[k];
      var kind = inp[0], main = inp[1], obscured = inp[2];
      var isStatement = specStatement(b.opcode, k) || (b.opcode === 'control_if_else_special' && /^(DO\d+|ELSE)$/.test(k));
      if (isStatement) {
        if (typeof main === 'string') x += '<statement name="' + esc(k) + '">' + chainXml(main, blocks) + '</statement>';
        return;
      }
      var inner = '';
      if (kind === 1) {
        inner = Array.isArray(main) ? primShadowXml(b.opcode, k, main) : blockXml(main, blocks, true);
      } else if (kind === 2) {
        inner = typeof main === 'string' ? blockXml(main, blocks, false) : (Array.isArray(main) ? primShadowXml(b.opcode, k, main) : '');
      } else if (kind === 3) {
        inner = (Array.isArray(obscured) ? primShadowXml(b.opcode, k, obscured) : (typeof obscured === 'string' ? blockXml(obscured, blocks, true) : '')) +
          (typeof main === 'string' ? blockXml(main, blocks, false) : '');
      }
      if (inner) x += '<value name="' + esc(k) + '">' + inner + '</value>';
    });
    x += '</' + tag + '>';
    return x;
  }

  function chainXml(id, blocks) {
    var b = blocks[id];
    if (!b) return '';
    var x = blockXml(id, blocks, false);
    if (b.next) x = x.replace(/<\/block>$/, '<next>' + chainXml(b.next, blocks) + '</next></block>');
    return x;
  }

  function loadProject(ws, project) {
    var targets = project.targets || [];
    // Mind+ 1.x：{page0: {targets}}
    if (!targets.length) Object.keys(project).forEach(function (k) { if (project[k] && project[k].targets) targets = targets.concat(project[k].targets); });
    var xml = '<xml xmlns="http://www.w3.org/1999/xhtml"><variables>';
    targets.forEach(function (t) {
      Object.keys(t.variables || {}).forEach(function (id) { xml += '<variable type="" id="' + esc(id) + '">' + esc(t.variables[id][0]) + '</variable>'; });
      Object.keys(t.strings || {}).forEach(function (id) { xml += '<variable type="string" id="' + esc(id) + '">' + esc(t.strings[id][0]) + '</variable>'; });
      Object.keys(t.lists || {}).forEach(function (id) { xml += '<variable type="list" id="' + esc(id) + '">' + esc(t.lists[id][0]) + '</variable>'; });
    });
    xml += '</variables>';
    targets.forEach(function (t) {
      var blocks = t.blocks || {};
      Object.keys(blocks).forEach(function (id) {
        var b = blocks[id];
        if (b && b.topLevel && !b.shadow && typeof b === 'object' && !Array.isArray(b)) xml += chainXml(id, blocks);
      });
    });
    xml += '</xml>';
    ws.clear();
    Blockly.Xml.domToWorkspace(Blockly.Xml.textToDom(xml), ws);
  }

  // ---------------- zip（.mpcode）----------------
  var CRC_TABLE = (function () {
    var t = [];
    for (var n = 0; n < 256; n++) {
      var c = n;
      for (var k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
      t[n] = c >>> 0;
    }
    return t;
  })();
  function crc32(bytes) {
    var c = 0xFFFFFFFF;
    for (var i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
    return (c ^ 0xFFFFFFFF) >>> 0;
  }
  // 只存不压（store），Mind+ 和平台都能读
  function makeZip(files) {
    var enc = new TextEncoder();
    var parts = [], central = [], offset = 0;
    files.forEach(function (f) {
      var name = enc.encode(f.name);
      var data = typeof f.data === 'string' ? enc.encode(f.data) : f.data;
      var crc = crc32(data);
      var lh = new DataView(new ArrayBuffer(30));
      lh.setUint32(0, 0x04034b50, true); lh.setUint16(4, 20, true); lh.setUint16(6, 0x0800, true); lh.setUint16(8, 0, true);
      lh.setUint32(14, crc, true); lh.setUint32(18, data.length, true); lh.setUint32(22, data.length, true); lh.setUint16(26, name.length, true);
      parts.push(new Uint8Array(lh.buffer), name, data);
      var ch = new DataView(new ArrayBuffer(46));
      ch.setUint32(0, 0x02014b50, true); ch.setUint16(4, 20, true); ch.setUint16(6, 20, true); ch.setUint16(8, 0x0800, true);
      ch.setUint32(16, crc, true); ch.setUint32(20, data.length, true); ch.setUint32(24, data.length, true); ch.setUint16(28, name.length, true);
      ch.setUint32(42, offset, true);
      central.push(new Uint8Array(ch.buffer), name);
      offset += 30 + name.length + data.length;
    });
    var cdSize = central.reduce(function (s, a) { return s + a.length; }, 0);
    var end = new DataView(new ArrayBuffer(22));
    end.setUint32(0, 0x06054b50, true); end.setUint16(8, files.length, true); end.setUint16(10, files.length, true);
    end.setUint32(12, cdSize, true); end.setUint32(16, offset, true);
    return new Blob(parts.concat(central, [new Uint8Array(end.buffer)]), { type: 'application/zip' });
  }

  async function inflateRaw(bytes) {
    var ds = new DecompressionStream('deflate-raw');
    var stream = new Blob([bytes]).stream().pipeThrough(ds);
    return new Uint8Array(await new Response(stream).arrayBuffer());
  }

  async function readZip(buf) {
    var u8 = new Uint8Array(buf), dv = new DataView(buf);
    var e = u8.length - 22;
    while (e >= 0 && dv.getUint32(e, true) !== 0x06054b50) e--;
    if (e < 0) throw new Error('不是 zip 文件');
    var count = dv.getUint16(e + 10, true), cd = dv.getUint32(e + 16, true);
    var out = {}, dec = new TextDecoder();
    for (var i = 0, p = cd; i < count; i++) {
      var method = dv.getUint16(p + 10, true), csize = dv.getUint32(p + 20, true);
      var nlen = dv.getUint16(p + 28, true), xlen = dv.getUint16(p + 30, true), clen = dv.getUint16(p + 32, true);
      var lho = dv.getUint32(p + 42, true);
      var name = dec.decode(u8.subarray(p + 46, p + 46 + nlen));
      var lnlen = dv.getUint16(lho + 26, true), lxlen = dv.getUint16(lho + 28, true);
      var start = lho + 30 + lnlen + lxlen;
      var data = u8.subarray(start, start + csize);
      out[name] = method === 8 ? await inflateRaw(data) : data;
      p += 46 + nlen + xlen + clen;
    }
    return out;
  }

  window.MPProject = {
    toProject: toProject,
    load: loadProject,
    // 存成 .mpcode：project.json + 平台附带的 Python 代码（ittools.code）
    toMpcode: function (ws, code) {
      var p = toProject(ws, { code: code, savedAt: new Date().toISOString() });
      // 和 Mind+ 存的文件一样带上舞台背景和 config.json，否则 Mind+ 打不开（它要求至少有一个造型）
      return makeZip([{ name: 'project.json', data: JSON.stringify(p) }, { name: 'config.json', data: MP_CONFIG },
        { name: BACKDROP.md5ext, data: BACKDROP_SVG }]);
    },
    fromFile: async function (file) {
      var buf = await file.arrayBuffer();
      var u8 = new Uint8Array(buf);
      if (u8[0] === 0x7B) return JSON.parse(new TextDecoder().decode(u8));   // 直接是 project.json
      var files = await readZip(buf);
      if (!files['project.json']) throw new Error('文件里没有 project.json，不是 Mind+ 程序');
      return JSON.parse(new TextDecoder().decode(files['project.json']));
    }
  };
})();
