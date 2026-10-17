// 温室栽培关 · 拓展任务：温室运维工程师巡检——读程序、对文档、修故障，再把植物种到生长 100%。
// 故事：温室系统刚部署完，程序和设计文档对不上。学生是运维员，手上有三样东西：
//   《设计文档》（说明卡里，是对的）：掌控板上做什么动作应该发出哪个命令；
//   《故障单》：同事报上来的怪现象（“我挡住光线想补光，温室却加温了”），只写现象、不写答案；
//   右边的程序：照文档写的，但被人弄错了两处。
// 学生对照文档读程序、结合温室的实际反应，找出错在哪、改好、重新刷入，然后把植物养到生长 100%。
// 温室只认“浇水 / 施肥 / 补光 / 遮阳 / 加温 / 通风”六个命令（温室页面那边是栽培游戏，见 farm/index.html 的 ?game=1）。
//
// 每组都不一样：设计文档（哪个动作对应哪个命令）和埋的两处故障都按组号打乱，看同桌的屏幕没用。
// 六个动作：A 键、B 键、挡住光线，再加三个不同的触摸键——不用加速度传感器（两人共用一块插着线的板子，
// 又歪又晃很别扭，实测平放时 X 轴读数也不是 0，倾斜判断不可靠）。
// 四种故障，每组随机抽两种，都是“改下拉框 / 往空槽里放一块”就能修好的（积木锁着也能改下拉框）：
//   发错命令（“告诉网页”选错了）、接错触摸键、比较符号反了（光线 > 30 写成了 < 的反面）、漏了命令（“执行”是空的）。
(function () {
  "use strict";
  var lock = MplKit.lock;

  var ACTIONS = ["water", "fert", "glow", "shade", "heat", "vent"];
  var PADS = ["p", "y", "t", "h", "o", "n"];
  // 故障单里描述“温室里发生了什么”用的话：学生要拿它去对照说明卡里“设备有什么用”那张表，反推出收到的是哪个命令
  var EFFECT = {
    water: "土壤水分涨了", fert: "养分涨了", glow: "补光灯亮了、光照往上走",
    shade: "屋顶拉上了遮阳网、光照往下掉", heat: "加温灯亮了、温度往上走", vent: "风扇呼呼转、温度降了"
  };

  // 同一组每次打开都是同一份文档和故障，不同组不一样
  var rng = MplKit.rng, shuffle = MplKit.shuffle, pick = MplKit.pick, seedOf = MplKit.seed;
  function txt(a) { return MPL_SENDS[a].text; }

  // ---- 设计文档：六个动作和六个命令的正确对应 ----
  function plan() {
    var r = rng(seedOf() * 7919 + 13);
    var pads = shuffle(PADS.slice(), r).slice(0, 3);
    var trig = shuffle([{ kind: "btnA" }, { kind: "btnB" }, { kind: "cover" }].concat(pads.map(function (p) { return { kind: "touch", pad: p }; })), r);
    var acts = shuffle(ACTIONS.slice(), r);
    return acts.map(function (a, i) { return { action: a, kind: trig[i].kind, pad: trig[i].pad }; });
  }
  function specDesc(x) {
    return x.kind === "touch" ? "摸触摸键 " + x.pad.toUpperCase()
      : x.kind === "btnA" ? "按住 A 键" : x.kind === "btnB" ? "按住 B 键" : "用手挡住光线传感器";
  }
  function specKey(x) {
    return x.kind === "touch" ? "touch:" + x.pad : x.kind === "btnA" ? "btn:a" : x.kind === "btnB" ? "btn:b" : "light";
  }

  // ---- 两处故障 + 故障单 ----
  function faults() {
    var p = plan(), r = rng(seedOf() * 104729 + 7);
    var names = shuffle(["小王", "小李", "小陈", "小张", "小刘", "小赵"], r);
    var used = {}, out = [];
    shuffle(["action", "trigger", "op", "missing"], r).forEach(function (t) {
      if (out.length >= 2) return;
      var cand = p.filter(function (x) {
        if (used[x.action]) return false;
        if (t === "op") return x.kind === "cover";          // 只有“挡光线”那条有比较符号
        if (t === "trigger") return x.kind === "touch";      // 接错键只埋在触摸键上：改一个下拉框就能修
        return true;
      });
      if (!cand.length) return;
      var x = pick(cand, r), f = { type: t, action: x.action, spec: x, who: names[out.length] };
      used[x.action] = true;
      if (t === "action") f.wrong = pick(ACTIONS.filter(function (a) { return a !== x.action; }), r);
      if (t === "trigger") f.wrongPad = pick(PADS.filter(function (pd) { return !p.some(function (y) { return y.kind === "touch" && y.pad === pd; }); }), r);
      out.push(f);
    });
    return out;
  }
  // 故障单只写“看到的现象”，不写答案
  function report(f) {
    var d = specDesc(f.spec), y = txt(f.action);
    switch (f.type) {
      case "action": return "我" + d + "，想" + y + "，温室里却" + EFFECT[f.wrong] + "。";
      case "trigger": return "我照文档" + d + "，想" + y + "，温室一点反应都没有。可“" + y + "”这个命令明明写进程序里了啊？";
      case "op": return "没人碰板子，温室却一直在" + y + "，日志刷个不停；我用手挡住光线传感器，它反而停了。";
      case "missing": return "我" + d + "，想" + y + "，温室没反应，温室的日志里也没收到任何命令。";
    }
  }

  function condBlock(x, f) {
    if (x.kind === "touch") return { type: "mpl_touch", fields: { PAD: f && f.type === "trigger" ? f.wrongPad : x.pad, STATE: "down" } };
    if (x.kind === "btnA" || x.kind === "btnB") return { type: "mpl_button", fields: { BTN: x.kind === "btnA" ? "a" : "b", STATE: "down" } };
    // 挡住光线的阈值 < 30 勒克斯：教室里几百，手一盖就掉到个位数
    return { type: "logic_compare", fields: { OP: f && f.type === "op" ? "GT" : "LT" }, inputs: {
      A: { block: lock({ type: "mpl_light" }) }, B: { block: lock({ type: "math_number", fields: { NUM: 30 } }) } } };
  }

  // 认一个条件积木是哪种触发方式
  function condKey(c) {
    if (!c) return null;
    if (c.type === "mpl_touch") return "touch:" + c.getFieldValue("PAD");
    if (c.type === "mpl_button") return "btn:" + c.getFieldValue("BTN");
    if (c.type === "logic_compare") {
      var a = c.getInputTargetBlock("A");
      if (a && a.type === "mpl_light") return "light";
    }
    return "other";
  }
  // 某个命令在程序里是不是完全照文档接的：只出现在一个“如果”里、条件是文档写的那个动作、
  // 挡光线那条的比较符号是“<”、这个“如果”里没混进别的命令
  function actionOk(workspace, x) {
    var list = workspace.getAllBlocks(false).filter(function (b) {
      return b.type === "controls_if" && MplKit.inMain(b) && MplKit.inForever(b) && MplKit.sendsIn(b, "DO0").indexOf(x.action) >= 0;
    });
    if (list.length !== 1) return false;
    var b = list[0], c = b.getInputTargetBlock("IF0");
    if (condKey(c) !== specKey(x)) return false;
    if (x.kind === "cover" && c.getFieldValue("OP") !== "LT") return false;
    var s = MplKit.sendsIn(b, "DO0");
    return s.length === 1;
  }

  function renderCard(el, c) {
    function rng2(v, u) { return v[0] + "～" + v[1] + u; }
    var n = c.plant.needs, p = plan(), fs = faults();
    el.innerHTML =
      '<h4>📋 智能温室栽培说明 · 运维巡检单</h4>' +
      '<div class="gc-sec"><b>本轮种的是：' + c.plant.icon + ' ' + c.plant.name + '</b><span>' + c.plant.tip + '</span></div>' +
      '<table class="gc-tab"><tr><th>指标</th><th>它喜欢的范围</th></tr>' +
      '<tr><td>💧 土壤水分</td><td>' + rng2(n.moist, "%") + '</td></tr>' +
      '<tr><td>🌡 温度</td><td>' + rng2(n.temp, "°C") + '</td></tr>' +
      '<tr><td>☀ 光照</td><td>' + rng2(n.light, "%") + '</td></tr>' +
      '<tr><td>🧪 养分</td><td>' + rng2(n.nutri, "%") + '</td></tr></table>' +
      '<div class="gc-sec"><b>天气预报（今天起 3 天）</b></div><div class="gc-wx">' +
      c.weather.map(function (w, i) {
        return '<div class="' + (i === 0 ? "now" : "") + '"><i>' + w.icon + '</i><b>' + (i === 0 ? "今天" : "第" + w.day + "天") + ' ' + w.name +
          '</b><span>' + w.desc + '</span></div>';
      }).join("") + '</div>' +
      '<div class="gc-sec"><b>温室里的设备能做什么</b></div><table class="gc-tab">' +
      c.effects.map(function (e) { return '<tr><td>' + e.name + '</td><td>' + e.desc + '</td></tr>'; }).join("") + '</table>' +
      '<div class="gc-sec"><b>📐 设计文档：掌控板操作规范（这份是对的）</b></div><table class="gc-tab gc-spec">' +
      '<tr><th>掌控板上怎么做</th><th>温室应该收到的命令</th></tr>' +
      p.map(function (x) { return '<tr><td>' + specDesc(x) + '</td><td>' + txt(x.action) + '</td></tr>'; }).join("") + '</table>' +
      '<div class="gc-sec"><b>🛠 故障单（同事报上来的）</b></div>' +
      fs.map(function (f, i) { return '<div class="gc-bug"><b>#' + (i + 1) + ' ' + f.who + '：</b>' + report(f) + '</div>'; }).join("") +
      '<ul class="gc-rules">' + c.rules.map(function (t) { return "<li>" + t + "</li>"; }).join("") + '</ul>' +
      '';
  }


  // ================= 温室巡检小关（熟悉课） =================
  // 第一次摸掌控板的学生，一上来就读六条“如果”、修两处故障太陡。拆成四个小关，每关一张故障单、只埋一处错，
  // 每一处错正好是一个知识点（和公开课的四关一一对应），最后才是上面那个综合巡检：
  //   ① 补光灯  —— 光线传感器 + 如果…否则（= 智能小夜灯）：执行 / 否则两格放反了
  //   ② 声控风扇 —— 声音传感器 + 比大小（= 声控楼道灯）：传感器用错了，读的是光线
  //   ③ 一键浇水 —— 当按键被按下时（= 智能门）：写成了“如果按住”，按住就连着浇
  //   ④ 摇一摇施肥 —— 被摇晃 + 放进一直重复（= 摇一摇骰子）：“如果”放在了“一直重复”外面
  // 温室用练习模式（farm ?drill=1）：不种植、不会枯死，只看设备有没有照命令动。
  // 过关 = 积木改对 + 温室真的收到了设计要求里的命令（板子上真跑起来了）。
  function ifBlock(cond, dos, elses, unlockedSends) {
    function send(k) { var b = { type: "mpl_send", fields: { KIND: k } }; return unlockedSends ? b : lock(b); }
    var b = { type: "controls_if", inputs: { IF0: { block: lock(cond) } } };
    if (dos) b.inputs.DO0 = { block: send(dos) };
    if (elses) { b.extraState = { hasElse: true }; b.inputs.ELSE = { block: send(elses) }; }
    return b;
  }
  function mainWith(first) {
    return { blocks: { languageVersion: 0, blocks: [{ type: "mpl_main", x: 24, y: 24, deletable: false, inputs: { DO: { block: first } } }] } };
  }
  function forever(body) {
    var sleep = lock({ type: "mpl_sleep", fields: { SEC: 0.1 } });
    if (body) body.next = { block: sleep };
    return lock({ type: "mpl_forever", inputs: { DO: { block: body || sleep } } });
  }
  // 找“一直重复”里条件是某种积木的那个“如果”
  function findIf(ws, test) {
    return ws.getAllBlocks(false).filter(function (b) {
      return b.type === "controls_if" && MplKit.inMain(b) && test(b.getInputTargetBlock("IF0"));
    })[0] || null;
  }
  function sendsWhere(ws, kind) {
    return ws.getAllBlocks(false).filter(function (b) { return b.type === "mpl_send" && b.isEnabled() && b.getFieldValue("KIND") === kind; });
  }

  // 故障单统一写成三句：①现在是什么情况（症状）②我希望/我想要怎样（陈述语句，写清楚期望）
  // ③正确运行起来应该是什么样子（验收标准）。原来只有①一句话，学生容易读完"哦这里反了"就直接
  // 去改代码，不会停下来想清楚"到底该怎样才算对"——现在逼着先把"期望"和"验收标准"说清楚
  var SUBS = [{
    id: "gh1", icon: "💡", short: "补光灯", title: "温室 · 补光灯",
    spec: ["挡住光线 → 补光", "不挡 → 遮阳"],
    bug: ["小王", "我用手挡住光线，温室反而拉上了遮阳网；手一拿开，补光灯倒亮了。",
      "光线暗的时候自动补光，亮的时候自动遮阳。",
      "手一挡住光线传感器，补光灯就亮；松开手，遮阳网就拉上。"],
    learn: ["光线传感器：手一挡，读数就变小", "如果…否则：条件成立做“执行”，不成立做“否则”"],
    hint: "• 看程序里“执行”和“否则”各放了什么\n• 把两块“告诉网页”拖动对调",
    need: ["glow", "shade"],
    starter: function () {
      var cond = { type: "logic_compare", fields: { OP: "LT" }, inputs: {
        A: { block: lock({ type: "mpl_light" }) }, B: { block: lock({ type: "math_number", fields: { NUM: 30 } }) } } };
      return mainWith(forever(lock(ifBlock(cond, "shade", "glow", true))));
    },
    check: function (ws) {
      var b = findIf(ws, function (c) { return c && c.type === "logic_compare"; });
      var d = MplKit.sendsIn(b, "DO0"), e = MplKit.sendsIn(b, "ELSE");
      return [
        { label: "变暗（执行）→ 补光", ok: d.length === 1 && d[0] === "glow", note: d.length ? "“执行”里放的不是“补光”" : "“执行”还空着" },
        { label: "不暗（否则）→ 遮阳", ok: e.length === 1 && e[0] === "shade", note: e.length ? "“否则”里放的不是“遮阳”" : "“否则”还空着" }
      ];
    }
  }, {
    id: "gh2", icon: "🔊", short: "声控风扇", title: "温室 · 声控风扇",
    spec: ["拍一下手 → 通风"],
    bug: ["小李", "我拍手想开风扇，风扇不转；后来把板子拿到窗边晒太阳，风扇自己转个不停。",
      "拍手声音一大，风扇就转起来通风。",
      "对着板子拍一下手，风扇立刻转；没有声音的时候风扇不转，跟光线强弱没关系。"],
    learn: ["声音传感器：声音越大，读数越大", "比大小：读数 > 2000 才算“拍手”"],
    hint: "• 比较的左边读的是“光线”\n• 去“输入”分类拖“声音的响度”\n• 放到“光线”的位置上，把它顶掉",
    need: ["vent"],
    starter: function () {
      var cond = { type: "logic_compare", fields: { OP: "GT" }, inputs: {
        A: { block: { type: "mpl_light" } }, B: { block: lock({ type: "math_number", fields: { NUM: 2000 } }) } } };
      return mainWith(forever(lock(ifBlock(cond, "vent"))));
    },
    check: function (ws) {
      var b = findIf(ws, function (c) { return c && c.type === "logic_compare"; });
      var a = b && b.getInputTargetBlock("IF0").getInputTargetBlock("A");
      return [
        { label: "比较的是“声音的响度”", ok: !!a && a.type === "mpl_sound",
          note: a ? (a.type === "mpl_light" ? "这里读的是光线，不是声音" : "这里放的不是声音传感器") : "比较的左边空着" },
        { label: "声音大 → 通风", ok: MplKit.sendsIn(b, "DO0").indexOf("vent") >= 0, note: "“执行”里要有“通风”" }
      ];
    }
  }, {
    id: "gh3", icon: "💧", short: "一键浇水", title: "温室 · 一键浇水",
    spec: ["按一下 A 键 → 只浇一次水"],
    bug: ["小陈", "我按住 A 键想浇一次水，结果连着浇了好几次，土都快淹了。",
      "按一下 A 键，就浇一次水，按住不放也只浇一次。",
      "手指点一下 A 键，浇水只执行一次；就算手指按住不放，也不会连续浇水。"],
    learn: ["“如果按住 A”：每一圈都检查，按住就一直成立", "“当按键 A 被按下时”：按一下只做一次"],
    hint: "• 去“输入”拖“当按键 A 被按下时”，放空白处\n• 把“浇水”拖进去\n• 原来的“如果”拖进垃圾桶",
    need: ["water"],
    starter: function () {
      var cond = { type: "mpl_button", fields: { BTN: "a", STATE: "down" } };
      var b = ifBlock(cond, "water", null, true);
      b.inputs.IF0.block = cond;      // 整块“如果”连条件都能拖走、删掉
      return mainWith(forever(b));
    },
    check: function (ws) {
      var ws_ = sendsWhere(ws, "water");
      var inEvt = ws_.some(function (s) { var r = s.getRootBlock(); return r.type === "mpl_on_button" && r.getFieldValue("BTN") === "a" && r.getFieldValue("EVT") === "pressed"; });
      var inLoop = ws_.some(function (s) { return s.getRootBlock().type === "mpl_main"; });
      return [
        { label: "“浇水”放在“当按键 A 被按下时”里", ok: inEvt, note: "去“输入”分类拖“当按键 A 被按下时”" },
        { label: "“一直重复”里不再有“浇水”", ok: !inLoop, note: "把原来那个“如果按住 A”删掉" }
      ];
    }
  }, {
    id: "gh4", icon: "🧪", short: "摇摇施肥", title: "温室 · 摇一摇施肥",
    spec: ["摇一摇掌控板 → 施肥"],
    bug: ["小张", "我摇了半天板子，温室一点反应都没有，日志里也没收到命令。",
      "随时摇一摇掌控板，温室就能收到施肥的命令。",
      "不管什么时候摇动板子，温室都会收到“施肥”命令，不是只有开机那一下才检查。"],
    learn: ["“一直重复”里面的积木：一圈圈不停地检查", "放在外面：只在开机时检查一次"],
    hint: "• “如果”在“一直重复”外面\n• 把它拖进“一直重复”里，放在“等待”上面",
    need: ["fert"],
    starter: function () {
      var b = ifBlock({ type: "mpl_shaken" }, "fert", null, false);
      b.inputs.IF0.block = lock({ type: "mpl_shaken" });
      b.next = { block: forever(null) };
      return mainWith(b);
    },
    check: function (ws) {
      var b = findIf(ws, function (c) { return c && c.type === "mpl_shaken"; });
      return [
        { label: "“如果被摇晃”放在“一直重复”里", ok: !!b && MplKit.inForever(b), note: b ? "还在“一直重复”外面" : "“如果被摇晃”不见了，点右上“恢复成初始积木”" },
        { label: "摇晃 → 施肥", ok: MplKit.sendsIn(b, "DO0").indexOf("fert") >= 0, note: "“执行”里要有“施肥”" }
      ];
    }
  }];

  // 小关的说明卡：全部简要分点
  function subCard(s, no) {
    function ul(a) { return "<ul class='gc-rules'>" + a.map(function (t) { return "<li>" + t + "</li>"; }).join("") + "</ul>"; }
    return '<h4>🛠 巡检单 ' + "①②③④"[no] + '</h4>' +
      '<div class="gc-sec"><b>🐞 故障单</b></div>' +
      '<div class="gc-bug"><b>' + s.bug[0] + '：</b>' + s.bug[1] +
      '<div class="gc-wish">💬 我希望：' + s.bug[2] + '</div>' +
      '<div class="gc-expect">✅ 正确运行应该是：' + s.bug[3] + '</div></div>' +
      '<div class="gc-sec"><b>💡 这一关学会</b></div>' + ul(s.learn);
  }

  function subLevel(s, no, base) {
    return {
      id: s.id, color: "#0D9488", icon: s.icon, short: s.short, title: s.title,
      starterVersion: "1",
      brief: s.spec.map(function (t) { return "• " + t; }).join("\n"),
      steps: "1. 读左下的故障单，找出错在哪\n2. 改好 → 刷入\n3. 照要求做一遍，温室有反应就过关",
      hint: s.hint,
      sends: ACTIONS,
      need: s.need,
      doneHint: "改好了！点“刷入”，照设计要求做一遍。",
      scene: '<iframe class="farm" src="farm/index.html?game=1&drill=1&seed=' + seedOf() + '" title="智能温室" allow="fullscreen"></iframe>' +
        '<button class="farm-full" type="button" title="全屏（Esc 退出）">⛶ 全屏</button>' +
        '<div class="grow-card">' + subCard(s, no) + '</div>',
      // 样式和综合巡检同一套，只换场景类名
      css: base.css.replace(/s-demo6/g, "s-" + s.id),
      starter: s.starter,
      check: s.check,
      onEvent: function (type, scene) {
        var frame = scene.querySelector("iframe.farm");
        if (!scene._gh) {
          scene._gh = true;
          scene.querySelector(".farm-full").addEventListener("click", function () {
            if (document.fullscreenElement) document.exitFullscreen();
            else if (scene.requestFullscreen) scene.requestFullscreen().catch(function () {});
          });
        }
        if (type && ACTIONS.indexOf(type) >= 0 && frame.contentWindow) {
          frame.contentWindow.postMessage({ type: "mpl-grow", action: type }, location.origin === "null" ? "*" : location.origin);
          return "掌控板发来：" + txt(type);
        }
        return "等掌控板发来命令";
      }
    };
  }

  var D6 = {
    id: "demo6",
    color: "#0D9488", icon: "🌻",   // 🧑‍🌾 是拼起来的表情，机房字体里会拆成两个
    short: "综合巡检",
    // 初始积木跟组号有关（文档和故障按组号出）：换了组，本机存的旧积木作废
    get starterVersion() { return "3-g" + seedOf(); },
    title: "温室 · 综合巡检",
    brief: "• 6 个操作都照《设计文档》来\n• 修好 2 处错，把植物种到 100%",
    steps: "1. 读故障单，对照设计文档\n2. 改好 → 刷入\n3. 照文档操作，指标保持绿色",
    hint: "• 故障单说的是哪个命令，就找发它的那个“如果”\n• 和设计文档比：条件、键、命令哪个不一样\n• 锁住的积木也能改下拉框\n• 漏掉的命令去“网页”分类拖",
    sends: ACTIONS,
    need: ["harvest"],
    doneHint: "修好了！点“刷入”，照文档把植物种到 100%。",

    get scene() {
      return '<iframe class="farm" src="farm/index.html?game=1&seed=' + seedOf() + '" title="智能温室栽培" allow="fullscreen"></iframe>' +
        '<button class="farm-full" type="button" title="全屏（Esc 退出）">⛶ 全屏</button>' +
        '<div class="grow-card">温室准备中……</div>';
    },
    css:
      '.scene.s-demo6{height:auto;background:#fff;overflow:visible}' +
      // app.js 收到新消息会给整块 scene 来一下通用的"变亮一下"（.scene.flash，filter:brightness）。
      // 这一关的 scene 是整个温室 3D 画面 + 下面一张卡片，比别的关卡高很多，这下亮一下看着
      // 就像"整个左边网页闪了一下"——跟认识掌控板那关一样的问题，直接关掉（gh1~gh5 子关卡
      // 靠 .replace(/s-demo6/g, ...) 复用这份 css，这条规则也会跟着换成各自的类名）
      '.s-demo6.flash{animation:none}' +
      '.s-demo6 .farm{display:block;width:100%;height:330px;border:0;border-radius:12px 12px 0 0;background:#0d0f14}' +
      '.s-demo6 .farm-full{position:absolute;right:10px;top:286px;z-index:10;background:#0D9488;border-color:#0D9488;color:#fff;' +
      '  font-weight:700;padding:5px 12px;box-shadow:0 3px 10px rgba(0,0,0,.35)}' +
      '.s-demo6 .farm-full:hover{background:#0B7F75;border-color:#0B7F75}' +
      '.s-demo6 .caption{top:290px;bottom:auto;font-size:12px}' +
      '.s-demo6 .grow-card{padding:12px 14px;font-size:13px;line-height:1.55;color:#18222E;background:#F4FBFA;border-top:1px solid #CDEBE7;' +
      '  border-radius:0 0 12px 12px}' +
      '.grow-card h4{margin:0 0 6px;font-size:15px;color:#0B6B62}' +
      '.grow-card .gc-sec{margin:10px 0 4px}.grow-card .gc-sec b{color:#0B6B62}.grow-card .gc-sec span{display:block;color:#5A6878;font-size:12px}' +
      '.grow-card .gc-tab{width:100%;border-collapse:collapse;font-size:12.5px;background:#fff;border-radius:8px;overflow:hidden}' +
      '.grow-card .gc-tab th,.grow-card .gc-tab td{padding:4px 8px;border-bottom:1px solid #E3EEEC;text-align:left}' +
      '.grow-card .gc-tab th{background:#E2F4F1;color:#0B6B62;font-weight:700}' +
      '.grow-card .gc-spec td:last-child{font-weight:700;color:#0B6B62}' +
      '.grow-card .gc-wx{display:grid;grid-template-columns:repeat(3,1fr);gap:6px}' +
      '.grow-card .gc-wx div{background:#fff;border:1px solid #E3EEEC;border-radius:8px;padding:5px 6px;font-size:12px}' +
      '.grow-card .gc-wx div.now{border-color:#0D9488;box-shadow:0 0 0 2px rgba(13,148,136,.2)}' +
      '.grow-card .gc-wx i{font-style:normal;font-size:18px;display:block}.grow-card .gc-wx span{display:block;color:#5A6878;font-size:11px}' +
      '.grow-card .gc-bug{margin:5px 0;padding:6px 10px;border-radius:8px;background:#FEF2F2;border-left:4px solid #EF4444;font-size:12.5px;color:#4A1D1D}' +
      '.grow-card .gc-bug b{color:#B91C1C}' +
      // 症状之后再加两行：我希望怎样（陈述期望）、正确运行是什么样（验收标准）。
      // 颜色跟症状那行区分开（症状是红底，这两行换成蓝/绿），一眼看出这是三件不同的事
      '.grow-card .gc-wish{margin-top:4px;padding-top:4px;border-top:1px dashed #FBC8C8;color:#1E40AF}' +
      '.grow-card .gc-expect{margin-top:3px;color:#0B6B62;font-weight:700}' +
      '.grow-card .gc-rules{margin:8px 0 0;padding-left:18px;color:#5A6878;font-size:12px}' +
      '.grow-card .gc-q{margin-top:8px;padding:6px 10px;border-radius:8px;background:#FFF7E6;color:#7A4B00;font-size:12.5px}' +
      '.s-demo6:fullscreen{display:flex;background:#fff;border-radius:0}' +
      '.s-demo6:fullscreen .farm{flex:1;height:100%;border-radius:0}' +
      '.s-demo6:fullscreen .grow-card{width:380px;overflow:auto;border-radius:0;border-top:0;border-left:1px solid #CDEBE7}' +
      '.s-demo6:fullscreen .farm-full{top:auto;bottom:12px;right:396px}.s-demo6:fullscreen .caption{top:auto;bottom:12px}',

    starter: function () {
      var fs = faults();
      var ifs = plan().map(function (x) {
        var f = fs.filter(function (y) { return y.action === x.action; })[0];
        var b = { type: "controls_if", inputs: { IF0: { block: lock(condBlock(x, f)) } } };
        if (!f || f.type !== "missing") {
          b.inputs.DO0 = { block: lock({ type: "mpl_send", fields: { KIND: f && f.type === "action" ? f.wrong : x.action } }) };
        }
        return lock(b);
      });
      ifs.push(lock({ type: "mpl_sleep", fields: { SEC: 0.1 } }));
      for (var i = ifs.length - 2; i >= 0; i--) ifs[i].next = { block: ifs[i + 1] };
      return {
        blocks: {
          languageVersion: 0,
          blocks: [{
            type: "mpl_main", x: 24, y: 24, deletable: false,
            inputs: { DO: { block: lock({ type: "mpl_forever", inputs: { DO: { block: ifs[0] } } }) } }
          }]
        }
      };
    },

    // 一张故障单一条：修好了就打勾（只告诉“好了没”，不告诉错在哪）；最后一条看全部六个是不是都照文档
    check: function (workspace) {
      var fs = faults(), p = plan();
      var allOk = p.every(function (x) { return actionOk(workspace, x); });
      var items = fs.map(function (f, i) {
        return { label: "故障单 #" + (i + 1) + "（" + f.who + "报的问题）修好了", ok: actionOk(workspace, f.spec),
                 note: "还没修好：读故障单，到程序里找“" + txt(f.action) + "”这一条和设计文档比一比" };
      });
      var faultsOk = items.every(function (it) { return it.ok; });
      items.push({ label: "全部六个操作都和设计文档一致", ok: allOk,
                   note: faultsOk ? "有别的地方被改乱了，对照设计文档再看看，或点右上“恢复成初始积木”重来" : "先把上面的故障修好" });
      return items;
    },

    // 场景第一次画出来时（type 为 null）：接上全屏按钮、开始听温室页面报上来的说明和结果；
    // 之后每收到一个栽培命令就转给温室
    onEvent: function (type, scene) {
      var frame = scene.querySelector("iframe.farm");
      if (!scene._d6) {
        scene._d6 = true;
        scene.querySelector(".farm-full").addEventListener("click", function () {
          if (document.fullscreenElement) document.exitFullscreen();
          else if (scene.requestFullscreen) scene.requestFullscreen().catch(function () {});
        });
        var card = scene.querySelector(".grow-card");
        var onMsg = function (e) {
          if (!scene.isConnected) { window.removeEventListener("message", onMsg); return; }   // 换关后旧场景不再接
          if (e.source !== frame.contentWindow || !e.data) return;
          if (e.data.type === "mpl-grow-card") renderCard(card, e.data.card);
          if (e.data.type === "mpl-grow-result" && e.data.result === "harvest" && window.MPL_REPORT) window.MPL_REPORT("harvest");
        };
        window.addEventListener("message", onMsg);
      }
      if (type && ACTIONS.indexOf(type) >= 0 && frame.contentWindow) {
        frame.contentWindow.postMessage({ type: "mpl-grow", action: type }, location.origin === "null" ? "*" : location.origin);
        return "掌控板发来：" + txt(type);
      }
      if (type === "harvest") return "🎉 收获了！";
      return "等掌控板发来栽培命令";
    }
  };
  // 先四个小关，最后综合巡检
  SUBS.forEach(function (x, i) { MplKit.register(subLevel(x, i, D6)); });
  MplKit.register(D6);
})();
