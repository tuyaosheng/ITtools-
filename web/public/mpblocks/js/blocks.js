// 平台图形化编程（掌控板）：按 Mind+ 2.0.7 上传模式的积木规格（spec.js）定义积木，外形用 scratch-blocks（和 Mind+ 同一套渲染）。
// 这里只管积木长什么样；生成 MicroPython 见 codegen.js，保存成 .mpcode 见 project.js。
(function () {
  'use strict';
  var SPEC = window.MP_SPEC;

  // ---------------- 颜色（主色 / 次色 / 描边，取自 Mind+）----------------
  var COLOURS = {
    control: ['#FFAB19', '#EC9C13', '#CF8B17'],
    operators: ['#59C059', '#46B946', '#389438'],
    var_number: ['#FF8C1A', '#FF8000', '#DB6E00'],
    var_string: ['#921AFF', '#8000FF', '#7000DB'],
    var_list: ['#FF661A', '#FF5500', '#E64D00'],
    myBlocks: ['#FF6680', '#FF4D6A', '#FF3355'],
    handpy: ['#87C482', '#7FB57B', '#559E4F'],
    net: ['#8B92E8', '#6E78E6', '#3E46A7']
  };
  function coloursOf(b) {
    if (/^(dev-DFRobot-handpy)/.test(b.type)) return COLOURS.handpy;
    if (/^ext-DFRobot-(MQTT|WIFI)/.test(b.type)) return COLOURS.net;
    if (b.cat === 'control') return COLOURS.control;
    if (b.cat === 'operators') return COLOURS.operators;
    if (b.colour === '#921AFF') return COLOURS.var_string;
    if (b.colour === '#FF661A') return COLOURS.var_list;
    if (b.cat === 'variables') return COLOURS.var_number;
    return [b.colour, b.colour, b.colour];
  }
  window.MP_COLOURS = COLOURS;

  // 这一版 scratch-blocks 带着“猫耳朵帽子”（愚人节彩蛋）：换回标准的圆弧帽子，和 Mind+ 一样
  Blockly.BlockSvg.START_HAT_HEIGHT = 16;
  Blockly.BlockSvg.START_HAT_PATH = 'c 25,-22 71,-22 96,0';
  Blockly.BlockSvg.TOP_LEFT_CORNER_DEFINE_HAT = 'a ' + Blockly.BlockSvg.DEFINE_HAT_CORNER_RADIUS + ',' + Blockly.BlockSvg.DEFINE_HAT_CORNER_RADIUS + ' 0 0,1 ' +
    Blockly.BlockSvg.DEFINE_HAT_CORNER_RADIUS + ',-' + Blockly.BlockSvg.DEFINE_HAT_CORNER_RADIUS;
  Blockly.BlockSvg.prototype.renderCatFace_ = function () {};
  Blockly.BlockSvg.prototype.initCatStuff = function () {};
  // 积木区（左边弹出的那一列）加宽：掌控板的长积木不被切掉
  Blockly.VerticalFlyout.prototype.DEFAULT_WIDTH = 340;
  Blockly.Toolbox.prototype.width = 60 + 340;   // 工具箱总宽 = 分类栏 60 + 积木区

  // 自定义分类（变量）的弹出内容：工具箱建好之前就要能找到回调
  window.MP_CAT_CB = window.MP_CAT_CB || {};
  var origCatCb = Blockly.WorkspaceSvg.prototype.getToolboxCategoryCallback;
  Blockly.WorkspaceSvg.prototype.getToolboxCategoryCallback = function (name) {
    return window.MP_CAT_CB[name] || origCatCb.call(this, name);
  };

  // ---------------- 积木左边的小图标（平台自己画的，位置和 Mind+ 一样）----------------
  function svgUri(s) { return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(s); }
  var ICONS = {
    handpy: svgUri('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><g fill="none" stroke="#fff" stroke-width="2.6" stroke-linecap="round"><circle cx="15" cy="25" r="8"/><path d="M12 25h6M15 22v6"/><path d="M19 15l9-9M22 19l11-11M25 23l9-9"/></g></svg>'),
    wifi: svgUri('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><g fill="none" stroke="#fff" stroke-width="2.8" stroke-linecap="round"><path d="M8 17a17 17 0 0 1 24 0"/><path d="M12.5 21.5a10.5 10.5 0 0 1 15 0"/><path d="M17 26a4 4 0 0 1 6 0"/></g><circle cx="20" cy="30" r="2.2" fill="#fff"/></svg>'),
    mqtt: svgUri('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><g fill="none" stroke="#fff" stroke-width="2.6" stroke-linecap="round"><path d="M9 14a15 15 0 0 1 22 0"/><path d="M13 18a9 9 0 0 1 14 0"/></g><circle cx="20" cy="21.5" r="2" fill="#fff"/><text x="20" y="33" font-size="9" font-family="Arial" font-weight="bold" fill="#fff" text-anchor="middle">MQTT</text></svg>'),
    plus: svgUri('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="none" stroke="#fff" stroke-width="2"/><path d="M12 7v10M7 12h10" stroke="#fff" stroke-width="2.4" stroke-linecap="round"/></svg>'),
    minus: svgUri('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="none" stroke="#fff" stroke-width="2"/><path d="M7 12h10" stroke="#fff" stroke-width="2.4" stroke-linecap="round"/></svg>'),
    gear: svgUri('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="11" fill="#fff"/><path fill="#8B92E8" d="M12 7.2l1-.2.4-1.6h1.6l.4 1.6 1 .5 1.4-.9 1.1 1.1-.9 1.4.5 1 1.6.4v1.6l-1.6.4-.5 1 .9 1.4-1.1 1.1-1.4-.9-1 .5-.4 1.6h-1.6l-.4-1.6-1-.5-1.4.9-1.1-1.1.9-1.4-.5-1-1.6-.4v-1.6l1.6-.4.5-1-.9-1.4 1.1-1.1 1.4.9zM12 9.6a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8z" transform="translate(-1.6 -1.6) scale(1.12)"/></svg>'),
    eye: svgUri('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 24"><rect x="1" y="1" width="30" height="22" rx="11" fill="none" stroke="#fff" stroke-width="1.6" opacity=".7"/><path d="M6 12s4-6 10-6 10 6 10 6-4 6-10 6-10-6-10-6z" fill="none" stroke="#fff" stroke-width="2"/><circle cx="16" cy="12" r="3" fill="#fff"/></svg>'),
    image: svgUri('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect x="2" y="4" width="20" height="16" rx="2" fill="none" stroke="#fff" stroke-width="2"/><circle cx="8" cy="10" r="2" fill="#fff"/><path d="M3 18l6-5 4 3 4-4 4 4" fill="none" stroke="#fff" stroke-width="2"/></svg>')
  };
  window.MP_ICONS = ICONS;
  function iconOf(type) {
    if (/^dev-DFRobot-handpy/.test(type)) return ICONS.handpy;
    if (/^ext-DFRobot-WIFI/.test(type)) return ICONS.wifi;
    if (/^ext-DFRobot-MQTT/.test(type)) return ICONS.mqtt;
    return null;
  }

  // ---------------- 自定义字段 ----------------
  // scratch-blocks 没有 Blockly.utils.inherits，自己写一个（和 goog.inherits 一样）
  function inherits(Child, Parent) {
    function Tmp() {}
    Tmp.prototype = Parent.prototype;
    Child.superClass_ = Parent.prototype;
    Child.prototype = new Tmp();
    Child.prototype.constructor = Child;
  }
  // 带引号的文字框：Mind+ 里字符串参数显示成 " Mind+ "
  function FieldQuotedText(text, opt_validator) {
    FieldQuotedText.superClass_.constructor.call(this, text, opt_validator);
  }
  inherits(FieldQuotedText, Blockly.FieldTextInput);
  FieldQuotedText.fromJson = function (o) { return new FieldQuotedText(o.text); };
  // Mind+ 的样子：文字加引号，数字和空的不加
  FieldQuotedText.prototype.getDisplayText_ = function () {
    var t = FieldQuotedText.superClass_.getDisplayText_.call(this);
    var raw = this.getText();
    if (raw === '' || /^\s*-?(\d+(\.\d*)?|\.\d+)\s*$/.test(raw)) return t;
    return '" ' + t + ' "';
  };
  Blockly.Field.register('field_text_quoted', FieldQuotedText);

  // 可点的小按钮（MQTT 设置、预览、选图片、+/-）：点了调 onClick
  function FieldButton(src, w, h, alt, onClick) {
    FieldButton.superClass_.constructor.call(this, src, w, h, alt, false);
    this.onClick_ = onClick;
    this.EDITABLE = true;
  }
  inherits(FieldButton, Blockly.FieldImage);
  FieldButton.prototype.showEditor_ = function () { if (this.onClick_) this.onClick_.call(this, this.sourceBlock_); };
  FieldButton.prototype.SERIALIZABLE = false;
  // FieldImage.init 不像 Field.init 那样绑 mousedown，不补上的话鼠标点了不会进 showEditor_（dispose 时 Field 会解绑）
  FieldButton.prototype.init = function () {
    FieldButton.superClass_.init.call(this);
    if (!this.mouseDownWrapper_ && this.fieldGroup_) {
      this.mouseDownWrapper_ = Blockly.bindEventWithChecks_(this.fieldGroup_, 'mousedown', this, this.onMouseDown_);
    }
  };
  window.MPFieldButton = FieldButton;

  // 带隐藏值的按钮：值存在 field 里（MQTT 设置 JSON、图片），显示成图标
  function FieldValueButton(value, src, w, h, onClick) {
    FieldValueButton.superClass_.constructor.call(this, src, w, h, '', onClick);
    // FieldImage 构造时用 setValue(src) 设图标，这里 setValue 被改成存值了，要补上图标地址
    this.src_ = src;
    this.value_ = value || '';
    this.SERIALIZABLE = true;
  }
  inherits(FieldValueButton, FieldButton);
  FieldValueButton.prototype.getValue = function () { return this.value_; };
  FieldValueButton.prototype.setValue = function (v) {
    if (v === null || v === undefined) return;
    var old = this.value_;
    this.value_ = String(v);
    if (this.sourceBlock_ && Blockly.Events.isEnabled() && old !== this.value_) {
      Blockly.Events.fire(new Blockly.Events.BlockChange(this.sourceBlock_, 'field', this.name, old, this.value_));
    }
  };
  FieldValueButton.prototype.getText = function () { return ''; };
  // FieldImage.init 里也用 setValue(src_) 画图标：init 期间走父类的 setValue，不碰存的值
  FieldValueButton.prototype.init = function () {
    var own = this.value_;
    this.setValue = Blockly.FieldImage.prototype.setValue;
    try { FieldValueButton.superClass_.init.call(this); } finally { delete this.setValue; this.value_ = own; }
  };

  // 钢琴音符：显示“1 低 C/C3”，值是频率（和 Mind+ 一样，代码里写 131）
  var NOTE_NAMES = [['1', 'C'], ['1#', 'C#'], ['2', 'D'], ['2#', 'D#'], ['3', 'E'], ['4', 'F'], ['4#', 'F#'], ['5', 'G'], ['5#', 'G#'], ['6', 'A'], ['6#', 'A#'], ['7', 'B']];
  var PIANO = [];
  [['低', 3], ['中', 4], ['高', 5]].forEach(function (o, oi) {
    NOTE_NAMES.forEach(function (n, i) {
      var midi = 48 + oi * 12 + i;
      var f = Math.round(440 * Math.pow(2, (midi - 69) / 12));
      PIANO.push([n[0] + ' ' + o[0] + ' ' + n[1] + '/' + n[1] + o[1], String(f)]);
    });
  });
  window.MP_PIANO = PIANO;

  // ---------------- 影子积木（参数框）----------------
  function shadowBlock(type, json) {
    Blockly.Blocks[type] = { init: function () { this.jsonInit(json); } };
  }
  shadowBlock('mp_text_q', { message0: '%1', args0: [{ type: 'field_text_quoted', name: 'TEXT', text: '' }], output: 'String', outputShape: Blockly.OUTPUT_SHAPE_ROUND, colour: '#FFFFFF', colourSecondary: '#FFFFFF', colourTertiary: '#FFFFFF' });
  // 带范围的数字（Mind+ 的 slider）：外形同数字框
  Blockly.Blocks['math_slider'] = {
    init: function () {
      this.jsonInit({ message0: '%1', args0: [{ type: 'field_number', name: 'NUM', value: 0 }], output: 'Number', outputShape: Blockly.OUTPUT_SHAPE_ROUND, colour: '#FFFFFF', colourSecondary: '#FFFFFF', colourTertiary: '#FFFFFF' });
    }
  };
  Blockly.Blocks['piano'] = {
    init: function () {
      this.jsonInit({ message0: '%1', args0: [{ type: 'field_dropdown', name: 'PIANO', options: PIANO }], output: 'Number', outputShape: Blockly.OUTPUT_SHAPE_ROUND, extensions: ['colours_more'] });
      this.setColour('#FFFFFF', '#FFFFFF', '#CCCCCC');
      // scratch-blocks 的下拉文字固定白色（!important），白底上看不见：给这个积木加类名，CSS 里改成深色（和 Mind+ 一样白底黑字）
      if (this.svgGroup_) Blockly.utils.addClass(this.svgGroup_, 'mp-white-shadow');
    }
  };
  Blockly.Blocks['colour_palette'] = {
    init: function () {
      this.jsonInit({ message0: '%1', args0: [{ type: 'field_colour_slider', name: 'COLOUR', colour: '#0000ff' }], output: 'Colour', outputShape: Blockly.OUTPUT_SHAPE_ROUND, colour: '#FFFFFF', colourSecondary: '#FFFFFF', colourTertiary: '#FFFFFF' });
    }
  };
  // MQTT 初始化参数的齿轮：值是 Mind+ 一样的 JSON（platform、params.topic、siotwww…）
  var MQTT_DEFAULT = '{"platform":"siot","params":{"siotwww":"","siotid":"siot","siotpwd":"dfrobot","topic":""}}';
  Blockly.Blocks['mqtt_settings'] = {
    init: function () {
      this.appendDummyInput().appendField(new FieldValueButton(MQTT_DEFAULT, ICONS.gear, 26, 26, function (block) {
        var f = this;
        if (window.MPApp && window.MPApp.editMqtt) window.MPApp.editMqtt(f.getValue(), function (v) { f.setValue(v); });
      }), 'MQTT');
      this.setOutput(true, 'setting');
      this.setOutputShape(Blockly.OUTPUT_SHAPE_ROUND);
      this.setColour(COLOURS.net[0], COLOURS.net[1], COLOURS.net[2]);
    }
  };
  Blockly.Blocks['text_preview'] = {
    init: function () {
      this.appendDummyInput().appendField(new FieldButton(ICONS.eye, 32, 24, '预览', function (block) {
        if (window.MPApp && window.MPApp.previewText) window.MPApp.previewText(block.getParent());
      }));
      this.setOutput(true, null);
      this.setOutputShape(Blockly.OUTPUT_SHAPE_ROUND);
      this.setColour(COLOURS.handpy[1], COLOURS.handpy[1], COLOURS.handpy[2]);
    }
  };
  Blockly.Blocks['img_preview'] = {
    init: function () {
      this.appendDummyInput().appendField(new FieldValueButton('', ICONS.image, 26, 26, function () {
        var f = this;
        if (window.MPApp && window.MPApp.pickImage) window.MPApp.pickImage(f.getValue(), function (v) { f.setValue(v); });
      }), 'ESP32_IMG');
      this.setOutput(true, 'setting');
      this.setOutputShape(Blockly.OUTPUT_SHAPE_ROUND);
      this.setColour(COLOURS.handpy[1], COLOURS.handpy[1], COLOURS.handpy[2]);
    }
  };

  // 下拉菜单影子（Mind+ 扩展的 menu_xxx）：和父积木同色的圆角下拉
  function defineMenu(type, field, options, colours) {
    Blockly.Blocks[type] = {
      init: function () {
        this.jsonInit({ message0: '%1', args0: [{ type: 'field_dropdown', name: field, options: options }], output: 'String', outputShape: Blockly.OUTPUT_SHAPE_ROUND });
        this.setColour(colours[1], colours[1], colours[2]);
      }
    };
  }
  Object.keys(SPEC.menus).forEach(function (t) {
    var m = SPEC.menus[t];
    defineMenu(t, m.field, m.options, COLOURS.handpy);
  });

  // ---------------- 主积木 ----------------
  var GETTERS = { data_variable: 1, data_string: 1, data_listcontents: 1 };
  var SHAPE_EXT = { hat: 'shape_hat', stack: 'shape_statement', end: 'shape_end', c: 'shape_statement', c_end: 'shape_end' };
  function argJson(a, type) {
    if (a.type === 'field_dropdown') return { type: 'field_dropdown', name: a.name, options: a.options.length ? a.options : [['', '']] };
    if (a.type === 'field_variable') {
      // 变量本身（圆角积木）只显示名字，没有下拉
      if (GETTERS[type]) return { type: 'field_variable_getter', name: a.name, text: a.value || '', variableType: a.variableTypes[0] };
      return { type: 'field_variable', name: a.name, variable: a.value || null, variableTypes: a.variableTypes, defaultType: a.variableTypes[0] };
    }
    if (a.type === 'field_input') return { type: 'field_input', name: a.name, text: a.value || '' };
    if (a.type === 'input_statement') return { type: 'input_statement', name: a.name };
    // 没有参数框的输入口在 Mind+ 里都是放条件的六边形口
    if (!a.shadow) return { type: 'input_value', name: a.name, check: 'Boolean' };
    return { type: 'input_value', name: a.name };
  }

  // spec 里的积木 → scratch-blocks 定义。if_else_special 另外写（可以加“否则如果”）
  var SKIP = { control_if_else_special: 1 };
  window.MP_BLOCK_INFO = {};
  SPEC.blocks.forEach(function (b) {
    window.MP_BLOCK_INFO[b.type] = b;
    if (SKIP[b.type]) return;
    var cols = coloursOf(b);
    var json = { colour: cols[0], colourSecondary: cols[1], colourTertiary: cols[2] };
    var icon = iconOf(b.type);
    var isEventReporter = /_eventHeadInner_$/.test(b.type);
    b.lines.forEach(function (ln, i) {
      var text = ln.text, args = ln.args.map(function (a) { return argJson(a, b.type); });
      if (text === '*' && !args.length) {
        // Mind+ 循环积木右下角的 ↻（导出时只留下了图片的替代文字 *）
        json['message' + i] = '%1';
        json['args' + i] = [{ type: 'field_image', src: 'vendor/scratch-blocks/media/repeat.svg', width: 24, height: 24, alt: '*', flip_rtl: true }];
        json['lastDummyAlign' + i] = 'RIGHT';
        return;
      }
      if (i === 0 && icon && !isEventReporter) {
        // 扩展积木：最左边一个图标和一条竖线（Mind+ 的样子）
        text = '%1 %2 ' + text.replace(/%(\d+)/g, function (_, n) { return '%' + (Number(n) + 2); });
        args = [{ type: 'field_image', src: icon, width: 40, height: 40 }, { type: 'field_vertical_separator' }].concat(args);
      }
      json['message' + i] = text || ' ';
      json['args' + i] = args;
    });
    if (b.shape === 'reporter' || b.shape === 'boolean') {
      json.output = b.shape === 'boolean' ? 'Boolean' : null;
      json.outputShape = b.shape === 'boolean' ? Blockly.OUTPUT_SHAPE_HEXAGONAL : Blockly.OUTPUT_SHAPE_ROUND;
    } else {
      json.extensions = [SHAPE_EXT[b.shape]];
    }
    if (icon && !isEventReporter) json.extensions = (json.extensions || []).concat(['scratch_extension']);

    Blockly.Blocks[b.type] = {
      init: function () {
        this.jsonInit(json);
        if (isEventReporter) this.setOutputShape(Blockly.OUTPUT_SHAPE_ROUND);
      }
    };
  });

  // 事件积木里的“MQTT消息”“调试信息”这种参数：像 Scratch 自定义积木的参数一样，拖出来是一份拷贝
  var origIsArg = Blockly.scratchBlocksUtils.isShadowArgumentReporter;
  Blockly.scratchBlocksUtils.isShadowArgumentReporter = function (block) {
    return origIsArg(block) || (block.isShadow() && /_eventHeadInner_$/.test(block.type));
  };

  // ---------------- 如果……那么 +（可以加“否则如果”“否则”）----------------
  Blockly.Blocks['control_if_else_special'] = {
    init: function () {
      this.elseifCount_ = 0;
      this.elseCount_ = 0;
      this.setColour(COLOURS.control[0], COLOURS.control[1], COLOURS.control[2]);
      this.setPreviousStatement(true);
      this.setNextStatement(true);
      this.setOutputShape(Blockly.OUTPUT_SHAPE_SQUARE);
      this.rebuild_();
    },
    mutationToDom: function () {
      var m = document.createElement('mutation');
      m.setAttribute('elseif', this.elseifCount_);
      m.setAttribute('else', this.elseCount_);
      return m;
    },
    domToMutation: function (xml) {
      this.elseifCount_ = parseInt(xml.getAttribute('elseif'), 10) || 0;
      this.elseCount_ = parseInt(xml.getAttribute('else'), 10) || 0;
      this.rebuild_();
    },
    rebuild_: function () {
      // 保留已经接上的积木
      var saved = {};
      var self = this;
      this.inputList.slice().forEach(function (inp) {
        if (inp.connection && inp.connection.targetBlock()) saved[inp.name] = inp.connection.targetBlock();
      });
      while (this.inputList.length) this.removeInput(this.inputList[0].name);
      this.appendValueInput('IF0').setCheck('Boolean').appendField('如果');
      this.appendDummyInput('THEN0').appendField('那么');
      this.appendStatementInput('DO0');
      for (var i = 1; i <= this.elseifCount_; i++) {
        this.appendValueInput('IF' + i).setCheck('Boolean').appendField('否则如果');
        this.appendDummyInput('THEN' + i).appendField('那么');
        this.appendStatementInput('DO' + i);
      }
      if (this.elseCount_) {
        this.appendDummyInput('ELSE_LABEL').appendField('否则');
        this.appendStatementInput('ELSE');
      }
      var btns = this.appendDummyInput('BUTTONS');
      if (this.elseifCount_ || this.elseCount_) {
        btns.appendField(new FieldButton(ICONS.minus, 20, 20, '-', function () { self.changeBranches_(-1); }));
      }
      btns.appendField(new FieldButton(ICONS.plus, 20, 20, '+', function () { self.changeBranches_(1); }));
      Object.keys(saved).forEach(function (name) {
        var inp = self.getInput(name);
        var t = saved[name];
        if (!inp || t.isDisposed && t.isDisposed()) return;
        var conn = t.outputConnection || t.previousConnection;
        if (conn && !conn.isConnected()) inp.connection.connect(conn);
      });
    },
    // “+”：先加“否则”，再加“否则如果”；“−”：先去“否则如果”，最后去“否则”
    changeBranches_: function (d) {
      var old = Blockly.Xml.domToText(this.mutationToDom());
      if (d > 0) { if (!this.elseCount_) this.elseCount_ = 1; else this.elseifCount_++; }
      else if (this.elseifCount_) this.elseifCount_--;
      else this.elseCount_ = 0;
      this.rebuild_();
      if (this.rendered) this.render();
      Blockly.Events.fire(new Blockly.Events.BlockChange(this, 'mutation', null, old, Blockly.Xml.domToText(this.mutationToDom())));
    }
  };
})();
