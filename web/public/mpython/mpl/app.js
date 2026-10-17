// 学生页面主逻辑：积木编辑器 ↔ 掌控板串口 ↔ 教师端服务器 三方接线。
(function () {
  "use strict";

  var $ = function (id) { return document.getElementById(id); };
  var DEMOS = window.MPL_DEMOS;
  var DEMO = null;          // 当前关卡（setLevel 里换）
  var params = new URLSearchParams(location.search);

  // 页面一般从老师电脑的服务器打开（同源，API 前缀为空）；
  // 也支持从本地文件打开（file://），这时用 ?server=192.168.x.x:8790 指定服务器。
  var API = "";
  if (location.protocol === "file:") {
    var s = params.get("server") || store("mpl_server");
    if (s) { API = "http://" + s.replace(/^https?:\/\//, ""); store("mpl_server", s); }
  }

  function store(k, v) {
    try {
      if (v === undefined) return localStorage.getItem(k);
      if (v === null) localStorage.removeItem(k); else localStorage.setItem(k, v);
    } catch (e) { return null; }
  }

  // 同一时间只留一条提示：连点两个按钮时，两条叠在同一位置会糊成一团谁也看不清
  var toastEl = null, toastTimer = null;
  function toast(msg, ms) {
    if (toastEl) toastEl.remove();
    clearTimeout(toastTimer);
    toastEl = document.createElement("div");
    toastEl.className = "toast"; toastEl.textContent = msg;
    document.body.appendChild(toastEl);
    var el = toastEl;
    toastTimer = setTimeout(function () { el.remove(); if (toastEl === el) toastEl = null; }, ms || 2600);
  }

  var cfg = { wifi_ssid: "", wifi_pwd: "", groups: 30, board_host: location.host || "" };
  // 关卡文件要读老师的设置（温室栽培关的填空模式），给它们一个只读入口；boot 里是往同一个对象里合并，引用不变
  window.MPL_CFG = cfg;
  // 教师编辑模式：从教师看板的“编辑学生初始积木”进来。这时不属于任何一组，不上传代码，
  // 只用来调初始积木、决定哪些锁住。真正保存给全班只认老师自己的电脑（服务器那边再挡一次）。
  var EDIT = window.MPL_EDIT = params.get("edit") === "1";
  // 演示模式：教师看板上点“在我的电脑上打开”进来，?from=3 = 把 3 组现在的积木原样搬到老师这台电脑上，
  // 可以投影讲解、也可以接老师自己的板子刷进去演示。只读他们的、不写回去：
  // 不上传代码、不存本机，老师在这里怎么改都不会动到 3 组学生屏幕上的积木。
  var VIEW = !EDIT && params.get("from") ? String(params.get("from")) : null;
  var LOCAL = EDIT || !!VIEW;   // 不属于任何一组：不上传、不记通关
  var group = EDIT ? "老师" : VIEW ? "看" + VIEW : (params.get("group") || store("mpl_group"));
  // 老师存过的初始积木（服务器上），按关卡缓存；没有就用 demo 文件里写死的那份
  var serverStarters = {};
  var serverStarter = null;
  var seen = {};            // 这一关网页收到过哪几句“告诉网页”（通关要求真的收到过）
  var workspace = null;
  var serial = new MplSerial();
  var es = null;

  // ---------------- 组号 ----------------
  function setGroup(g) {
    group = String(g);
    store("mpl_group", group);
    $("groupNo").textContent = group;
    window.MPL_RUNTIME.group = group;
    $("groupModal").hidden = true;
    openStream();
    return setLevel(savedLevel(), true).then(function () { pushCode("open"); });
  }
  function askGroup() {
    var grid = $("groupGrid");
    grid.innerHTML = "";
    // 5 列，每列从下往上数：第1列最下面是1、最上面是(行数)，第2列接着往上数，以此类推——
    // 对应老师现场喊号的习惯（从教室第一列最后一排开始数）。
    var cols = 5;
    var rows = Math.ceil(cfg.groups / cols);
    for (var r = 1; r <= rows; r++) {
      for (var c = 1; c <= cols; c++) {
        var i = c * rows - r + 1;
        if (i < 1 || i > cfg.groups) continue;
        var b = document.createElement("button");
        b.textContent = i;
        b.addEventListener("click", setGroup.bind(null, i));
        grid.appendChild(b);
      }
    }
    $("groupModal").hidden = false;
  }
  $("groupBtn").addEventListener("click", askGroup);

  // ---------------- 积木区 ----------------
  function wsKey() { return "mpl_ws_" + DEMO.id + "_g" + group; }
  // ---------------- AI 写代码（只有带 ai 配置的关卡） ----------------
  // 学生在“🤖 AI 写代码”页签里粘了代码，这一关就以那段代码为准：检查、刷入、传给老师看板都用它。
  // 默认切回“积木”页签就回到积木——两种做法各存各的，切来切去谁也不会盖掉谁，适合积木和 AI
  // 都是“真能做出来”的两条路的关卡。但像汉堡店这种积木那边锁死只剩一个样例、唯一真做法是 AI 的关卡，
  // 这条规矩反而让人困惑：刷完 AI 代码，切到“看代码”却看见不相关的样例。这种关卡在 register() 里
  // 加 aiSticky:true，一旦粘过 AI 代码就不管停在哪个页签，“看代码”“检查清单”“刷入”全部认它
  var tab = "blocks";
  function aiKey() { return "mpl_ai_" + DEMO.id + "_g" + group; }
  // AI 回答常常把代码包在 ```python … ``` 里，学生整段复制过来连带着那两行，板子第一行就报语法错
  function aiText() {
    var t = $("aiCode").value, m = /```[a-zA-Z]*[ \t]*\r?\n([\s\S]*?)```/.exec(t);
    return (m ? m[1] : t).replace(/\t/g, "    ").trim();
  }
  function aiActive() { return !!(DEMO && DEMO.ai && (DEMO.aiSticky || tab === "ai")); }
  function aiOn() { return aiActive() && !!aiText(); }
  // 清单跟着页签走（不是跟着有没有粘代码）：AI 页签里场景是升级版的店，清单得是那几级，哪怕还没粘代码
  function currentItems() { return aiActive() ? DEMO.checkCode(aiText()) : DEMO.check(workspace); }
  function currentCode() { return aiOn() ? aiText() + "\n" : mplGenerateCode(workspace); }

  function starterState() { return serverStarter ? serverStarter.state : DEMO.starter(cfg); }
  function starterVersion() { return serverStarter ? serverStarter.version : "builtin-" + (DEMO.starterVersion || "1"); }

  // 学生本机存的积木是基于哪一版初始积木拼的：老师改过初始积木（锁了别的块、换了骨架），
  // 学生刷新后要换成新版——不然老师在看板上锁好的东西，学生那边还是旧的、照样拖得动。
  // state：演示模式下直接给一份积木（从服务器取来的那一组的）。
  function loadWorkspace(forceStarter, state) {
    Blockly.Events.disable();
    workspace.clear();
    var saved = state ? JSON.stringify(state) : (forceStarter || VIEW) ? null : store(wsKey());
    if (saved && !state && store(wsKey() + "_base") !== starterVersion()) saved = null;
    try {
      Blockly.serialization.workspaces.load(saved ? JSON.parse(saved) : starterState(), workspace);
    } catch (e) {
      Blockly.serialization.workspaces.load(starterState(), workspace);
    }
    Blockly.Events.enable();
    if (!VIEW) store(wsKey() + "_base", starterVersion());
    MplLock.syncAll(workspace);
    refresh(true);
  }

  var pushTimer = null;
  function refresh(silent) {
    // “看代码”原来一直是 mplGenerateCode(workspace)（积木生成的代码），对没有 ai 配置的关卡
    // 没差——currentCode() 在那种情况下算出来的就是同一个值。汉堡店这种 aiSticky 关卡不一样：
    // 积木那边锁死只有一块样例，刷进板子的其实是 AI 代码，“看代码”也该跟着显示那份，不是样例
    var code = currentCode();
    $("codeView").textContent = code;
    var items = currentItems();
    renderChecklist(items);
    evaluatePass(items);
    if (!VIEW) try { store(wsKey(), JSON.stringify(Blockly.serialization.workspaces.save(workspace))); } catch (e) {}
    if (silent) return;
    clearTimeout(pushTimer);
    pushTimer = setTimeout(function () { pushCode("edit"); }, 600);
  }

  function renderChecklist(items) {
    var ul = $("checklist");
    ul.innerHTML = "";
    var all = true;
    items.forEach(function (it, idx) {
      var li = document.createElement("li");
      li.className = it.ok ? "ok" : "";
      li.innerHTML = '<span class="mark">' + (it.ok ? "✓" : idx + 1) + '</span><span></span>';
      li.lastChild.textContent = it.label;
      var note = document.createElement("span");
      note.className = "note"; note.textContent = it.note || "";
      li.lastChild.appendChild(note);
      ul.appendChild(li);
      if (!it.ok) all = false;
    });
    $("check").classList.toggle("all", all);
  }

  // ---------------- 关卡 ----------------
  function levelKey() { return "mpl_level_g" + group; }
  function savedLevel() {
    var q = parseInt(params.get("level"), 10);
    if (q >= 1 && q <= DEMOS.length) return q - 1;
    var n = parseInt(store(levelKey()), 10);
    return n >= 0 && n < DEMOS.length ? n : 0;
  }
  function passKey() { return "mpl_pass_g" + group; }
  function passedIds() {
    if (LOCAL) return [];
    try { return JSON.parse(store(passKey()) || "[]"); } catch (e) { return []; }
  }

  // 通关 = 积木全放对 + 网页真的收到过这一关要求的每一句。
  // 只看积木的话，学生没刷进板子、甚至没插板子也能“通关”——这一关要的是板子真的指挥了网页。
  // 老师在看板上“模拟”的也算：板子坏了的组不该因此卡住。
  function evaluatePass(items) {
    if (!DEMO) return;
    if (!LOCAL) {
      var ids = passedIds();
      var ok = items.every(function (i) { return i.ok; }) &&
               (DEMO.need || []).every(function (k) { return seen[k]; });
      if (ok && ids.indexOf(DEMO.id) < 0) {
        ids.push(DEMO.id);
        store(passKey(), JSON.stringify(ids));
        toast("🎉 第 " + (DEMOS.indexOf(DEMO) + 1) + " 关通关！", 4000);
        pushCode("pass");
      }
    }
    renderLevels();
  }

  function renderLevels() {
    var nav = $("levels"), ids = passedIds(), cur = DEMOS.indexOf(DEMO);
    nav.innerHTML = "";
    DEMOS.forEach(function (d, i) {
      var pass = ids.indexOf(d.id) >= 0;
      if (i) {
        // 两关都通了，中间的连线变绿
        var link = document.createElement("span"), prev = DEMOS[i - 1];
        link.className = "lv-link" + (pass && ids.indexOf(prev.id) >= 0 ? " pass" : "");
        nav.appendChild(link);
      }
      var b = document.createElement("button");
      b.className = "lv" + (i === cur ? " cur" : "") + (pass ? " pass" : "") + (d.extra ? " extra" : "");
      b.style.setProperty("--lc", d.color || "#1E88E5");
      b.title = "第 " + (i + 1) + " 关：" + d.title + (pass ? "（已通关）" : "");
      // 拓展关没通关时下面写“拓展”，通关了照样写“已通关”
      b.innerHTML = '<span class="c">' + (i + 1) + '</span><span class="t"></span><span class="s">' +
                    (pass || !d.extra ? "已通关" : "拓展") + '</span>';
      b.querySelector(".t").textContent = (d.icon ? d.icon + " " : "") + (d.short || d.title);
      b.addEventListener("click", function () { if (i !== DEMOS.indexOf(DEMO)) setLevel(i); });
      nav.appendChild(b);
    });
    var done = DEMOS.filter(function (d) { return ids.indexOf(d.id) >= 0; }).length;
    $("levelsProgress").textContent = LOCAL ? "" : "已通关 " + done + " / " + DEMOS.length;
    var here = ids.indexOf(DEMO.id) >= 0;
    $("passedBox").hidden = !here;
    $("check").classList.toggle("passed", here);
    var next = DEMOS[cur + 1];
    $("nextBtn").hidden = !next;
    if (next) {
      $("nextBtn").textContent = "下一关：" + (next.icon ? next.icon + " " : "") + next.title + " →";
      $("nextBtn").style.background = $("nextBtn").style.borderColor = next.color || "";
    }
    $("allDone").hidden = !!next || DEMOS.some(function (d) { return ids.indexOf(d.id) < 0; });
  }
  $("hintBtn").addEventListener("click", function () {
    var h = $("taskHint");
    h.hidden = !h.hidden;
    this.textContent = h.hidden ? "💡 看提示" : "收起提示";
  });
  $("nextBtn").addEventListener("click", function () {
    var i = DEMOS.indexOf(DEMO);
    if (DEMOS[i + 1]) setLevel(i + 1);
  });

  async function fetchStarter(id) {
    if (id in serverStarters) return serverStarters[id];
    try {
      var sr = await fetch(API + "/api/starter?demo=" + encodeURIComponent(id));
      var sd = await sr.json();
      serverStarters[id] = sd && sd.state ? sd : null;
    } catch (e) { serverStarters[id] = null; }
    return serverStarters[id];
  }

  var cssDone = {};
  // 换关：场景、任务说明、工具箱（“网页”分类只放这一关的几句）、积木全部换掉。
  // 场景元素整个换新的：上一关场景里的计时器（楼道灯倒数）挂在旧元素上，不会跑到新场景里来乱改。
  async function setLevel(i, quiet, state) {
    DEMO = window.MPL_DEMO = DEMOS[i];
    if (DEMO.setAiMode) DEMO.setAiMode(tab === "ai");   // 场景马上要画，先告诉它画哪家店
    seen = {};
    if (!LOCAL) store(levelKey(), String(i));
    try {
      var u = new URL(location.href);
      u.searchParams.set("level", String(i + 1));
      history.replaceState(null, "", u);
    } catch (e) {}
    params = new URLSearchParams(location.search);
    if (DEMO.css && !cssDone[DEMO.id]) {
      var st = document.createElement("style"); st.textContent = DEMO.css;
      document.head.appendChild(st); cssDone[DEMO.id] = true;
    }
    $("left").style.setProperty("--lc", DEMO.color || "#1E88E5");
    $("demoTitle").textContent = "· 第 " + (i + 1) + " 关 " + DEMO.title;
    $("taskTitle").textContent = (DEMO.icon ? DEMO.icon + " " : "") + "第 " + (i + 1) + " 关 · " + DEMO.title;
    $("taskBrief").textContent = DEMO.brief;
    // 步骤可以不写全；具体操作放进提示，换关时提示重新收起
    $("taskSteps").textContent = DEMO.steps || "";
    $("taskStepsBox").hidden = !DEMO.steps;
    $("taskHint").textContent = DEMO.hint || "";
    $("taskHint").hidden = true;
    $("hintBtn").hidden = !DEMO.hint;
    $("hintBtn").textContent = "💡 看提示";
    $("doneHint").textContent = DEMO.doneHint || "都放对了！点上面的“刷入”试试。";
    var old = $("scene"), scene = document.createElement("div");
    scene.id = "scene"; scene.className = "scene s-" + DEMO.id; scene.setAttribute("aria-live", "polite");
    scene.innerHTML = DEMO.scene + '<div class="caption"></div>';
    scene._cap = scene.querySelector(".caption");
    old.replaceWith(scene);
    scene._cap.textContent = DEMO.onEvent(null, scene);
    lastApplied = null;
    serverStarter = await fetchStarter(DEMO.id);
    workspace.updateToolbox(mplToolbox(DEMO));
    $("aiTab").hidden = !DEMO.ai;
    // 只有汉堡店把“AI 写代码”页签摆最前面（见 style.css 的 .tabs.ai-first）：这一关是给不会编程的
    // 学生用 AI 的，先看见“积木/看代码”会让人以为这关也得拼积木或看懂代码
    document.querySelector(".tabs").classList.toggle("ai-first", DEMO.id === "demo7");
    if (DEMO.ai) $("aiCode").value = (!LOCAL && store(aiKey())) || "";
    if (tab === "ai" && !DEMO.ai) showTab("blocks");
    // 进汉堡店默认直接停在“AI 写代码”——这关本来就不是靠拼积木过的，不用学生自己想起来点一下
    if (DEMO.id === "demo7" && tab !== "ai") showTab("ai");
    loadWorkspace(false, state);
    if (DEMO.ai) {
      $("aiTask").textContent = "🎯 " + DEMO.ai.task();
      $("aiPrompt").value = (!LOCAL && store(aiKey() + "_req")) || "";
      renderBuilder();
      showFull();   // 样子代码取自这一关的积木，得等积木载进来再生成
    }
    Blockly.svgResize(workspace);
    if (!quiet) pushCode("level");
  }

  function pushCode(action) {
    if (!group || LOCAL || !DEMO) return;
    // 老师看板“看代码”里分得出这段是积木生成的还是 AI 写的（积木那一栏画的仍是这组的积木）
    var code = (aiOn() ? "# 这一组用的是“🤖 AI 写代码”页签里粘的代码，不是积木生成的\n" : "") + currentCode();
    var body = { group: group, code: code, filled: currentItems(), action: action, ai: aiOn(),
                 level: DEMO.id, levelNo: DEMOS.indexOf(DEMO) + 1, passed: passedIds(),
                 // 整份积木也传上去：教师看板要把这组的程序画成积木，而不只是一段 Python
                 ws: Blockly.serialization.workspaces.save(workspace) };
    fetch(API + "/api/code", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body) }).catch(function () {});
  }

  function initWorkspace() {
    workspace = Blockly.inject("blocklyDiv", {
      // 放大缩小、垃圾桶这些按钮的图片，Blockly 默认去 blockly-demo.appspot.com 下载——
      // 机房访问不了，右下角就是几个糊掉的方块。用 static/blockly/media 里的本地副本。
      media: "blockly/media/",
      plugins: MPL_PLUGINS,   // 锁住的积木之间允许插入、拖走时不带走锁住的骨架（见 lock.js）
      theme: mplTheme(),
      toolbox: mplToolbox(DEMOS[0]),
      trashcan: true,
      zoom: { controls: true, wheel: true, startScale: 1.0, maxScale: 2, minScale: 0.5 },
      move: { scrollbars: true, drag: true, wheel: false },
      grid: { spacing: 24, length: 3, colour: "#E3E8EE", snap: true }
    });
    // 关卡场景偶尔要碰积木区（认识掌控板关：碰哪个部件，程序里管它的那块“如果”闪一下）
    window.MPL_WS = workspace;
    workspace.addChangeListener(function (e) {
      if (e.isUiEvent || !DEMO) return;
      refresh(false);
    });
    window.addEventListener("resize", function () { Blockly.svgResize(workspace); });
  }

  $("resetBtn").addEventListener("click", function () {
    store(wsKey(), null);
    loadWorkspace(true);
    pushCode("reset");
    toast("已恢复成初始积木");
  });

  // ---------------- 教师编辑模式：锁定 / 解锁 / 存为初始积木 ----------------
  MplLock.onChange(function () { refresh(true); });
  $("lockAllBtn").addEventListener("click", function () { MplLock.setAll(workspace, true); toast("已全部锁定"); });
  $("unlockAllBtn").addEventListener("click", function () { MplLock.setAll(workspace, false); toast("已全部解锁"); });
  $("saveStarterBtn").addEventListener("click", async function () {
    try {
      var r = await fetch(API + "/api/starter", { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ demo: DEMO.id, state: Blockly.serialization.workspaces.save(workspace) }) });
      var d = await r.json();
      if (!d.ok) throw new Error(d.error || r.status);
      serverStarter = serverStarters[DEMO.id] = { version: d.version, state: Blockly.serialization.workspaces.save(workspace) };
      store(wsKey() + "_base", d.version);
      toast("已存为第 " + (DEMOS.indexOf(DEMO) + 1) + " 关的初始积木。学生刷新页面就会换成这一版（他们这一关已经拼的会被替换）。", 5000);
    } catch (e) { toast("保存失败：" + e.message, 5000); }
  });
  $("resetStarterBtn").addEventListener("click", async function () {
    try {
      var r = await fetch(API + "/api/starter", { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ demo: DEMO.id, state: null }) });
      var d = await r.json();
      if (!d.ok) throw new Error(d.error || r.status);
      serverStarter = serverStarters[DEMO.id] = null;
      loadWorkspace(true);
      toast("已恢复成默认的初始积木", 3500);
    } catch (e) { toast("恢复失败：" + e.message, 5000); }
  });

  document.querySelectorAll(".tabs button[data-tab]").forEach(function (b) {
    b.addEventListener("click", function () {
      document.querySelectorAll(".tabs button[data-tab]").forEach(function (x) { x.classList.remove("on"); });
      b.classList.add("on");
      showTab(b.getAttribute("data-tab"));
    });
  });
  function showTab(t) {
    if (t === "ai" && !(DEMO && DEMO.ai)) t = "blocks";
    var wasAi = tab === "ai";
    tab = t;
    // 进出 AI 页签：场景换成升级版的店 / 换回原来那家（看代码 ↔ 积木之间不用换）
    if (DEMO && DEMO.setAiMode && wasAi !== (t === "ai")) {
      DEMO.setAiMode(t === "ai");
      $("scene")._cap.textContent = DEMO.onEvent(null, $("scene"));
    }
    document.querySelectorAll(".tabs button[data-tab]").forEach(function (x) {
      x.classList.toggle("on", x.getAttribute("data-tab") === t);
    });
    $("codeView").hidden = t !== "code";
    $("aiView").hidden = t !== "ai";
    $("blocklyDiv").style.display = t === "blocks" ? "" : "none";
    if (t === "blocks") Blockly.svgResize(workspace);
    if (t === "ai") showFull();
    // 积木 ↔ AI 代码换了“以谁为准”：检查清单、操作台、老师看板都要跟着换
    if (wasAi !== (t === "ai")) refresh();
  }

  // 学生只写“我的要求”，发给 AI 的完整的话 = 要求 + 页面补的技术交代 + “看代码”里那份代码。
  // 每次现拼：学生在积木里多接了一个键，样子代码也跟着变
  function fullPrompt() { return DEMO.ai.wrap($("aiPrompt").value || "（还没写）", mplGenerateCode(workspace)); }
  function showFull() { if (DEMO && DEMO.ai) $("aiFull").textContent = fullPrompt(); }

  // ---------------- 拼接区：句子模板 + 词块 ----------------
  // 跟自定义题目「第3课 互联网+各行各业-句子积木」同一套交互（点选 + Pointer Events 拖拽），
  // 直接搬现成验证过的做法：句子模板点一下/拖一下加一行，词块点一下/拖一下填进行里的下划线。
  // 没学过编程、没拼过积木的学生，不用自己想“这句话该怎么说”，只管挑词填空、拼几行——
  // 练的是“顺序对不对、该填哪个词”，不是造句或打字。关卡用 DEMO.ai.builder() 按当前级别给
  // 模板和词块，没有就整块不显示，退回手动在①里打字。
  var bPicked = null;
  function bUnpick() {
    if (bPicked) bPicked.classList.remove("picked");
    bPicked = null;
    $("aiZone").querySelectorAll(".ai-slot.wait").forEach(function (s) { s.classList.remove("wait"); });
  }
  // 拼接区本身就是①的内容，不用再点一次“生成”——拖一下、填一个空，①马上跟着变。
  // 按钮少一步，学生不会漏按；手动在①里再加几句也不会被这步覆盖掉以外的内容，
  // 只有拼接区自己变化时才重新拼一遍
  function bSync() {
    var rows = $("aiZone").querySelectorAll(".ai-row");
    var lines = [];
    rows.forEach(function (r) { lines.push(r.querySelector(".txt").textContent.replace(/＿＿/g, "____").trim()); });
    $("aiPrompt").value = lines.join("\n");
    $("aiPrompt").dispatchEvent(new Event("input"));
  }
  function bFillSlot(s, text) { s.textContent = text; s.classList.remove("empty"); bSync(); }
  function bClearSlot(s) { s.textContent = "＿＿"; s.classList.add("empty"); bSync(); }
  function bOnSlot(s) {
    if (bPicked) { bFillSlot(s, bPicked.textContent); bUnpick(); return; }
    if (!s.classList.contains("empty")) { bClearSlot(s); return; }
    toast("先点一个绿色词块，再点这条下划线", 2500);
  }
  function bAddRow(tpl) {
    var row = document.createElement("div"); row.className = "ai-row";
    var t = document.createElement("span"); t.className = "txt";
    var parts = tpl.split("___");
    parts.forEach(function (part, i) {
      t.appendChild(document.createTextNode(part));
      if (i < parts.length - 1) {
        var s = document.createElement("span");
        s.className = "ai-slot empty"; s.textContent = "＿＿";
        s.addEventListener("click", function () { bOnSlot(s); });
        t.appendChild(s);
      }
    });
    var ctl = document.createElement("span"); ctl.className = "ctl";
    ctl.innerHTML = '<button type="button" title="上移">↑</button> <button type="button" title="下移">↓</button> <button type="button" title="删掉这一行">×</button>';
    var btns = ctl.querySelectorAll("button");
    btns[0].onclick = function () { if (row.previousElementSibling) { row.parentNode.insertBefore(row, row.previousElementSibling); bSync(); } };
    btns[1].onclick = function () { if (row.nextElementSibling) { row.parentNode.insertBefore(row.nextElementSibling, row); bSync(); } };
    btns[2].onclick = function () { row.remove(); bSync(); };
    row.appendChild(t); row.appendChild(ctl);
    $("aiZone").appendChild(row);
    bSync();
  }
  // 词块/句子块共用的拖拽手势：按住不动先当点击（pointerup 时触发），挪开一点才算拖，
  // 松手时看落在哪个目标上——句子块落在拼接区就加一行，词块落在下划线上就填进去
  function bBindDrag(b, isWord, onClick) {
    b.addEventListener("pointerdown", function (e) {
      e.preventDefault();
      b.setPointerCapture(e.pointerId);
      var sx = e.clientX, sy = e.clientY, ghost = null, over = null;
      var target = function (ev) {
        var el = document.elementFromPoint(ev.clientX, ev.clientY);
        if (!el) return null;
        return isWord ? el.closest(".ai-slot") : el.closest("#aiZone");
      };
      var move = function (ev) {
        if (!ghost) {
          if (Math.abs(ev.clientX - sx) + Math.abs(ev.clientY - sy) < 6) return;
          bUnpick();
          ghost = b.cloneNode(true); ghost.className = "ai-blk ai-ghost-drag";
          document.body.appendChild(ghost);
        }
        ghost.style.left = (ev.clientX + 8) + "px"; ghost.style.top = (ev.clientY + 8) + "px";
        var t = target(ev);
        if (over && over !== t) over.classList.remove("over");
        over = t;
        if (t) t.classList.add("over");
      };
      var up = function (ev) {
        b.removeEventListener("pointermove", move);
        b.removeEventListener("pointerup", up);
        b.removeEventListener("pointercancel", up);
        if (over) over.classList.remove("over");
        if (!ghost) { if (ev.type === "pointerup") onClick(); return; }
        ghost.remove();
        var t = ev.type === "pointerup" ? target(ev) : null;
        if (t) { if (isWord) bFillSlot(t, b.textContent); else bAddRow(b.dataset.full); }
      };
      b.addEventListener("pointermove", move);
      b.addEventListener("pointerup", up);
      b.addEventListener("pointercancel", up);
    });
  }
  function renderBuilder() {
    var cfg = DEMO && DEMO.ai && DEMO.ai.builder && DEMO.ai.builder();
    // ①只显示一种东西：有拼接区就显示 #aiZone、隐藏手动打字的 #aiPrompt，没有就反过来——
    // 不然两个框都摆出来，看着像同一句话被重复显示了一遍
    $("aiBuilder").hidden = !cfg;
    $("aiZone").hidden = !cfg;
    $("aiPrompt").hidden = !!cfg;
    $("aiBuildReset").hidden = !cfg;
    bUnpick();
    $("aiZone").innerHTML = "";
    var sb = $("aiSentBank"), wb = $("aiWordBank");
    sb.innerHTML = ""; wb.innerHTML = "";
    if (!cfg) return;
    (cfg.sentences || []).forEach(function (s) {
      var b = document.createElement("div");
      b.className = "ai-blk"; b.textContent = s.t.replace(/___/g, "＿＿"); b.dataset.full = s.t;
      bBindDrag(b, false, function () { bAddRow(s.t); });
      sb.appendChild(b);
    });
    (cfg.words || []).forEach(function (g) {
      var row = document.createElement("div"); row.className = "ai-word-row";
      var head = document.createElement("b"); head.textContent = g.label; row.appendChild(head);
      g.items.forEach(function (w) {
        var b = document.createElement("div");
        b.className = "ai-blk"; b.textContent = w;
        bBindDrag(b, true, function () {
          if (bPicked === b) { bUnpick(); return; }
          bUnpick(); bPicked = b; b.classList.add("picked");
          $("aiZone").querySelectorAll(".ai-slot.empty").forEach(function (s) { s.classList.add("wait"); });
        });
        row.appendChild(b);
      });
      wb.appendChild(row);
    });
  }
  $("aiBuildReset").addEventListener("click", function () { bUnpick(); $("aiZone").innerHTML = ""; bSync(); });

  $("aiCopyBtn").addEventListener("click", function () {
    if ($("aiPrompt").value.trim().length < 8) { toast("先在 ① 里用中文写清楚你的要求", 3000); $("aiPrompt").focus(); return; }
    var text = fullPrompt();
    // 学生机是 http://老师IP 打开的，clipboard API 不一定给用；退回老办法：放进一个临时文本框选中再 execCommand
    function fallback() {
      var ta = document.createElement("textarea");
      ta.value = text; ta.style.position = "fixed"; ta.style.opacity = "0";
      document.body.appendChild(ta); ta.select();
      try { document.execCommand("copy"); toast("已复制，去粘给 AI 吧", 2500); }
      catch (e) { toast("复制不了：展开“看看发给 AI 的完整的话”，手动全选复制", 4000); }
      ta.remove();
    }
    if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(text).then(function () { toast("已复制，去粘给 AI 吧", 2500); }, fallback);
    else fallback();
  });
  // 存的键换成 _req：之前版本存的是整段提示词，读回来会被当成“我的要求”，又长又乱
  $("aiPrompt").addEventListener("input", function () {
    if (!LOCAL) store(aiKey() + "_req", $("aiPrompt").value);
    showFull();
  });
  $("aiCode").addEventListener("input", function () {
    if (!LOCAL) store(aiKey(), $("aiCode").value);
    refresh();
  });
  $("aiFlashBtn").addEventListener("click", function () {
    if (!aiText()) { toast("先把 AI 写的代码粘进下面的框", 3000); return; }
    if (!serial.connected()) { toast("先点上面的“连接掌控板”", 3000); return; }
    $("saveBtn").click();
  });

  // ---------------- 服务器推送（板子的消息从这里到网页） ----------------
  function openStream() {
    if (!group || LOCAL) return;
    if (es) es.close();
    es = new EventSource(API + "/stream?group=" + encodeURIComponent(group));
    es.onopen = function () { setChip("srvChip", "ok", "服务器已连接"); };
    es.onerror = function () { setChip("srvChip", "bad", "服务器断开，重连中"); };
    es.onmessage = function (m) {
      var msg; try { msg = JSON.parse(m.data); } catch (e) { return; }
      if (msg.kind !== "event") return;
      // 自己经 USB 转发上去的会被服务器推回来（回声）；老师模拟的不是
      applyEvent(msg.type, msg.src === "usb");
    };
  }

  // 本机先反应一次、服务器再推回来一次（回声）：回声要和本机转发过的那一句一条条对掉——
  // state 型（开/关）重复处理只是多闪一下，pulse 型（拍手、摇一摇）会让骰子滚两回、楼道灯多续几秒。
  // 早先只认“和上一句一样”：汉堡店一口气发“面包、肉饼”，面包的回声在肉饼之后才到，面包就被叠了两片。
  var lastApplied = null, lastAt = 0, sentUp = [];
  function applyEvent(type, echo) {
    // 别的关卡的话（比如老师模拟错了关），不理。“farm”这种不在 sends 里、但在通关要求里的也认
    if (!DEMO || ((DEMO.sends || []).indexOf(type) < 0 && (DEMO.need || []).indexOf(type) < 0)) return;
    if (echo) {
      var now = Date.now();
      sentUp = sentUp.filter(function (e) { return now - e.at < 5000; });
      for (var i = 0; i < sentUp.length; i++) if (sentUp[i].type === type) { sentUp.splice(i, 1); return; }
    }
    var pulse = MPL_SENDS[type] && MPL_SENDS[type].pulse;
    var scene = $("scene");
    var fresh = type !== lastApplied || pulse;
    lastApplied = type; lastAt = Date.now();
    scene._cap.textContent = DEMO.onEvent(type, scene);
    if (fresh) { scene.classList.remove("flash"); void scene.offsetWidth; scene.classList.add("flash"); }
    // 关卡可以选择性地定义 accept()：这一句算不算通关要求里的“收到过”。默认都算；
    // “认识掌控板”那关要求按展示顺序一个个来，乱碰的那次不该被记成“已经见过这句了”，
    // 不然“只能按顺序”就只是网页上好看、通关条件该过还是照样过
    if ((!DEMO.accept || DEMO.accept(type)) && !seen[type]) { seen[type] = true; evaluatePass(DEMO.check(workspace)); }
  }

  // 板子通过 USB 线报上来的事件：本机网页立刻反应（不用等服务器绕一圈），
  // 同时用学生机的网线转发给老师电脑，教师看板和数据记录才看得到。
  function boardEvent(kind) {
    applyEvent(kind);
    if (LOCAL || !group) return;
    sentUp.push({ type: kind, at: Date.now() });
    fetch(API + "/event?group=" + encodeURIComponent(group) + "&type=" + kind + "&via=usb").catch(function () {});
  }
  // 不是板子说的、而是场景页面自己得出的结果（温室栽培关“收获了 / 枯死了”）也走同一条路：
  // 本机算通关、转给老师电脑记一笔、教师看板实时动态里看得到
  window.MPL_REPORT = function (kind) { if (MPL_SENDS[kind]) boardEvent(kind); };
  // 反方向：网页经 USB 线给正在跑的程序发一行字（汉堡店第⑤级发订单 ORDER:芝士堡）。
  // 板子上的程序用 select.poll + sys.stdin.readline 收。正在刷入时不发——会混进写 main.py 的那段代码里。
  // 只发普通文字，绝不带 Ctrl-C（\x03）：那会把学生的程序直接停掉
  window.MPL_TO_BOARD = function (text) {
    if (!serial.connected() || busy) return false;
    serial._write(String(text).replace(/[\x00-\x1f]/g, "") + "\r\n").catch(function () {});
    return true;
  };
  // 汉堡店升了一级：清单、发给 AI 的话（每级补的技术交代不一样）、老师看板都跟着换
  window.MPL_AI_STAGE = function () { renderBuilder(); showFull(); refresh(); };

  function setChip(id, state, text) {
    var c = $(id);
    c.className = "chip" + (state ? " " + state : "");
    c.textContent = text;
  }

  // ---------------- 掌控板 ----------------
  // “板子说”按行处理：整行是 @@MPL:暗号 的，是板子在“告诉网页”，
  // 转成事件，显示成学生看得懂的一句话；其余原样显示。串口数据会被切成任意小段送来，
  // 一行可能分好几次才到齐，所以没换行的那一截先攒着。
  // 粘贴模式会回显代码（"=== print('@@MPL:' + kind)"），那一行不是以 @@MPL 开头，不会被误认。
  var consoleBuf = "", lineBuf = "";
  var MARK = /^@@MPL:([a-z_]+)\s*$/;
  // 自由农场关：板子每 0.1 秒打一行 @@FARM:{一整帧传感器数据}，原样转给左边嵌着的 3D 农场。
  // 这种行一秒十行，不进“板子说”（会把别的输出全刷没），只在第一次出现时提一句。
  var FARM = /^@@FARM:(\{.*\})\s*$/, farmNoted = false;
  function farmFrame(json) {
    var data;
    try { data = JSON.parse(json); } catch (e) { return; }
    var f = document.querySelector("#scene iframe.farm");
    if (f && f.contentWindow) f.contentWindow.postMessage({ type: "mpl-farm", data: data }, location.origin === "null" ? "*" : location.origin);
    if (!farmNoted) { farmNoted = true; consoleBuf += "→ 传感器数据正在发给 3D 农场（每秒约 10 次，不再逐条显示）\n"; }
    // 通关和看板只要知道“通了”这一次，不逐帧上报
    if (DEMO && (DEMO.need || []).indexOf("farm") >= 0 && !seen.farm) boardEvent("farm");
  }
  serial.onText = function (t) {
    lineBuf += t.replace(/\r/g, "");
    var parts = lineBuf.split("\n");
    lineBuf = parts.pop();
    parts.forEach(function (line) {
      var fm = FARM.exec(line);
      if (fm) { farmFrame(fm[1]); return; }
      var m = MARK.exec(line);
      if (m && MPL_SENDS[m[1]]) { boardEvent(m[1]); line = "→ 告诉网页：" + MPL_SENDS[m[1]].text; }
      consoleBuf += line + "\n";
    });
    consoleBuf = consoleBuf.slice(-6000);
    var pre = $("console");
    pre.textContent = consoleBuf + (lineBuf.indexOf("@@") === 0 ? "" : lineBuf);
    pre.scrollTop = pre.scrollHeight;
  };
  var waitTimer = null;
  serial.onStatus = function (s) {
    var on = s === "connected";
    if (s === "waiting") setChip("boardChip", "", "板子复位中，等它回来…");
    else setChip("boardChip", on ? "ok" : "bad", on ? "掌控板已连接" : "掌控板已断开");
    $("connectBtn").textContent = (on || s === "waiting") ? "断开掌控板" : "连接掌控板";
    ["saveBtn", "stopBtn", "rebootBtn"].forEach(function (id) { $(id).disabled = !on; });
    if (s !== "connected") boardIsPython = null;
    clearTimeout(waitTimer);
    if (on) {
      farmNoted = false;
      var id = serial.deviceInfo();
      serial.onText("\n[已连接掌控板" + (id ? "，USB 身份 " + id : "") + "]\n");
    }
    // 正常复位 1~2 秒就会回来；等了 6 秒还没回来，多半是被误送进了下载模式，
    // USB 身份变了，浏览器不认得它，自然等不回来
    if (s === "waiting") waitTimer = setTimeout(function () {
      var w = $("boardWarn");
      w.hidden = false; w.className = "board-warn";
      w.innerHTML = "<b>板子断开后一直没回来。</b>最常见的原因是它被误送进了“下载模式”（刷固件用的状态）——" +
        "<b>固件没有丢，不用重刷</b>。按一下板子上的复位键，再点“断开掌控板”→“连接掌控板”重新选一次。";
    }, 6000);
  };
  serial.onReconnected = function () {
    toast("板子复位后已自动重新连上");
    checkBoard(false);
  };

  // ---- 板子里是不是 MicroPython ----
  var boardIsPython = null;  // null = 还没测过
  // interrupt=false：刚连上时安静地看（不打断板子上正在跑的程序）；
  // interrupt=true：点刷入前，反正要换程序了，可以发 Ctrl-C 把它停下再看。
  // 板子一声不吭（多半是打开串口时被送进了下载模式）：自动按 esptool 的正确顺序硬复位一次，
  // 再安静地看一次。10 秒内只自动复位一次——万一复位本身又让 USB 重连，不能来回死循环。
  var lastAutoReset = 0;
  async function checkBoard(interrupt, afterReset) {
    var w = $("boardWarn");
    w.hidden = false; w.className = "board-warn ok"; w.textContent = "正在检查板子……";
    var r = await serial.probe(!!interrupt);
    if (r.python === null && !afterReset && Date.now() - lastAutoReset > 10000) {
      lastAutoReset = Date.now();
      w.textContent = r.download ? "板子掉进了下载模式，正在帮它重启回正常模式……" : "板子没出声，正在帮它重启一下……";
      try { await serial.hardReset(); } catch (e) {}
      await new Promise(function (res) { setTimeout(res, 2500); });
      if (!serial.connected()) return null;  // 复位让 USB 重连了，交给自动重连那条路去查
      return checkBoard(false, true);
    }
    boardIsPython = r.python;
    if (r.python === null) {
      w.className = "board-warn";
      w.innerHTML = "<b>帮板子重启了一次，它还是没出声。</b>点下面的“重启板子”再试一次；" +
        "还不行就拔掉 USB 线重新插上，再点“连接掌控板”。不用急着重刷固件。";
      pushCode("board-noreply");
      return null;
    } else if (r.python) {
      w.className = "board-warn ok";
      w.textContent = r.running ? "板子正常，而且正在运行程序——下面滚动的就是它打印的内容。"
                                : "板子正常：里面是 MicroPython，可以运行积木程序。";
      setTimeout(function () { if (boardIsPython) w.hidden = true; }, 3000);
    } else {
      w.className = "board-warn";
      w.innerHTML = r.idf
        ? "<b>这块板子里装的不是 MicroPython 固件。</b>它在不停打印“I (数字) 名称: …”这样的系统日志，" +
          "这是乐鑫 ESP-IDF 的 C 语言固件（看标签很像“小智 AI”语音固件），没有能执行 Python 的命令行，" +
          "积木程序发过去也不会运行。请老师先用官方 mPython 软件给板子刷回 mPython 固件，再来连接。"
        : "<b>板子没有回应 MicroPython 的提示符。</b>点“重启板子”再试一次，不用急着重刷固件。";
    }
    pushCode(r.python ? "board-ok" : (r.idf ? "board-idf" : "board-noreply"));
    return r.python;
  }

  async function ensurePython() {
    if (boardIsPython) return true;
    return await checkBoard(true);
  }

  $("connectBtn").addEventListener("click", async function () {
    try {
      if ($("connectBtn").textContent.indexOf("断开") === 0) {
        userDisconnected = true;
        await serial.disconnect(); boardIsPython = null; $("boardWarn").hidden = true; return;
      }
      userDisconnected = false;
      await serial.connect();
      await checkBoard(false);
    } catch (e) {
      if (e && e.name === "NotFoundError") return; // 学生在系统弹窗里点了取消
      toast("连接失败：" + (e && e.message ? e.message : e), 4000);
    }
  });

  function notReadyToast() {
    toast(boardIsPython === false ? "板子里不是 MicroPython，程序发过去不会运行——看左下角的说明。"
                                  : "板子没有回应，程序先不发——看左下角的说明。", 4000);
  }

  $("rebootBtn").addEventListener("click", async function () {
    try {
      lastAutoReset = Date.now();
      await serial.hardReset();
      toast("已重启板子");
      await new Promise(function (res) { setTimeout(res, 2500); });
      if (serial.connected()) checkBoard(false, true);
    } catch (e) { toast("重启失败：" + e.message, 4000); }
  });

  // 失败时把“卡在哪一步、报了什么”同时写在页面上和服务器日志里：
  // 学生知道下一步该干嘛，老师（和排查的人）事后能从 code_log.csv 里看到到底卡在哪
  function showFailure(what, step, e) {
    var w = $("boardWarn");
    w.hidden = false; w.className = "board-warn";
    var stuck = e && e.code === "WRITE_TIMEOUT";
    w.innerHTML = "<b>" + what + "失败</b>（卡在：" + step + "）：" + (e && e.message ? e.message : e) + "。" +
      (stuck ? "板子那头没在收数据。点“↻ 重启板子”，还不行就点“断开掌控板”再“连接掌控板”。"
             : "点“↻ 重启板子”后再试一次。");
    boardIsPython = null;
    pushCode(what + "-fail:" + step + ":" + (e && e.code ? e.code : (e && e.message) || e));
  }

  var busy = false;
  var userDisconnected = false;   // 学生自己点了“断开掌控板”：自动连接就别再把它连回去
  function setBusy(on) {
    busy = on;
    $("saveBtn").disabled = on || !serial.connected();
  }

  // 刷入 = 写进 main.py 再软重启：写完马上就在跑，拔掉电脑线也照跑
  $("saveBtn").addEventListener("click", async function () {
    if (busy) return;
    setBusy(true);
    var step = "检查板子";
    toast("正在刷入……", 8000);
    try {
      if (!(await ensurePython())) { notReadyToast(); return; }
      var unfinished = currentItems().some(function (i) { return !i.ok; });
      await serial.saveMain(currentCode(), function (s) { step = s; toast("正在刷入：" + s + "……", 8000); });
      pushCode("save");
      // 汉堡店 AI 升级要数“改了几次程序”，⑤ 还要在板子重启后把订单重发一遍
      window.dispatchEvent(new Event("mpl-flashed"));
      toast(unfinished ? "程序已写入并重启。还有积木没放对——试一试，看看会发生什么。" : "程序已写入并重启。", 4200);
    } catch (e) {
      toast("刷入失败，看左下角", 3000);
      showFailure("刷入", step, e);
    } finally { setBusy(false); }
  });
  $("stopBtn").addEventListener("click", function () { serial.stop().catch(function () {}); });

  // ---------------- 安全上下文提示 ----------------
  // 浏览器只在 https、localhost、本地文件里开放串口；学生机用 http://老师IP 打开时不算，
  // 得用“学生机打开.bat”带参数启动 Edge，把这个地址加进“视为安全”的名单。
  function checkSecure() {
    if (MplSerial.supported() && window.isSecureContext) return true;
    var w = $("secureWarn");
    var origin = location.origin;
    w.hidden = false;
    if (!("serial" in navigator) && window.isSecureContext) {
      w.innerHTML = "这个浏览器不能连接掌控板，请换成电脑上的 <b>Edge</b> 或 <b>Chrome</b> 打开。";
    } else {
      w.innerHTML = "现在这个窗口不能连接掌控板（浏览器只允许“安全”的网址使用串口）。请关掉它，" +
        "请老师在这台电脑上运行一次 <b>配置学生机浏览器.bat</b>（在 ITtools 的 scripts/mpython 文件夹里，需要管理员权限）后重开浏览器；或者在 Edge 地址栏打开 " +
        "<code>edge://flags/#unsafely-treat-insecure-origin-as-secure</code>，把 <code>" + origin +
        "</code> 填进去并启用后重启浏览器。积木照样可以先拼。";
    }
    $("connectBtn").disabled = true;
    return false;
  }

  // ---------------- 左右分栏：拖动调宽度、收起/展开左栏 ----------------
  // 宽度和收起状态记在本机：学生调好一次，换关、刷新都不用再调。
  (function () {
    var split = $("split"), sp = $("splitter"), fold = $("foldBtn"), w = 0;
    function resize() { if (workspace) Blockly.svgResize(workspace); }
    function setFolded(on) {
      split.classList.toggle("folded", on);
      fold.querySelector(".ar").textContent = on ? "›" : "‹";
      fold.querySelector(".tx").textContent = on ? "展开任务" : "收起任务";
      fold.title = on ? "展开左边的任务和关卡" : "收起左边，积木区变大";
      fold.setAttribute("aria-label", fold.title);
      store("mpl_fold", on ? "1" : null);
      resize();
    }
    var saved = parseInt(store("mpl_lw"), 10);
    if (saved > 0) split.style.setProperty("--lw", saved + "px");
    if (store("mpl_fold") === "1") setFolded(true);
    fold.addEventListener("click", function (e) { e.stopPropagation(); setFolded(!split.classList.contains("folded")); });
    fold.addEventListener("pointerdown", function (e) { e.stopPropagation(); });
    sp.addEventListener("pointerdown", function (e) {
      if (e.button !== 0) return;
      sp.setPointerCapture(e.pointerId);
      split.classList.add("dragging");
      var box = split.getBoundingClientRect(), pad = 12, raf = 0;
      w = 0;
      function move(ev) {
        // 左栏至少 260（任务说明、场景还看得清），右边至少留 420 给积木区
        var x = Math.max(260, Math.min(ev.clientX - box.left - pad, box.width - pad * 2 - 14 - 420));
        split.style.setProperty("--lw", x + "px");
        w = Math.round(x);
        if (split.classList.contains("folded")) setFolded(false);
        if (!raf) raf = requestAnimationFrame(function () { raf = 0; resize(); });
      }
      function up() {
        sp.removeEventListener("pointermove", move);
        sp.removeEventListener("pointerup", up);
        sp.removeEventListener("pointercancel", up);
        split.classList.remove("dragging");
        if (w > 0) store("mpl_lw", String(w));
        resize();
      }
      sp.addEventListener("pointermove", move);
      sp.addEventListener("pointerup", up);
      sp.addEventListener("pointercancel", up);
    });
    // 双击分隔条恢复默认宽度
    sp.addEventListener("dblclick", function (e) {
      if (fold.contains(e.target)) return;
      split.style.removeProperty("--lw"); store("mpl_lw", null); resize();
    });
  })();

  // ---------------- 启动 ----------------
  async function boot() {
    try {
      var r = await fetch(API + "/api/config");
      cfg = Object.assign(cfg, await r.json());
    } catch (e) {
      setChip("srvChip", "bad", "连不上服务器");
    }
    // 这节课上哪几关（老师在看板顶上选）。原地删，DEMOS 别处拿的是同一个数组。
    // 熟悉课：第一次摸掌控板，温室巡检四个小关 + 综合巡检（四个小关的知识点就是公开课那四关的）；
    // 公开课：夜灯、声控灯、智能门、骰子 + 汉堡店，不放 3D 温室，免得注意力被拽走
    var LESSONS = { "熟悉课": ["kb0", "gh1", "gh2", "gh3", "gh4", "demo6"], "公开课": ["demo1", "demo2", "demo3", "demo4", "demo7"] };
    var keep = LESSONS[cfg.lesson];
    if (keep) for (var di = DEMOS.length - 1; di >= 0; di--) if (keep.indexOf(DEMOS[di].id) < 0) DEMOS.splice(di, 1);
    window.MPL_RUNTIME.host = cfg.board_host;
    initWorkspace();
    if (LOCAL) {
      $("groupBtn").textContent = EDIT ? "教师编辑" : "演示 " + VIEW + " 组";
      $("groupBtn").disabled = true;
      window.MPL_RUNTIME.group = "1";   // 生成的代码只是预览，组号随便给一个
      if (cfg.board_host) setChip("srvChip", "ok", "服务器已连接");
    }
    if (EDIT) {
      $("editBar").hidden = false;
      await setLevel(savedLevel(), true);
    } else if (VIEW) {
      // 取这一组此刻的积木和关卡
      var gs = null;
      try { gs = ((await (await fetch(API + "/api/state")).json()).state || {})[VIEW]; } catch (e) {}
      var li = gs && gs.level ? DEMOS.findIndex(function (d) { return d.id === gs.level; }) : -1;
      $("viewBar").hidden = false;
      $("viewTip").textContent = gs && gs.ws
        ? VIEW + " 组的程序（" + (gs.updated || "") + " 时的样子）。在这里改动、刷入都不会影响他们自己的积木。"
        : VIEW + " 组还没有上传过积木，下面是初始积木。";
      $("resetBtn").hidden = true;
      await setLevel(li >= 0 ? li : savedLevel(), true, gs && gs.ws ? gs.ws : null);
    } else if (group) {
      await setGroup(group);
    } else {
      await setLevel(savedLevel(), true);
      askGroup();
    }
    if (checkSecure()) {
      // 自动连接：浏览器已经批准过的板子（上次手动点“连接掌控板”选中的那块，或者
      // “学生机打开.bat”配的 Edge 策略提前批准过的 USB 芯片）不用学生点。
      // 原来只在页面打开那一刻试一次——学生常常是先开页面、后插板子，那样就永远连不上。
      // 现在板子插上时浏览器会发 connect 事件，再加每 3 秒一次兜底。
      // 学生自己点了“断开”就不再自动连回去，不然断都断不掉。
      var autoBusy = false;
      var tryAuto = function () {
        if (autoBusy || busy || userDisconnected || serial.connected()) return;
        autoBusy = true;
        // 连不上时把原因写在“掌控板”那颗状态上：没被批准（策略没生效 / 板子没插 / 芯片不在策略里）
        // 和批准了却打不开，处理办法完全不同；只写“未连接”的话，老师远程排查只能干猜
        navigator.serial.getPorts().then(function (ports) {
          if (!ports.length) {
            setChip("boardChip", "bad", "掌控板未连接 · 浏览器未批准自动连接");
            return;
          }
          return serial.reconnectRemembered()
            .then(function (ok) { if (ok) return checkBoard(false); })
            .catch(function (e) {
              setChip("boardChip", "bad", "已批准但打不开：" + ((e && e.message) || e).toString().slice(0, 40));
            });
        }).catch(function () {}).then(function () { autoBusy = false; });
      };
      tryAuto();
      if (navigator.serial) navigator.serial.addEventListener("connect", tryAuto);
      setInterval(tryAuto, 3000);
    }
  }

  boot();
})();
