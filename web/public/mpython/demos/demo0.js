// 熟悉课第 0 关：认识掌控板。程序已经写好、全部锁住，学生只做两件事：连上板子刷入，然后照官方硬件图一个个去碰。
// 碰到哪个部件，图上那个位置就亮、右边程序里管它的那一块“如果”就闪一下——
// 学生第一次摸掌控板，先建立“板子上这个东西 = 程序里这一块积木”的对应，后面的温室巡检才看得懂程序。
// 图是掌控板 3.0 的官方硬件配置图（课件/初二/第3课 环境数据要感知 第一页）。
(function () {
  "use strict";
  var lock = MplKit.lock;

  // 图上的位置按原图 1172×646 像素量的，换成百分比放热点；front/back 只是给自己看的
  // 页面上用的是裁掉上方标题和说明段落的 board3_crop.png（原图 y=200 以下，1172×446）：
  // 那段说明一整段字没人读，还把两块板子挤小了。下面的坐标仍按原图量，换算时减掉 OY
  var W = 1172, H = 446, OY = 200;

  var PADS = [["p", 205], ["y", 258], ["t", 314], ["h", 368], ["o", 462], ["n", 520]];
  // 六个触摸键各算一个独立要求（不是“摸到任意一个就算”）：P Y T H O N 每个都要摸到才算这一组过
  var TOUCH_GROUP = PADS.map(function (p) {
    var L = p[0].toUpperCase();
    return { key: "kn_t" + p[0], name: "触摸键 " + L, how: "用手指摸一下 " + L, sense: "有没有被摸", spots: [[p[1], 585]],
      cond: { type: "mpl_touch", fields: { PAD: p[0], STATE: "down" } } };
  });
  // “6 个要求”按组存：前四个和六轴各自一组（1 个），触摸键一组（6 个，必须挨在一起显示，
  // 不能被拆开插到别的要求中间）——下面展示顺序时整组一起挪，组内顺序各自再乱一次
  var GROUPS = [
    [{ key: "kn_a", name: "按键 A", how: "按一下 A 键", sense: "按没按下", spots: [[228, 297]],
      cond: { type: "mpl_button", fields: { BTN: "a", STATE: "down" } } }],
    [{ key: "kn_b", name: "按键 B", how: "按一下 B 键", sense: "按没按下", spots: [[490, 297]],
      cond: { type: "mpl_button", fields: { BTN: "b", STATE: "down" } } }],
    [{ key: "kn_dark", name: "光敏传感器", how: "用手捂住，让它变暗", sense: "亮不亮（光线强弱）", spots: [[357, 548]],
      cond: { type: "logic_compare", fields: { OP: "LT" }, inputs: {
        A: { block: lock({ type: "mpl_light" }) }, B: { block: lock({ type: "math_number", fields: { NUM: 30 } }) } } } }],
    [{ key: "kn_loud", name: "麦克风", how: "对着它拍一下手", sense: "声音大小", spots: [[205, 458], [517, 458]],
      cond: { type: "logic_compare", fields: { OP: "GT" }, inputs: {
        A: { block: lock({ type: "mpl_sound" }) }, B: { block: lock({ type: "math_number", fields: { NUM: 2000 } }) } } } }],
    TOUCH_GROUP,
    [{ key: "kn_shake", name: "六轴传感器（背面）", how: "拿起来晃一晃", sense: "晃动、倾斜", spots: [[752, 366]],
      cond: { type: "mpl_shaken" } }]
  ];
  // 固定顺序的全量列表：给程序（starter 的 if 链）和通关判定（need/sends）用，这两处不需要乱序
  var ALL_PARTS = [].concat.apply([], GROUPS);

  // 展示顺序才要乱：按组号出固定的随机数，同一组学生刷新页面顺序不变，不同组彼此不同
  // （看同桌屏幕没用）。只在第一次真用到时算一遍，算完存住——demo0.js 在 app.js 还没跑到
  // boot() 之前就已经加载，这时 MplKit.seed() 读到的组号还是默认值，不能在模块顶层就算。
  var _displayParts = null;
  function displayParts() {
    if (!_displayParts) {
      var r = MplKit.rng(MplKit.seed());
      var groups = MplKit.shuffle(GROUPS.map(function (g) { return MplKit.shuffle(g.slice(), r); }), r);
      _displayParts = [].concat.apply([], groups);
    }
    return _displayParts;
  }

  function partOf(type) { return ALL_PARTS.filter(function (p) { return p.key === type; })[0] || null; }
  function pct(x, y) { return "left:" + (x / W * 100).toFixed(2) + "%;top:" + ((y - OY) / H * 100).toFixed(2) + "%"; }

  // 学生程序里管这句暗号的那块“如果”闪一下：把“板子上的东西”和“程序里的积木”连起来
  function flashBlock(type) {
    var ws = window.MPL_WS;
    if (!ws) return;
    ws.getAllBlocks(false).forEach(function (b) {
      if (b.type !== "controls_if" || MplKit.sendsIn(b, "DO0").indexOf(type) < 0) return;
      try { b.addSelect(); setTimeout(function () { try { b.removeSelect(); } catch (e) {} }, 900); } catch (e) {}
    });
  }

  var found = {};
  var lastAccepted = true;    // 上一次 onEvent 算不算数，accept() 给 app.js 看
  var targetTimer = null;     // 提示框的倒计时：清空重排
  var TARGET_DELAY = 10000;   // 先给学生自己在图上找 10 秒，到时候还没通关才把答案摆出来
  function drawTarget(scene, cur) {
    var spots = scene.querySelector(".kb-spots");
    cur.spots.forEach(function (xy) {
      var t = document.createElement("i");
      t.className = "kb-target";
      t.setAttribute("style", pct(xy[0], xy[1]));
      spots.appendChild(t);
    });
  }
  // 一次只亮一个任务（11 个一股脑列出来太吓人），完成的那个挪进下面小圆点行，
  // 下一个自动顶上来。justKey 有值时才给卡片加一下"刚完成"的闪烁 + 对应圆点弹一下，
  // 重复碰已经认识过的部件不用再闪
  function renderTask(scene, justKey) {
    var dp = displayParts();
    var taskEl = scene.querySelector(".kb-task"), dotsEl = scene.querySelector(".kb-dots");
    var spots = scene.querySelector(".kb-spots");
    var doneN = dp.filter(function (p) { return found[p.key]; }).length;
    var cur = dp.filter(function (p) { return !found[p.key]; })[0];
    taskEl.innerHTML = cur
      ? '<div class="kb-task-tag">第 ' + (doneN + 1) + " / " + dp.length + ' 个</div>' +
        '<div class="kb-task-name">' + cur.name + '</div>' +
        '<div class="kb-task-how">' + cur.how + " · 感知：" + cur.sense + "</div>"
      : '<div class="kb-done">🎉 六个部件全部认识了！</div>';
    dotsEl.innerHTML = dp.map(function (p, i) {
      var cls = found[p.key] ? "ok" : (p === cur ? "cur" : "");
      return '<span class="kb-dot ' + cls + (p.key === justKey ? " pop" : "") + '" title="' + p.name + '">' +
        (found[p.key] ? "✓" : i + 1) + "</span>";
    }).join("");
    if (justKey) { taskEl.classList.remove("flash"); void taskEl.offsetWidth; taskEl.classList.add("flash"); }
    // 每换一个任务（或刚进关）先清掉上一个任务留下的框和倒计时——不是一上来就把答案摆在
    // 图上，学生应该先自己找找看；10 秒内没通关，才在图上画出来指给他看
    clearTimeout(targetTimer);
    spots.querySelectorAll(".kb-target").forEach(function (el) { el.remove(); });
    if (cur) targetTimer = setTimeout(function () { drawTarget(scene, cur); }, TARGET_DELAY);
  }

  MplKit.register({
    id: "kb0",
    color: "#2563EB", icon: "🔍",
    short: "认识板子",
    title: "认识掌控板",
    starterVersion: "2",
    brief: "• 找到 6 个会“感知”的部件\n• 触摸键 P Y T H O N 六个字母都要摸到\n• 下面一次只显示一个任务，必须按这个顺序来，碰别的不算",
    steps: "1. 连接掌控板 → 刷入\n2. 照着当前任务卡，一个个去碰（顺序不能跳）",
    hint: "• 程序已写好，不用改\n• 光敏传感器：用手捂住\n• 麦克风：对着它拍手\n• 触摸键：P Y T H O N 六个都要摸一遍（顺序随意）\n• 六轴传感器：拿起板子晃一晃\n• 碰到时，右边管它的那块“如果”会闪",
    sends: ALL_PARTS.map(function (p) { return p.key; }),
    need: ALL_PARTS.map(function (p) { return p.key; }),
    doneHint: "点上面的“刷入”，然后去碰板子。",

    scene: '<div class="kb-root"><div class="kb-pic"><img src="demos/board3_crop.png" alt="掌控板 3.0 硬件配置图">' +
      '<div class="kb-spots"></div></div><div class="kb-task"></div><div class="kb-dots"></div></div>',
    css:
      '.scene.s-kb0{height:auto;background:#fff;overflow:visible;padding:10px 12px}' +
      // app.js 对每一关都通用地做一次"整块 scene 变亮一下"（.scene.flash）；这一关自己已经有
      // 任务卡闪绿 + 整屏闪绿两种反馈，再加这个纯粹是乱，所以在这儿单独关掉（只对 kb0 生效）
      '.s-kb0.flash{animation:none}' +
      // 全局 .caption 是给深色场景用的白字+半透明深色底；这一关整体是白底卡片风格，
      // 只换了字色、没换底色，于是蓝字糊在暗底上——这才是"看着阴暗"的真正原因，补上浅色底
      '.s-kb0 .caption{position:static;margin-top:8px;font-size:14px;font-weight:700;color:#1E40AF;' +
      '  background:#EFF6FF;border:1px solid #BFDBFE;border-radius:8px;padding:6px 10px}' +
      // 碰错顺序时提示就写在这条 caption 里（"这个不算数，当前要做的是……"），所以闪的也该是
      // 这一条，不是上面的任务卡——学生的眼睛该被引去读这句话，不是任务卡本身
      '.s-kb0 .caption.wrong{animation:kbCapWrong .5s ease}' +
      '@keyframes kbCapWrong{0%{background:#EFF6FF;border-color:#BFDBFE}40%{background:#FEF9C3;border-color:#FACC15}100%{background:#EFF6FF;border-color:#BFDBFE}}' +
      '.s-kb0 .kb-pic{position:relative}.s-kb0 .kb-pic img{display:block;width:100%;height:auto}' +
      '.s-kb0 .kb-spot{position:absolute;width:5%;aspect-ratio:1;margin:-2.5% 0 0 -2.5%;border-radius:50%;' +
      '  border:3px solid #22C55E;box-shadow:0 0 0 4px rgba(34,197,94,.35),0 0 18px #22C55E;animation:kbpop .9s ease-out forwards;pointer-events:none}' +
      '@keyframes kbpop{0%{transform:scale(.4);opacity:1}70%{transform:scale(1.25);opacity:1}100%{transform:scale(1);opacity:0}}' +
      // 当前任务该碰哪：图上画一个方框一直闪着，不会自动消失（跟上面那个"碰对了"的绿色小圆点
      // 不是同一个东西）；换成橙色是故意跟绿色错开，别让学生把"指引"和"确认"看混了
      '.s-kb0 .kb-target{position:absolute;width:11%;aspect-ratio:1;margin:-5.5% 0 0 -5.5%;border-radius:10px;' +
      '  border:5px solid #EA580C;box-shadow:0 0 0 5px rgba(234,88,12,.3),0 0 16px rgba(234,88,12,.6);pointer-events:none;' +
      '  animation:kbTargetPulse 1s ease-in-out infinite}' +
      '@keyframes kbTargetPulse{0%,100%{transform:scale(1);opacity:.8}50%{transform:scale(1.2);opacity:1}}' +
      // 当前任务卡：一次只显示一个要求，完成那一下背景绿一下、角落弹个大对勾，比光改文字好察觉
      '.s-kb0 .kb-task{position:relative;margin-top:10px;padding:14px 16px;border:2px solid #BFDBFE;border-radius:14px;background:#F8FAFF;overflow:hidden}' +
      '.s-kb0 .kb-task-tag{font-size:12px;color:#5A6878;font-weight:700}' +
      '.s-kb0 .kb-task-name{font-size:22px;font-weight:800;color:#1E3A8A;margin:4px 0}' +
      '.s-kb0 .kb-task-how{font-size:14px;color:#334155}' +
      '.s-kb0 .kb-task.flash{animation:kbTaskFlash .7s ease}' +
      '@keyframes kbTaskFlash{0%{background:#F8FAFF;border-color:#BFDBFE}30%{background:#DCFCE7;border-color:#22C55E}100%{background:#F8FAFF;border-color:#BFDBFE}}' +
      '.s-kb0 .kb-task.flash::after{content:"✓";position:absolute;right:16px;top:50%;font-size:40px;font-weight:900;color:#22C55E;' +
      '  transform:translateY(-50%) scale(.4);opacity:0;animation:kbCheckPop .7s ease}' +
      '@keyframes kbCheckPop{0%{opacity:0;transform:translateY(-50%) scale(.4)}35%{opacity:1;transform:translateY(-50%) scale(1.3)}65%{transform:translateY(-50%) scale(1)}100%{opacity:0;transform:translateY(-50%) scale(1)}}' +
      '.s-kb0 .kb-done{text-align:center;padding:18px 4px;font-size:20px;font-weight:800;color:#15803D;animation:kbDoneIn .5s ease}' +
      '@keyframes kbDoneIn{0%{transform:scale(.7);opacity:0}100%{transform:scale(1);opacity:1}}' +
      // 下面一排小圆点：认完的变绿勾，当前这个带蓝色外圈，剩下的只是个数字——一眼看出进度，不用全列出来
      '.s-kb0 .kb-dots{display:flex;gap:6px;flex-wrap:wrap;margin-top:10px}' +
      '.s-kb0 .kb-dot{width:24px;height:24px;border-radius:50%;background:#E2E8F0;color:#94A3B8;display:grid;place-items:center;font-size:11px;font-weight:800}' +
      '.s-kb0 .kb-dot.ok{background:#22C55E;color:#fff}' +
      '.s-kb0 .kb-dot.cur{box-shadow:0 0 0 3px #93C5FD}' +
      '.s-kb0 .kb-dot.pop{animation:kbDotPop .5s ease}' +
      '@keyframes kbDotPop{0%{transform:scale(1)}45%{transform:scale(1.6)}100%{transform:scale(1)}}',

    // 每个部件一个“如果”，全部锁住：这一关只认识，不改程序。顺序按 ALL_PARTS 固定顺序就行，
    // 程序里这串 if 谁先谁后不影响判定，不用跟着展示顺序一起乱
    starter: function () {
      var ifs = ALL_PARTS.map(function (p) {
        return lock({ type: "controls_if", inputs: {
          IF0: { block: lock(JSON.parse(JSON.stringify(p.cond))) },
          DO0: { block: lock({ type: "mpl_send", fields: { KIND: p.key } }) } } });
      });
      ifs.push(lock({ type: "mpl_sleep", fields: { SEC: 0.1 } }));
      for (var i = ifs.length - 2; i >= 0; i--) ifs[i].next = { block: ifs[i + 1] };
      return { blocks: { languageVersion: 0, blocks: [{
        type: "mpl_main", x: 24, y: 24, deletable: false,
        inputs: { DO: { block: lock({ type: "mpl_forever", inputs: { DO: { block: ifs[0] } } }) } }
      }] } };
    },

    check: function () {
      return [{ label: "程序已经写好了（不用改），连上掌控板点“刷入”", ok: true }];
    },

    onEvent: function (type, scene) {
      var spots = scene.querySelector(".kb-spots");
      if (!type) { found = {}; lastAccepted = true; renderTask(scene, null); return "刷入后，去碰一碰板子上的部件"; }
      var p = partOf(type);
      if (!p) { lastAccepted = true; return ""; }
      // 必须按当前任务卡显示的那个来，碰别的不算——不然"一次只显示一个任务"就是摆设，
      // 学生乱碰也能一路跳到底，根本没按顺序认一遍。lastAccepted 给 app.js 看：
      // 这一句算不算"网页真的收到过"（通关要求用的就是这个），不算的话连 seen 都不该标
      var cur = displayParts().filter(function (x) { return !found[x.key]; })[0];
      if (!cur) { lastAccepted = false; return "🎉 全部认识了！"; }
      if (p.key !== cur.key) {
        lastAccepted = false;
        var cap = scene._cap;
        cap.classList.remove("wrong"); void cap.offsetWidth; cap.classList.add("wrong");
        return "这个不算数，当前要做的是：" + cur.name;
      }
      lastAccepted = true;
      found[p.key] = true;
      p.spots.forEach(function (xy) {
        var s = document.createElement("i");
        s.className = "kb-spot"; s.setAttribute("style", pct(xy[0], xy[1]));
        spots.appendChild(s);
        setTimeout(function () { s.remove(); }, 950);
      });
      flashBlock(type);
      renderTask(scene, type);
      var n = ALL_PARTS.filter(function (x) { return found[x.key]; }).length;
      return (n === ALL_PARTS.length ? "🎉 全部认识了！" : "认识了 " + n + " / " + ALL_PARTS.length + "：") +
        (n === ALL_PARTS.length ? "" : p.name + "，感知" + p.sense);
    },

    // app.js 在 onEvent 之后问一句："刚才那个 type 算不算通关要求里的'收到过'？"
    // 不实现这个方法的关卡默认都算；这一关要求按顺序，上面 onEvent 已经算出了答案存在这儿
    accept: function () { return lastAccepted; }
  });
})();
