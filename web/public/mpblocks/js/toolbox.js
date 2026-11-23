// 工具箱：分类顺序、分类里的小标题和 Mind+ 一样；变量分类按 Mind+ 的“数字 / 字符串 / 列表”三种变量动态生成。
(function () {
  'use strict';
  var SPEC = window.MP_SPEC;
  var C = window.MP_COLOURS;
  var H = 'dev-DFRobot-handpyEsp32@0.0.6_';

  // Mind+ 掌控分类里的小标题：放在哪个积木前面
  var LABEL_BEFORE = {};
  LABEL_BEFORE[H + 'esp32_buttonPress'] = '事件程序';
  LABEL_BEFORE[H + 'esp32_serialSend'] = '串口操作';
  LABEL_BEFORE[H + 'esp32_set_pinMode'] = '引脚操作';
  LABEL_BEFORE[H + 'esp32_timer'] = '系统资源';

  var CATS = [
    { id: 'control', name: '控制', colour: C.control },
    { id: 'operators', name: '运算符', colour: C.operators },
    { id: 'variables', name: '变量', colour: C.var_number, custom: 'MP_VARIABLE' },
    { id: 'myBlocks', name: '函数', colour: C.myBlocks, custom: 'PROCEDURE' },
    { id: 'handpy', name: '掌控', colour: C.handpy, head: '掌控' },
    { id: 'handpy_sensor', name: '板载传感器', colour: C.handpy, head: '板载传感器' },
    { id: 'handpy_screen', name: '屏幕显示', colour: C.handpy, head: '屏幕显示' },
    { id: 'handpy_led', name: 'LED控制', colour: C.handpy, head: 'LED控制' },
    { id: 'handpy_music', name: '音乐', colour: C.handpy, head: '音乐' },
    { id: 'mqtt', name: 'MQTT', colour: C.net, head: 'MQTT' },
    { id: 'wifi', name: 'Wi-Fi', colour: C.net, head: 'Wi-Fi' }
  ];

  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }

  // 参数框（影子积木）的 XML
  function shadowXml(a) {
    var s = a.shadow;
    if (!s) return '';
    var type = s.type, fields = s.fields || {};
    // 文字参数框：Mind+ 里显示引号（扩展积木里没标引号的除外）
    if (type === 'text' && s.quote !== false) type = 'mp_text_q';
    var inner = '';
    if (/_eventHeadInner_$/.test(type)) return '<value name="' + a.name + '"><shadow type="' + esc(type) + '"></shadow></value>';
    Object.keys(fields).forEach(function (k) { inner += '<field name="' + esc(k) + '">' + esc(fields[k]) + '</field>'; });
    return '<value name="' + a.name + '"><shadow type="' + esc(type) + '">' + inner + '</shadow></value>';
  }

  function blockXml(b) {
    var x = '<block type="' + esc(b.type) + '">';
    b.lines.forEach(function (ln) {
      ln.args.forEach(function (a) {
        if (a.type === 'input_value') x += shadowXml(a);
        else if (a.type === 'field_dropdown' && a.value != null) x += '<field name="' + a.name + '">' + esc(a.value) + '</field>';
      });
    });
    if (b.type === 'control_if_else_special') x += '<mutation elseif="0" else="0"></mutation>';
    return x + '</block>';
  }
  window.MP_BLOCK_XML = blockXml;

  function catBlocks(id) {
    return SPEC.blocks.filter(function (b) { return b.cat === id && !/_eventHeadInner_$/.test(b.type); });
  }

  window.MP_TOOLBOX_XML = function () {
    var x = '<xml id="toolbox" style="display:none">';
    CATS.forEach(function (c) {
      x += '<category name="' + esc(c.name) + '" id="' + c.id + '" colour="' + c.colour[0] + '" secondaryColour="' + c.colour[2] + '"' +
        (c.custom ? ' custom="' + c.custom + '"' : '') + '>';
      if (!c.custom) {
        catBlocks(c.id).forEach(function (b) {
          if (LABEL_BEFORE[b.type]) x += '<label text="' + esc(LABEL_BEFORE[b.type]) + '"></label>';
          x += blockXml(b);
        });
      }
      x += '</category>';
    });
    return x + '</xml>';
  };

  function byType(t) { return SPEC.blocks.filter(function (b) { return b.type === t; })[0]; }

  // 变量分类：三个按钮 + 每种变量的积木（和 Mind+ 一样分开放）
  window.MP_VARIABLE_FLYOUT = function (ws) {
    var xml = [];
    function btn(text, key) {
      var el = document.createElement('button');
      el.setAttribute('text', text);
      el.setAttribute('callbackKey', key);
      xml.push(el);
    }
    function add(str) { xml.push(Blockly.Xml.textToDom('<xml>' + str + '</xml>').firstChild); }
    function withVar(type, fieldName, v) {
      var b = byType(type);
      var s = blockXml(b).replace('<block type="' + type + '">',
        '<block type="' + type + '"><field name="' + fieldName + '" id="' + esc(v.getId()) + '" variabletype="' + esc(v.type) + '">' + esc(v.name) + '</field>');
      add(s);
    }
    btn('新建数字类型变量', 'MP_NEW_NUMBER');
    var nums = ws.getVariablesOfType('');
    nums.sort(function (a, b) { return a.name.localeCompare(b.name); });
    nums.forEach(function (v) { add('<block type="data_variable"><field name="VARIABLE" id="' + esc(v.getId()) + '" variabletype="">' + esc(v.name) + '</field></block>'); });
    if (nums.length) {
      withVar('data_setvariableto', 'VARIABLE', nums[0]);
      withVar('data_changevariableby', 'VARIABLE', nums[0]);
    }
    btn('新建字符串类型变量', 'MP_NEW_STRING');
    var strs = ws.getVariablesOfType('string');
    strs.sort(function (a, b) { return a.name.localeCompare(b.name); });
    strs.forEach(function (v) { add('<block type="data_string"><field name="STRING" id="' + esc(v.getId()) + '" variabletype="string">' + esc(v.name) + '</field></block>'); });
    if (strs.length) {
      withVar('data_setStringto', 'STRING', strs[0]);
      withVar('data_stringStartWith', 'STRING', strs[0]);
    }
    btn('新建列表', 'MP_NEW_LIST');
    var lists = ws.getVariablesOfType('list');
    lists.sort(function (a, b) { return a.name.localeCompare(b.name); });
    lists.forEach(function (v) { add('<block type="data_listcontents"><field name="LIST" id="' + esc(v.getId()) + '" variabletype="list">' + esc(v.name) + '</field></block>'); });
    if (lists.length) {
      ['data_addtolist', 'data_deleteoflist', 'data_deletealloflist', 'data_insertatlist', 'data_replaceitemoflist',
        'data_itemoflist', 'data_itemnumoflist', 'data_lengthoflist', 'data_listcontainsitem'].forEach(function (t) { withVar(t, 'LIST', lists[0]); });
    }
    return xml;
  };

  window.MP_CATS = CATS;
  window.MP_CAT_CB = window.MP_CAT_CB || {};
  window.MP_CAT_CB.MP_VARIABLE = window.MP_VARIABLE_FLYOUT;
})();
