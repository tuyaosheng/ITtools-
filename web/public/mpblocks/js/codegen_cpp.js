// 积木 → Arduino C++（和 Mind+ 上传模式生成的代码一致）
// - 程序的整体拼装（include、动态变量、创建对象、setup/loop、事件回调函数……）、基础积木（控制/运算/变量/列表/函数）
//   按 Mind+ 的规则写在这里；
// - 掌控板、Wi-Fi、MQTT 这些设备/扩展积木，直接调用 Mind+ 自己的生成函数（学生机/教师机上 Mind+ 安装目录里的 main.js，
//   由平台从助手或教师机读来，不随平台分发），所以和 Mind+ 一字不差。
(function () {
  'use strict';

  var BOARD_NAME = 'DFRobot, 掌控板';
  var HANDPY = 'dev-DFRobot-handpyEsp32@0.0.6';

  // ---------------- Mind+ 的 main.js（设备/扩展）----------------
  var PRIMS = {};      // 前缀（dev-xxx / ext-xxx）→ 生成函数对象
  var DEVICE = null;   // 设备的生成函数（会覆盖部分基础积木，例如循环里加 yield()）
  function makeRuntime() {
    var base = {
      getBoardPinsByType: function () { return []; },
      assetPath: { getArduinoPath: function () { return ''; } },
      getCurrentDeviceId: function () { return 'DFRobot-handpyEsp32'; },
      isWin: function () { return true; }, isMac: function () { return false; }, isLinux: function () { return false; },
      getChildProcess: function () { return null; }
    };
    return typeof Proxy === 'function' ? new Proxy(base, { get: function (t, k) { return k in t ? t[k] : function () { return []; }; } }) : base;
  }
  // name: 例如 dev-DFRobot-handpyEsp32@0.0.6；src: 它的 main.js 文本
  function loadPrims(name, src) {
    var mod = { exports: {} };
    /* eslint-disable no-new-func */
    new Function('module', 'exports', src)(mod, mod.exports);
    var C = mod.exports.default || mod.exports;
    var inst = new C(makeRuntime());
    try { if (inst.setLocale) inst.setLocale('zh-cn'); } catch (e) { /* 没关系 */ }
    var cp = inst.getCodePrimitives();
    PRIMS[name] = cp;
    if (/^dev-/.test(name)) DEVICE = cp;
    return Object.getOwnPropertyNames(Object.getPrototypeOf(cp)).filter(function (k) { return k !== 'constructor'; }).length;
  }
  function primsReady() { return !!(PRIMS[HANDPY] && DEVICE); }

  // ---------------- 基础积木 ----------------
  var ORDER = { ATOMIC: 0, UNARY_POSTFIX: 1, UNARY_PREFIX: 2, MULTIPLICATIVE: 3, ADDITIVE: 4, SHIFT: 5, RELATIONAL: 6, EQUALITY: 7, LOGICAL_AND: 11, LOGICAL_OR: 12, CONDITIONAL: 13, ASSIGNMENT: 14, NONE: 99 };

  function opText(g, p, kind) {
    // 比较积木的参数框是不带引号的文字框：能当数字就写数字，否则写成字符串
    if (p.parType !== 'text') return p.code;
    var s = g.escapeCharacter(String(p.code), [/\\/g, /"/g], ['\\\\', '\\"']);
    if (s === '') s = 0;
    if (!isNaN(Number(s))) return Number(s);
    if (s[0] === '\\' && s[s.length - 1] === '"') return s.substr(1, s.length - 3) + '"';
    return kind === 2 ? 'String("' + s + '")' : '"' + s + '"';
  }
  function cmp(sym, kind, order) {
    return function (g, b, p) {
      return [opText(g, p.OPERAND1, kind) + sym + opText(g, p.OPERAND2, kind), order];
    };
  }
  function mathop(op, num) {
    switch (op) {
      case 'floor': return ['floor(' + num + ')', 1];
      case 'ceiling': return ['ceil(' + num + ')', 1];
      case 'sqrt': return ['sqrt(' + num + ')', 1];
      case 'sin': return ['sin((float)' + num + ' / 180 * PI)', 1];
      case 'cos': return ['cos((float)' + num + ' / 180 * PI)', 1];
      case 'tan': return ['tan((float)' + num + ' / 180 * PI)', 1];
      case 'asin': return ['asin(' + num + ') / PI * 180', 3];
      case 'acos': return ['acos(' + num + ') / PI * 180', 3];
      case 'atan': return ['atan(' + num + ') / PI * 180', 3];
      case 'ln': return ['log(' + num + ')', 1];
      case 'log': return ['log10(' + num + ')', 3];
      case 'e ^': return ['exp(' + num + ')', 1];
      case '10 ^': return ['pow(10, ' + num + ')', 1];
      default: return ['abs(' + num + ')', 1];
    }
  }
  function listIndex(p) { return p.INDEX.code < 1 ? 1 : p.INDEX.code; }
  function listName(g, b) { var n = g.addVariable(g.getVariableName(b, 'LIST'), 'list'); g.addInclude('SimpleList.h'); return n; }
  function braces(g, head, body) { return g.formatCodeInBraces(head + ' {\n\t' + (body || '') + '\n\t}'); }

  var BUILTIN = {
    // 控制
    control_wait: function (g, b, p) {
      var t = p.DURATION.code;
      t = /(^-?\d+$)|(^(-?\d+)(\.\d+)?$)/.test(t) ? Number(t) * 1000 : t + ' * 1000';
      return 'delay(' + t + ');';
    },
    control_repeat: function (g, b, p) { return braces(g, 'for (int index = 0; index < ' + p.TIMES.code + '; index++)', p.SUBSTACK.code); },
    control_if: function (g, b, p) { return braces(g, 'if (' + p.CONDITION.code + ')', p.SUBSTACK.code); },
    control_if_else: function (g, b, p) {
      var c = 'if (' + p.CONDITION.code + ') {\n' + (p.SUBSTACK.code ? p.SUBSTACK.code + '\n' : '') + '}\n';
      c += '  else {\n' + (p.SUBSTACK2.code ? p.SUBSTACK2.code + '\n' : '') + '}';
      return g.formatCodeInBraces(c);
    },
    control_if_else_special: function (g, b, p) {
      var c = '', i = 0;
      while (p['IF' + i]) {
        var cond = p['IF' + i].code ? p['IF' + i].code : 'false';
        c += (i === 0 ? 'if (' : '\n else if (') + cond + ') {\n' + p['DO' + i].code + '\n}';
        i++;
      }
      if (p.ELSE) c += '\n else {\n' + p.ELSE.code + '\n}';
      return g.formatCodeInBraces(c);
    },
    control_wait_until: function (g, b, p) { return 'while (!' + p.CONDITION.code + ') {}'; },
    control_repeat_until: function (g, b, p) { return braces(g, 'while (!' + p.CONDITION.code + ')', p.SUBSTACK.code); },
    control_forever: function (g, b, p) { return braces(g, 'while (1)', p.SUBSTACK.code); },
    // 运算
    operator_add: function (g, b, p) { return [p.NUM1.code + ' + ' + p.NUM2.code, ORDER.ADDITIVE]; },
    operator_subtract: function (g, b, p) { return [p.NUM1.code + ' - ' + p.NUM2.code, ORDER.ADDITIVE]; },
    operator_multiply: function (g, b, p) { return [p.NUM1.code + ' * ' + p.NUM2.code, ORDER.ADDITIVE]; },
    operator_divide: function (g, b, p) { return [p.NUM1.code + ' / ' + p.NUM2.code, ORDER.ADDITIVE]; },
    operator_random: function (g, b, p) { return ['random(' + p.FROM.code + ', ' + p.TO.code + '+1)', ORDER.UNARY_POSTFIX]; },
    operator_lt: cmp('<', 1, ORDER.RELATIONAL),
    operator_lt_equals: cmp('<=', 1, ORDER.RELATIONAL),
    operator_gt: cmp('>', 1, ORDER.RELATIONAL),
    operator_gt_equals: cmp('>=', 1, ORDER.RELATIONAL),
    operator_equals: cmp('==', 2, ORDER.EQUALITY),
    operator_and: function (g, b, p) { return [p.OPERAND1.code + ' && ' + p.OPERAND2.code, ORDER.LOGICAL_AND]; },
    operator_or: function (g, b, p) { return [p.OPERAND1.code + ' || ' + p.OPERAND2.code, ORDER.LOGICAL_OR]; },
    operator_not: function (g, b, p) { return ['!' + p.OPERAND.code, ORDER.UNARY_PREFIX]; },
    operator_join: function (g, b, p) { return ['String(' + p.STRING1.code + ') + String(' + p.STRING2.code + ')', 1]; },
    operator_letter_of: function (g, b, p) { return ['String(String(' + p.STRING.code + ').charAt(' + p.LETTER.code + '-1))', 1]; },
    operator_length: function (g, b, p) { return ['String(' + p.STRING.code + ').length()', 1]; },
    operator_contains: function (g, b, p) { return ['(String(' + p.STRING1.code + ').indexOf(String(' + p.STRING2.code + ')) != -1)', 1]; },
    operator_textsrobotseven: function (g, b, p) {
      g.addInclude('DFString.h');
      var a = p.LIST1.code === 'first' ? 0 : 1, c = p.LIST2.code === 'first' ? 0 : 1;
      return ['dfstring.substring(' + p.STRING1.code + ',' + a + ',' + p.STRING2.code + ',' + c + ',' + p.STRING3.code + ')', 1];
    },
    operator_textsrobotsevenNew: function (g, b, p) {
      g.addInclude('DFString.h');
      var s1 = typeof p.STRING1.code === 'number' ? '"' + p.STRING1.code + '"' : p.STRING1.code;
      var s2 = typeof p.STRING2.code === 'number' ? '"' + p.STRING2.code + '"' : p.STRING2.code;
      return ['dfstring.' + (p.LIST1.code === 'first' ? 'indexOf' : 'lastIndexOf') + '(String(' + s1 + '), String(' + s2 + '))', 1];
    },
    operator_mod: function (g, b, p) { return ['(int32_t(' + p.NUM1.code + ')) % (int32_t(' + p.NUM2.code + '))', ORDER.MULTIPLICATIVE]; },
    operator_round: function (g, b, p) { return ['round(' + p.NUM.code + ')', 1]; },
    operator_mathop: function (g, b, p) { return mathop(p.OPERATOR.code, p.NUM.code); },
    operator_stringToNum: function (g, b, p) { return ['String(' + p.STRING.code + ').' + (p.TYPE.code == 'Integer' ? 'toInt()' : 'toFloat()'), 1]; },
    operator_numToASCII: function (g, b, p) { return ['String(char(' + p.NUM.code + '))', 1]; },
    operator_stringToASCII: function (g, b, p) {
      var s = p.STRING;
      if (s.parType !== 'text') return ['toascii(' + s.code + '.charAt(0))', 1];
      var ch = s.codeType === 'string' ? String(s.code)[1] : String(s.code)[0];
      return ["toascii('" + ch + "')", 1];
    },
    operator_numToString: function (g, b, p) { return ['String(' + p.NUM.code + ')', 1]; },
    operator_map: function (g, b, p) { return ['map(' + p.VALUE.code + ', ' + p.FROMLOW.code + ', ' + p.FROMHIGH.code + ', ' + p.TOLOW.code + ', ' + p.TOHIGH.code + ')', 1]; },
    operator_constrain: function (g, b, p) { return ['constrain(' + p.VALUE.code + ', ' + p.MIN.code + ', ' + p.MAX.code + ')', 1]; },
    // 变量
    data_variable: function (g, b) { return [g.addVariable(g.getVariableName(b, 'VARIABLE'), 'number'), 0]; },
    data_setvariableto: function (g, b, p) { return g.addVariable(g.getVariableName(b, 'VARIABLE'), 'number') + ' = ' + p.VALUE.code + ';'; },
    data_changevariableby: function (g, b, p) {
      var n = g.addVariable(g.getVariableName(b, 'VARIABLE'), 'number');
      return Number(p.VALUE.code) < 0 ? n + ' -= ' + Math.abs(Number(p.VALUE.code)) + ';' : n + ' += ' + p.VALUE.code + ';';
    },
    data_string: function (g, b) { return [g.addVariable(g.getVariableName(b, 'STRING'), 'string'), 0]; },
    data_setStringto: function (g, b, p) { return g.addVariable(g.getVariableName(b, 'STRING'), 'string') + ' = ' + p.VALUE.code + ';'; },
    data_stringStartWith: function (g, b, p) { return [g.addVariable(g.getVariableName(b, 'STRING'), 'string') + '.startsWith(' + p.VALUE.code + ')', 0]; },
    data_listcontents: function (g, b) { return [listName(g, b), 0]; },
    data_addtolist: function (g, b, p) { return listName(g, b) + '.push_back(' + p.ITEM.code + ');'; },
    data_deleteoflist: function (g, b, p) { return listName(g, b) + '.remove(' + p.INDEX.code + '-1);'; },
    data_deletealloflist: function (g, b) { return listName(g, b) + '.clear();'; },
    data_insertatlist: function (g, b, p) { var i = listIndex(p); return listName(g, b) + '.insert(' + i + '-1, ' + p.ITEM.code + ');'; },
    data_replaceitemoflist: function (g, b, p) { var i = listIndex(p); return listName(g, b) + '.replace(' + i + '-1, ' + p.ITEM.code + ');'; },
    data_itemoflist: function (g, b, p) { var i = listIndex(p); return [listName(g, b) + '[' + i + '-1]', 0]; },
    data_itemnumoflist: function (g, b, p) { return [listName(g, b) + '.indexOf(' + p.ITEM.code + ')', 0]; },
    data_lengthoflist: function (g, b) { return [listName(g, b) + '.size()', 0]; },
    data_listcontainsitem: function (g, b, p) { return [listName(g, b) + '.contains(' + p.ITEM.code + ')', 0]; },
    // 函数（自定义模块）
    procedures_definition: function (g, b) {
      var cb = b.getInputTargetBlock('custom_block');
      var proc = cb.procCode_;
      var name = proc.indexOf('%') !== -1 ? proc.slice(0, proc.indexOf('%')).trim() : proc;
      var types = procTypes(proc);
      var args = cb.displayNames_.map(function (n, i) { return types[i] + ' ' + varPrefix(types[i]) + g.transTextForVar(n); });
      return 'void DF_' + g.transTextForVar(name) + '(' + args.join(', ') + ')';
    },
    procedures_call: function (g, b, p) {
      var proc = b.procCode_;
      var name = 'DF_' + g.transTextForVar(proc.indexOf('%') !== -1 ? proc.slice(0, proc.indexOf('%')).trim() : proc);
      var parts = [];
      Object.keys(p).forEach(function (k) { if (p[k] && p[k].code !== '') parts.push(p[k].code); });
      return name + '(' + parts.join(', ') + ');';
    }
  };
  function procTypes(proc) {
    var t = [];
    var re = /%([snb])/g, m;
    while ((m = re.exec(proc))) t.push(m[1] === 's' ? 'String' : m[1] === 'n' ? 'float' : 'bool');
    return t;
  }
  function varPrefix(type) {
    if (type === 'argument_reporter_string_number' || type === 'String') return 'mind_s_';
    if (type === 'argument_reporter_number' || type === 'float') return 'mind_n_';
    if (type === 'argument_reporter_boolean' || type === 'bool') return 'mind_b_';
    return '';
  }

  // ---------------- 参数框（影子积木）----------------
  function shadowCode(g, s) {
    var t = s.type;
    if (/^math_/.test(t)) {
      var raw = String(s.getFieldValue('NUM') === null ? '' : s.getFieldValue('NUM'));
      if (t === 'math_integer' || t === 'math_angle' || t === 'math_slider') return raw.indexOf('.') !== -1 ? raw : Number(raw) || 0;
      return raw || 0;
    }
    // 比较积木的框在 Mind+ 里不带引号；以前存的程序里可能是带引号的框，按不带引号处理
    if (t === 'mp_text_q' && s.getParent() && /^operator_(gt|gt_equals|lt|lt_equals|equals)$/.test(s.getParent().type)) t = 'text';
    if (t === 'mp_text_q') return '"' + g.escapeCharacter(String(s.getFieldValue('TEXT') || ''), [/\\/g, /"/g], ['\\\\', '\\"']) + '"';
    if (t === 'text') return String(s.getFieldValue('TEXT') === null ? '' : s.getFieldValue('TEXT'));
    if (t === 'colour_palette' || t === 'colour_picker') return String(s.getFieldValue('COLOUR') || '#FF7D00').toUpperCase().replace('#', '0x');
    if (t === 'mqtt_settings') { try { return JSON.parse(s.getFieldValue('MQTT')); } catch (e) { return ''; } }
    if (t === 'img_preview') return imageArg(s.getFieldValue('ESP32_IMG'));
    // 下拉菜单、钢琴等：取值
    var f = s.inputList[0] && s.inputList[0].fieldRow[0];
    var v = f && f.getValue ? f.getValue() : '';
    return v || '0';
  }
  // 平台的“选图片”存的是 1 位点阵（bits，每行按字节对齐），转成 Mind+ 生成函数要的 RGBA
  function imageArg(json) {
    var info = {};
    try { info = JSON.parse(json || '{}') || {}; } catch (e) { info = {}; }
    var w = Number(info.w) || 0, h = Number(info.h) || 0, bits = info.bits || '';
    var data = new Uint8Array(w * h * 4), rowBytes = Math.ceil(w / 8);
    for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) {
      var byte = parseInt(bits.substr((y * rowBytes + (x >> 3)) * 2, 2), 16) || 0;
      if (byte & (0x80 >> (x & 7))) data[(y * w + x) * 4] = 255;
    }
    return { data: { data: data }, w: w, h: h };
  }

  // ---------------- 生成器 ----------------
  function Gen(ws) {
    this.ws = ws;
    this.warnings = [];
    this.init();
    var self = this;
    Object.keys(ORDER).forEach(function (k) { self['ORDER_' + k] = ORDER[k]; });
    this.ORDER_SHIFT = 5; this.ORDER_BITWISE_AND = 8; this.ORDER_BITWISE_XOR = 9; this.ORDER_BITWISE_OR = 10;
  }
  Gen.prototype.init = function () {
    this.errPrompts = {}; this.helpPrompts = []; this.codeLabel = ['MindPlus', BOARD_NAME];
    this.include = {}; this.initCode = {}; this.defineCode = {}; this.variable = {}; this.constVar = {};
    this.object = {}; this.globalCode = {}; this.setupCode = []; this.setupInitCode = []; this.loopCode = [];
    this.function = {}; this.customFunction = {}; this.staticFunction = {}; this.comment = {};
    this.cache = {}; // Mind+ 的 MQTT 生成函数用它记主题
  };

  // 找积木的生成函数：设备覆盖的基础积木 → 基础积木 → 设备/扩展积木
  Gen.prototype.funcOf = function (type) {
    var obj = null, op = type;
    if (/^(data|control|operator|procedures)_/.test(type)) {
      if (DEVICE && typeof DEVICE[type] === 'function') return { obj: DEVICE, fn: DEVICE[type] };
      return BUILTIN[type] ? { obj: null, fn: BUILTIN[type] } : null;
    }
    var m = /^((?:dev|ext)-[^_]+)_(.+)$/.exec(type);
    if (!m) return null;
    obj = PRIMS[m[1]];
    op = m[2];
    if (!obj || typeof obj[op] !== 'function') return null;
    return { obj: obj, fn: obj[op] };
  };

  Gen.prototype.isOutput = function (b) { return !!b.outputConnection; };

  Gen.prototype.blockToCode = function (block) {
    if (!block || block.disabled) return '';
    var text = block.getCommentText && block.getCommentText();
    if (text) {
      if (isHat(block) && isMain(block.type)) this.addCommentTop(text.split('\n'));
      else this.addComment(block.id, text.split('\n'), 0, false);
    }
    var f = this.funcOf(block.type);
    if (!f) {
      if (!(isHat(block) && isMain(block.type))) this.warnings.push('这个积木还不能生成 C++：' + block.type);
      return '';
    }
    var code = f.fn.call(f.obj, this, block, this.getParameter(block));
    if (code === null || code === undefined) code = '';
    if (block.type !== 'control_if_else_special' && this.isOutput(block) && !(code instanceof Array)) code = [code, 0];
    if (code instanceof Array) {
      if (this.comment[block.id] && block.getParent()) this.addComment(block.getParent().id, this.comment[block.id].comment, this.comment[block.id].position, false);
      return code;
    }
    var c = this.comment[block.id];
    if (c && code) {
      var lines = c.comment;
      if (c.position === 1) code = code + '\n\t// ' + lines.join('\n\t');
      else if (c.position === 2) code = '/* ' + lines.join(' ') + ' */ ' + code;
      else if (c.position === 3) code = code + ' // ' + lines.join(' ');
      else code = isHat(block) ? '// ' + lines.join('\n// ') + '\n' + code : '// ' + lines.join('\n\t// ') + '\n\t' + code;
    }
    return code;
  };

  Gen.prototype.valueToCode = function (block, order) {
    if (!block) return '';
    var isEventArg = /_eventHeadInner_$/.test(block.type);
    if (!block.isShadow() || isEventArg || block.type === 'procedures_call') {
      var cur = '';
      var r = this.blockToCode(block);
      if (r instanceof Array) {
        cur = r[0];
        var inner = r[1];
        if (cur !== '' && cur !== null && cur !== undefined && order <= inner && !(order === inner && (order === 0 || order === 99))) cur = '(' + cur + ')';
      } else cur = r;
      var next = this.valueToCode(block.getNextBlock(), 1);
      var sym = next && next !== '' && cur && cur !== '' ? '\n' : '';
      return cur + sym + next;
    }
    if (/^argument_reporter_/.test(block.type)) return varPrefix(block.type) + this.transTextForVar(String(block.getFieldValue('VALUE')));
    return shadowCode(this, block);
  };

  Gen.prototype.getParameter = function (block) {
    var p = {};
    for (var i = 0; i < block.inputList.length; i++) {
      var inp = block.inputList[i];
      var name = inp.name;
      if (name && name !== '_TEMP_COLLAPSED_INPUT') {
        p[name] = { code: '', parType: undefined, codeType: undefined };
        if (inp.type === Blockly.INPUT_VALUE && inp.connection && inp.connection.check_ && inp.connection.check_.length === 1 && inp.connection.check_[0] === 'Boolean') {
          p[name] = { code: false, parType: 'bool', codeType: 'number' };
        }
        var t = block.getInputTargetBlock(name);
        if (t) {
          var v = this.valueToCode(t, 1);
          // Mind+ 的影子积木类型名：带引号的文字框也叫 text
          p[name] = { code: v, parType: t.type === 'mp_text_q' ? 'text' : t.type, codeType: typeof v };
        }
      }
      for (var j = 0; j < inp.fieldRow.length; j++) {
        var fd = inp.fieldRow[j];
        if (fd.name && fd.SERIALIZABLE !== false) {
          var val = block.getFieldValue(fd.name);
          p[fd.name] = { code: val, parType: 'menu', codeType: typeof val };
        }
      }
    }
    // 带引号的文字框：Mind+ 里是 parType "text" 且代码带引号；比较积木用的是不带引号的 text
    return p;
  };

  // ---- 给 Mind+ 生成函数用的接口（名字、行为和 Mind+ 一样）----
  Gen.prototype.escapeCharacter = function (str, regs, reps) {
    if (regs.length === reps.length) for (var i = 0; i < reps.length; i++) str = String(str).replace(regs[i], reps[i]);
    return str;
  };
  Gen.prototype.transTextForVar = function (v) {
    v = String(v);
    if (/[^a-zA-Z0-9_$]/.test(v)) {
      var t = (window.MPPinyin ? window.MPPinyin(v) : v).replace(/;/g, '').replace(/&#x/g, '_');
      if (!isNaN(Number(t.slice(0, 1)))) t = '_' + t;
      v = t;
    }
    if (!isNaN(Number(v.slice(0, 1)))) v = '_' + v;
    return v.trim().replace(/\s/g, '_');
  };
  Gen.prototype.getVariableName = function (block, field) {
    var f = block.getField(field);
    var name = f ? (f.getText ? f.getText() : '') : '';
    if (!name && f && f.getValue) {
      var v = this.ws.getVariableById(f.getValue());
      name = v ? v.name : '';
    }
    return this.transTextForVar(name);
  };
  Gen.prototype.addVariable = function (name, type) {
    if (!name) return '';
    name = (type === 'string' ? 'mind_s_' : type === 'list' ? 'mind_l_' : 'mind_n_') + name;
    if (!this.variable[name]) this.variable[name] = { name: name, type: type };
    return name;
  };
  Gen.prototype.addInclude = function (id, code) {
    if (!id) return;
    this.include[id] = code === undefined ? '#include <' + id + '>' : '#include "' + code + '"';
  };
  Gen.prototype.addInitCode = function (id, code) { if (!id) return; if (code.indexOf(';') === -1) code += ';'; this.initCode[id] = code; };
  Gen.prototype.addObject = function (id, code, cover) { if (code.indexOf(';') === -1) code += ';'; if (cover || !this.object[id]) this.object[id] = code; };
  Gen.prototype.addFunction = function (code, cover) {
    var m = /(\w+[*&\s]+)(\w+)\s*\([^)]*\)\s*{/.exec(code);
    if (!m) return;
    if (!this.staticFunction[m[2]] || cover) this.staticFunction[m[2]] = code;
  };
  Gen.prototype.addConst = function (name, type, content, isArray, cover) {
    if (isArray === undefined) isArray = true;
    content = String(content).replace(/[ ;]/g, '');
    if (!this.constVar[name]) this.constVar[name] = { type: type, data: [], isArray: isArray };
    if (cover) { this.constVar[name].data[0] = content; return name; }
    var i = this.constVar[name].data.indexOf(content);
    if (i === -1) { this.constVar[name].data.push(content); i = this.constVar[name].data.length - 1; }
    return i > 0 ? name + '_' + i : name;
  };
  Gen.prototype.addSetup = function (id, code, priority, cover) {
    priority = priority || 0;
    var t = null;
    for (var i = 0; i < this.setupInitCode.length; i++) if (this.setupInitCode[i].id === id) { t = this.setupInitCode[i]; break; }
    if (cover || !t) {
      if (t) { t.priority = priority; t.code = code; } else this.setupInitCode.push({ id: id, code: code, priority: priority });
    }
  };
  Gen.prototype.addDefine = function (name, value, cover) {
    var id = name.replace(/ /g, '');
    if (!this.defineCode[id] || cover) this.defineCode[id] = '#define ' + name + ' ' + value;
  };
  Gen.prototype.addComment = function (id, comment, position, cover) {
    if (cover || !this.comment[id]) {
      if (!this.comment[id]) this.comment[id] = { comment: [] };
      this.comment[id].position = position;
    }
    if (typeof comment === 'object') this.comment[id].comment = this.comment[id].comment.concat(comment);
    else this.comment[id].comment.push(String(comment));
    return true;
  };
  Gen.prototype.addCommentTop = function (c) { this.codeLabel = this.codeLabel.concat(typeof c === 'object' ? c : [String(c)]); };
  Gen.prototype.addErrorPrompt = function (m) { this.errPrompts[m] = m; };
  Gen.prototype.addHelpPrompt = function (m) { this.helpPrompts.push(m); };
  Gen.prototype.addGlobalCode = function (id, code, cover) { if (cover || !this.globalCode[id]) this.globalCode[id] = code; };
  Gen.prototype.formatCodeInBraces = function (code) {
    var arr = code.split('\n').filter(function (x) { return x; }).map(function (x) { return x.trim(); });
    var sym = '\n\t', t = arr.slice(0), n = t.length;
    for (var i = 0; i < n; i++) {
      if (t[i].indexOf('{') !== -1 && t[i].trim()[t[i].length - 1] === '{') sym += '\t';
      if (t[i].indexOf('}') !== -1 && t[i].length === 1) { t[i - 1] = t[i - 1].slice(0, t[i - 1].length - 1); sym = sym.slice(0, sym.length - 1); }
      t[i] = t[i] + (sym === '\n\t' ? '\n\t' : sym);
    }
    if (n > 0) t[n - 1] = t[n - 1].replace(/(\s*$)/g, '');
    return t.join('');
  };
  Gen.prototype.splitName = function (type) {
    var i = type.indexOf('.');
    if (i === -1) i = type.indexOf('_');
    return [type.substring(0, i), type.substring(i + 1)];
  };

  function isMain(type) { return /_start$/.test(type); }
  // 帽子积木：最上面没有连接口、本身也不是参数
  function isHat(b) { return !b.previousConnection && !b.outputConnection; }
  function isProcDef(type) { return /_functionDefinition$/.test(type) || type === 'procedures_definition'; }

  Gen.prototype.mainToCode = function (top) {
    var b = top;
    do {
      var c = this.blockToCode(b);
      if (c && c !== '') this.setupCode.push(typeof c === 'object' ? c[0] + ';' : c);
      b = b.getNextBlock();
    } while (b && b.type !== 'control_forever');
    if (b && b.type === 'control_forever') {
      b = b.getInputTargetBlock('SUBSTACK');
      while (b) {
        var lc = this.blockToCode(b);
        if (lc && lc !== '') this.loopCode.push(typeof lc === 'object' ? lc[0] + ';' : lc);
        b = b.getNextBlock();
      }
    }
  };
  Gen.prototype.funcToCode = function (top) {
    var key = String(top);
    var tmp = isProcDef(top.type) ? this.customFunction : this.function;
    if (tmp[key]) { this.errPrompts[key] = '!错误提示：事件头 [' + key + '] 同时只能使用一个，请删除重复的模块。'; return; }
    var hat = this.blockToCode(top);
    if (!hat) return;
    tmp[key] = [hat + ' {'];
    for (var b = top.getNextBlock(); b; b = b.getNextBlock()) {
      var c = this.blockToCode(b);
      if (c && c !== '' && typeof c === 'string') tmp[key].push(c);
    }
  };

  Gen.prototype.finish = function () {
    var self = this;
    var out = '';
    var pr = Object.keys(this.errPrompts).map(function (k) { return self.errPrompts[k]; });
    if (pr.length) out += pr.join('\n') + '\n';
    if (this.helpPrompts.length) out += this.helpPrompts.join('\n') + '\n';
    // 顶部注释
    out += ['/*!'].concat(this.codeLabel.map(function (l) { return ' * ' + String(l).replace(/\n(.)/g, '\n * $1'); })).concat([' */']).join('\n') + '\n';
    // include（按长度排）
    var inc = Object.keys(this.include).map(function (k) { return self.include[k]; }).filter(Boolean);
    if (inc.length) out += inc.sort(function (a, b) { return a.length - b.length; }).join('\n') + '\n';
    var ic = Object.keys(this.initCode).map(function (k) { return self.initCode[k]; }).filter(Boolean);
    if (ic.length) out += ic.join('\n') + '\n';
    var df = Object.keys(this.defineCode).map(function (k) { return self.defineCode[k]; });
    if (df.length) out += [''].concat(df).concat(['']).join('\n');
    out += this.variableCode();
    // 静态常量
    var cs = [];
    Object.keys(this.constVar).forEach(function (n) {
      var v = self.constVar[n];
      v.data.forEach(function (d, i) { cs.push('const ' + v.type + ' ' + n + (i === 0 ? '' : '_' + i) + (v.isArray ? '[]' : '') + ' = ' + d + ';'); });
    });
    if (cs.length) out += '// 静态常量\n' + cs.join('\n') + '\n\n';
    var ob = Object.keys(this.object).map(function (k) { return self.object[k]; }).join('\n');
    if (ob) out += '// 创建对象\n' + ob + '\n';
    out += Object.keys(this.globalCode).map(function (k) { return self.globalCode[k]; }).join('\n') + '\n';
    // 函数声明
    var decl = [];
    function d(code) { var m = /^\s*([\w:*&<>[\]\s]+)\s+(\w+)\s*\([^)]*\)\s*\{/m.exec(code); if (m) decl.push(m[0].replace(/\s*\{.*/, ';').trim()); }
    Object.keys(this.customFunction).forEach(function (k) { d(self.customFunction[k][0]); });
    Object.keys(this.staticFunction).forEach(function (k) { d(self.staticFunction[k]); });
    Object.keys(this.function).forEach(function (k) { d(self.function[k][0]); });
    out += decl.join('\n');
    // setup / loop
    var su = this.setupInitCode.slice().map(function (x, i) { x._i = i; return x; })
      .sort(function (a, b) { return b.priority - a.priority || a._i - b._i; })
      .filter(function (x) { return x.id && x.code; }).map(function (x) { return x.code; }).concat(this.setupCode);
    out += '\n\n// 主程序开始\n' + (su.length ? 'void setup() {\n\t' + su.join('\n\t') + '\n}\n' : 'void setup() {\t\n\n}\n');
    out += this.loopCode.length ? 'void loop() {\n\t' + this.loopCode.join('\n\t') + '\n}\n\n' : 'void loop() {\t\n\n}\n';
    function fns(map, title) {
      var c = '';
      Object.keys(map).forEach(function (k) { c += map[k].join('\n\t') + '\n}\n'; });
      return c ? '\n// ' + title + '\n' + c + '\n' : '';
    }
    out += fns(this.customFunction, '自定义函数');
    out += fns(this.function, '事件回调函数');
    var st = Object.keys(this.staticFunction).map(function (k) { return self.staticFunction[k] + '\n'; }).join('');
    if (st) out += '\n// 静态函数\n' + st + '\n';
    return out.replace(/^\s+\n/, '').replace(/\n\s+$/, '\n').replace(/[ \t]+\n/g, '\n');
  };
  Gen.prototype.variableCode = function () {
    var self = this, num = [], str = [], list = [];
    Object.keys(this.variable).forEach(function (k) {
      var v = self.variable[k];
      if (v.type === 'number') num.push(v.name); else if (v.type === 'string') str.push(v.name); else if (v.type === 'list') list.push(v.name);
    });
    var w;
    if (str.length) w = 'String'.length;
    if (num.length) w = 'volatile float'.length;
    if (list.length) w = 'SimpleList<String>'.length;
    function align(type, items) {
      var s = type, code = '';
      for (var i = 0; i < w - type.length + 1; i++) s += ' ';
      for (var j = 0; j < items.length; j++) {
        s += items[j];
        if (j >= items.length - 1) { code += s + ';\n'; break; }
        s += ', ';
        if (s.length >= 80) { code += s + '\n'; s = new Array(w + 2).join(' '); }
      }
      return code;
    }
    var c = '';
    if (str.length) c += align('String', str);
    if (num.length) c += align('volatile float', num);
    if (list.length) c += align('SimpleList<String>', list);
    return c ? '\n// 动态变量\n' + c + '\n' : '';
  };

  // opts.keepComments：保留 Mind+ 原样的注释（和 Mind+ 逐字对照测试用）
  function generate(ws, opts) {
    var g = new Gen(ws);
    if (!primsReady()) return { code: '', warnings: ['还没有拿到 Mind+ 的掌控板积木文件，不能生成 C++'], ok: false };
    var tops = ws.getTopBlocks(true).filter(isHat);
    var hasMain = false;
    tops.forEach(function (b) {
      if (isMain(b.type)) {
        if (!hasMain) { hasMain = true; g.mainToCode(b); }
        else g.errPrompts[String(b)] = '!错误提示：程序头 [' + String(b) + '] 同时只能使用一个，请删除重复的模块。';
        return;
      }
      g.funcToCode(b);
    });
    // 不留注释：去掉开头的 /*! MindPlus … */ 和整行的 // 注释（分区标题、积木注释），代码本身和 Mind+ 一样
    var raw = g.finish();
    if (opts && opts.keepComments) return { code: raw, warnings: g.warnings, ok: true };
    var code = raw.replace(/^\/\*![\s\S]*?\*\/\n?/, '').split('\n')
      .filter(function (l) { return !/^\s*\/\//.test(l); }).join('\n')
      .replace(/\n{3,}/g, '\n\n').replace(/^\n+/, '');
    return { code: code, warnings: g.warnings, ok: true };
  }

  window.MPCppGen = { loadPrims: loadPrims, ready: primsReady, generate: generate, HANDPY: HANDPY };
})();
