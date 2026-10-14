// 第 6 关 · 拓展：掌控汉堡店——自己搭操作台，再照顾客的要求一层层做汉堡。
//
// 温室巡检练的是“读懂别人的程序、找错”；这一关反过来，练“自己搭逻辑”：
//   ① 编程：给五种食材 + 上菜 + 撤掉一层，各安排一个按键 / 触摸键（用“当…被按下时”）。
//      必须是事件积木——放进“一直重复 + 如果”的话，按住不放会一直往上叠。
//   ② 出餐：顾客一个接一个来，要求越来越绕——
//      照做法做 → 只报菜名（自己查菜单）→ 菜单改配方（不要 X、把 X 换成 Y）→ 出谜语（只给几条线索）。
//      谜语单按“线索全对上”判，不按某一个标准答案：符合全部线索的汉堡都算对，这才是在考推理。
//
// 刻意不计时：一计时就成了拼手速，学生不会再停下来想“生菜到底在不在肉饼上面”。
// 放错了随时“撤掉一层”，不扣分；只有端上去做错了才扣 5 分——鼓励上菜前自己先核对一遍。
// 每组的顾客顺序、谜语按组号出，看同桌的屏幕没用。
(function () {
  "use strict";
  var lock = MplKit.lock, rng = MplKit.rng, shuffle = MplKit.shuffle, pick = MplKit.pick;

  var ING = ["bg_bun", "bg_meat", "bg_cheese", "bg_veg", "bg_tom"];
  var FILL = ING.slice(1);                         // 能当“馅”的四样
  var CMD = ING.concat(["bg_serve", "bg_undo"]);
  var PASS = 70, WRONG = 5, MAX = 9;               // 9 层：最长的谜语汉堡 8 层，再留一层给放错
  var PTS = { list: 5, menu: 10, mod: 10, riddle: 20 };
  function n(k) { return MPL_SENDS[k].text; }

  var MENU = [
    { name: "经典堡", fill: ["bg_meat", "bg_veg"] },
    { name: "芝士堡", fill: ["bg_meat", "bg_cheese"] },
    { name: "番茄堡", fill: ["bg_veg", "bg_tom", "bg_meat"] },
    { name: "双层芝士堡", fill: ["bg_meat", "bg_cheese", "bg_meat", "bg_cheese"] },
    { name: "素食堡", fill: ["bg_veg", "bg_tom", "bg_veg"] }
  ];
  // 动物顾客：单个码位的表情。👨‍🍳 这类拼起来的表情机房字体里会拆成两个
  var GUESTS = ["🐱 小猫", "🐶 小狗", "🐼 熊猫", "🐰 兔子", "🦊 狐狸", "🐻 小熊", "🐸 青蛙", "🐯 老虎", "🐧 企鹅", "🐨 考拉", "🐷 小猪", "🐵 猴子"];

  function bun(fill) { return ["bg_bun"].concat(fill, ["bg_bun"]); }
  function cnt(f, x) { var c = 0; for (var i = 0; i < f.length; i++) if (f[i] === x) c++; return c; }
  function adjacent(f, x, y) {
    for (var i = 1; i < f.length; i++) if ((f[i] === x && f[i - 1] === y) || (f[i] === y && f[i - 1] === x)) return true;
    return false;
  }
  function chain(s) { return s.map(n).join(" → "); }

  // ---------------- 订单 ----------------
  // 改配方：每次在“当前的馅”上改一处，返回 {text, fill, used}。保证改完还剩至少一层馅、不超过 6 层。
  // avoid：上一处改过的食材这次不再碰——不然会出“不要肉饼，再多加一片肉饼”这种自相矛盾的单
  function modify(fill, r, avoid) {
    var ok = function (x) { return avoid.indexOf(x) < 0; };
    var present = FILL.filter(function (x) { return fill.indexOf(x) >= 0 && ok(x); });
    var absent = FILL.filter(function (x) { return fill.indexOf(x) < 0 && ok(x); });
    var opts = ["add"];
    if (present.some(function (x) { return fill.some(function (y) { return y !== x; }); })) opts.push("no");
    if (present.length && absent.length) opts.push("swap");
    if (fill.length >= 6) opts = opts.filter(function (t) { return t !== "add"; });
    var t = pick(opts, r), x, y;
    if (t === "no") {
      x = pick(present.filter(function (p) { return fill.some(function (q) { return q !== p; }); }), r);
      return { text: "不要" + n(x), fill: fill.filter(function (z) { return z !== x; }), used: [x] };
    }
    if (t === "swap") {
      x = pick(present, r); y = pick(absent, r);
      return { text: "把" + n(x) + "换成" + n(y), fill: fill.map(function (z) { return z === x ? y : z; }), used: [x, y] };
    }
    x = pick(FILL.filter(ok), r);
    // “放在 Y 上面”只挑馅里只出现一次的 Y，不然说不清是哪一片
    var uniq = FILL.filter(function (q) { return cnt(fill, q) === 1 && ok(q) && q !== x; });
    if (!uniq.length) return { text: "多加一片" + n(x) + "，放在所有馅的最上面", fill: fill.concat([x]), used: [x] };
    y = pick(uniq, r);
    var i = fill.indexOf(y), f = fill.slice();
    f.splice(i + 1, 0, x);
    return { text: "多加一片" + n(x) + "，直接压在" + n(y) + "上面", fill: f, used: [x, y] };
  }

  // 谜语：先随手做一个“标准答案”，从它身上能说的真话里挑几条当线索，
  // 再把所有可能的汉堡（馅 1～6 层）枚举一遍，挑一组“符合的汉堡不超过 4 种”的线索——
  // 太松了谁都蒙得对，就不是推理了；但也不要求唯一，答案不止一种时哪种都算对。
  var ALL = null;
  function allFills() {
    if (ALL) return ALL;
    ALL = [];
    (function grow(pre) {
      if (pre.length) ALL.push(pre);
      if (pre.length < 6) FILL.forEach(function (x) { grow(pre.concat([x])); });
    })([]);
    return ALL;
  }
  function cluesFor(f) {
    var out = [], L = f.length;
    out.push({ text: "一共 " + (L + 2) + " 层（算上下两片面包）", test: function (g) { return g.length === L; } });
    FILL.forEach(function (x) {
      var c = cnt(f, x);
      if (!c) out.push({ text: "不要" + n(x), test: function (g) { return cnt(g, x) === 0; } });
      else {
        out.push({ text: "要有" + n(x), test: function (g) { return cnt(g, x) > 0; } });
        if (c >= 2) out.push({ text: n(x) + "正好 " + c + " 片", test: function (g) { return cnt(g, x) === c; } });
      }
    });
    var lo = f[0], hi = f[L - 1];
    out.push({ text: "铺在下面那片面包上的第一层是" + n(lo), test: function (g) { return g[0] === lo; } });
    out.push({ text: "紧贴着上面那片面包的是" + n(hi), test: function (g) { return g[g.length - 1] === hi; } });
    for (var i = 1; i < L; i++) (function (a, b) {
      if (a === b) return;
      out.push({ text: n(a) + "直接压在" + n(b) + "上面", test: function (g) {
        for (var j = 1; j < g.length; j++) if (g[j] === a && g[j - 1] === b) return true;
        return false;
      } });
    })(f[i], f[i - 1]);
    FILL.forEach(function (x, xi) {
      FILL.forEach(function (y, yi) {
        if (yi <= xi || !cnt(f, x) || !cnt(f, y)) return;
        if (!adjacent(f, x, y)) out.push({ text: n(x) + "和" + n(y) + "不能挨着", test: function (g) { return !adjacent(g, x, y); } });
      });
      FILL.forEach(function (y) {
        if (x !== y && cnt(f, y) && cnt(f, x) > cnt(f, y))
          out.push({ text: n(x) + "比" + n(y) + "多", test: function (g) { return cnt(g, x) > cnt(g, y); } });
      });
    });
    return out;
  }
  function riddle(r, k, minLen, maxLen) {
    for (var tries = 0; tries < 30; tries++) {
      var L = minLen + Math.floor(r() * (maxLen - minLen + 1)), f = [];
      for (var i = 0; i < L; i++) f.push(pick(FILL, r));
      var cs = cluesFor(f);
      for (var kk = k; kk <= k + 1; kk++) {
        for (var t = 0; t < 40; t++) {
          var sub = shuffle(cs.slice(), r).slice(0, kk);
          var sols = allFills().filter(function (g) { return sub.every(function (c) { return c.test(g); }); }).length;
          if (sols <= 4) return { type: "riddle", clues: sub, sample: f };
        }
      }
    }
    return null;
  }

  // 第 idx 单（从 0 数）：0～1 照做法做、2～3 报菜名、4～5 改一处配方、之后谜语为主，每三单夹一单改两处的
  var cache = {};
  function orderAt(idx) {
    var key = MplKit.seed() + ":" + idx;
    if (cache[key]) return cache[key];
    var r = rng(MplKit.seed() * 7907 + idx * 131 + 5), o;
    var guest = GUESTS[(MplKit.seed() * 5 + idx) % GUESTS.length];
    if (idx < 2) {
      var L = 2 + idx, f = [];
      for (var i = 0; i < L; i++) f.push(pick(FILL, r));
      o = { type: "list", want: bun(f) };
    } else if (idx < 4) {
      var m = MENU[(MplKit.seed() + idx) % MENU.length];
      o = { type: "menu", menu: m.name, want: bun(m.fill) };
    } else if (idx < 6 || idx % 3 === 2) {
      var mm = pick(MENU, r), mods = [], fill = mm.fill, used = [];
      for (var j = 0; j < (idx < 6 ? 1 : 2); j++) { var md = modify(fill, r, used); mods.push(md.text); fill = md.fill; used = used.concat(md.used); }
      o = { type: "mod", menu: mm.name, mods: mods, want: bun(fill) };
    } else {
      o = riddle(r, idx < 9 ? 3 : 4, idx < 9 ? 2 : 3, idx < 9 ? 4 : 6);
      if (!o) { var m2 = pick(MENU, r); o = { type: "menu", menu: m2.name, want: bun(m2.fill) }; }
    }
    o.guest = guest;
    return (cache[key] = o);
  }

  // 判一个端上来的汉堡：返回 null 是做对了，否则是顾客的一句抱怨（指出哪里不对，但谜语单不说正确答案）
  function judge(o, s) {
    if (o.type !== "riddle") {
      var e = o.want;
      for (var i = 0; i < Math.max(s.length, e.length); i++) {
        if (s[i] === e[i]) continue;
        if (i >= s.length) return "还没做完呢：从下往上第 " + (i + 1) + " 层该放" + n(e[i]);
        if (i >= e.length) return "多放了：从下往上第 " + (i + 1) + " 层的" + n(s[i]) + "不该有";
        return "从下往上第 " + (i + 1) + " 层应该是" + n(e[i]) + "，你放的是" + n(s[i]);
      }
      return null;
    }
    if (s.length < 3 || s[0] !== "bg_bun" || s[s.length - 1] !== "bg_bun") return "汉堡最下面和最上面都得是面包呀（看店规）";
    var g = s.slice(1, -1);
    if (g.indexOf("bg_bun") >= 0) return "中间怎么夹了一片面包？（看店规）";
    for (var j = 0; j < o.clues.length; j++) if (!o.clues[j].test(g)) return "我说了「" + o.clues[j].text + "」呀！";
    return null;
  }

  // ---------------- 画面 ----------------
  function layers(s, fresh) {
    return s.map(function (k, i) {
      var c = k === "bg_bun" ? (i === 0 ? "bun-b" : "bun-t") : k.slice(3);
      return '<i class="ly ' + c + (fresh && i === s.length - 1 ? " drop" : "") + '"></i>';
    }).join("");
  }
  function mini(s) { return '<span class="bg-mini">' + layers(s) + '</span>'; }

  function orderHtml(o) {
    var who = '<b class="bg-who">' + o.guest.split(" ")[1] + '：</b>';
    if (o.type === "list") return who + "照这个做（从下往上）：<div class='bg-want'>" + mini(o.want) + "<span>" + chain(o.want) + "</span></div>";
    if (o.type === "menu") return who + "来一个<b class='bg-dish'>" + o.menu + "</b>！<small>（配方看下面的菜单）</small>";
    if (o.type === "mod") return who + "来一个<b class='bg-dish'>" + o.menu + "</b>，" + o.mods.join("，再") + "。";
    return who + "我很挑剔，听好了——<ol>" + o.clues.map(function (c) { return "<li>" + c.text + "</li>"; }).join("") +
      "</ol><small>符合全部要求就行，做法不止一种。</small>";
  }

  // 学生程序里“哪个键管哪件事”：check() 每次都会算一遍，场景里那排“你的操作台”就照它画
  var keymap = [];
  function keysHtml() {
    var have = {};
    var chips = keymap.map(function (k) {
      k.kinds.forEach(function (x) { have[x] = true; });
      return '<span class="bg-chip"><b>' + k.key + '</b> → ' + (k.kinds.length ? k.kinds.map(n).join("、") : "（空的）") + '</span>';
    });
    var miss = CMD.filter(function (x) { return !have[x]; });
    return '<b>你的操作台：</b>' + (chips.length ? chips.join("") : "<span class='bg-none'>还没有接任何键</span>") +
      (miss.length ? '<div class="bg-miss">还没接：' + miss.map(n).join("、") + '</div>' : "");
  }

  var S = null;   // 这一组的店：{score, idx, done, stack, passed}
  function storeKey() { return "mpl-burger-g" + MplKit.seed(); }
  function load() {
    try {
      var s = JSON.parse(localStorage.getItem(storeKey()) || "null");
      if (s && typeof s.score === "number" && Array.isArray(s.stack)) return s;
    } catch (e) {}
    return { score: 0, idx: 0, done: 0, stack: [], passed: false };
  }
  function save() { try { localStorage.setItem(storeKey(), JSON.stringify(S)); } catch (e) {} }

  function render(root, msg, cls, fresh) {
    var o = orderAt(S.idx);
    var pct = Math.min(100, Math.round(S.score / PASS * 100));
    root.innerHTML =
      '<div class="bg-top"><b>🍔 掌控汉堡店</b><span>得分 <b class="bg-score">' + S.score + '</b></span>' +
      '<span>已出餐 ' + S.done + ' 单</span><button type="button" class="bg-reset" title="分数清零，从第一位顾客重新开始">重新开店</button></div>' +
      '<div class="bg-prog"><i style="width:' + pct + '%"></i><em>' + (S.passed ? "🎉 已通关，继续接单吧" : "攒到 " + PASS + " 分通关") + '</em></div>' +
      '<div class="bg-main"><div class="bg-cust"><div class="bg-ava">' + o.guest.split(" ")[0] + '</div>' +
      '<div class="bg-bub">' + orderHtml(o) + '<em class="bg-pts">做对 +' + PTS[o.type] + '</em></div></div>' +
      '<div class="bg-plate"><div class="bg-stack">' + layers(S.stack, fresh) + '</div><div class="bg-dish-plate"></div>' +
      '<div class="bg-count">' + (S.stack.length ? S.stack.length + " 层" : "空盘子") + '</div></div></div>' +
      '<div class="bg-msg ' + (cls || "") + '">' + (msg || "等掌控板发来指令") + '</div>' +
      '<div class="bg-keys">' + keysHtml() + '</div>' +
      '<div class="bg-card"><b>📖 菜单</b><table>' + MENU.map(function (m) {
        return '<tr><td>' + mini(bun(m.fill)) + '</td><td><b>' + m.name + '</b><br><small>' + chain(bun(m.fill)) + '</small></td></tr>';
      }).join("") + '</table>' +
      '<b>📜 店规</b><ul><li>最下、最上都是面包，中间不夹面包</li>' +
      '<li>放错了“撤掉一层”，不扣分</li>' +
      '<li>上菜才算数：做对加分，做错 −' + WRONG + ' 分</li>' +
      '<li>不限时间</li></ul>' +
      '<b>🤖 挑战</b><ul><li>一个键做出一整个经典堡</li>' +
      '<li>同一块“当…被按下时”里，按顺序放几句“告诉网页”</li></ul></div>';
  }

  var recent = [];   // 最近几次食材到达的时刻：一口气来三样 = 学生做了“一键出餐”
  var lastIng = {};

  // ================= AI 写代码页签：三级升级 =================
  // 同一家汉堡店，做汉堡的方式一级比一级智能。原来拆过五级，但②跟④、①跟③站在“操作的人”
  // 这一侧看其实是同一回事（②④都是按一个键出一整个汉堡，①③都是一个个按食材），合并成三级更清楚：
  //   ① 手动配菜：一个个按食材键；也可以先按食材键“记下来”、再按出餐键一次性放出来——不用学生自己想出
  //      两种做法的区别，两种都算这一级，练的是“照着顺序一步步把汉堡拼出来”
  //   ② 一键出餐：按一个键，直接出一整个汉堡。换一种汉堡，要么改要求重新刷入（笨办法），要么一开始就把
  //      整张菜单告诉 AI、让它自己查表（聪明办法）——这一级故意先让学生感受笨办法的麻烦，
  //      真正通关要“不改程序也能做对 3 种不同的汉堡”，逼着他们换成菜单做法
  //   ③ 全自动：网页把订单发给板子，板子自己查菜单、做好、上菜——一个键都不按
  // 判过关只看板子实际做了什么（层是一口气来的还是一层层来的、中间刷没刷过程序），不看代码怎么写：
  // 学生没学过代码，AI 的写法又五花八门，看代码判只会冤枉人。
  var LADDER = [
    { name: "手动配菜", need: 2,
      goal: ["7 件事各接一个键：五种食材、上菜、撤掉一层", "一个个按；也可以先按食材键“记下”、再按出餐键一次性放出来", "做对 2 单"] },
    { name: "一键出餐", need: 3,
      goal: ["按一个键，直接做出一整个汉堡", "换一种汉堡：改要求重新刷入——或者把整张菜单告诉 AI，让它自己查", "不改程序，做对 3 种不同的汉堡才算真正通关"] },
    { name: "全自动", need: 3,
      goal: ["网页把订单（如 ORDER:芝士堡）发给板子", "板子自己查菜单、做好、上菜", "一个键都不按，做对 3 单"] }
  ];
  var BURST = 800;      // 一个汉堡的所有层在这么多毫秒内到齐 = 板子一口气放的，不是人一层层按的
  var AUTO = 5000;      // ③：订单发出去这么久之内上菜，才算板子自己做的（人看到再按来不及）

  var aiMode = false;
  var L = null;         // {stage, idx, cnt, run:[菜名], flashes:[每级刷了几次], times:[[每单毫秒]], stack, at}
  function ladderKey() { return "mpl-burger-ai-g" + MplKit.seed(); }
  function ladderLoad() {
    try {
      var s = JSON.parse(localStorage.getItem(ladderKey()) || "null");
      // 五级改三级之前存的进度，stage 可能是 2（原"组装模式"）、3、4——现在 LADDER 只有 3 项
      // （0~2），3、4 会让 LADDER[s.stage] 变成 undefined，renderAi 一读 .need 就整页报错；
      // 原 stage 2 虽然数字上还在范围内，但新的 index 2 已经是"全自动"，意思完全对不上。
      // 旧进度和新的三级分法没法原样接着（原来的②③④⑤被拆重组进新的①②），干脆当没存过，
      // 只有原 0、1（手工配菜、一键出餐，和新①②意思没变）才续得上
      if (s && typeof s.stage === "number" && s.stage < 2 && Array.isArray(s.stack)) return s;
    } catch (e) {}
    return { stage: 0, idx: 0, cnt: 0, run: [], flashes: [0, 0, 0], times: [[], [], []], stack: [], at: [] };
  }
  function ladderSave() { try { localStorage.setItem(ladderKey(), JSON.stringify(L)); } catch (e) {} }

  // 这一级的第几位顾客：①给做法，之后都报菜名。菜名按组号错开、相邻两位一定不一样——
  // ②要的就是“不改程序也能换着做”
  function aiOrder() {
    var guest = GUESTS[(MplKit.seed() * 3 + L.stage * 4 + L.idx) % GUESTS.length];
    if (L.stage === 0) {
      var r = rng(MplKit.seed() * 4409 + L.idx * 97 + 11), f = [];
      for (var i = 0; i < 2 + L.idx % 2; i++) f.push(pick(FILL, r));
      return { type: "list", want: bun(f), guest: guest };
    }
    var m = MENU[(MplKit.seed() + L.stage + L.idx) % MENU.length];
    return { type: "menu", menu: m.name, want: bun(m.fill), guest: guest };
  }

  // 订单开始计时的时刻（不存盘：刷新后从刷新那一刻重新算，最多多算几秒）
  var orderT0 = 0, orderSeen = -1, sentAt = 0, sentFor = -1;
  function startOrder() {
    var key = L.stage * 1000 + L.idx;
    if (orderSeen !== key) { orderSeen = key; orderT0 = Date.now(); }
  }
  // ③：把当前订单经 USB 线发给板子。没连板子 / 正在刷入就发不出去，返回 false
  function sendOrder(force) {
    if (L.stage !== 2) return true;
    var key = L.stage * 1000 + L.idx;
    if (!force && sentFor === key) return true;
    var o = aiOrder();
    if (!window.MPL_TO_BOARD || !window.MPL_TO_BOARD("ORDER:" + o.menu)) return false;
    sentFor = key; sentAt = Date.now();
    return true;
  }

  function fmtSec(ms) { return (ms / 1000).toFixed(ms < 10000 ? 1 : 0) + " 秒"; }
  function avg(a) { var s = 0; a.forEach(function (x) { s += x; }); return a.length ? s / a.length : 0; }

  // 越来越智能的证据：每一级平均做一单要多久、改了几次程序。只列做过的几级
  function statsHtml() {
    var rows = LADDER.map(function (st, i) {
      if (!L.times[i].length && i !== L.stage) return "";
      return "<tr" + (i === L.stage ? " class='cur'" : "") + "><td>" + "①②③"[i] + " " + st.name + "</td><td>" +
        (L.times[i].length ? fmtSec(avg(L.times[i])) : "—") + "</td><td>" + L.flashes[i] + " 次</td></tr>";
    }).join("");
    return "<b>📊 越来越智能</b><table class='bg-stats'><tr><th>做法</th><th>平均每单用时</th><th>改程序（刷入）</th></tr>" + rows + "</table>";
  }

  function renderAi(root, msg, cls, fresh) {
    var st = LADDER[L.stage], done = L.stage >= LADDER.length - 1 && L.cnt >= st.need;
    startOrder();
    var o = aiOrder();
    var pills = LADDER.map(function (s, i) {
      return '<button type="button" class="bg-lv' + (i === L.stage ? " cur" : "") + (i < L.stage ? " done" : "") +
        '" data-lv="' + i + '">' + "①②③"[i] + " " + s.name + "</button>";
    }).join('<i class="bg-arrow">→</i>');
    var prog = L.stage >= 1 ? "不改程序已连续做对 " + L.run.length + " 种（要 " + st.need + " 种）" : "已做对 " + L.cnt + " / " + st.need + " 单";
    var send = L.stage === 2 ? '<button type="button" class="bg-send">📨 把订单发给板子</button>' : "";
    root.innerHTML =
      '<div class="bg-ladder">' + pills + '</div>' +
      '<div class="bg-goal"><b>第 ' + "①②③"[L.stage] + ' 级 · ' + st.name + '</b><ul>' + st.goal.map(function (g) { return '<li>' + g + '</li>'; }).join('') + '</ul>' +
      '<div class="bg-goal-prog">' + (done ? "🎉 三级全部完成！" : prog) + send + '</div></div>' +
      '<div class="bg-main"><div class="bg-cust"><div class="bg-ava">' + o.guest.split(" ")[0] + '</div>' +
      '<div class="bg-bub">' + orderHtml(o) + '</div></div>' +
      '<div class="bg-plate"><div class="bg-stack">' + layers(L.stack, fresh) + '</div><div class="bg-dish-plate"></div>' +
      '<div class="bg-count">' + (L.stack.length ? L.stack.length + " 层" : "空盘子") + '</div></div></div>' +
      '<div class="bg-msg ' + (cls || "") + '">' + (msg || (L.stage === 2 ? "订单已经发给板子，等它自己做" : "等掌控板发来指令")) + '</div>' +
      '<div class="bg-keys">' + keysHtml() + '</div>' +
      '<div class="bg-card">' + statsHtml() +
      (done ? '<b>🤔 想一想</b><ul><li>谜语单（“生菜比芝士多，番茄和肉饼不能挨着”）板子能全自动做吗？</li><li>为什么？还缺什么？</li></ul>' : "") +
      '<b>📖 菜单</b><table>' + MENU.map(function (m) {
        return '<tr><td>' + mini(bun(m.fill)) + '</td><td><b>' + m.name + '</b><br><small>' + chain(bun(m.fill)) + '</small></td></tr>';
      }).join("") + '</table></div>';
    var task = document.getElementById("aiTask");
    if (task) task.textContent = "🎯 第 " + "①②③"[L.stage] + " 级 · " + st.name + "：" + st.goal.join("；");
  }

  function gotoStage(i, root, msg) {
    L.stage = i; L.idx = 0; L.cnt = 0; L.run = []; L.stack = []; L.at = [];
    ladderSave();
    var ok = sendOrder();
    renderAi(root, msg || (i === 2 && !ok ? "连上掌控板后，点上面的“📨 把订单发给板子”" : ""), msg ? "ok" : (i === 2 && !ok ? "tip" : ""));
    if (window.MPL_AI_STAGE) window.MPL_AI_STAGE();
  }

  function onAi(type, scene) {
    var root = scene.querySelector(".bg-root");
    if (!scene._d7ai) {
      scene._d7ai = true;
      root.addEventListener("click", function (e) {
        var lv = e.target.getAttribute && e.target.getAttribute("data-lv");
        if (lv !== null && lv !== undefined) { gotoStage(+lv, root); return; }
        if (e.target.classList.contains("bg-send")) {
          renderAi(root, sendOrder(true) ? "订单「" + aiOrder().menu + "」已发给板子" : "发不出去：先连上掌控板", sentFor >= 0 ? "" : "tip");
        }
      });
    }
    if (!type) { var ok0 = sendOrder(); renderAi(root, L.stage === 2 && !ok0 ? "连上掌控板后，点上面的“📨 把订单发给板子”" : "", L.stage === 2 && !ok0 ? "tip" : ""); return ""; }
    if (type === "bg_pass") { renderAi(root, "🎉 前两级都完成了，这一关通关！还可以挑战第 ③ 级。", "ok"); return ""; }

    var msg = "", cls = "", fresh = false, now = Date.now();
    if (ING.indexOf(type) >= 0) {
      if (L.stack.length >= MAX) { msg = "盘子堆不下了！先“撤掉一层”，或者“上菜”"; cls = "tip"; }
      else { L.stack.push(type); L.at.push(now); fresh = true; msg = "放上一层" + n(type); }
    } else if (type === "bg_undo") {
      if (L.stack.length) { L.at.pop(); msg = "撤掉了最上面的" + n(L.stack.pop()); } else msg = "盘子已经是空的了";
    } else if (type === "bg_serve") {
      var o = aiOrder(), st = LADDER[L.stage];
      if (!L.stack.length) { msg = "盘子是空的，先做汉堡再上菜"; cls = "tip"; }
      else {
        var bad = judge(o, L.stack);
        var burst = L.at.length >= 3 && L.at[L.at.length - 1] - L.at[0] < BURST;
        var took = now - (L.stage === 2 && sentAt ? sentAt : orderT0);
        L.stack = []; L.at = [];
        var who = o.guest.split(" ")[1];
        if (bad) { msg = "😣 " + who + "：" + bad + "。汉堡退回来了，再做一个。"; cls = "bad"; }
        // 做对了，但不是这一级要的做法：说清楚差在哪，不算数
        else if (L.stage >= 1 && !burst) {
          msg = "😋 " + who + "：好吃！不过这是一层层按出来的——这一级要板子" +
            (L.stage === 2 ? "自己做" : "一下子做出来") + "，这单不算。改要求、问 AI、重新刷入再试。"; cls = "tip";
          L.idx++;
        } else if (L.stage === 2 && took > AUTO) {
          msg = "😋 " + who + "：好吃！不过订单发出去 " + fmtSec(took) + " 才上菜，像是人按的——这一级要板子收到订单自己做、自己上菜。"; cls = "tip";
          L.idx++;
        } else {
          L.times[L.stage].push(took);
          L.idx++;
          if (L.stage >= 1) { if (L.run.indexOf(o.menu) < 0) L.run.push(o.menu); L.cnt = L.run.length; } else L.cnt++;
          msg = "😋 " + who + "：好吃！用了 " + fmtSec(took) + "。"; cls = "ok";
          if (L.cnt >= st.need && L.stage < LADDER.length - 1) {
            var next = L.stage + 1;
            if (next === 2) setTimeout(function () { if (window.MPL_REPORT) window.MPL_REPORT("bg_pass"); }, 0);
            ladderSave();
            setTimeout(function () { gotoStage(next, root, "🎉 升级成功！第 " + "①②③"[next] + " 级：" + LADDER[next].name); }, 1600);
          }
        }
        sendOrder();
      }
    }
    ladderSave();
    renderAi(root, msg, cls, fresh);
    return "";
  }

  // 刷入了一次新程序：记到这一级的“改程序”次数上；②要“不改程序连做几种”，从头数起。
  // ③ 板子刚重启、还没开始读订单，等两秒再把当前订单重发一次
  window.addEventListener("mpl-flashed", function () {
    if (!L) return;
    L.flashes[L.stage]++;
    if (L.stage >= 1) { L.run = []; L.cnt = 0; }
    ladderSave();
    var sc = document.querySelector(".s-demo7 .bg-root");
    if (aiMode && sc) {
      renderAi(sc, L.stage >= 1 ? "刷入了新程序。这一级要“不改程序”连着做，从头数起。" : "刷入了新程序。", "tip");
      if (L.stage === 2) setTimeout(function () { if (sendOrder(true)) renderAi(sc, "订单「" + aiOrder().menu + "」已发给板子"); }, 2500);
    }
  });

  MplKit.register({
    id: "demo7",
    color: "#EA580C", icon: "🍔",
    short: "汉堡店",
    starterVersion: "1",
    title: "掌控汉堡店",
    // 积木那边锁死只有一块样例，唯一真做法是 AI——粘过 AI 代码以后，不管停在哪个页签，
    // “看代码”“检查清单”“刷入”都认 AI 代码，不会切回“积木”页签就显示那块不相关的样例
    aiSticky: true,
    // 说明一律简要、分点（taskBrief / taskHint 按换行分行显示）：学生读长段落会跳过去
    brief: "• 顾客排队买汉堡，照要求一层层做\n" +
      "• 做对加分，做错扣分，攒到 " + PASS + " 分通关\n" +
      "• 越往后顾客越挑剔，还会出谜语\n" +
      "• 不会编程？点右边“🤖 AI 写代码”",
    hint: "• 7 件事各接一个键：五种食材、上菜、撤掉一层\n" +
      "• 用“输入”里的“当按键…被按下时”“当触摸键…被摸到时”\n" +
      "• A、B 不够用，剩下的用触摸键",
    sends: CMD,
    need: ["bg_pass"],
    doneHint: "操作台搭好了！点“刷入”开始接单。",

    scene: '<div class="bg-root"></div>',
    css:
      '.scene.s-demo7{height:auto;background:#FFF8EE;overflow:visible;padding:10px 12px 12px}' +
      '.s-demo7 .caption{display:none}' +
      '.s-demo7 .bg-top{display:flex;align-items:center;gap:12px;font-size:13px;color:#7C2D12}' +
      '.s-demo7 .bg-top>b{font-size:16px;margin-right:auto}' +
      '.s-demo7 .bg-score{font-size:20px;color:#EA580C}' +
      '.s-demo7 .bg-reset{background:#fff;color:#9A3412;border:1px solid #FDBA74;padding:2px 8px;font-size:12px}' +
      '.s-demo7 .bg-reset:hover{background:#FFEDD5}' +
      '.s-demo7 .bg-prog{position:relative;height:16px;margin:6px 0 10px;border-radius:8px;background:#FDE7CF;overflow:hidden}' +
      '.s-demo7 .bg-prog i{position:absolute;left:0;top:0;bottom:0;background:linear-gradient(90deg,#FDBA74,#EA580C);transition:width .5s}' +
      '.s-demo7 .bg-prog em{position:relative;display:block;text-align:center;font:700 11px/16px var(--font);color:#7C2D12;font-style:normal}' +
      '.s-demo7 .bg-main{display:flex;gap:10px;align-items:flex-end}' +
      '.s-demo7 .bg-cust{flex:1;display:flex;gap:8px;align-items:flex-start;min-width:0}' +
      '.s-demo7 .bg-ava{font-size:40px;line-height:1;animation:bgbob 2.4s ease-in-out infinite}' +
      '@keyframes bgbob{50%{transform:translateY(-4px)}}' +
      '.s-demo7 .bg-bub{position:relative;flex:1;background:#fff;border:2px solid #FDBA74;border-radius:12px;padding:8px 10px;font-size:13px;line-height:1.6;color:#1F2937}' +
      '.s-demo7 .bg-bub ol{margin:4px 0;padding-left:20px}.s-demo7 .bg-bub small{color:#6B7280}' +
      '.s-demo7 .bg-who{color:#C2410C}.s-demo7 .bg-dish{color:#EA580C;font-size:15px}' +
      '.s-demo7 .bg-pts{position:absolute;right:8px;top:-10px;background:#EA580C;color:#fff;font:700 11px/18px var(--font);padding:0 7px;border-radius:9px;font-style:normal}' +
      '.s-demo7 .bg-want{display:flex;align-items:center;gap:8px}.s-demo7 .bg-want span{font-size:12px;color:#6B7280}' +
      '.s-demo7 .bg-plate{width:150px;flex:none;text-align:center}' +
      '.s-demo7 .bg-stack{display:flex;flex-direction:column-reverse;align-items:center;min-height:150px;justify-content:flex-start}' +
      '.s-demo7 .bg-dish-plate{height:12px;margin:2px auto 0;width:150px;border-radius:50%;background:#E5E7EB;box-shadow:0 3px 0 #CBD5E1}' +
      '.s-demo7 .bg-count{font-size:12px;color:#9A3412;margin-top:4px}' +
      '.s-demo7 .ly{display:block;width:118px;height:12px;border-radius:6px;margin-top:-1px}' +
      '.s-demo7 .ly.bun-b{height:16px;border-radius:3px 3px 14px 14px;background:#E7A13C;box-shadow:inset 0 -3px 0 #C9832A}' +
      '.s-demo7 .ly.bun-t{height:30px;border-radius:60px 60px 5px 5px;background:' +
      '  radial-gradient(ellipse 3px 2px at 30% 35%,#FFF6DD 90%,transparent),radial-gradient(ellipse 3px 2px at 52% 22%,#FFF6DD 90%,transparent),' +
      '  radial-gradient(ellipse 3px 2px at 70% 40%,#FFF6DD 90%,transparent),radial-gradient(ellipse 3px 2px at 42% 55%,#FFF6DD 90%,transparent),#F0AE45;' +
      '  box-shadow:inset 0 -4px 0 #D8922F}' +
      '.s-demo7 .ly.meat{width:124px;height:15px;background:#7A3E1C;box-shadow:inset 0 -3px 0 #5C2C12}' +
      '.s-demo7 .ly.cheese{width:130px;height:7px;border-radius:2px;background:#FFC928}' +
      '.s-demo7 .ly.veg{width:134px;height:8px;border-radius:8px;background:#58B947;box-shadow:inset 0 -2px 0 #3E9531}' +
      '.s-demo7 .ly.tom{width:116px;height:9px;border-radius:5px;background:#E2402F;box-shadow:inset 0 -2px 0 #B92E20}' +
      '.s-demo7 .ly.drop{animation:bgdrop .35s cubic-bezier(.3,1.4,.5,1)}' +
      '@keyframes bgdrop{from{transform:translateY(-36px);opacity:0}}' +
      '.s-demo7 .bg-mini{display:inline-flex;flex-direction:column-reverse;align-items:center;zoom:.42;vertical-align:middle}' +
      '.s-demo7 .bg-msg{margin:8px 0;padding:7px 10px;border-radius:8px;background:#fff;border:1px solid #FED7AA;font-size:13px;min-height:20px}' +
      '.s-demo7 .bg-msg.ok{background:#ECFDF5;border-color:#6EE7B7;color:#065F46;font-weight:700}' +
      '.s-demo7 .bg-msg.bad{background:#FEF2F2;border-color:#FCA5A5;color:#991B1B}' +
      '.s-demo7 .bg-msg.tip{background:#FFFBEB;border-color:#FCD34D;color:#7A4B00}' +
      '.s-demo7 .bg-keys{font-size:12.5px;line-height:2}' +
      '.s-demo7 .bg-chip{display:inline-block;margin:0 6px 0 0;padding:0 8px;border-radius:10px;background:#fff;border:1px solid #FDBA74;line-height:20px}' +
      '.s-demo7 .bg-miss{color:#B45309}.s-demo7 .bg-none{color:#9CA3AF}' +
      '.s-demo7 .bg-card{margin-top:8px;padding:10px 12px;border-radius:10px;background:#fff;border:1px solid #FED7AA;font-size:12.5px;line-height:1.6}' +
      '.s-demo7 .bg-card>b{display:block;margin:6px 0 2px;color:#9A3412}' +
      '.s-demo7 .bg-card table{border-collapse:collapse;width:100%}' +
      '.s-demo7 .bg-card td{padding:3px 6px;border-bottom:1px solid #FFEDD5;vertical-align:middle}' +
      '.s-demo7 .bg-card td:first-child{width:70px;text-align:center}' +
      '.s-demo7 .bg-card ol,.s-demo7 .bg-card ul,.s-demo7 .bg-goal ul{margin:0;padding-left:18px}' +
      // AI 页签的三级升级
      '.s-demo7 .bg-ladder{display:flex;flex-wrap:wrap;align-items:center;gap:4px;margin-bottom:8px}' +
      '.s-demo7 .bg-lv{background:#fff;color:#9A3412;border:1px solid #FDBA74;border-radius:14px;padding:2px 10px;font-size:12.5px}' +
      '.s-demo7 .bg-lv:hover{background:#FFEDD5}' +
      '.s-demo7 .bg-lv.done{background:#ECFDF5;border-color:#6EE7B7;color:#065F46}' +
      '.s-demo7 .bg-lv.cur{background:#EA580C;border-color:#EA580C;color:#fff;font-weight:700}' +
      '.s-demo7 .bg-arrow{color:#FDBA74;font-style:normal;font-size:12px}' +
      '.s-demo7 .bg-goal{padding:8px 10px;border-radius:8px;background:#fff;border:1px solid #FDBA74;font-size:13px;line-height:1.6;margin-bottom:8px}' +
      '.s-demo7 .bg-goal-prog{display:flex;align-items:center;gap:10px;margin-top:4px;color:#C2410C;font-weight:700}' +
      '.s-demo7 .bg-send{background:#EA580C;color:#fff;border:none;border-radius:6px;padding:3px 10px;font-size:12.5px}' +
      '.s-demo7 .bg-send:hover{background:#C2410C}' +
      '.s-demo7 .bg-stats{margin-bottom:4px}.s-demo7 .bg-stats th{font-size:12px;color:#9A3412;text-align:left;padding:3px 6px}' +
      '.s-demo7 .bg-stats td:first-child{width:auto;text-align:left}.s-demo7 .bg-stats tr.cur td{background:#FFF4E5;font-weight:700}',

    starter: function () {
      // 事件积木靠中断触发，主程序得一直活着；这一圈空转替学生写好、锁住。再给一块“A 键 → 面包”当样子
      return {
        blocks: {
          languageVersion: 0,
          blocks: [{
            type: "mpl_main", x: 24, y: 24, deletable: false,
            inputs: { DO: { block: lock({
              type: "mpl_forever",
              inputs: { DO: { block: lock({ type: "mpl_sleep", fields: { SEC: 0.1 } }) } }
            }) } }
          }, {
            type: "mpl_on_button", x: 24, y: 150, fields: { BTN: "a", EVT: "pressed" },
            inputs: { DO: { block: { type: "mpl_send", fields: { KIND: "bg_bun" } } } }
          }]
        }
      };
    },

    check: function (workspace) {
      var map = {}, order = [], dup = [], inLoop = false, loose = false;
      workspace.getAllBlocks(false).forEach(function (b) {
        if (b.type === "mpl_on_button" || b.type === "mpl_on_touch") {
          var key = (b.type === "mpl_on_button" ? b.getFieldValue("BTN").toUpperCase() + " 键" : "触摸 " + b.getFieldValue("PAD").toUpperCase()) +
            (b.getFieldValue("EVT") === "released" ? "（松开）" : "");
          var ks = b.getDescendants(false).filter(function (x) { return x.type === "mpl_send" && x.isEnabled(); })
            .map(function (x) { return x.getFieldValue("KIND"); });
          if (map[key]) dup.push(key); else order.push(key);
          map[key] = (map[key] || []).concat(ks);
        } else if (b.type === "mpl_send") {
          var root = b.getRootBlock().type;
          if (root === "mpl_main") inLoop = true;
          else if (root !== "mpl_on_button" && root !== "mpl_on_touch") loose = true;
        }
      });
      keymap = order.map(function (k) { return { key: k, kinds: map[k] }; });
      var el = document.querySelector(".s-demo7 .bg-keys");
      if (el) el.innerHTML = keysHtml();

      var have = {};
      keymap.forEach(function (k) { k.kinds.forEach(function (x) { have[x] = true; }); });
      function miss(list) { return list.filter(function (x) { return !have[x]; }).map(n).join("、"); }
      return [
        { label: "五种食材都有键能放", ok: !miss(ING), note: "还没接：" + miss(ING) },
        { label: "“上菜”和“撤掉一层”都有键", ok: !miss(["bg_serve", "bg_undo"]), note: "还没接：" + miss(["bg_serve", "bg_undo"]) },
        { label: "同一个键只写了一块事件积木", ok: !dup.length,
          note: dup.join("、") + " 写了两块——按一下两块都会跑，一次放两样。合并成一块，或者换个键" },
        { label: "“告诉网页”都放在“当…被按下时”里", ok: !inLoop && !loose,
          note: inLoop ? "有一句放在“一直重复”里：按住不放会一直往上叠。汉堡店要用“当按键…被按下时”"
                       : "有一句单独摆着，不会执行" }
      ];
    },

    // “🤖 AI 写代码”页签：给没学过代码的学生的做法——不拼积木、也不读代码，只用中文把要求写清楚，让 AI 写。
    // 学生只写“我的要求”那一段；代码、暗号、触摸键怎么写这些技术细节由页面替他包进去（wrap）。
    // 关卡走的是上面的三级升级（LADDER），场景换成升级版的店；切回积木页签又是原来那家店，两边各记各的分。
    ai: {
      task: function () {
        if (!L) L = ladderLoad();
        return "第 " + "①②③"[L.stage] + " 级 · " + LADDER[L.stage].name + "：" + LADDER[L.stage].goal.join("；");
      },
      // 学生的要求放最前面——那是他自己写的，AI 最该照着做的；后面是固定的技术交代。
      // 样子代码就是“看代码”里那份，开头注释里有暗号对照（blocks.js 生成），不另列。
      // 每一级只补这一级 AI 真用得上的那句，不然整段话越堆越长，学生看不下去
      wrap: function (req, code) {
        if (!L) L = ladderLoad();
        var extra = [
          "如果想做“先记下、再一次性放出来”的组装模式，要记住按过什么，可以用变量和列表；最简单的“一个键一样食材”不用这招。\n",
          "一个键要做好几层，就在它里面按顺序连着写几句 _mpl_send。\n",
          "网页会通过 USB 线给板子发一行字，比如 ORDER:芝士堡。在 while True 里这样读：\n" +
          "  先在开头写 import sys, select 和 _poll = select.poll(); _poll.register(sys.stdin, select.POLLIN)\n" +
          "  循环里 if _poll.poll(0): line = sys.stdin.readline().strip()\n"
        ][L.stage];
        return "我的要求：\n" + req.trim() + "\n\n" +
          "请照下面这段掌控板代码的样子（按 A 键告诉网页“面包”）补全，暗号看代码开头的注释。\n" + extra +
          "触摸键写法：touchpad_p、touchpad_y、touchpad_t、touchpad_h、touchpad_o、touchpad_n。\n" +
          "保留原来的代码，只回复完整代码。\n\n" + code;
      },
      // 拼接区：跟自定义题目「第3课 互联网+各行各业-句子积木」同一套做法——句子模板点一下加一行，
      // 词块点一下/拖一下填进下划线。没学过编程、没拼过积木的学生，不用自己想“这句话怎么说”，
      // 只管挑词填空、拼几行：①手动配菜是简单的一一对应，②一键出餐不用说做成什么汉堡——
      // “按哪个键、按顺序放哪几层”已经把要求说全了，汉堡叫什么名字对 AI 写代码没用；
      // 要做第二种汉堡，三行模板换个键再整套点一遍就行，不用查表、不用取名字。
      // ③全自动是条件触发（收到就自动）——和“越来越智能”阶梯的三级一一对应
      builder: function () {
        if (!L) L = ladderLoad();
        var keys = ["A键", "B键", "触摸键P", "触摸键Y", "触摸键T", "触摸键H", "触摸键O", "触摸键N"];
        var foods = ["面包", "肉饼", "芝士", "生菜", "番茄"];
        var dishes = MENU.map(function (m) { return m.name; });
        if (L.stage === 0) return {
          sentences: [{ t: "___，按一下就___。" }],
          words: [
            { label: "按键", items: keys },
            { label: "放什么 / 做什么", items: ["放面包", "放肉饼", "放芝士", "放生菜", "放番茄", "撤掉一层", "出餐"] }
          ]
        };
        // 不用说做成什么汉堡——“按哪个键、按顺序放哪几层”已经把要求说全了，汉堡叫什么名字
        // 对 AI 写代码没用。按___键 单独一行（点一次定下是哪个键），放___ 可以重复点好几次
        // （有几层点几次），最后 放完就出餐 收尾。要做第二种汉堡，三行再整套点一遍、换一个键就行
        if (L.stage === 1) return {
          sentences: [
            { t: "按___键：" },
            { t: "放___，" },
            { t: "放完就出餐。" }
          ],
          words: [
            { label: "按键", items: keys },
            { label: "食材", items: foods }
          ]
        };
        return {
          sentences: [
            { t: "网页会发一行字，格式是 ORDER:汉堡名，比如 ORDER:___。" },
            { t: "板子收到以后，自动查配方表、自动一层层放好、最后自动上菜，中途不用按任何键。" }
          ],
          words: [{ label: "汉堡", items: dishes }]
        };
      }
    },
    // app.js 切页签时调：AI 页签里是升级版的店，积木页签里是原来那家
    setAiMode: function (on) { aiMode = !!on; if (on && !L) L = ladderLoad(); },

    // AI 页签的检查清单：一条“AI 写的代码能用”（学生看不懂代码，只告诉他该跟 AI 说哪句话），
    // 加上前两级各一条——两级都过了就通关，第③级（全自动）要网页往板子发订单，没在真板子上试过，算加分不卡通关。
    // 代码只粗看几种 AI 最常犯的毛病；判每一级过没过看的是板子实际做了什么（见 onAi），不看代码。
    checkCode: function (src) {
      if (!L) L = ladderLoad();
      var fns = {}, cur = null, dup = [], inLoop = false, keys = {}, order = [];
      var sawWhile = false;
      function kindsIn(s) {
        var out = [], re = /['"](?:@@MPL:)?(bg_[a-z]+)['"]/g, m;
        while ((m = re.exec(s))) if (CMD.indexOf(m[1]) >= 0) out.push(m[1]);
        return out;
      }
      var BIND = /^(button_([ab])|touchpad_([pythonPYTHON]))\.event_(pressed|released)\s*=\s*(.+)$/;
      src.split(/\r?\n/).forEach(function (raw) {
        var line = raw.replace(/#.*$/, "");      // 行尾注释里的“面包”不算数
        if (!line.trim()) return;
        var d = /^def\s+(\w+)\s*\(/.exec(line);
        if (d) { cur = d[1]; fns[cur] = []; return; }
        if (!/^\s/.test(line)) cur = null;
        if (/^while\b/.test(line)) sawWhile = true;
        var b = BIND.exec(line.trim());
        if (b && !cur) {
          var key = (b[2] ? b[2].toUpperCase() + " 键" : "触摸 " + b[3].toUpperCase()) + (b[4] === "released" ? "（松开）" : "");
          var rhs = b[5].trim();
          if (keys[key]) dup.push(key); else order.push(key);
          keys[key] = /^lambda\b/.test(rhs) ? kindsIn(rhs) : { fn: rhs };
          return;
        }
        var ks = kindsIn(line);
        if (!ks.length) return;
        if (cur) fns[cur] = fns[cur].concat(ks);
        else if (sawWhile || /^\s/.test(line)) inLoop = true;
      });
      keymap = order.map(function (k) {
        var v = keys[k];
        return { key: k, kinds: Array.isArray(v) ? v : (fns[v.fn] || []) };
      });
      var el = document.querySelector(".s-demo7 .bg-keys");
      if (el) el.innerHTML = keysHtml();

      var usesSend = /_mpl_send\s*\(/.test(src), defSend = /^def\s+_mpl_send\s*\(/m.test(src);
      // 前两级（手动配菜、一键出餐）“告诉网页”写进 while 循环 = 按住就一直放；全自动那一级循环里
      // 放东西是正常的（就是在循环里自己做）
      var probs = [];
      if (!src) probs.push("还没粘 AI 写的代码");
      else {
        if ((usesSend && !defSend) || !sawWhile) probs.push("AI 删掉了原来代码里的一段，板子会出错。跟它说：“保留原来的代码，只在上面添加”");
        if (dup.length) probs.push(dup.join("、") + " 被安排了两次，后面的会把前面的顶掉。检查你的要求里是不是有键写重了");
        if (inLoop && L.stage <= 1) probs.push("AI 写成了“按住就一直放”。跟它说：“改成按一下键只放一次”");
      }
      var items = [{ label: "AI 写的代码能用", ok: !probs.length, note: probs.join("；") }];
      for (var i = 0; i < 2; i++) items.push({
        label: "第 " + "①②"[i] + " 级 · " + LADDER[i].name, ok: L.stage > i,
        note: L.stage === i ? "正在做，看上面的任务" : "还没到"
      });
      return items;
    },

    onEvent: function (type, scene) {
      if (aiMode) {
        if (!L) L = ladderLoad();
        // 换了电脑 / 刷新过：前两级做完这件事要重新报一次，本机才算通关
        if (!scene._d7aiPass && L.stage >= 2) { scene._d7aiPass = true; setTimeout(function () { if (window.MPL_REPORT) window.MPL_REPORT("bg_pass"); }, 0); }
        return onAi(type, scene);
      }
      var root = scene.querySelector(".bg-root");
      if (!scene._d7) {
        scene._d7 = true;
        S = load();
        root.addEventListener("click", function (e) {
          if (!e.target.classList.contains("bg-reset")) return;
          if (!confirm("分数清零、从第一位顾客重新开始？")) return;
          S = { score: 0, idx: 0, done: 0, stack: [], passed: S.passed };
          save(); render(root, "新的一天开张了！", "tip");
        });
        // 换了电脑 / 刷新过：攒够分这件事要重新报一次，本机才算通关
        if (S.passed) setTimeout(function () { if (window.MPL_REPORT) window.MPL_REPORT("bg_pass"); }, 0);
      }
      if (!type) { render(root); return ""; }
      if (type === "bg_pass") { render(root, "🎉 攒够 " + PASS + " 分，汉堡店通关！还可以继续接单。", "ok"); return ""; }

      var msg = "", cls = "", fresh = false, now = Date.now();
      if (ING.indexOf(type) >= 0) {
        if (S.stack.length >= MAX) { msg = "盘子堆不下了！先“撤掉一层”，或者“上菜”"; cls = "tip"; }
        else {
          S.stack.push(type); fresh = true;
          msg = "放上一层" + n(type);
          recent = recent.filter(function (t) { return now - t < 700; }).concat([now]);
          if (recent.length >= 3) { msg = "🤖 一键出餐！一下放了 " + recent.length + " 层"; cls = "ok"; }
          else if (lastIng[type] && now - lastIng[type] < 800) {
            msg = "一下子放了两片" + n(type) + "？如果是按住不放一直在叠，说明“告诉网页”放进了“一直重复”里——改用“当…被按下时”。多的可以“撤掉一层”。";
            cls = "tip";
          }
          lastIng[type] = now;
        }
      } else if (type === "bg_undo") {
        msg = S.stack.length ? "撤掉了最上面的" + n(S.stack.pop()) : "盘子已经是空的了";
      } else if (type === "bg_serve") {
        var o = orderAt(S.idx);
        if (!S.stack.length) { msg = "盘子是空的，先做汉堡再上菜"; cls = "tip"; }
        else {
          var bad = judge(o, S.stack);
          S.stack = [];
          if (!bad) {
            S.score += PTS[o.type]; S.done++; S.idx++;
            msg = "😋 " + o.guest.split(" ")[1] + "：好吃！+" + PTS[o.type] + " 分。下一位顾客来了。"; cls = "ok";
            if (!S.passed && S.score >= PASS) {
              S.passed = true;
              setTimeout(function () { if (window.MPL_REPORT) window.MPL_REPORT("bg_pass"); }, 0);
            }
          } else {
            S.score = Math.max(0, S.score - WRONG);
            msg = "😣 " + o.guest.split(" ")[1] + "：" + bad + "　−" + WRONG + " 分，汉堡退回来了，再做一个。"; cls = "bad";
          }
        }
      }
      save();
      render(root, msg, cls, fresh);
      return "";
    }
  });
})();
