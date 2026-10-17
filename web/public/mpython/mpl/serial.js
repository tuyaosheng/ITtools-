// 掌控板串口通讯（WebSerial）。协议照搬 BIPES（GPL-3.0，https://github.com/BIPES/BIPES，
// ui/core/channel.js + utils.js）已经在真机上跑了几年的做法，只是去掉了它和自家 UI 的耦合：
//   - 115200 波特率；
//   - 代码换行统一成 \r，切成 100 字符一包逐包写——ESP32 的 USB 串口接收缓冲小，
//     一次塞一大段会丢字，BIPES 也是因为这个才分包的；
//   - 运行 = 先 \r\x03\x03（两次 Ctrl-C 打断板子上正在跑的程序），
//     再 \x05（进入粘贴模式）+ 代码 + \x04（结束粘贴并执行）。
//     粘贴模式不会自动缩进，所以多行的 while/if 原样粘进去不会被搞乱缩进；
//   - 保存成 main.py = 在粘贴模式里执行一段 open/write/close，板子重启后自动跑这个文件。
(function () {
  "use strict";

  var PACKET = 100;
  var GAP_MS = 12;

  function MplSerial() {
    this.port = null;
    this.reader = null;
    this.onText = function () {};
    this.onStatus = function () {};
    this._encoder = new TextEncoder();
    // 所有写入排成一队：串口的写通道同一时间只能被一个 writer 占着，
    // 上一次“运行”还没写完又点“保存”，第二个 getWriter() 会直接抛 “WritableStream is locked”。
    this._queue = Promise.resolve();
    this._recent = "";  // 最近收到的一段文字，探测提示符用
    this.onReconnected = function () {};
    // 学生主动点了“断开”就别再自动连回去；只有意外断开（复位、松了线）才自动重连
    this._wantConnected = false;

    // 带原生 USB 的掌控板（ESP32-S3 这类）一按复位，USB 设备会先消失再重新出现。
    // 浏览器对授权过的设备会发 connect 事件，这时候直接 open 不需要用户点击、也不弹选择框——
    // 只有第一次选设备（requestPort）才必须学生亲手点。
    var self = this;
    if (MplSerial.supported()) {
      navigator.serial.addEventListener("connect", function (ev) {
        if (self._wantConnected && !self.port) self._reopen(ev.target);
      });
      navigator.serial.addEventListener("disconnect", function (ev) {
        if (ev.target === self.port) {
          self.port = null;
          self.onStatus(self._wantConnected ? "waiting" : "disconnected");
        }
      });
    }
  }

  var DEVICE_KEY = "mpl_serial_device";

  function remember(port) {
    try {
      var info = port.getInfo();
      localStorage.setItem(DEVICE_KEY, JSON.stringify({ v: info.usbVendorId, p: info.usbProductId }));
    } catch (e) {}
  }

  function isRemembered(port) {
    try {
      var saved = JSON.parse(localStorage.getItem(DEVICE_KEY) || "null");
      var info = port.getInfo();
      return !!saved && saved.v === info.usbVendorId && saved.p === info.usbProductId;
    } catch (e) { return false; }
  }

  MplSerial.supported = function () {
    return typeof navigator !== "undefined" && "serial" in navigator;
  };

  MplSerial.prototype.connected = function () {
    return !!(this.port && this.port.writable);
  };

  // 页面打开时：浏览器里授权过的设备里，优先挑上次连的那一块（同一个 USB 厂商号+产品号），
  // 找到就直接连，不弹选择框。
  MplSerial.prototype.reconnectRemembered = async function () {
    if (!MplSerial.supported()) return false;
    var ports = await navigator.serial.getPorts();
    if (!ports.length) return false;
    var port = ports.filter(isRemembered)[0] || ports[0];
    this._wantConnected = true;
    await this._open(port);
    return true;
  };

  MplSerial.prototype.connect = async function () {
    if (!MplSerial.supported()) {
      throw new Error("这个浏览器不支持连接掌控板，请用电脑上的 Chrome 或 Edge 打开。");
    }
    var port = await navigator.serial.requestPort();
    this._wantConnected = true;
    await this._open(port);
    remember(port);
  };

  // 复位后 USB 刚重新出现的那一瞬间，系统驱动可能还没就绪，open 会失败；隔一会儿重试几次。
  MplSerial.prototype._reopen = async function (port) {
    for (var i = 0; i < 6 && this._wantConnected && !this.port; i++) {
      try {
        await new Promise(function (r) { setTimeout(r, 300 + i * 300); });
        await this._open(port);
        this.onReconnected();
        return;
      } catch (e) {
        this.port = null;
      }
    }
  };

  MplSerial.prototype._open = async function (port) {
    this.port = port;
    try {
      await port.open({ baudRate: 115200 });
    } catch (e) {
      // InvalidStateError：端口其实已经开着（比如上一次没关干净），直接接着用。
      if (!(e && e.name === "InvalidStateError")) { this.port = null; throw e; }
    }
    // 乐鑫芯片原生 USB（TinyUSB）的 MicroPython：只有 DTR 拉高它才往电脑发输出，
    // 而且一旦出现“上一刻 RTS 高 DTR 低 → 这一刻 DTR 高 RTS 低”就会跳进下载模式
    // （micropython shared/tinyusb/mp_usbd_cdc.c）。这里明确把两根线都拉高并保持，
    // 我们自己永远不单独放低 RTS，就不会拉出那个顺序。
    if (this.deviceKind() === "esp-usb") {
      try { await port.setSignals({ dataTerminalReady: true, requestToSend: true }); } catch (e) {}
    }
    // 从打开这一刻开始攒收到的内容：板子要是在打开时被送进了下载模式，
    // 那句 "waiting for download" 就是在这一瞬间打出来的，检测时不能把它清掉
    this._recent = "";
    this.onStatus("connected");
    this._readLoop();
  };

  // 设备身份，排查用：乐鑫原生 USB 跑 MicroPython 时一般是 303A:4001 一类；
  // 303A:1001 是芯片内置的 USB-串口/JTAG，板子掉进下载模式时也会变成这个身份。
  MplSerial.prototype.deviceInfo = function () {
    try {
      var i = this.port.getInfo();
      if (i.usbVendorId === undefined) return "";
      var hex = function (n) { return ("0000" + n.toString(16).toUpperCase()).slice(-4); };
      return hex(i.usbVendorId) + ":" + hex(i.usbProductId);
    } catch (e) { return ""; }
  };

  MplSerial.prototype.deviceKind = function () {
    var id = this.deviceInfo();
    if (!id) return "unknown";
    if (id.indexOf("303A:") !== 0) return "bridge";      // CH340、CP210x 这类 USB 转串口芯片
    return id === "303A:1001" ? "esp-jtag" : "esp-usb";
  };

  MplSerial.prototype._readLoop = async function () {
    var decoder = new TextDecoder();
    while (this.port && this.port.readable) {
      this.reader = this.port.readable.getReader();
      try {
        for (;;) {
          var r = await this.reader.read();
          if (r.done) break;
          var text = decoder.decode(r.value, { stream: true });
          this._recent = (this._recent + text).slice(-4000);
          this.onText(text);
        }
      } catch (e) {
        // 拔线或断开时 read() 会抛错，交给外层循环判断端口是否还在。
      } finally {
        try { this.reader.releaseLock(); } catch (e) {}
      }
      if (!this.port || !this.port.readable) break;
    }
  };

  // 每一包最多等 3 秒：板子那头不收数据时（比如复位过程中 USB 通道没恢复），
  // writer.write() 会一直挂着不返回——不设上限的话按钮点了毫无反应，
  // 后面排队的写入也全被拖死，学生和老师都看不出发生了什么。
  var WRITE_TIMEOUT_MS = 3000;
  function writeWithTimeout(writer, bytes) {
    return new Promise(function (resolve, reject) {
      var t = setTimeout(function () {
        var e = new Error("板子没有接收数据（写入超时）");
        e.code = "WRITE_TIMEOUT";
        reject(e);
      }, WRITE_TIMEOUT_MS);
      writer.write(bytes).then(function () { clearTimeout(t); resolve(); },
                               function (e) { clearTimeout(t); reject(e); });
    });
  }

  MplSerial.prototype._write = function (text) {
    var self = this;
    var job = this._queue.then(async function () {
      if (!self.connected()) throw new Error("还没连上掌控板。");
      var writer = self.port.writable.getWriter();
      try {
        for (var i = 0; i < text.length; i += PACKET) {
          await writeWithTimeout(writer, self._encoder.encode(text.slice(i, i + PACKET)));
          await new Promise(function (r) { setTimeout(r, GAP_MS); });
        }
      } finally {
        // 超时时那一包可能还挂在流里，放开锁让后面的写入至少能拿到 writer、自己超时报错，
        // 而不是在 getWriter() 上抛 “WritableStream is locked”
        try { writer.releaseLock(); } catch (e) {}
      }
    });
    // 一次写失败不能把后面排队的都拖死
    this._queue = job.catch(function () {});
    return job;
  };

  // 探测板子里是不是 MicroPython：打断一下当前程序再敲回车，正常的 MicroPython
  // 会回一个 ">>> " 提示符。没有提示符却在不停打印 "I (12345) 标签: ..." 这种行，
  // 说明跑的是 ESP-IDF 的 C 固件（比如小智 AI 语音固件），根本没有能执行 Python 的命令行，
  // 发什么代码过去都不会运行——这种情况必须先告诉老师刷固件，而不是让学生对着没反应的板子干着急。
  //
  // 两种探法：
  //   - 安静探（默认，连上板子时用）：先只听不说；板子要是已经在打印东西，说明它正跑着程序，
  //     绝不能发 Ctrl-C——学生刚“保存到板子”再按复位，main.py 正在打印光线值，
  //     一个 Ctrl-C 就把它掐了，看起来就像“打印不出来”。安静时只敲一个回车，
  //     空闲的 MicroPython 会回 ">>> "，正在跑程序的不受影响。
  //   - 打断探（点“刷入”时用）：反正马上要换程序，可以发 Ctrl-C 把它停下再看提示符。
  // 返回 python: true / false / null（null = 板子一声不吭，判断不了，等真点刷入时再打断探）。
  //
  // 判断“是不是 ESP-IDF 的 C 固件”要排除开机那几行：MicroPython 自己的二级引导程序每次开机
  // 也会打印 "I (24) boot: ..." 这种格式，只看格式会把刚重启的 MicroPython 误判成小智固件。
  // 只有出现开机标签以外的日志（小智固件的 SystemInfo、WifiBoard 这类会一直打）才算。
  var BOOT_TAGS = { boot: 1, "boot.esp32s3": 1, "boot.esp32": 1, esp_image: 1, cpu_start: 1, heap_init: 1,
    spi_flash: 1, app_start: 1, main_task: 1, sleep: 1, sleep_gpio: 1, esp_psram: 1, octal_psram: 1,
    quad_psram: 1, efuse_init: 1, qio_mode: 1, rtc_clk_init: 1, gpio: 1, phy_init: 1 };
  function looksIdf(text) {
    var re = /[IWE] \(\d+\) ([\w.-]+):/g, m;
    while ((m = re.exec(text))) if (!BOOT_TAGS[m[1]]) return true;
    return false;
  }
  function looksPython(text) { return /MicroPython|>>> |>>>\s*$/.test(text); }
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

  MplSerial.prototype._waitPrompt = async function (ms) {
    var deadline = Date.now() + ms;
    while (Date.now() < deadline) {
      if (/>>> /.test(this._recent) || />>>$/.test(this._recent.trimEnd())) return true;
      await sleep(100);
    }
    return false;
  };

  MplSerial.prototype.probe = async function (interrupt) {
    if (interrupt) {
      this._recent = "";
      await this._write("\r\x03\x03\r\n");
      if (await this._waitPrompt(2500)) return { python: true };
      return { python: looksIdf(this._recent) ? false : null, idf: looksIdf(this._recent),
               download: /waiting for download/.test(this._recent), sample: this._recent.slice(-400) };
    }
    await sleep(/waiting for download/.test(this._recent) ? 0 : 1500);
    var t = this._recent;
    if (/waiting for download/.test(t)) return { python: null, download: true };
    if (looksPython(t)) return { python: true, running: !/>>> ?\s*$/.test(t) };
    if (looksIdf(t)) return { python: false, idf: true, sample: t.slice(-400) };
    if (t.trim()) return { python: true, running: true };
    await this._write("\r\n");
    if (await this._waitPrompt(1500)) return { python: true };
    return { python: null };
  };

  // 硬复位到“正常运行”模式——esptool 的复位顺序：
  //   掌控板 3.0 走芯片内置的 USB 串口/JTAG（USB 身份 303A:1001），这条通道的 DTR/RTS
  //   在硬件上直接连着芯片的复位和启动脚：RTS 高 + DTR 低 = 按住复位；
  //   松开复位那一刻 DTR 若是高的，芯片就进下载模式（刷固件用、一声不吭）。
  //   浏览器打开串口时会把两根线都拉高，中间经过“RTS 高 DTR 低”，于是板子先被复位、
  //   松开时 DTR 恰好是高的——每次打开串口都可能把板子送进下载模式。
  //   这里：先确保 RTS 高、再放低 DTR（进入复位，且 DTR 已经是低的），停一下再放低 RTS（松开复位），
  //   松开时 DTR 是低的，芯片就正常启动、跑 main.py。
  //   USB 转串口芯片（CH340、CP210x）的自动复位电路是同一套逻辑，这个顺序同样适用。
  // Chromium 的 setSignals 一次调用里固定先改 DTR 再改 RTS，所以必须拆成三次调用来控制顺序。
  MplSerial.prototype.hardReset = async function () {
    if (!this.port) throw new Error("还没连上掌控板。");
    var port = this.port;
    function sig(s) {
      return Promise.race([port.setSignals(s), sleep(2000).then(function () { throw new Error("设置复位信号超时"); })]);
    }
    await sig({ requestToSend: true });
    await sig({ dataTerminalReady: false });
    await sleep(120);
    await sig({ requestToSend: false });
  };

  // 软重启：MicroPython 在 >>> 提示符下收到 Ctrl-D 会重启解释器并重新跑 main.py。
  // 不碰 USB、不碰 DTR/RTS，所以不会掉进下载模式——“保存到板子”之后用这个代替按复位键。
  MplSerial.prototype.softReboot = async function () {
    await this.stop();
    await sleep(200);
    await this._write("\x04");
  };

  function crLines(code) {
    return code.replace(/\r\n|\n/g, "\r");
  }

  MplSerial.prototype.stop = function () {
    return this._write("\r\x03\x03");
  };

  // JSON 字符串字面量同时也是合法的 Python 字符串字面量（\n、\"、\uXXXX 两边含义一致），
  // 用它包住整份代码，不用自己处理引号和中文转义。
  // onStep(名字)：每进入一步报一次，卡住时页面能说清卡在哪一步，日志里也能查到
  MplSerial.prototype.saveMain = async function (code, onStep) {
    var step = onStep || function () {};
    var payload = "f=open('main.py','w')\nf.write(" + JSON.stringify(code) + ")\nf.close()\nprint('[已保存为 main.py]')\n";
    step("停止板子上正在跑的程序");
    await this.stop();
    await new Promise(function (r) { setTimeout(r, 150); });
    this._recent = "";
    step("把程序写进 main.py");
    await this._write("\x05" + crLines(payload) + "\x04");
    // 等写完回到提示符，再软重启让新的 main.py 马上跑起来——学生不用再去按复位键，
    // 按复位键会让 USB 断开重连，正是最容易把板子送进下载模式的时候
    step("等板子确认写完");
    var ok = await this._waitPrompt(4000);
    // 粘贴模式会把粘进去的每一行原样回显（"=== print('[已保存为…" 这样），
    // 所以要认行首的 "[已保存为 main.py]"——那才是 print 真正执行后打出来的
    // 行首或紧跟在粘贴提示 "=== " 后面都算（不同固件在执行前换不换行不一致）；
    // 回显的那一行是 "=== print('[已保存…"，中间隔着 print(' ，不会被误认
    if (!ok || !/(^|\n|=== )\[已保存为 main\.py\]/.test(this._recent)) {
      var e = new Error("板子没有确认写入成功");
      e.code = "NO_CONFIRM";
      throw e;
    }
    step("重启板子运行新程序");
    await sleep(100);
    await this._write("\x04");
  };

  MplSerial.prototype.disconnect = async function () {
    this._wantConnected = false;
    if (!this.port) { this.onStatus("disconnected"); return; }
    var port = this.port;
    // 关之前先放 RTS、再放 DTR（分两次调用，Chromium 一次调用里固定先动 DTR）。
    // 系统自己关串口时往往先放 DTR，留下“RTS 高 DTR 低”，下次打开时先拉 DTR，
    // 正好凑成 MicroPython 跳下载模式的顺序；我们先放 RTS 就不会留下这个状态。
    // 这个顺序对 USB 转串口芯片的自动复位电路、对芯片内置 USB-JTAG 也都不会触发复位。
    try { await port.setSignals({ requestToSend: false }); } catch (e) {}
    try { await port.setSignals({ dataTerminalReady: false }); } catch (e) {}
    this.port = null;
    try { if (this.reader) await this.reader.cancel(); } catch (e) {}
    try { await port.close(); } catch (e) {}
    this.onStatus("disconnected");
  };

  window.MplSerial = MplSerial;
})();
