// ITtools 桥接：mpython-lite 原来访问教师机上的 server.py，在 ITtools 里同样的接口由平台后端提供
// （/api/{student|teacher}/mpy/...，按课堂 + 页面隔离，身份取登录会话）。
// 这里只改请求地址、补上 CSRF 头、把平台的 {code,message,data} 信封拆回 server.py 的裸 JSON，原有逻辑一行不动。
// 必须在其它脚本之前加载。网址参数：page=页面id [&lesson=课堂id] [&role=teacher] [&group=组]
(function () {
  "use strict";
  var q = new URLSearchParams(location.search);
  var page = q.get("page"), lesson = q.get("lesson");
  var role = q.get("role") === "teacher" ? "teacher" : "student";
  if (!page) return;   // 单独打开（离线试玩）时保持原样
  var base = "/api/" + role + "/mpy" + (lesson ? "/lessons/" + lesson : "") + "/pages/" + page;
  var ctx = window.MPY_CTX = { page: page, lesson: lesson, role: role, base: base, me: null };

  var ROUTES = { "/api/code": "/code", "/api/state": "/state", "/api/starter": "/starter", "/api/config": "/config",
                 "/api/settings": "/settings", "/event": "/event", "/stream": "/stream" };
  function rewrite(url) {
    if (typeof url !== "string") return url;
    var m = /^(\/api\/[a-z]+|\/event|\/stream)(\?.*)?$/.exec(url);
    return m && ROUTES[m[1]] ? base + ROUTES[m[1]] + (m[2] || "") : url;
  }
  function xsrf() {
    var m = /(?:^|;\s*)XSRF-TOKEN=([^;]*)/.exec(document.cookie);
    return m ? decodeURIComponent(m[1]) : null;
  }
  function jsonResponse(obj) {
    return new Response(JSON.stringify(obj == null ? {} : obj), { status: 200, headers: { "Content-Type": "application/json" } });
  }

  // ---------------- 本机存储按 课堂+页面 分开 ----------------
  // 原程序的积木 / 通关 / 关卡记录 key 只带组号；平台里不同课堂的“3 组”是不同的人，必须隔开
  var NS = "mpy:" + (lesson || "-") + ":" + page + ":";
  var SCOPED = /^mpl_(ws|pass|level|ai)_/;
  var sp = Storage.prototype, rawGet = sp.getItem, rawSet = sp.setItem, rawRemove = sp.removeItem;
  function nk(k) { return SCOPED.test(k) ? NS + k : k; }
  sp.getItem = function (k) { return rawGet.call(this, nk(k)); };
  sp.setItem = function (k, v) { return rawSet.call(this, nk(k), v); };
  sp.removeItem = function (k) { return rawRemove.call(this, nk(k)); };

  // 换了电脑 / 清了浏览器：用服务器上本组的进度把本机缺的补上（通关记录、上次停在哪关、这一关的积木）
  function seed(cfg) {
    var me = cfg && cfg.me, st = me && me.state;
    if (!me || !st) return;
    var g = me.key;
    try {
      var passKey = "mpl_pass_g" + g, local = JSON.parse(localStorage.getItem(passKey) || "[]");
      (st.passed || []).forEach(function (id) { if (local.indexOf(id) < 0) local.push(id); });
      localStorage.setItem(passKey, JSON.stringify(local));
      if (localStorage.getItem("mpl_level_g" + g) == null && st.levelNo) localStorage.setItem("mpl_level_g" + g, String(st.levelNo - 1));
      if (st.level && st.ws) {
        var wsKey = "mpl_ws_" + st.level + "_g" + g;
        if (localStorage.getItem(wsKey) == null) {
          var demo = (window.MPL_DEMOS || []).filter(function (d) { return d.id === st.level; })[0];
          var ver = (cfg.starterVersions || {})[st.level] || "builtin-" + ((demo && demo.starterVersion) || "1");
          localStorage.setItem(wsKey, JSON.stringify(st.ws));
          localStorage.setItem(wsKey + "_base", ver);
        }
      }
    } catch (e) {}
  }

  // 学生页右上角的组号：按组时“第 3 组”；按人或没分组时换成自己的名字，且不能点开换组
  function showMe(me) {
    var btn = document.getElementById("groupBtn");
    if (!btn) return;
    btn.disabled = true;
    btn.title = "";
    if (!me.label) return;
    // 原程序之后还会往 #groupNo 里写组号，留一个隐藏的给它写
    btn.innerHTML = "<span></span><b id=\"groupNo\" hidden></b>";
    btn.firstChild.textContent = me.label;
  }

  var rawFetch = window.fetch.bind(window);
  window.fetch = function (input, init) {
    var url = typeof input === "string" ? input : null;
    var nu = rewrite(url);
    if (!url || nu === url) return rawFetch(input, init);
    init = Object.assign({ credentials: "same-origin" }, init || {});
    var method = (init.method || "GET").toUpperCase();
    var headers = new Headers(init.headers || {});
    if (method !== "GET" && method !== "HEAD") { var t = xsrf(); if (t) headers.set("X-XSRF-TOKEN", t); }
    init.headers = headers;
    return rawFetch(nu, init).then(function (resp) {
      return resp.clone().json().then(function (b) {
        var out = b && typeof b === "object" && "code" in b && "message" in b
          ? (b.code === 0 ? b.data : { ok: false, error: b.message }) : b;
        // 原程序拿 board_host 判断“服务器已连接”；平台就是这个站点本身
        if (/\/config$/.test(nu.split("?")[0]) && out && !out.board_host) out.board_host = location.host;
        if (/\/config$/.test(nu.split("?")[0]) && out && out.me) {
          ctx.me = out.me;
          seed(out);
          showMe(out.me);
        }
        return jsonResponse(out);
      }, function () {
        return resp.status === 401 ? jsonResponse({ ok: false, error: "登录已失效，请刷新页面重新登录" }) : resp;
      });
    });
  };

  var RawES = window.EventSource;
  function BridgedES(url, opts) { return new RawES(rewrite(url), opts); }
  BridgedES.prototype = RawES.prototype;
  window.EventSource = BridgedES;

  // 教师看板里的“编辑初始积木”“在我的电脑上打开”：原来是 /index.html?...，补上页面、课堂和身份
  function carry(u) {
    if (typeof u !== "string" || !/^\/?index\.html/.test(u)) return u;
    var x = new URL(u.replace(/^\//, ""), location.href);
    x.searchParams.set("page", page);
    if (lesson) x.searchParams.set("lesson", lesson);
    x.searchParams.set("role", role);
    return x.pathname + x.search;
  }
  var rawOpen = window.open;
  window.open = function (u, n, f) { return rawOpen.call(window, carry(u), n, f); };
  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll('a[href^="/index.html"]').forEach(function (a) { a.setAttribute("href", carry(a.getAttribute("href"))); });
  });
})();
