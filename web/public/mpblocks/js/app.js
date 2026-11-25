// 图形化编程页面：工作区、代码区、连接掌控板上传、打开 / 另存为 .mpcode、在课堂里自动保存和提交作品。
(function () {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var params = new URLSearchParams(location.search);
  var H = 'dev-DFRobot-handpyEsp32@0.0.6_';

  // ---------------- 小工具 ----------------
  var toastTimer = null;
  function toast(msg, ms) {
    var t = $('toast');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.hidden = true; }, ms || 2600);
  }
  function esc(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  // 嵌在课堂作品页里时，编辑器框可能比屏幕高（下半截在屏幕外）：弹框只放在框里屏幕看得见的那一段
  function fitModal() {
    var m = $('modal');
    var top = 0, h = window.innerHeight;
    try {
      var fe = window.frameElement;
      if (fe && window.parent !== window) {
        var r = fe.getBoundingClientRect();
        var ph = window.parent.innerHeight;
        top = Math.max(0, -r.top);
        var bottom = Math.min(window.innerHeight, ph - r.top);
        if (bottom - top >= 200) h = bottom - top; else top = 0;
      }
    } catch (e) { top = 0; h = window.innerHeight; }
    m.style.top = top + 'px';
    m.style.height = h + 'px';
    m.style.bottom = 'auto';
  }
  function dialog(title, bodyHtml, buttons, onClose) {
    $('dlgTitle').textContent = title;
    $('dlgBody').innerHTML = bodyHtml;
    var foot = $('dlgFoot');
    foot.innerHTML = '';
    (buttons || [{ text: '关闭' }]).forEach(function (b) {
      var el = document.createElement('button');
      el.textContent = b.text;
      if (b.ok) el.className = 'ok';
      el.onclick = function () { if (!b.onClick || b.onClick() !== false) close(); };
      foot.appendChild(el);
    });
    $('modal').hidden = false;
    fitModal();
    window.addEventListener('resize', fitModal);
    try { window.parent.addEventListener('scroll', fitModal, true); window.parent.addEventListener('resize', fitModal); } catch (e) { /* 不同源 */ }
    function close() {
      $('modal').hidden = true;
      window.removeEventListener('resize', fitModal);
      try { window.parent.removeEventListener('scroll', fitModal, true); window.parent.removeEventListener('resize', fitModal); } catch (e) { /* 不同源 */ }
      if (onClose) { var f = onClose; onClose = null; f(); }
    }
    $('dlgClose').onclick = close;
    return close;
  }

  // ---------------- 课堂里打开时的环境（服务器给：项目名、服务器地址、组名、配对号、保存接口）----------------
  var CTX = { lesson: params.get('lesson'), page: params.get('page'), mode: params.get('mode') || '' };
  var READONLY = params.get('readonly') === '1';

  // 和平台后端说话：同源，带登录会话和 CSRF 头
  function xsrf() { var m = document.cookie.match(/XSRF-TOKEN=([^;]+)/); return m ? decodeURIComponent(m[1]) : ''; }
  async function api(method, url, body) {
    var isForm = body instanceof FormData;
    var r = await fetch(url, {
      method: method, credentials: 'same-origin',
      headers: isForm ? { 'X-XSRF-TOKEN': xsrf() } : { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf() },
      body: isForm ? body : body === undefined ? undefined : JSON.stringify(body)
    });
    var j = await r.json().catch(function () { return {}; });
    if (!r.ok || (j.code !== undefined && j.code !== 0)) throw new Error(j.message || ('出错了（' + r.status + '）'));
    return j.data;
  }

  // 平台注入（课堂里）：{ load(), save(project, code), submit(blob, code) }
  var host = window.MP_HOST || null;
  if (!host && CTX.lesson && CTX.page && params.get('student')) {
    // 老师看学生自动保存的积木（只读）
    host = { load: function () { return api('GET', '/api/teacher/mpblocks/lessons/' + CTX.lesson + '/pages/' + CTX.page + '/students/' + params.get('student')); } };
    READONLY = true;
  } else if (!host && CTX.lesson && CTX.page) {
    var base = '/api/student/mpblocks/lessons/' + CTX.lesson + '/pages/' + CTX.page;
    host = {
      load: function () { return api('GET', base); },
      save: function (project, code) { return api('PUT', base, { project: project, code: code }); },
      submit: async function (blob, code) {
        var fd = new FormData();
        fd.append('file', new File([blob], (CTX.fileName || '作品') + '.mpcode', { type: 'application/zip' }));
        fd.append('title', CTX.fileName || '图形化编程作品');
        var r = await api('POST', '/api/student/homework/lessons/' + CTX.lesson + '/pages/' + CTX.page, fd);
        try { window.parent && window.parent.postMessage({ type: 'mpblocks-submitted' }, location.origin); } catch (e) { /* 不在框架里 */ }
        var sub = r && r.submission;
        var auto = sub && sub.autoScore != null ? '，自动评分 ' + sub.autoScore + (sub.autoMax ? ' / ' + sub.autoMax : '') : '';
        return { message: '作品已提交' + auto };
      }
    };
  } else if (!host && params.get('starter')) {
    // 老师编辑作品页的起始程序：自动保存到那一页，学生第一次打开那一页时可以从它开始
    var sbase = '/api/teacher/mpblocks/pages/' + encodeURIComponent(params.get('starter')) + '/starter';
    host = {
      load: function () { return api('GET', sbase); },
      save: function (project) { return api('PUT', sbase, { project: project }); },
      starterMode: true
    };
  } else if (!host && params.get('free')) {
    // 学生随时练习（不在课里）：每人一份，自动保存到平台，换电脑也能接着做
    host = {
      load: function () { return api('GET', '/api/student/mpblocks/free'); },
      save: function (project, code) { return api('PUT', '/api/student/mpblocks/free', { project: project, code: code }); }
    };
  } else if (!host && params.get('file')) {
    // 老师打开学生交的 .mpcode（只读）
    host = {
      load: async function () {
        var r = await fetch(params.get('file'), { credentials: 'same-origin' });
        if (!r.ok) throw new Error('下载作品失败（' + r.status + '）');
        var p = await window.MPProject.fromFile(await r.blob());
        return { project: p, context: { label: params.get('title') || '学生作品（只读）' }, canSubmit: false };
      }
    };
    READONLY = true;
  }

  // ---------------- 工作区 ----------------
  Blockly.ScratchMsgs.setLocale('zh-cn');
  var workspace = Blockly.inject('blocklyDiv', {
    // 只读（老师查看学生的程序）：没有工具箱、不能改，可以连接掌控板上传演示
    readOnly: READONLY,
    toolbox: READONLY ? null : window.MP_TOOLBOX_XML(),
    media: 'vendor/scratch-blocks/media/',
    zoom: { controls: true, wheel: true, startScale: 0.75 },
    grid: { spacing: 40, length: 2, colour: '#ddd' },
    colours: { workspace: '#F9F9F9', flyout: '#F9F9F9', toolbox: '#FFFFFF', toolboxSelected: '#E9EEF2', scrollbar: '#CECDCE', scrollbarHover: '#CECDCE', insertionMarker: '#000000', insertionMarkerOpacity: 0.2, fieldShadow: 'rgba(255, 255, 255, 0.3)', dragShadowOpacity: 0.6 },
    comments: true,
    collapse: false,
    sounds: false,
    trashcan: true
  });
  window.MP_WORKSPACE = workspace;

  // 拖积木到左边（分类栏 / 积木区）：和 Mind+ 一样盖一层半透明遮罩，中间一个垃圾桶，进到能删除的地方桶盖翘起来、积木变淡
  (function () {
    var inj = document.querySelector('#blocklyDiv .injectionDiv');
    if (!inj || READONLY) return;
    var wrap = document.createElement('div');
    wrap.className = 'mp-trash';
    wrap.innerHTML = '<svg class="lid" viewBox="0 0 40 9" width="40" height="9"><path d="M14 0h12a2 2 0 0 1 2 2v3h10a2 2 0 0 1 0 4H2a2 2 0 0 1 0-4h10V2a2 2 0 0 1 2-2z" fill="#e6e6e6"/></svg>' +
      '<svg class="body" viewBox="0 0 36 32" width="36" height="32"><path d="M2 0h32a2 2 0 0 1 2 2v24a6 6 0 0 1-6 6H6a6 6 0 0 1-6-6V2a2 2 0 0 1 2-2zm10 8a1 1 0 0 0-1 1v15a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1V9a1 1 0 0 0-1-1zm10 0a1 1 0 0 0-1 1v15a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1V9a1 1 0 0 0-1-1z" fill="#e6e6e6"/></svg>';
    inj.appendChild(wrap);
    function area() {
      var tb = inj.querySelector('.blocklyToolboxDiv'), fo = inj.querySelector('.blocklyFlyout');
      var r = inj.getBoundingClientRect(), right = r.left;
      [tb, fo].forEach(function (el) { if (el) { var b = el.getBoundingClientRect(); if (b.width) right = Math.max(right, b.right); } });
      return { left: r.left, top: r.top, width: right - r.left, height: r.height };
    }
    var shown = false;
    function show(on, open) {
      if (on && !shown) { var a = area(); wrap.style.width = a.width + 'px'; }
      shown = on;
      wrap.classList.toggle('show', on);
      wrap.classList.toggle('open', !!open);
    }
    var origDrag = Blockly.BlockDragger.prototype.dragBlock;
    Blockly.BlockDragger.prototype.dragBlock = function (e) {
      var r = origDrag.apply(this, arguments);
      if (this.workspace_ === workspace && e) {
        var a = area();
        var inside = e.clientX < a.left + a.width + 20 && e.clientY >= a.top && e.clientY <= a.top + a.height;
        var deletable = this.draggingBlock_ && this.draggingBlock_.isDeletable();
        show(inside && deletable, inside && this.wouldDeleteBlock_);
        var surf = this.workspace_.getBlockDragSurface && this.workspace_.getBlockDragSurface();
        if (surf && surf.getGroup) surf.getGroup().style.opacity = inside && deletable ? 0.7 : '';
      }
      return r;
    };
    var origEnd = Blockly.BlockDragger.prototype.endBlockDrag;
    Blockly.BlockDragger.prototype.endBlockDrag = function () {
      var r = origEnd.apply(this, arguments);
      show(false);
      var surf = workspace.getBlockDragSurface && workspace.getBlockDragSurface();
      if (surf && surf.getGroup) surf.getGroup().style.opacity = '';
      return r;
    };
  })();

  function newVar(type, label) {
    Blockly.prompt('新建' + label + '变量：', '', function (name) {
      name = (name || '').trim();
      if (!name) return;
      if (workspace.getVariable(name, type)) { toast('已经有叫“' + name + '”的变量了'); return; }
      workspace.createVariable(name, type);
      workspace.refreshToolboxSelection_();
    });
  }
  workspace.registerButtonCallback('MP_NEW_NUMBER', function () { newVar('', '数字类型'); });
  workspace.registerButtonCallback('MP_NEW_STRING', function () { newVar('string', '字符串类型'); });
  workspace.registerButtonCallback('MP_NEW_LIST', function () { newVar('list', '列表'); });

  // 自定义模块（函数）：名字 + 参数（数字或字符串 / 布尔值）
  Blockly.Procedures.externalProcedureDefCallback = function (mutation, cb) {
    var rows = [];
    var html = '<div class="form-row"><label>模块名称</label><input id="pName" placeholder="如：闪灯"></div>' +
      '<div id="pArgs"></div>' +
      '<div class="chips"><button id="pAddN">＋ 数字或字符串参数</button><button id="pAddB">＋ 布尔值参数</button></div>' +
      '<div class="hint">参数在模块里用“定义”积木上的参数小积木拿到。</div>';
    dialog('自定义模块', html, [{ text: '取消', onClick: function () { cb(null); } }, {
      text: '完成', ok: true, onClick: function () {
        var name = $('pName').value.trim();
        if (!name) { toast('先写模块名称'); return false; }
        var proccode = name, ids = [], names = [], defs = [];
        rows.forEach(function (r) {
          var n = r.input.value.trim() || ('参数' + (ids.length + 1));
          proccode += r.bool ? ' %b' : ' %s';
          ids.push(Blockly.utils.genUid());
          names.push(n);
          defs.push(r.bool ? 'false' : '');
        });
        var m = document.createElement('xml').ownerDocument.createElement('mutation');
        m.setAttribute('proccode', proccode);
        m.setAttribute('argumentids', JSON.stringify(ids));
        m.setAttribute('argumentnames', JSON.stringify(names));
        m.setAttribute('argumentdefaults', JSON.stringify(defs));
        m.setAttribute('warp', 'false');
        cb(m);
      }
    }]);
    function addRow(bool) {
      var div = document.createElement('div');
      div.className = 'form-row';
      div.innerHTML = '<label>' + (bool ? '布尔值参数' : '数字/字符串') + '</label><input placeholder="参数名">';
      $('pArgs').appendChild(div);
      rows.push({ bool: bool, input: div.querySelector('input') });
    }
    $('pAddN').onclick = function () { addRow(false); };
    $('pAddB').onclick = function () { addRow(true); };
  };

  // ---------------- MQTT 初始化参数（Mind+ 一样的 JSON）----------------
  window.MPApp = {};
  window.MPApp.editMqtt = function (json, done) {
    var s = {};
    try { s = JSON.parse(json || '{}'); } catch (e) { s = {}; }
    var p = s.params || {};
    var defServer = (CTX.server || location.hostname || '');
    var proj = CTX.mqttProject || '';
    var html =
      '<div class="form-row"><label>物联网平台</label><select id="mPlat"><option value="siot">SIoT（教师机 / 平台内置服务器）</option></select></div>' +
      '<div class="form-row"><label>服务器地址</label><input id="mWww" value="' + esc(p.siotwww || defServer) + '"></div>' +
      '<div class="form-row"><label>账号</label><input id="mId" value="' + esc(p.siotid || 'siot') + '"></div>' +
      '<div class="form-row"><label>密码</label><input id="mPwd" value="' + esc(p.siotpwd || 'dfrobot') + '"></div>' +
      (function () {
        var row = function (i) {
          var key = i === 0 ? 'topic' : 'topic_' + i;
          return '<div class="form-row"><label>Topic_' + i + '</label><input id="mT' + i + '" value="' + esc(p[key] || '') + '" placeholder="项目名/设备名"></div>';
        };
        // 一般只用 Topic_0：Topic_1~4 收起来，填过的话自动展开
        var more = [1, 2, 3, 4].some(function (i) { return p['topic_' + i]; });
        return row(0) + '<details class="more-topics"' + (more ? ' open' : '') + '><summary>更多主题（Topic_1~Topic_4）</summary>' +
          [1, 2, 3, 4].map(row).join('') + '</details>';
      })() +
      (proj ? '<div class="hint">本节课的 MQTT 项目：<b>' + esc(proj) + '</b>。主题要写成 “项目名/设备名”，如 ' + esc(proj) + '/' + esc(CTX.device || 'G01') + '</div>' +
        '<div class="chips">' + (CTX.topicSuggestions || []).map(function (t) { return '<button data-t="' + esc(t) + '">填入 ' + esc(t) + '</button>'; }).join('') + '</div>' : '');
    dialog('MQTT 初始化参数', html, [{ text: '取消' }, {
      text: '确定', ok: true, onClick: function () {
        var np = { siotwww: $('mWww').value.trim(), siotid: $('mId').value.trim(), siotpwd: $('mPwd').value, topic: $('mT0').value.trim() };
        for (var i = 1; i <= 4; i++) { var v = $('mT' + i).value.trim(); if (v) np['topic_' + i] = v; }
        done(JSON.stringify({ platform: 'siot', params: np }));
      }
    }]);
    document.querySelectorAll('.chips button[data-t]').forEach(function (b) {
      b.onclick = function () {
        var empty = [0, 1, 2, 3, 4].map(function (i) { return $('mT' + i); }).filter(function (el) { return !el.value; })[0] || $('mT0');
        empty.value = b.getAttribute('data-t');
      };
    });
  };

  // 屏幕文字预览：128x64 黑底白字
  window.MPApp.previewText = function (block) {
    if (!block) return;
    var gen = function (name) {
      var t = block.getInputTargetBlock(name);
      if (!t) return '';
      return t.getFieldValue('TEXT') != null ? t.getFieldValue('TEXT') : t.getFieldValue('NUM');
    };
    var text = gen('TEXT'), x = Number(gen('X')) || 0, y = Number(gen('Y')) || 0;
    dialog('屏幕预览（128×64）', '<canvas id="pv" class="oled" width="128" height="64" style="width:384px;height:192px"></canvas><div class="hint" style="margin-left:0;text-align:center">实际字体以掌控板为准</div>');
    var c = $('pv').getContext('2d');
    c.fillStyle = '#000'; c.fillRect(0, 0, 128, 64);
    c.fillStyle = '#fff'; c.font = '12px "Microsoft YaHei", sans-serif'; c.textBaseline = 'top';
    c.fillText(text, x, y + 1);
  };

  // 屏幕显示图片：选一张图，转成黑白点阵（最大 128x64）
  window.MPApp.pickImage = function (value, done) {
    var info = {};
    try { info = JSON.parse(value || '{}') || {}; } catch (e) { info = {}; }
    var html = '<div class="form-row"><label>图片</label><input id="imgFile" type="file" accept="image/*"></div>' +
      '<div class="form-row"><label>宽 × 高</label><input id="imgW" type="number" min="1" max="128" value="' + (info.w || 50) + '"><input id="imgH" type="number" min="1" max="64" value="' + (info.h || 50) + '"></div>' +
      '<div class="form-row"><label>亮度阈值</label><input id="imgT" type="range" min="10" max="245" value="' + (info.t || 128) + '"></div>' +
      '<canvas id="imgPv" class="oled" width="128" height="64" style="width:384px;height:192px"></canvas>';
    var img = new Image();
    if (info.imgSrc) img.src = info.imgSrc;
    var result = info;
    function render() {
      if (!img.src) return;
      var w = Math.max(1, Math.min(128, Number($('imgW').value) || 50)), h = Math.max(1, Math.min(64, Number($('imgH').value) || 50));
      var th = Number($('imgT').value);
      var cv = document.createElement('canvas'); cv.width = w; cv.height = h;
      var cx = cv.getContext('2d'); cx.fillStyle = '#fff'; cx.fillRect(0, 0, w, h); cx.drawImage(img, 0, 0, w, h);
      var px = cx.getImageData(0, 0, w, h).data;
      var rowBytes = Math.ceil(w / 8), bytes = new Uint8Array(rowBytes * h);
      var pv = $('imgPv').getContext('2d'); pv.fillStyle = '#000'; pv.fillRect(0, 0, 128, 64); pv.fillStyle = '#fff';
      for (var yy = 0; yy < h; yy++) for (var xx = 0; xx < w; xx++) {
        var i = (yy * w + xx) * 4;
        var lum = 0.299 * px[i] + 0.587 * px[i + 1] + 0.114 * px[i + 2];
        if (lum < th) { bytes[yy * rowBytes + (xx >> 3)] |= 0x80 >> (xx & 7); pv.fillRect(xx, yy, 1, 1); }
      }
      var hex = Array.prototype.map.call(bytes, function (b) { return ('0' + b.toString(16)).slice(-2); }).join('');
      result = { imgSrc: img.src, w: w, h: h, t: th, bits: hex };
    }
    dialog('屏幕显示图片', html, [{ text: '取消' }, { text: '确定', ok: true, onClick: function () { if (result && result.bits) done(JSON.stringify(result)); } }]);
    img.onload = render;
    $('imgFile').onchange = function (e) {
      var f = e.target.files[0];
      if (!f) return;
      var rd = new FileReader();
      rd.onload = function () { img.src = rd.result; };
      rd.readAsDataURL(f);
    };
    ['imgW', 'imgH', 'imgT'].forEach(function (id) { $(id).oninput = render; });
  };

  // ---------------- 默认程序：ESP32 主程序 + 循环执行（Mind+ 新建项目就是这样）----------------
  function defaultProgram() {
    workspace.clear();
    var xml = '<xml><block type="' + H + 'start" x="80" y="80"><next><block type="control_forever"></block></next></block></xml>';
    Blockly.Xml.domToWorkspace(Blockly.Xml.textToDom(xml), workspace);
  }

  // ---------------- 代码区 ----------------
  var lastCode = '';
  var dirty = false;
  var HOLD = { on: false, releaseAt: 0 };
  var lastCpp = '';
  // lastCode 始终是 MicroPython（存进作品里）；Mind+ 方式时代码区显示 C++（和 Mind+ 上传模式生成的一样）
  function refreshCode() {
    var r = window.MPCodegen.generate(workspace);
    lastCode = r.code;
    var shown = r.code, warnings = r.warnings;
    if (upMode() === 'mindplus' && window.MPCppGen && window.MPCppGen.ready()) {
      var c = window.MPCppGen.generate(workspace);
      lastCpp = c.code;
      shown = c.code;
      warnings = c.warnings;
    }
    $('code').textContent = shown;
    var w = $('warn');
    w.hidden = !warnings.length;
    w.textContent = warnings.join('；');
  }
  var codeTimer = null;
  workspace.addChangeListener(function (e) {
    if (e.type === Blockly.Events.UI) return;
    clearTimeout(codeTimer);
    codeTimer = setTimeout(refreshCode, 200);
    // 第一次打开、还没在“从哪里开始？”里选：不保存（不然空白程序先存进这一页，起始程序就拿不到了）；
    // 学生关掉选择框没选时，等他自己动了积木才开始保存
    if (HOLD.on) {
      if (!HOLD.releaseAt || Date.now() < HOLD.releaseAt) return;
      HOLD.on = false;
    }
    if (!loading) { dirty = true; scheduleSave(); }
  });
  $('btnCode').onclick = function () {
    var p = $('codePane');
    p.classList.toggle('hidden');
    this.classList.toggle('on', !p.classList.contains('hidden'));
    Blockly.svgResize(workspace);
  };
  $('btnSerial').onclick = function () {
    var p = $('serialPane');
    p.hidden = !p.hidden;
    this.classList.toggle('on', !p.hidden);
    Blockly.svgResize(workspace);
  };
  $('btnCopy').onclick = function () {
    navigator.clipboard && navigator.clipboard.writeText($('code').textContent).then(function () { toast('代码已复制'); }, function () { toast('复制不了，手动选中复制'); });
  };
  window.addEventListener('resize', function () { Blockly.svgResize(workspace); });

  // ---------------- 打开 / 另存为 / 新建 ----------------
  var loading = false;
  function loadProjectJson(p) {
    loading = true;
    try {
      window.MPProject.load(workspace, p);
      if (!workspace.getTopBlocks().length) defaultProgram();
    } finally {
      loading = false;
    }
    refreshCode();
  }
  $('btnNew').onclick = function () {
    dialog('新建项目', '清空现在的积木，从“ESP32 主程序”重新开始？', [{ text: '取消' }, {
      text: '新建', ok: true, onClick: function () { defaultProgram(); refreshCode(); dirty = true; scheduleSave(); }
    }]);
  };
  function pickFile() { $('fileInput').value = ''; $('fileInput').click(); }

  // ---- 从老师给的起始程序 / 自己以前的程序开始 ----
  var STARTER = null;          // 课堂里：这一页老师给的起始程序（有才有）
  function studentHost() { return !!host && !READONLY && !host.starterMode && !!(params.get('free') || (CTX.lesson && CTX.page)); }
  function historyAllowed() { return studentHost() && (!!params.get('free') || CTX.allowHistory !== false); }
  function fmtTime(t) {
    var d = new Date(t);
    if (isNaN(d)) return '';
    var p2 = function (n) { return (n < 10 ? '0' : '') + n; };
    return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
  }
  function historyLabel(h) {
    if (h.kind === 'free') return '“图形化编程”随时练习';
    if (h.kind === 'file') return h.courseName + ' · ' + h.pageTitle + ' · 交过的作品 ' + h.fileName;
    return h.courseName + ' · ' + h.pageTitle;
  }
  function loadHistory() {
    var q = CTX.lesson && CTX.page && !params.get('free') ? '?lessonId=' + CTX.lesson + '&pageId=' + CTX.page : '';
    return api('GET', '/api/student/mpblocks/history' + q);
  }
  async function fetchHistory(h) {
    if (h.kind === 'work') return (await api('GET', '/api/student/mpblocks/history/work/' + h.lessonId + '/' + h.pageId)).project;
    if (h.kind === 'free') return (await api('GET', '/api/student/mpblocks/free')).project;
    var r = await fetch('/api/homework/submissions/' + h.submissionId + '/file', { credentials: 'same-origin' });
    if (!r.ok) throw new Error('下载作品失败（' + r.status + '）');
    return window.MPProject.fromFile(await r.blob());
  }
  function useProject(p, msg) {
    HOLD.on = false;
    if (p) loadProjectJson(p); else { defaultProgram(); refreshCode(); }
    dirty = true; scheduleSave();
    if (msg) toast(msg);
  }
  // 选项：老师给的起始程序 / 我以前的程序（列表）/ 电脑里的文件 / 空白
  function chooseStart(title, intro, opts, history, onClose) {
    var html = '<p class="muted">' + esc(intro) + '</p><div class="start-opts">';
    if (opts.starter) html += '<label class="opt"><input type="radio" name="st" value="starter" checked> <b>老师给的起始程序</b></label>';
    if (history && history.length) {
      html += '<div class="opt"><b>我以前的程序</b><div class="hist">';
      history.forEach(function (h, i) {
        html += '<label><input type="radio" name="st" value="h' + i + '"' + (!opts.starter && i === 0 ? ' checked' : '') + '> '
          + esc(historyLabel(h)) + ' <span class="muted">' + esc(fmtTime(h.time)) + '</span></label>';
      });
      html += '</div></div>';
    } else if (opts.historyEmpty) {
      html += '<div class="opt muted">我以前的程序：还没有</div>';
    }
    if (opts.file) html += '<label class="opt"><input type="radio" name="st" value="file"' + (!opts.starter && !(history && history.length) ? ' checked' : '') + '> <b>电脑里的文件</b>（.mpcode，Mind+ 做的也行）</label>';
    if (opts.blank) html += '<label class="opt"><input type="radio" name="st" value="blank"' + (!opts.starter && !(history && history.length) ? ' checked' : '') + '> <b>空白</b>（只有“ESP32 主程序”）</label>';
    html += '</div>';
    dialog(title, html, [{ text: '取消' }, {
      text: '确定', ok: true, onClick: function () {
        var el = document.querySelector('#dlgBody input[name=st]:checked');
        if (!el) { toast('先选一项'); return false; }
        var v = el.value;
        if (v === 'file') { pickFile(); return; }
        if (v === 'blank') { useProject(null, '已换成空白程序'); return; }
        if (v === 'starter') { useProject(JSON.parse(JSON.stringify(STARTER)), '已载入老师给的起始程序'); return; }
        var h = history[Number(v.slice(1))];
        fetchHistory(h).then(function (p) {
          if (!p) throw new Error('这份程序是空的');
          useProject(p, '已载入：' + historyLabel(h));
        }).catch(function (err) { toast('打不开：' + (err.message || err), 5000); });
      }
    }], onClose);
  }
  // 第一次打开这一页（本节课还没存过）：选从哪里开始；没有可选的就直接空白
  async function firstOpenChooser() {
    var history = [];
    if (historyAllowed()) { try { history = await loadHistory(); } catch (e) { history = []; } }
    if (!STARTER && !history.length) { defaultProgram(); refreshCode(); return; }
    HOLD.on = true; HOLD.releaseAt = 0;
    defaultProgram(); refreshCode();
    chooseStart('从哪里开始？', '这一页还没有你的程序，选一个开始（之后自动保存在这一页）：', { starter: !!STARTER, blank: true }, history,
      function () { if (HOLD.on) { HOLD.releaseAt = Date.now() + 800; dirty = false; } });
  }
  $('btnOpen').onclick = async function () {
    if (!studentHost() || (!STARTER && !historyAllowed())) { pickFile(); return; }
    var history = [];
    if (historyAllowed()) { try { history = await loadHistory(); } catch (e) { toast('读取以前的程序失败：' + (e.message || e), 4000); } }
    chooseStart('打开程序', '换成下面选的程序，现在的积木会被替换（要留着可以先点“另存为”存到电脑）：',
      { starter: !!STARTER, file: true, historyEmpty: historyAllowed() }, history);
  };
  $('fileInput').onchange = async function (e) {
    var f = e.target.files[0];
    if (!f) return;
    try {
      loadProjectJson(await window.MPProject.fromFile(f));
      HOLD.on = false;
      dirty = true; scheduleSave();
      toast('已打开 ' + f.name);
    } catch (err) {
      toast('打不开：' + (err.message || err), 5000);
    }
  };
  function download(blob, name) {
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name;
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
  }
  $('btnSaveAs').onclick = function () {
    refreshCode();
    download(window.MPProject.toMpcode(workspace, lastCode), (CTX.fileName || '掌控板程序') + '.mpcode');
  };

  // ---------------- 上传方式：Mind+ 方式（学生机上的掌控板助手本机编译）/ MicroPython 方式（浏览器串口）----------------
  // 默认 Mind+ 方式（老师定）；学生在下拉框里改过就记住
  var UP = { pref: 'mindplus', agent: null, busy: false, loaded: {} };
  try { UP.pref = localStorage.getItem('mpblocks.upMode2') || 'mindplus'; } catch (e) { /* 无痕模式 */ }
  function agentReady() { return !!(UP.agent && UP.agent.online && UP.agent.mindplus && window.MPCppGen && window.MPCppGen.ready()); }
  function upMode() {
    if (UP.pref === 'mpy' || UP.pref === 'mindplus') return UP.pref;
    return agentReady() ? 'mindplus' : 'mpy';
  }
  async function loadMindplusPrims(files) {
    for (var i = 0; i < files.length; i++) {
      var name = files[i];
      if (UP.loaded[name]) continue;
      var r = await fetch('/api/mpagent/mindplus/' + encodeURIComponent(name) + '.js', { credentials: 'same-origin' });
      if (!r.ok) continue;
      window.MPCppGen.loadPrims(name, await r.text());
      UP.loaded[name] = true;
    }
  }
  async function checkAgent() {
    var before = upMode();
    try {
      UP.agent = await api('GET', '/api/mpagent/client/status');
      if (UP.agent.files && UP.agent.files.length && window.MPCppGen && !window.MPCppGen.ready()) await loadMindplusPrims(UP.agent.files);
    } catch (e) {
      UP.agent = null;
    }
    applyMode();
    if (upMode() !== before) refreshCode();
  }
  function applyMode() {
    var m = upMode();
    $('upMode').value = UP.pref;
    var mp = m === 'mindplus';
    $('btnConnect').hidden = mp;
    $('btnFlash').hidden = mp;
    $('serialInput').hidden = mp;
    $('codeLang').textContent = mp ? 'C++（Mind+ 上传模式，只读）' : 'MicroPython（只读）';
    if (mp) {
      if (UP.busy) return;
      var s = $('devState'), a = UP.agent;
      if (!a || !a.online) { s.className = 'state off'; s.textContent = '没有打开掌控板助手'; }
      else if (!a.mindplus) { s.className = 'state off'; s.textContent = '助手没找到 Mind+'; }
      else if (!window.MPCppGen.ready()) { s.className = 'state off'; s.textContent = '缺 Mind+ 的掌控板文件'; }
      else { s.className = 'state ' + (a.port ? 'on' : 'off'); s.textContent = a.port ? '掌控板助手 · ' + a.port : '掌控板助手 · 没插板子'; }
      $('btnUpload').disabled = false;
    } else {
      setDev(serial ? serial.connected() : false);
    }
  }
  $('upMode').onchange = function () {
    UP.pref = this.value;
    try { localStorage.setItem('mpblocks.upMode2', UP.pref); } catch (e) { /* 无痕模式 */ }
    applyMode();
    refreshCode();
  };
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function appendOut(t) {
    outBuf = (outBuf + t).slice(-20000);
    var o = $('serialOut');
    o.textContent = outBuf;
    o.scrollTop = o.scrollHeight;
  }
  async function uploadViaAgent() {
    if (!UP.agent || !UP.agent.online) {
      dialog('没有打开掌控板助手', 'Mind+ 方式要在这台电脑上打开“掌控板助手”（交付包“学生机脚本”里的 ITtools掌控板助手.exe），它会用这台电脑上的 Mind+ 编译并写进掌控板。<div class="hint" style="margin-left:0">也可以把上传方式换成“MicroPython 方式”。</div>');
      return;
    }
    if (!window.MPCppGen.ready()) { dialog('还不能用 Mind+ 方式', '平台还没拿到 Mind+ 的掌控板积木文件。请确认这台电脑的 Mind+ 下载过掌控板和网络扩展，助手会自动交给平台。'); return; }
    refreshCode();
    UP.busy = true;
    var btn = $('btnUpload');
    btn.disabled = true;
    $('serialPane').hidden = false;
    $('btnSerial').classList.add('on');
    Blockly.svgResize(workspace);
    outBuf = '';
    appendOut('');
    var s = $('devState');
    try {
      // 网页占着串口时助手写不进去：先断开
      if (serial && serial.connected()) { try { await serial.disconnect(); } catch (e) { /* 不要紧 */ } }
      s.className = 'state on';
      s.textContent = '发给掌控板助手…';
      var job = await api('POST', '/api/mpagent/client/jobs', { code: lastCpp });
      var from = 0;
      for (;;) {
        await sleep(800);
        var j = await api('GET', '/api/mpagent/client/jobs/' + job.id + '?logFrom=' + from);
        if (j.log) appendOut(j.log);
        from = j.logEnd;
        s.textContent = j.message || j.status;
        s.title = s.textContent;
        if (j.status === 'DONE') { toast(j.message || '上传完成', 4000); break; }
        if (j.status === 'FAILED') { s.className = 'state off'; dialog('上传失败', esc(j.message || '') + '<div class="hint" style="margin-left:0">详细输出在下面的“掌控板输出”里。</div>'); break; }
      }
    } catch (err) {
      s.className = 'state off';
      dialog('上传失败', esc(err.message || err));
    } finally {
      UP.busy = false;
      btn.disabled = false;
      setTimeout(applyMode, 4000);
    }
  }

  // ---------------- 串口：连接、上传 ----------------
  var serial = window.MplSerial ? new window.MplSerial() : null;
  function setDev(on, text) {
    if (upMode() === 'mindplus' && !text) return;
    var s = $('devState');
    s.className = 'state ' + (on ? 'on' : 'off');
    s.textContent = text || (on ? '已连接掌控板' : '未连接设备');
    $('btnUpload').disabled = !on;
    $('btnConnect').textContent = on ? '断开' : '连接设备';
  }
  var outBuf = '';
  if (serial) {
    serial.onText = function (t) {
      outBuf = (outBuf + t).slice(-20000);
      var o = $('serialOut');
      o.textContent = outBuf;
      o.scrollTop = o.scrollHeight;
    };
    serial.onStatus = function () { setDev(serial.connected()); };
    serial.onReconnected = function () { setDev(true); };
  }
  $('btnConnect').onclick = async function () {
    if (!serial || !window.MplSerial.supported()) {
      dialog('连不上掌控板', '这个浏览器不能连接掌控板。请用 Chrome 或 Edge 打开；学生机要先运行一次交付包里的“配置学生机浏览器.bat”。');
      return;
    }
    try {
      if (serial.connected()) { await serial.disconnect(); setDev(false); return; }
      await serial.connect();
      setDev(true);
      var kind = serial.deviceKind ? serial.deviceKind() : '';
      if (kind === 'idf') dialog('板子里不是 MicroPython', '这块板子现在跑的不是 MicroPython（可能是 Mind+ 上传模式刷进去的程序）。点上面的“刷固件”给它刷一次 MicroPython 固件。');
    } catch (err) {
      setDev(false);
      if (err && err.name !== 'NotFoundError') toast('没连上：' + (err.message || err), 5000);
    }
  };
  $('btnUpload').onclick = async function () {
    if (upMode() === 'mindplus') { await uploadViaAgent(); return; }
    refreshCode();
    var btn = this;
    btn.disabled = true;
    $('serialPane').hidden = false;
    $('btnSerial').classList.add('on');
    Blockly.svgResize(workspace);
    try {
      await serial.saveMain(lastCode, function (step) { setDev(true, step); });
      setDev(true, '上传完成');
      toast('上传完成，程序已经在掌控板上运行');
    } catch (err) {
      setDev(serial.connected(), '上传失败');
      dialog('上传失败', esc(err.message || err) + '<div class="hint" style="margin-left:0">板子卡住时：按一下掌控板背面的复位键再上传；还不行就点“刷固件”。</div>');
    } finally {
      btn.disabled = !serial.connected();
    }
  };
  $('btnClear').onclick = function () { outBuf = ''; $('serialOut').textContent = ''; };
  $('serialInput').onkeydown = function (e) {
    if (e.key === 'Enter' && serial && serial.connected()) { serial._write(this.value + '\r'); this.value = ''; }
  };
  $('btnFlash').onclick = function () {
    if (window.MPFlash) window.MPFlash.open({ serial: serial, dialog: dialog, toast: toast });
    else dialog('刷固件', '刷固件功能还没有加载。');
  };

  // ---------------- 课堂：自动保存、提交作品 ----------------
  var saveTimer = null;
  function scheduleSave() {
    if (!host || !host.save || HOLD.on) return;
    clearTimeout(saveTimer);
    $('saveState').textContent = '未保存';
    saveTimer = setTimeout(saveNow, 3000);
  }
  async function saveNow() {
    if (!host || !host.save || !dirty || HOLD.on) return;
    refreshCode();
    dirty = false;
    try {
      $('saveState').textContent = '保存中…';
      await host.save(window.MPProject.toProject(workspace), lastCode);
      $('saveState').textContent = '已自动保存 ' + new Date().toTimeString().slice(0, 5);
    } catch (err) {
      dirty = true;
      $('saveState').textContent = '保存失败';
    }
  }
  window.addEventListener('beforeunload', function () { if (dirty) saveNow(); });
  $('btnSubmit').onclick = async function () {
    if (!host || !host.submit) return;
    refreshCode();
    try {
      $('btnSubmit').disabled = true;
      var r = await host.submit(window.MPProject.toMpcode(workspace, lastCode), lastCode);
      toast(r && r.message ? r.message : '作品已提交', 4000);
    } catch (err) {
      toast('提交失败：' + (err.message || err), 5000);
    } finally {
      $('btnSubmit').disabled = false;
    }
  };

  // ---------------- 启动 ----------------
  if (READONLY) { $('btnNew').hidden = true; $('btnOpen').hidden = true; }
  // 代码区默认收起来（老师定 2026-11-24），积木区宽一些；点“代码区”可以打开
  $('codePane').classList.add('hidden');
  $('btnCode').classList.remove('on');
  setTimeout(function () { Blockly.svgResize(workspace); }, 0);
  checkAgent();
  setInterval(checkAgent, 15000);
  async function boot() {
    if (host && host.load) {
      try {
        var r = await host.load();
        Object.assign(CTX, r.context || {});
        if (CTX.label) $('ctxInfo').textContent = CTX.label;
        if (host.submit && r.canSubmit !== false) $('btnSubmit').hidden = false;
        STARTER = r.starter || null;
        if (r.project) { loadProjectJson(r.project); return; }
        if (r.fresh && studentHost()) { await firstOpenChooser(); return; }
      } catch (err) {
        toast('读取已保存的程序失败：' + (err.message || err), 5000);
      }
    }
    defaultProgram();
    refreshCode();
  }
  window.MPApp.workspace = workspace;
  window.MPApp.refreshCode = refreshCode;
  window.MPApp.loadProject = loadProjectJson;
  window.MPApp.getCode = function () { refreshCode(); return lastCode; };
  window.MPApp.toast = toast;
  boot();
})();
