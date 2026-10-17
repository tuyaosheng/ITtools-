// 掌控板 3.0 专用积木 + MicroPython 代码生成。
// 接口名照 labplus-cn/mpython_esp32s3（dev 分支 boards/mpython_pro/modules/mpython.py）和
// labplus-cn/mpython-esp32s3-doc 的示例核对过：light.read()（勒克斯）、sound.read()、
// button_a.is_pressed()、touchpad_p.is_pressed()/read()、accelerometer/gyroscope.get_x()、
// magnetic.get_heading()、rgb[i]=(r,g,b)/rgb.write()、lv_gui 的 GUI().draw_label()、music.play()。
// 只认 V3：V2 是 OLED（oled.DispChar），V3 是彩屏（lv_gui），屏幕积木两代写法不通用。
// 积木外观（分类、配色、中文措辞）参照 mPython 的习惯，学生以后换到官方软件不会觉得陌生。
(function () {
  "use strict";

  var COLOR = {
    input: "#1E88E5", display: "#0097A7", rgb: "#E53935", music: "#7CB342", net: "#00A884",
    loop: "#E69A1A", logic: "#C34FD1", math: "#14A39A", text: "#8D6E63", variable: "#EC407A",
    procedure: "#5C6BC0"
  };
  window.MPL_COLOR = COLOR;
  var gen = python.pythonGenerator;
  var Order = python.Order;
  gen.INDENT = "    ";
  // 学生建一个叫 light / rgb 的变量，会把板子上的传感器对象盖掉，之后整段程序莫名其妙读不到数——
  // 登记成保留字，Blockly 生成代码时会自动改名成 light2 之类。
  gen.addReservedWords("light,rgb,sound,button_a,button_b,touchpad_p,touchpad_y,touchpad_t,touchpad_h," +
    "touchpad_o,touchpad_n,accelerometer,gyroscope,magnetic,motion,gui,music,time,numberMap,i2c");

  // 上课时由页面在生成代码前填好：服务器地址取老师电脑的局域网 IP（不是 localhost——
  // 板子上的 localhost 指的是板子自己），组号取学生在页面上选的那个。
  window.MPL_RUNTIME = window.MPL_RUNTIME || { host: "192.168.0.100:8790", group: "1" };

  var BTN = [["A", "a"], ["B", "b"]];
  var PADS = [["P", "p"], ["Y", "y"], ["T", "t"], ["H", "h"], ["O", "o"], ["N", "n"]];
  var AXES = [["X", "x"], ["Y", "y"], ["Z", "z"]];
  var LEDS = [["全部", "all"], ["1 号", "0"], ["2 号", "1"], ["3 号", "2"]];
  var FILL = [["实心", "1"], ["空心", "0"]];
  var MELODIES = [
    ["生日快乐", "BIRTHDAY"], ["欢乐颂", "ODE"], ["婚礼进行曲", "WEDDING"], ["娱乐者", "ENTERTAINER"],
    ["前奏曲", "PRELUDE"], ["彩虹猫", "NYAN"], ["铃声", "RINGTONE"], ["放克", "FUNK"], ["布鲁斯", "BLUES"],
    ["哒哒哒哒", "DADADADUM"], ["笑点", "PUNCHLINE"], ["Python", "PYTHON"], ["坏蛋", "BADDY"],
    ["追逐", "CHASE"], ["叮咚", "BA_DING"], ["哇哇哇", "WAWAWAWAA"], ["升调", "JUMP_UP"], ["降调", "JUMP_DOWN"],
    ["能量提升", "POWER_UP"], ["能量下降", "POWER_DOWN"], ["葬礼进行曲", "FUNERAL"],
    ["歌唱祖国", "GE_CHANG_ZU_GUO"], ["东方红", "DONG_FANG_HONG"], ["彩云追月", "CAI_YUN_ZHUI_YUE"],
    ["走进新时代", "ZOU_JIN_XIN_SHI_DAI"], ["茉莉花", "MO_LI_HUA"], ["沂蒙山小调", "YI_MENG_SHAN_XIAO_DIAO"]
  ];
  var NOTES = [["低音 6 (A3)", "A3"], ["低音 7 (B3)", "B3"], ["1 (C4)", "C4"], ["2 (D4)", "D4"], ["3 (E4)", "E4"],
    ["4 (F4)", "F4"], ["5 (G4)", "G4"], ["6 (A4)", "A4"], ["7 (B4)", "B4"], ["高音 1 (C5)", "C5"],
    ["高音 2 (D5)", "D5"], ["高音 3 (E5)", "E5"], ["休止", "R"]];
  // music 的时值单位是“拍的四分之一”：C4:4 是一拍
  var BEATS = [["1 拍", "4"], ["半拍", "2"], ["2 拍", "8"], ["4 拍", "16"]];

  function stmt(def) { def.previousStatement = null; def.nextStatement = null; return def; }
  function value(name) { return { type: "input_value", name: name }; }

  Blockly.defineBlocksWithJsonArray([
    // ---------------- 程序骨架 / 循环 ----------------
    {
      type: "mpl_main",
      message0: "当掌控板启动时 %1 %2",
      args0: [{ type: "input_dummy" }, { type: "input_statement", name: "DO" }],
      colour: COLOR.loop,
      tooltip: "掌控板一通电（或点“刷入”）就从这里开始往下执行。",
      deletable: false,
      movable: true
    },
    {
      type: "mpl_forever",
      message0: "一直重复 %1 %2",
      args0: [{ type: "input_dummy" }, { type: "input_statement", name: "DO" }],
      previousStatement: null,
      colour: COLOR.loop,
      tooltip: "里面的积木会一遍又一遍地执行，永不停止。"
    },
    stmt({
      type: "mpl_sleep",
      message0: "等待 %1 秒",
      args0: [{ type: "field_number", name: "SEC", value: 0.2, min: 0, precision: 0.1 }],
      colour: COLOR.loop
    }),

    // ---------------- 输入：按键 / 触摸 / 传感器 ----------------
    {
      type: "mpl_button",
      message0: "按键 %1 %2",
      args0: [{ type: "field_dropdown", name: "BTN", options: BTN },
              { type: "field_dropdown", name: "STATE", options: [["正被按着", "down"], ["没被按", "up"]] }],
      output: "Boolean",
      colour: COLOR.input,
      tooltip: "掌控板上沿的 A、B 两个按键。放进“如果”里用。"
    },
    {
      type: "mpl_on_button",
      message0: "当按键 %1 被 %2 时 %3 %4",
      args0: [{ type: "field_dropdown", name: "BTN", options: BTN },
              { type: "field_dropdown", name: "EVT", options: [["按下", "pressed"], ["松开", "released"]] },
              { type: "input_dummy" }, { type: "input_statement", name: "DO" }],
      colour: COLOR.input,
      tooltip: "不用放进“当掌控板启动时”，单独摆在空白处就生效：按一下键，里面的积木就跑一遍。"
    },
    {
      type: "mpl_touch",
      message0: "触摸键 %1 %2",
      args0: [{ type: "field_dropdown", name: "PAD", options: PADS },
              { type: "field_dropdown", name: "STATE", options: [["被摸着", "down"], ["没被摸", "up"]] }],
      output: "Boolean",
      colour: COLOR.input,
      tooltip: "掌控板下沿金手指上的 P Y T H O N 六个触摸键。"
    },
    {
      type: "mpl_on_touch",
      message0: "当触摸键 %1 被 %2 时 %3 %4",
      args0: [{ type: "field_dropdown", name: "PAD", options: PADS },
              { type: "field_dropdown", name: "EVT", options: [["摸到", "pressed"], ["松开", "released"]] },
              { type: "input_dummy" }, { type: "input_statement", name: "DO" }],
      colour: COLOR.input,
      tooltip: "单独摆在空白处就生效：手指一碰这个键，里面的积木就跑一遍。"
    },
    {
      type: "mpl_touch_value",
      message0: "触摸键 %1 的数值",
      args0: [{ type: "field_dropdown", name: "PAD", options: PADS }],
      output: "Number",
      colour: COLOR.input,
      tooltip: "触摸键读到的原始数值，手指碰上去数字会明显变化。"
    },
    {
      type: "mpl_light",
      message0: "光线传感器的数值",
      output: "Number",
      colour: COLOR.input,
      tooltip: "单位是勒克斯（lux），越亮数字越大。教室里一般几百，手掌盖住会掉到几十以下。"
    },
    {
      type: "mpl_sound",
      message0: "声音的响度",
      output: "Number",
      colour: COLOR.input,
      tooltip: "麦克风读到的数值，0 到 4095，声音越大数字越大。拍拍手试试看。"
    },
    {
      type: "mpl_accel",
      message0: "加速度 %1 轴",
      args0: [{ type: "field_dropdown", name: "AXIS", options: AXES }],
      output: "Number",
      colour: COLOR.input,
      tooltip: "单位是 g（重力加速度）。平放在桌上时 Z 轴约为 -1，X、Y 约为 0。"
    },
    {
      type: "mpl_angle",
      message0: "掌控板的 %1",
      args0: [{ type: "field_dropdown", name: "WHICH", options: [["左右倾斜角度", "0"], ["前后倾斜角度", "1"]] }],
      output: "Number",
      colour: COLOR.input,
      tooltip: "单位是度。放平时接近 0，往一边歪是正数，往另一边歪是负数。"
    },
    {
      type: "mpl_shaken",
      message0: "掌控板被摇晃",
      output: "Boolean",
      colour: COLOR.input,
      tooltip: "用力晃一下掌控板就成立。"
    },
    {
      type: "mpl_gyro",
      message0: "角速度 %1 轴",
      args0: [{ type: "field_dropdown", name: "AXIS", options: AXES }],
      output: "Number",
      colour: COLOR.input,
      tooltip: "掌控板绕这个轴转得多快。不动时接近 0，转得越快数字越大。"
    },
    {
      type: "mpl_magnetic",
      message0: "磁力计 %1",
      args0: [{ type: "field_dropdown", name: "WHAT", options: [
        ["指南针方向（度）", "heading"], ["磁场强度", "strength"],
        ["X 轴磁场", "x"], ["Y 轴磁场", "y"], ["Z 轴磁场", "z"]] }],
      output: "Number",
      colour: COLOR.input,
      tooltip: "指南针方向：0 度是北，90 度是东。拿块磁铁靠近，磁场强度会猛涨。"
    },

    // ---------------- 显示：彩屏 / 控制台 ----------------
    stmt({
      type: "mpl_gui_text",
      message0: "屏幕第 %1 行显示 %2 颜色 %3",
      args0: [{ type: "field_dropdown", name: "ROW", options: [["1", "1"], ["2", "2"], ["3", "3"], ["4", "4"], ["5", "5"]] },
              value("V"), { type: "field_colour", name: "C", colour: "#ffffff" }],
      inputsInline: true,
      colour: COLOR.display,
      tooltip: "数字、文字都能显示，中文也可以。"
    }),
    stmt({
      type: "mpl_gui_text_xy",
      message0: "屏幕在 x %1 y %2 处显示 %3 颜色 %4",
      args0: [value("X"), value("Y"), value("V"), { type: "field_colour", name: "C", colour: "#ffffff" }],
      inputsInline: true,
      colour: COLOR.display,
      tooltip: "屏幕宽 320、高 172，左上角是 (0, 0)，往右 x 变大，往下 y 变大。"
    }),
    stmt({
      type: "mpl_gui_clear",
      message0: "清空屏幕",
      colour: COLOR.display
    }),
    stmt({
      type: "mpl_gui_bg",
      message0: "屏幕背景设为 %1",
      args0: [{ type: "field_colour", name: "C", colour: "#000000" }],
      colour: COLOR.display
    }),
    stmt({
      type: "mpl_gui_circle",
      message0: "屏幕画 %1 圆 圆心 x %2 y %3 半径 %4 颜色 %5",
      args0: [{ type: "field_dropdown", name: "FILL", options: FILL }, value("X"), value("Y"), value("R"),
              { type: "field_colour", name: "C", colour: "#ffd54f" }],
      inputsInline: true,
      colour: COLOR.display
    }),
    stmt({
      type: "mpl_gui_rect",
      message0: "屏幕画 %1 矩形 x %2 y %3 宽 %4 高 %5 颜色 %6",
      args0: [{ type: "field_dropdown", name: "FILL", options: FILL }, value("X"), value("Y"), value("W"), value("H"),
              { type: "field_colour", name: "C", colour: "#4fc3f7" }],
      inputsInline: true,
      colour: COLOR.display
    }),
    stmt({
      type: "mpl_gui_line",
      message0: "屏幕画线 从 x %1 y %2 到 x %3 y %4 颜色 %5",
      args0: [value("X1"), value("Y1"), value("X2"), value("Y2"), { type: "field_colour", name: "C", colour: "#ffffff" }],
      inputsInline: true,
      colour: COLOR.display
    }),
    stmt({
      type: "mpl_print",
      message0: "在控制台显示 %1",
      args0: [value("V")],
      colour: COLOR.display,
      tooltip: "数值会出现在网页左下角的“板子说”里，用来看传感器读到了多少。"
    }),

    // ---------------- RGB 灯 ----------------
    stmt({
      type: "mpl_rgb_all",
      message0: "RGB 灯全部亮成 %1",
      args0: [{
        type: "field_dropdown", name: "C",
        options: [["暖黄色", "255,160,40"], ["白色", "255,255,255"], ["红色", "255,0,0"],
                  ["绿色", "0,255,0"], ["蓝色", "0,0,255"]]
      }],
      colour: COLOR.rgb
    }),
    stmt({
      type: "mpl_rgb_one",
      message0: "RGB 灯 %1 亮成 %2",
      args0: [{ type: "field_dropdown", name: "IDX", options: LEDS },
              { type: "field_colour", name: "C", colour: "#ff0000" }],
      colour: COLOR.rgb,
      tooltip: "掌控板正面有 3 颗 RGB 灯，从左到右是 1、2、3 号。"
    }),
    stmt({
      type: "mpl_rgb_mix",
      message0: "RGB 灯 %1 红 %2 绿 %3 蓝 %4",
      args0: [{ type: "field_dropdown", name: "IDX", options: LEDS }, value("R"), value("G"), value("B")],
      inputsInline: true,
      colour: COLOR.rgb,
      tooltip: "红、绿、蓝各 0 到 255，混出任意颜色。可以把传感器的数值接进来，让颜色跟着变。"
    }),
    stmt({
      type: "mpl_rgb_off",
      message0: "RGB 灯全部熄灭",
      colour: COLOR.rgb
    }),

    // ---------------- 音乐 ----------------
    stmt({
      type: "mpl_music_melody",
      message0: "播放内置乐曲 %1",
      args0: [{ type: "field_dropdown", name: "MEL", options: MELODIES }],
      colour: COLOR.music,
      tooltip: "播完这首才会接着往下执行。"
    }),
    stmt({
      type: "mpl_music_note",
      message0: "播放音符 %1 %2",
      args0: [{ type: "field_dropdown", name: "NOTE", options: NOTES },
              { type: "field_dropdown", name: "BEAT", options: BEATS }],
      colour: COLOR.music,
      tooltip: "一个接一个排起来就是一首曲子。"
    }),
    stmt({
      type: "mpl_music_pitch",
      message0: "发出频率 %1 赫兹的声音 %2 毫秒",
      args0: [value("F"), value("MS")],
      inputsInline: true,
      colour: COLOR.music,
      tooltip: "频率越高声音越尖。440 赫兹就是音乐里的“拉”。"
    }),

    // ---------------- 网页 ----------------
    {
      type: "mpl_wifi",
      message0: "连接 Wi-Fi 名称 %1 密码 %2",
      args0: [
        { type: "field_input", name: "SSID", text: "" },
        { type: "field_input", name: "PWD", text: "" }
      ],
      previousStatement: null,
      nextStatement: null,
      colour: COLOR.net,
      tooltip: "板子要先连上和老师电脑同一个 Wi-Fi，才能把消息发给网页。"
      // 已不放进工具箱（消息改走 USB 线）；定义留着，老的存档里有这块也能照常打开
    },

    // ---------------- 数学里补两块掌控板专用的 ----------------
    {
      type: "mpl_map",
      message0: "把 %1 从 %2 ~ %3 换算到 %4 ~ %5",
      args0: [value("V"), value("A"), value("B"), value("C"), value("D")],
      inputsInline: true,
      output: "Number",
      colour: COLOR.math,
      tooltip: "按比例换算。比如把光线 0~1000 换算成灯的亮度 0~255。"
    },
    {
      type: "mpl_ticks",
      message0: "开机到现在的毫秒数",
      output: "Number",
      colour: COLOR.math,
      tooltip: "1 秒 = 1000 毫秒。可以用来计时，比如测反应速度。"
    }
  ]);

  // ---------------- 告诉网页：每一关有自己的几句话 ----------------
  // 板子和网页之间只约定“暗号”（dark/loud/open…），中文说法只在积木上和网页上出现。
  // state 型（变暗/变亮、开门/关门）：同一句连着说只发一次——循环每 0.1 秒判断一次，
  // 不去重的话串口和老师电脑都会被同一句话刷屏。
  // pulse 型（拍手、摇一摇）：每一次都是新的一下，不能去重；但半秒内同一句只发一次，
  // 一下拍手持续零点几秒，循环会连着判断成立好几次。
  var SENDS = window.MPL_SENDS = {
    dark: { text: "变暗了" }, light: { text: "变亮了" },
    loud: { text: "有声音", pulse: true },
    open: { text: "开门" }, close: { text: "关门" },
    shake: { text: "摇了一下", pulse: true },
    // 自由农场关：3D 农场第一次收到板子的数据时记一笔（看板 / 通关用）。不是“告诉网页”能说的话，下拉框里不列
    farm: { text: "3D 农场收到数据", hidden: true },
    // 温室栽培关“智能温室栽培”：六种栽培操作。按住不放会每半秒发一次，温室那边每种操作有冷却，
    // 不会因为手按久了就连浇十次水
    water: { text: "浇水", pulse: true }, fert: { text: "施肥", pulse: true },
    glow: { text: "补光", pulse: true }, shade: { text: "遮阳", pulse: true },
    heat: { text: "加温", pulse: true }, vent: { text: "通风", pulse: true },
    // 栽培结果：由温室页面报上来（不是板子说的），只用于通关和看板
    harvest: { text: "收获了", hidden: true }, wither: { text: "植物枯死", hidden: true },
    // 汉堡店关：五种食材 + 上菜 + 撤掉一层。都是 pulse：双层肉饼就是连着两次“肉饼”，state 型会把第二片吞掉
    bg_bun: { text: "面包", pulse: true }, bg_meat: { text: "肉饼", pulse: true },
    bg_cheese: { text: "芝士", pulse: true }, bg_veg: { text: "生菜", pulse: true },
    bg_tom: { text: "番茄", pulse: true },
    bg_serve: { text: "上菜", pulse: true }, bg_undo: { text: "撤掉一层", pulse: true },
    bg_pass: { text: "汉堡店攒够分了", hidden: true },
    // 认识掌控板关：碰一下哪个部件就报哪句。都是 pulse：同一个键按两次是两次
    kn_a: { text: "按了 A 键", pulse: true }, kn_b: { text: "按了 B 键", pulse: true },
    kn_dark: { text: "捂住了光线", pulse: true }, kn_loud: { text: "听到大声音", pulse: true },
    kn_tp: { text: "摸了 P", pulse: true }, kn_ty: { text: "摸了 Y", pulse: true }, kn_tt: { text: "摸了 T", pulse: true },
    kn_th: { text: "摸了 H", pulse: true }, kn_to: { text: "摸了 O", pulse: true }, kn_tn: { text: "摸了 N", pulse: true },
    kn_shake: { text: "晃了一下", pulse: true }
  };
  // 下拉框只列当前这一关用得到的几句：第一关的学生看到“开门”“摇了一下”只会犯迷糊。
  // 教师看板上没有“当前关”，列全部。已经选着的那一句永远保留，不然换关卡读回旧积木时值会丢。
  function sendOptions() {
    var d = window.MPL_DEMO;
    var kinds = d && d.sends && d.sends.length ? d.sends.slice()
      : Object.keys(SENDS).filter(function (k) { return !SENDS[k].hidden; });
    var cur = this && this.getValue ? this.getValue() : null;
    if (cur && SENDS[cur] && kinds.indexOf(cur) < 0) kinds.push(cur);
    return kinds.map(function (k) { return [SENDS[k].text, k]; });
  }
  Blockly.Blocks["mpl_farm_send"] = {
    init: function () {
      this.appendDummyInput().appendField("把全部传感器数据发给 3D 农场");
      this.setPreviousStatement(true);
      this.setNextStatement(true);
      this.setColour(COLOR.net);
      this.setTooltip("光线、按键、触摸键、倾斜、摇晃、指南针、声音一次全部发过去。放进“一直重复”里，每 0.1 秒发一次。");
    }
  };

  Blockly.Blocks["mpl_send"] = {
    init: function () {
      this.appendDummyInput().appendField("告诉网页：").appendField(new Blockly.FieldDropdown(sendOptions), "KIND");
      this.setPreviousStatement(true);
      this.setNextStatement(true);
      this.setColour(COLOR.net);
      this.setTooltip("通过 USB 线给网页发一条消息（板子不用联网）。");
    }
  };

  function useMpython(g) {
    g.definitions_["import_mpython"] = "from mpython import *";
  }
  function useGui(g) {
    useMpython(g);
    g.definitions_["import_lv_gui"] = "from lv_gui import *";
    // GUI() 只能建一次：每画一笔都新建一个，屏幕会被反复初始化、闪个不停
    g.definitions_["mpl_gui_init"] = "gui = GUI()";
  }
  function useMusic(g) {
    g.definitions_["import_music"] = "import music";
  }
  function hex(block, name) {
    return "0x" + String(block.getFieldValue(name) || "#000000").replace("#", "");
  }
  function rgbTuple(block, name) {
    var h = String(block.getFieldValue(name) || "#000000").replace("#", "");
    return "(" + [0, 2, 4].map(function (i) { return parseInt(h.substr(i, 2), 16); }).join(", ") + ")";
  }
  // 数值输入一律套 int()：学生接一个“光线 / 3”进来是小数，彩屏和 RGB 灯只收整数，直接报错停机
  function num(block, g, name, fallback) {
    return "int(" + (g.valueToCode(block, name, Order.NONE) || fallback) + ")";
  }
  function setLeds(idx, tuple) {
    if (idx === "all") return "rgb.fill(" + tuple + ")\nrgb.write()\n";
    return "rgb[" + idx + "] = " + tuple + "\nrgb.write()\n";
  }

  gen.forBlock["mpl_main"] = function (block, g) {
    return g.statementToCode(block, "DO");
  };

  gen.forBlock["mpl_forever"] = function (block, g) {
    var body = g.statementToCode(block, "DO") || g.INDENT + "pass\n";
    return "while True:\n" + body;
  };

  gen.forBlock["mpl_sleep"] = function (block, g) {
    g.definitions_["import_time"] = "import time";
    return "time.sleep(" + Number(block.getFieldValue("SEC")) + ")\n";
  };

  // ---- 输入 ----
  gen.forBlock["mpl_button"] = function (block, g) {
    useMpython(g);
    var c = "button_" + block.getFieldValue("BTN") + ".is_pressed()";
    return block.getFieldValue("STATE") === "down" ? [c, Order.FUNCTION_CALL] : ["not " + c, Order.LOGICAL_NOT];
  };
  gen.forBlock["mpl_touch"] = function (block, g) {
    useMpython(g);
    var c = "touchpad_" + block.getFieldValue("PAD") + ".is_pressed()";
    return block.getFieldValue("STATE") === "down" ? [c, Order.FUNCTION_CALL] : ["not " + c, Order.LOGICAL_NOT];
  };
  gen.forBlock["mpl_touch_value"] = function (block, g) {
    useMpython(g);
    return ["touchpad_" + block.getFieldValue("PAD") + ".read()", Order.FUNCTION_CALL];
  };

  // 事件积木：生成一个回调函数，挂到 mpython 的 event_pressed / event_released 上。
  // 回调里改的变量要声明 global，否则 Python 会把它当成函数里的新变量，外面的程序看不到变化
  // （学生做“按 A 计数加 1”时，屏幕上的数字永远是 0）。
  function eventHandler(obj, evt) {
    return function (block, g) {
      useMpython(g);
      var fn = "on_" + obj + "_" + evt;
      var globals = Blockly.Variables.allUsedVarModels(block.workspace).map(function (v) {
        return g.nameDB_.getName(v.getId(), Blockly.Names.NameType.VARIABLE);
      });
      var body = g.statementToCode(block, "DO");
      var head = globals.length ? g.INDENT + "global " + globals.join(", ") + "\n" : "";
      g.definitions_["mpl_evt_" + fn] =
        "def " + fn + "(_):\n" + head + (body || g.INDENT + "pass\n") +
        obj + ".event_" + evt + " = " + fn;
      return null;
    };
  }
  gen.forBlock["mpl_on_button"] = function (block, g) {
    return eventHandler("button_" + block.getFieldValue("BTN"), block.getFieldValue("EVT"))(block, g);
  };
  gen.forBlock["mpl_on_touch"] = function (block, g) {
    return eventHandler("touchpad_" + block.getFieldValue("PAD"), block.getFieldValue("EVT"))(block, g);
  };

  gen.forBlock["mpl_light"] = function (block, g) {
    useMpython(g);
    return ["light.read()", Order.FUNCTION_CALL];
  };
  gen.forBlock["mpl_sound"] = function (block, g) {
    useMpython(g);
    return ["sound.read()", Order.FUNCTION_CALL];
  };
  gen.forBlock["mpl_accel"] = function (block, g) {
    useMpython(g);
    return ["accelerometer.get_" + block.getFieldValue("AXIS") + "()", Order.FUNCTION_CALL];
  };
  gen.forBlock["mpl_gyro"] = function (block, g) {
    useMpython(g);
    return ["gyroscope.get_" + block.getFieldValue("AXIS") + "()", Order.FUNCTION_CALL];
  };
  gen.forBlock["mpl_angle"] = function (block, g) {
    useMpython(g);
    return ["accelerometer.roll_pitch_angle()[" + block.getFieldValue("WHICH") + "]", Order.MEMBER];
  };
  // 不分方向：三个轴合起来的加速度超过约 1.6g 就算晃了（静止时恒为 1g）。
  // 只比平方和，省一次开方。
  gen.forBlock["mpl_shaken"] = function (block, g) {
    useMpython(g);
    g.definitions_["mpl_shaken_helper"] =
      "def _mpl_shaken():\n" +
      "    x, y, z = accelerometer.get_x(), accelerometer.get_y(), accelerometer.get_z()\n" +
      "    return x * x + y * y + z * z > 2.6";
    return ["_mpl_shaken()", Order.FUNCTION_CALL];
  };
  gen.forBlock["mpl_magnetic"] = function (block, g) {
    useMpython(g);
    var m = { heading: "get_heading", strength: "get_field_strength", x: "get_x", y: "get_y", z: "get_z" };
    return ["magnetic." + m[block.getFieldValue("WHAT")] + "()", Order.FUNCTION_CALL];
  };

  // ---- 显示 ----
  // 每块画完都 gui.update()：官方示例是“画几笔再统一 update”，可学生不知道要补这一步，
  // 少了它屏幕上什么都不出现——宁可多刷新几次。
  gen.forBlock["mpl_gui_text"] = function (block, g) {
    useGui(g);
    var v = g.valueToCode(block, "V", Order.NONE) || "''";
    return "gui.draw_label(text=str(" + v + "), row=" + block.getFieldValue("ROW") + ", color=" +
      hex(block, "C") + ", wrap=False)\ngui.update()\n";
  };
  gen.forBlock["mpl_gui_text_xy"] = function (block, g) {
    useGui(g);
    var v = g.valueToCode(block, "V", Order.NONE) || "''";
    return "gui.draw_label(text=str(" + v + "), x=" + num(block, g, "X", "0") + ", y=" + num(block, g, "Y", "0") +
      ", width=320, color=" + hex(block, "C") + ", wrap=False)\ngui.update()\n";
  };
  gen.forBlock["mpl_gui_clear"] = function (block, g) {
    useGui(g);
    return "gui.fill(type=0)\ngui.update()\n";
  };
  gen.forBlock["mpl_gui_bg"] = function (block, g) {
    useGui(g);
    return "gui.set_bg_color(" + hex(block, "C") + ")\ngui.update()\n";
  };
  gen.forBlock["mpl_gui_circle"] = function (block, g) {
    useGui(g);
    var c = hex(block, "C");
    return "gui.draw_round(" + num(block, g, "X", "160") + ", " + num(block, g, "Y", "86") + ", " +
      num(block, g, "R", "30") + ", 2, " + c + ", " + c + ", " + block.getFieldValue("FILL") + ", draw=True)\ngui.update()\n";
  };
  gen.forBlock["mpl_gui_rect"] = function (block, g) {
    useGui(g);
    var c = hex(block, "C");
    return "gui.draw_rect(" + num(block, g, "X", "0") + ", " + num(block, g, "Y", "0") + ", " +
      num(block, g, "W", "50") + ", " + num(block, g, "H", "30") + ", 2, " + c + ", " + c + ", 0, " +
      block.getFieldValue("FILL") + ", 1)\ngui.update()\n";
  };
  gen.forBlock["mpl_gui_line"] = function (block, g) {
    useGui(g);
    return "gui.draw_line(" + num(block, g, "X1", "0") + ", " + num(block, g, "Y1", "0") + ", " +
      num(block, g, "X2", "320") + ", " + num(block, g, "Y2", "172") + ", size=2, color=" + hex(block, "C") +
      ", draw=True)\ngui.update()\n";
  };
  gen.forBlock["mpl_print"] = function (block, g) {
    var v = g.valueToCode(block, "V", Order.NONE) || "''";
    return "print(" + v + ")\n";
  };

  // ---- RGB 灯 ----
  gen.forBlock["mpl_rgb_all"] = function (block, g) {
    useMpython(g);
    var c = block.getFieldValue("C");
    return "for _i in range(3):\n" + g.INDENT + "rgb[_i] = (" + c + ")\nrgb.write()\n";
  };
  gen.forBlock["mpl_rgb_one"] = function (block, g) {
    useMpython(g);
    return setLeds(block.getFieldValue("IDX"), rgbTuple(block, "C"));
  };
  // 超出 0~255 时 NeoPixel 直接抛错——学生把光线值（几百）直接接到“红”上是很自然的尝试，
  // 夹到范围里，灯照样亮，程序不停。
  gen.forBlock["mpl_rgb_mix"] = function (block, g) {
    useMpython(g);
    function ch(name) { return "max(0, min(255, " + num(block, g, name, "0") + "))"; }
    return setLeds(block.getFieldValue("IDX"), "(" + ch("R") + ", " + ch("G") + ", " + ch("B") + ")");
  };
  gen.forBlock["mpl_rgb_off"] = function (block, g) {
    useMpython(g);
    return "rgb.fill((0, 0, 0))\nrgb.write()\n";
  };

  // ---- 音乐 ----
  gen.forBlock["mpl_music_melody"] = function (block, g) {
    useMusic(g);
    return "music.play(music." + block.getFieldValue("MEL") + ")\n";
  };
  gen.forBlock["mpl_music_note"] = function (block, g) {
    useMusic(g);
    return "music.play(" + g.quote_(block.getFieldValue("NOTE") + ":" + block.getFieldValue("BEAT")) + ")\n";
  };
  gen.forBlock["mpl_music_pitch"] = function (block, g) {
    useMusic(g);
    return "music.pitch(" + num(block, g, "F", "440") + ", " + num(block, g, "MS", "200") + ")\n";
  };

  // ---- 网页 ----
  // 官方 connectWiFi 遇到空名称或找不到这个 Wi-Fi 会直接抛异常，程序停在第一行，
  // 后面的循环一步都不跑——学生看到的是“挡住传感器灯也不亮”，根本想不到是 Wi-Fi 的事。
  // 所以：没填名称就跳过；连不上只打印原因、接着往下跑，不联网的积木（RGB 灯）照样能用。
  gen.forBlock["mpl_wifi"] = function (block, g) {
    useMpython(g);
    var ssid = block.getFieldValue("SSID");
    if (!ssid) return "print('还没填 Wi-Fi 名称，先跳过联网，网页收不到消息')\n";
    return "try:\n" +
      g.INDENT + "wifi().connectWiFi(" + g.quote_(ssid) + ", " + g.quote_(block.getFieldValue("PWD")) + ")\n" +
      "except Exception as e:\n" +
      g.INDENT + "print('Wi-Fi 没连上，检查名称和密码:', e)\n";
  };

  // “告诉网页”走 USB 线，不走 Wi-Fi：学生机是有线网、板子插在学生机上，板子只要往串口打印一句
  // 约定好的暗号（@@MPL:dark），学生页面在“板子说”里认出来，再用学生机的网线转发给老师电脑。
  // 这样板子不用联网——不用开热点（Windows 热点官方只能连 8 台）、不用配 Wi-Fi、
  // 防火墙也只需放行学生机。
  // 去重规则见上面 SENDS 的说明。
  gen.forBlock["mpl_send"] = function (block, g) {
    g.definitions_["import_time"] = "import time";
    // 开头列一张这一关的暗号对照：积木上写中文、代码里写暗号，学生看代码时不至于以为暗号是个只有程序知道的黑盒；
    // AI 写代码页签把这份代码原样交给 AI，AI 也是从这几行注释里知道“生菜”该写 bg_veg
    var demo = window.MPL_DEMO;
    var table = ((demo && demo.sends) || []).filter(function (k) { return SENDS[k] && !SENDS[k].hidden; })
      .map(function (k) { return "#   " + SENDS[k].text + " → " + k + "\n"; }).join("");
    g.definitions_["mpl_send_helper"] =
      (table ? "# “告诉网页”的暗号对照（积木上写中文，代码里写暗号，网页认暗号）：\n" + table : "") +
      "_mpl_last = None\n" +
      "_mpl_t = 0\n" +
      "def _mpl_send(kind, pulse=False):\n" +
      "    global _mpl_last, _mpl_t\n" +
      "    if kind == _mpl_last and (not pulse or time.ticks_diff(time.ticks_ms(), _mpl_t) < 500):\n" +
      "        return\n" +
      "    _mpl_last = kind\n" +
      "    _mpl_t = time.ticks_ms()\n" +
      "    print('@@MPL:' + kind)\n";
    var kind = block.getFieldValue("KIND");
    // 行尾带上中文说法：积木上写的是“面包”，代码里只有暗号 bg_bun，学生切到“看代码”找不到汉堡在哪
    return "_mpl_send(" + g.quote_(kind) + (SENDS[kind] && SENDS[kind].pulse ? ", True" : "") + ")" +
      (SENDS[kind] ? "  # 告诉网页：" + SENDS[kind].text : "") + "\n";
  };

  // 3D 农场要的是“一整帧”传感器数据（字段名照农场页 handleData 认的那套）：打成一行 JSON，
  // 前面加 @@FARM: 让学生页认出来、转给农场。每个读数单独包 try——板子上某个传感器不在
  // （比如没检测到磁力计芯片，magnetic 根本不存在），只缺那一项，不至于整段程序停掉。
  // 触摸键发原始读数：农场页自己按“比平时高出多少倍”判断摸没摸到，不用在板子上定阈值。
  // 摇一摇按相邻两次三轴读数的变化量算（和农场原来的板子脚本一样，阈值 1.5）。
  gen.forBlock["mpl_farm_send"] = function (block, g) {
    useMpython(g);
    g.definitions_["import_ujson"] = "import ujson";
    // 光线：农场是照 2.0 写的，按 0~4095 换算大棚里的环境亮度；3.0 读的是勒克斯，教室里才几百，
    // 原样发过去整个大棚黑成一片。乘 8 封顶 4095：教室几百勒克斯≈最亮，手一盖≈全黑
    var fields = [["light", "min(4095, int(light.read() * 8))"], ["btn_a", "button_a.value()"], ["btn_b", "button_b.value()"]];
    ["p", "y", "t", "h", "o", "n"].forEach(function (k) { fields.push(["touch_" + k, "touchpad_" + k + ".read()"]); });
    fields.push(["heading", "magnetic.get_heading()"], ["sound", "sound.read()"]);
    g.definitions_["mpl_farm_helper"] =
      "_mpl_farm_a = [0, 0, 0]\n" +
      "def _mpl_try(f, d=None):\n" +
      "    try:\n" +
      "        return f()\n" +
      "    except Exception:\n" +
      "        return d\n" +
      "def _mpl_farm():\n" +
      "    x = _mpl_try(lambda: accelerometer.get_x(), 0)\n" +
      "    y = _mpl_try(lambda: accelerometer.get_y(), 0)\n" +
      "    z = _mpl_try(lambda: accelerometer.get_z(), 0)\n" +
      "    a = _mpl_farm_a\n" +
      "    shake = abs(x - a[0]) + abs(y - a[1]) + abs(z - a[2]) > 1.5\n" +
      "    a[0], a[1], a[2] = x, y, z\n" +
      "    d = {'accel_x': x, 'accel_y': y, 'shake': shake}\n" +
      fields.map(function (f) { return "    d['" + f[0] + "'] = _mpl_try(lambda: " + f[1] + ")\n"; }).join("") +
      "    print('@@FARM:' + ujson.dumps(d))\n";
    return "_mpl_farm()\n";
  };

  // ---- 数学 ----
  // Blockly 自带的“变量增加”会生成 `from numbers import Number` + isinstance 判断——
  // MicroPython 没有 numbers 模块，板子第一行 import 就报错停机。变量初值统一是 0（见 mplGenerateCode），
  // 直接加就行。
  gen.forBlock["math_change"] = function (block, g) {
    var name = g.nameDB_.getName(block.getFieldValue("VAR"), Blockly.Names.NameType.VARIABLE);
    var delta = g.valueToCode(block, "DELTA", Order.ADDITIVE) || "0";
    return name + " = " + name + " + " + delta + "\n";
  };
  gen.forBlock["mpl_map"] = function (block, g) {
    useMpython(g);
    var a = ["V", "A", "B", "C", "D"].map(function (n) { return g.valueToCode(block, n, Order.NONE) || "0"; });
    return ["numberMap(" + a.join(", ") + ")", Order.FUNCTION_CALL];
  };
  gen.forBlock["mpl_ticks"] = function (block, g) {
    g.definitions_["import_time"] = "import time";
    return ["time.ticks_ms()", Order.FUNCTION_CALL];
  };

  // 会被当成代码的顶层积木：“当掌控板启动时”，再加上事件积木和函数定义——
  // 这两种本来就是单独摆在空白处的，生成时只往文件开头放定义，不产生要执行的语句。
  // 学生从工具箱拖出来、还没拼上去的散积木不该混进代码里，
  // 否则一块悬空的“告诉网页”也会被当成要执行的语句。
  var HATS = { mpl_on_button: 1, mpl_on_touch: 1, procedures_defnoreturn: 1, procedures_defreturn: 1 };
  window.mplGenerateCode = function (workspace) {
    gen.init(workspace);
    // 变量初值从 None 改成 0：学生最常写的是“计数 +1”“分数 +10”，None + 1 直接报错，
    // 而他们根本没见过 None 这个词
    if (gen.definitions_["variables"]) {
      gen.definitions_["variables"] = gen.definitions_["variables"].replace(/ = None$/gm, " = 0");
    }
    var tops = workspace.getTopBlocks(true);
    tops.forEach(function (b) { if (HATS[b.type] && b.isEnabled()) gen.blockToCode(b); });
    var main = tops.filter(function (b) { return b.type === "mpl_main"; })[0];
    var body = main ? gen.blockToCode(main) : "";
    if (Array.isArray(body)) body = body[0];
    body = (body || "").replace(new RegExp("^" + gen.INDENT, "gm"), "");
    var code = gen.finish(body);
    return code.trim() + "\n";
  };

  // 分类前面的小图标：Blockly 默认的图标位是隐藏的（只给可折叠分类用），
  // 换成自己的 class 再在 style.css 里画图标，长相对齐官方 mPython 左侧那一栏。
  function icon(name) { return { icon: "mpl-cat-icon mpl-ic-" + name, row: "blocklyTreeRow mpl-cat-row" }; }
  function n(v) { return { shadow: { type: "math_number", fields: { NUM: v } } }; }
  function t(v) { return { shadow: { type: "text", fields: { TEXT: v } } }; }
  function label(text) { return { kind: "label", text: text }; }

  window.MPL_TOOLBOX = {
    kind: "categoryToolbox",
    contents: [
      { kind: "category", name: "输入", colour: COLOR.input, cssConfig: icon("input"), contents: [
        label("按键"),
        { kind: "block", type: "mpl_button" },
        { kind: "block", type: "mpl_on_button" },
        label("触摸键"),
        { kind: "block", type: "mpl_touch" },
        { kind: "block", type: "mpl_on_touch" },
        { kind: "block", type: "mpl_touch_value" },
        label("传感器"),
        { kind: "block", type: "mpl_light" },
        { kind: "block", type: "mpl_sound" },
        { kind: "block", type: "mpl_shaken" },
        { kind: "block", type: "mpl_angle" },
        { kind: "block", type: "mpl_accel" },
        { kind: "block", type: "mpl_gyro" },
        { kind: "block", type: "mpl_magnetic" }
      ]},
      { kind: "category", name: "显示", colour: COLOR.display, cssConfig: icon("display"), contents: [
        { kind: "block", type: "mpl_gui_text", inputs: { V: t("你好，掌控板") } },
        { kind: "block", type: "mpl_gui_text_xy", inputs: { X: n(10), Y: n(10), V: t("你好") } },
        { kind: "block", type: "mpl_gui_clear" },
        { kind: "block", type: "mpl_gui_bg" },
        { kind: "block", type: "mpl_gui_circle", inputs: { X: n(160), Y: n(86), R: n(30) } },
        { kind: "block", type: "mpl_gui_rect", inputs: { X: n(10), Y: n(10), W: n(100), H: n(50) } },
        { kind: "block", type: "mpl_gui_line", inputs: { X1: n(0), Y1: n(0), X2: n(320), Y2: n(172) } },
        label("电脑上看"),
        { kind: "block", type: "mpl_print" }
      ]},
      { kind: "category", name: "RGB 灯", colour: COLOR.rgb, cssConfig: icon("rgb"), contents: [
        { kind: "block", type: "mpl_rgb_all" },
        { kind: "block", type: "mpl_rgb_one" },
        { kind: "block", type: "mpl_rgb_mix", inputs: { R: n(255), G: n(0), B: n(0) } },
        { kind: "block", type: "mpl_rgb_off" }
      ]},
      { kind: "category", name: "音乐", colour: COLOR.music, cssConfig: icon("music"), contents: [
        { kind: "block", type: "mpl_music_melody" },
        { kind: "block", type: "mpl_music_note", fields: { NOTE: "C4" } },
        { kind: "block", type: "mpl_music_pitch", inputs: { F: n(440), MS: n(200) } }
      ]},
      { kind: "category", name: "网页", colour: COLOR.net, cssConfig: icon("net"), contents: [] },
      { kind: "sep" },
      { kind: "category", name: "逻辑", colour: COLOR.logic, cssConfig: icon("logic"), contents: [
        { kind: "block", type: "controls_if" },
        { kind: "block", type: "controls_if", extraState: { hasElse: true } },
        { kind: "block", type: "logic_compare", fields: { OP: "LT" } },
        { kind: "block", type: "logic_operation" },
        { kind: "block", type: "logic_negate" },
        { kind: "block", type: "logic_boolean" }
      ]},
      { kind: "category", name: "循环", colour: COLOR.loop, cssConfig: icon("loop"), contents: [
        { kind: "block", type: "mpl_forever" },
        { kind: "block", type: "mpl_sleep" },
        { kind: "block", type: "controls_repeat_ext", inputs: { TIMES: n(3) } },
        { kind: "block", type: "controls_whileUntil" },
        { kind: "block", type: "controls_for", inputs: { FROM: n(1), TO: n(10), BY: n(1) } },
        { kind: "block", type: "controls_flow_statements" }
      ]},
      { kind: "category", name: "数学", colour: COLOR.math, cssConfig: icon("math"), contents: [
        { kind: "block", type: "math_number", fields: { NUM: 100 } },
        { kind: "block", type: "math_arithmetic", inputs: { A: n(1), B: n(1) } },
        { kind: "block", type: "math_random_int", inputs: { FROM: n(1), TO: n(100) } },
        { kind: "block", type: "math_modulo", inputs: { DIVIDEND: n(10), DIVISOR: n(3) } },
        { kind: "block", type: "math_round", inputs: { NUM: n(3.1) } },
        { kind: "block", type: "math_single", inputs: { NUM: n(9) } },
        { kind: "block", type: "mpl_map", inputs: { A: n(0), B: n(1000), C: n(0), D: n(255) } },
        { kind: "block", type: "mpl_ticks" }
      ]},
      { kind: "category", name: "文本", colour: COLOR.text, cssConfig: icon("text"), contents: [
        { kind: "block", type: "text" },
        { kind: "block", type: "text_join" },
        { kind: "block", type: "text_length", inputs: { VALUE: t("abc") } }
      ]},
      { kind: "category", name: "变量", colour: COLOR.variable, cssConfig: icon("var"), custom: "VARIABLE" },
      { kind: "category", name: "函数", colour: COLOR.procedure, cssConfig: icon("func"), custom: "PROCEDURE" }
    ]
  };

  // 工具箱按当前关卡生成：“网页”分类里只摆这一关能说的那几句，每句一块，拖出来就是对的那句，
  // 不用再去下拉框里挑。
  window.mplToolbox = function (demo) {
    var tb = JSON.parse(JSON.stringify(window.MPL_TOOLBOX));
    var kinds = demo && demo.sends ? demo.sends : Object.keys(SENDS);
    tb.contents.forEach(function (c) {
      if (c.name !== "网页") return;
      c.contents = kinds.map(function (k) { return { kind: "block", type: "mpl_send", fields: { KIND: k } }; });
      // 关卡自己要的额外积木（自由农场关的“发给 3D 农场”）
      (demo && demo.netBlocks || []).forEach(function (t) { c.contents.push({ kind: "block", type: t }); });
    });
    return tb;
  };

  // Blockly 自带的“如果/比较/数字/循环/变量”积木颜色跟着主题走，和分类栏的颜色对不上；
  // 改成和分类栏一样的颜色，学生按颜色就能找到积木在哪个分类。学生页和教师看板共用这一份。
  var theme = null;
  window.mplTheme = function () {
    if (theme) return theme;
    function style(c) { return { colourPrimary: c }; }
    theme = Blockly.Theme.defineTheme("mpl", {
      base: Blockly.Themes.Classic,
      blockStyles: {
        logic_blocks: style(COLOR.logic),
        math_blocks: style(COLOR.math),
        loop_blocks: style(COLOR.loop),
        text_blocks: style(COLOR.text),
        variable_blocks: style(COLOR.variable),
        variable_dynamic_blocks: style(COLOR.variable),
        procedure_blocks: style(COLOR.procedure)
      }
    });
    return theme;
  };
})();
