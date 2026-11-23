// 刷固件：在浏览器里给掌控板刷 MicroPython 固件（esptool-js，Apache-2.0）。
// Mind+ 上传模式刷进去的是 Arduino 程序，板子里就没有 MicroPython 了，平台的图形化编程要先刷一次固件。
// 固件文件由老师选（掌控板官方 mPython 固件 .bin）；选过的固件存在这台电脑的浏览器里，下一块板子不用再选。
(function () {
  'use strict';
  var DB = 'mpblocks-firmware';

  function idb() {
    return new Promise(function (ok, fail) {
      var r = indexedDB.open(DB, 1);
      r.onupgradeneeded = function () { r.result.createObjectStore('fw'); };
      r.onsuccess = function () { ok(r.result); };
      r.onerror = function () { fail(r.error); };
    });
  }
  async function saveFw(name, buf) {
    try {
      var db = await idb();
      db.transaction('fw', 'readwrite').objectStore('fw').put({ name: name, data: buf, at: Date.now() }, 'last');
    } catch (e) { /* 浏览器不让存就算了 */ }
  }
  async function loadFw() {
    try {
      var db = await idb();
      return await new Promise(function (ok) {
        var r = db.transaction('fw').objectStore('fw').get('last');
        r.onsuccess = function () { ok(r.result || null); };
        r.onerror = function () { ok(null); };
      });
    } catch (e) { return null; }
  }
  function toBinaryString(buf) {
    var u8 = new Uint8Array(buf), out = '', CH = 0x8000;
    for (var i = 0; i < u8.length; i += CH) out += String.fromCharCode.apply(null, u8.subarray(i, i + CH));
    return out;
  }

  window.MPFlash = {
    open: async function (ui) {
      var last = await loadFw();
      var fw = last ? { name: last.name, data: last.data } : null;
      var html =
        '<p style="margin-top:0">Mind+ 上传模式刷过程序的掌控板，要先刷一次 MicroPython 固件，平台的图形化编程才能上传程序。一块板子刷一次就行。</p>' +
        '<div class="form-row"><label>固件文件</label><input id="fwFile" type="file" accept=".bin"></div>' +
        '<div class="hint" id="fwName">' + (fw ? '上次用的：' + fw.name + '（不选就用它）' : '选掌控板的 mPython 固件（.bin）') + '</div>' +
        '<div class="form-row"><label>写入地址</label><select id="fwAddr"><option value="0">0x0（整合固件，掌控板官方固件一般选这个）</option><option value="4096">0x1000（MicroPython 官网的 ESP32 固件）</option></select></div>' +
        '<div class="form-row"><label><input id="fwErase" type="checkbox" checked style="height:auto;flex:none"></label><span>刷之前先清空（会删掉板子里原来的程序和文件）</span></div>' +
        '<pre id="fwLog" style="height:150px;overflow:auto;background:#111;color:#ddd;padding:6px;font-size:12px;border-radius:4px;margin:0"></pre>' +
        '<div style="height:8px;background:#eee;border-radius:4px;margin-top:8px"><div id="fwBar" style="height:8px;width:0;background:#4cbf56;border-radius:4px"></div></div>';
      var running = false;
      ui.dialog('刷固件', html, [{ text: '关闭', onClick: function () { return !running; } }, {
        text: '开始刷', ok: true, onClick: function () { if (!running) start(); return false; }
      }]);
      var log = function (t) { var el = document.getElementById('fwLog'); if (el) { el.textContent += t + '\n'; el.scrollTop = el.scrollHeight; } };
      document.getElementById('fwFile').onchange = async function (e) {
        var f = e.target.files[0];
        if (!f) return;
        fw = { name: f.name, data: await f.arrayBuffer() };
        document.getElementById('fwName').textContent = '要刷的：' + f.name + '（' + Math.round(f.size / 1024) + ' KB）';
      };

      async function start() {
        if (!fw) { ui.toast('先选固件文件'); return; }
        if (!('serial' in navigator)) { ui.toast('这个浏览器不能连接掌控板，用 Chrome 或 Edge'); return; }
        running = true;
        var transport = null;
        try {
          var mod = await import('../vendor/esptool-js/bundle.js');
          // 先放开平台自己占着的串口
          var port = ui.serial && ui.serial.port;
          if (ui.serial && ui.serial.connected()) await ui.serial.disconnect();
          if (!port) port = await navigator.serial.requestPort();
          transport = new mod.Transport(port, true);
          var loader = new mod.ESPLoader({
            transport: transport, baudrate: 460800, romBaudrate: 115200,
            terminal: { clean: function () {}, writeLine: log, write: function (t) { log(t); } }
          });
          log('连接掌控板……（连不上时按住板子背面的 BOOT 键再点“开始刷”）');
          var chip = await loader.main();
          log('芯片：' + chip);
          if (document.getElementById('fwErase').checked) { log('清空中……'); await loader.eraseFlash(); }
          await saveFw(fw.name, fw.data);
          log('写入 ' + fw.name + ' ……');
          await loader.writeFlash({
            fileArray: [{ data: toBinaryString(fw.data), address: Number(document.getElementById('fwAddr').value) }],
            flashSize: 'keep', flashMode: 'keep', flashFreq: 'keep', eraseAll: false, compress: true,
            reportProgress: function (i, written, total) {
              var bar = document.getElementById('fwBar');
              if (bar) bar.style.width = Math.round(written / total * 100) + '%';
            }
          });
          log('写完了，重启掌控板');
          await loader.hardReset();
          log('✔ 刷好了。可以拔下来换下一块板子；回到编辑器点“连接设备”试一试。');
          ui.toast('固件刷好了');
        } catch (err) {
          log('✘ 失败：' + (err && err.message || err));
        } finally {
          try { if (transport) await transport.disconnect(); } catch (e) { /* 已断开 */ }
          running = false;
        }
      }
    }
  };
})();
