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
    this._loop = this._readLoop();
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
        // 超时时不能 abort 写通道：实测 abort 会一直挂着不返回，之后 port.close() 也跟着永远关不上，
        // 自动修复里“关了串口重开”那一步就白做了。不 abort 的话，板子进一次下载模式把缓冲读空，
        // 挂着的那几包自己就发出去了，close 照常能关（recover 里的说明）
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
  // 板子刚复位、还在开机：这几秒里别往里发东西（见 recover 里的说明），MPL_TO_BOARD 会先问一句
  MplSerial.prototype.booting = function () { return Date.now() < (this._bootUntil || 0); };

  MplSerial.prototype.hardReset = async function () {
    if (!this.port) throw new Error("还没连上掌控板。");
    this._bootUntil = Date.now() + 3500;
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

  // 刷入卡住后的自动修复，学生什么都不用点。由轻到重三步，哪一步之后板子回了 ">>>" 就停：
  //   ① 打断探：板子可能卡在粘贴模式里等结束符（上一次刷到一半被打断），Ctrl-C 就能出来；
  //   ② 关掉串口再用同一个设备重新打开（不弹选择框）：写通道在浏览器/驱动那一层堵死时，
  //      只有关了重开才能换一条干净的；
  //   ③ 硬复位，并在它开机那几秒里连发 Ctrl-C：板子里的 main.py 一开机就把板子拖死
  //      （比如死循环里狂打印、没有 sleep）的话，这样能在它跑起来之前把它掐住，回到 ">>>"；
  //      接下来刷进去的新程序会覆盖掉那个坏的 main.py——老师原来“重刷固件才好”的那种卡死，
  //      多数就是这一类。
  // 返回 true = 修好了，可以重新刷。
  MplSerial.prototype.recover = async function (onStep) {
    var step = onStep || function () {};
    var self = this;
    // 原来排着的写入不管了：它们各自会超时退出，新的写入不再排在它们后面
    this._queue = Promise.resolve();
    function quietWrite(t) { return self._write(t).catch(function () {}); }
    async function prompt(ms) {
      self._recent = "";
      await quietWrite("\r\x03\x03\r\n");
      return self._waitPrompt(ms);
    }

    // 复位后先只听不说：开机那一两秒里发进去的字没人收，会把刚清空的接收缓冲又堵上（真机实测）。
    // 等它开完机、打印出东西再停一会儿，才发 Ctrl-C 把它跑的程序停下来
    async function rebootAndCatch() {
      self._recent = "";
      try { await self.hardReset(); } catch (e) {}
      // 复位可能让 USB 断开重连（原生 USB 的板子），等它自己连回来
      for (var i = 0; i < 20 && !self.connected(); i++) await sleep(250);
      if (!self.connected()) return false;
      var t = Date.now() + 5000;
      while (Date.now() < t && !/boot:0x[1-9a-f]/.test(self._recent)) await sleep(100);
      if (!/boot:0x[1-9a-f]/.test(self._recent)) return false;   // 没开机（多半还停在下载模式）
      // "boot:0x8" 是芯片一复位就打的第一行，那时 MicroPython 还没起来；这时候发 Ctrl-C 一样会堵死。
      // 要等固件自己打出东西来（掌控板开机打 "mpython_v3"，之后是 main.py 打印的内容），
      // 才说明 MicroPython 已经在收数据了。开机那几行（"entry 0x…"、"boot..."）还是引导程序打的，不算
      t = Date.now() + 6000;
      while (Date.now() < t && !/mpython|MicroPython|@@MPL|>>>/.test(self._recent)) await sleep(100);
      await sleep(1000);
      var deadline = Date.now() + 4000;
      while (Date.now() < deadline) {
        await quietWrite("\r\x03\x03");
        await sleep(400);
        if (/>>> ?\s*$/.test(self._recent)) return true;
      }
      return prompt(1500);
    }

    step("叫醒板子");
    if (this.connected() && await prompt(1500)) return true;

    // 真机上实测到的卡死（2026-10-17，掌控板 3.0，USB 身份 303A:1001）：板子照常在跑程序、
    // 照常往电脑打印，但电脑发过去的每一个字节都写入超时——按复位重启也一样。
    // 原因：板子上的程序不读输入，网页发过去的字攒满了 MicroPython 的输入缓冲（两百来个字节），
    // 再来的字就堵在芯片内置 USB 串口的接收缓冲里没人取；普通复位只重启芯片、不复位这块 USB，
    // 那几个字节一直堵着，固件又只在“收到新数据”时才去读，于是谁也不动、永远卡着。
    // 老师“重刷固件就好了”正是因为刷固件要进下载模式，而下载模式的引导程序会把缓冲读空。
    // 这里照做一遍，用不着真的刷固件，固件和板子里的文件一个字节都不碰：
    //   送进下载模式（缓冲被读空）→ 关掉串口再打开（实测不关一次的话，复位信号在下载模式下
    //   怎么发都不起作用）→ 复位回正常模式 → 等它开完机再发 Ctrl-C。
    if (this.port && this.deviceKind() !== "bridge") {
      step("清空板子的接收缓冲");
      try { await this.enterDownload(); } catch (e) {}
      await sleep(600);
      await quietWrite("\r\n\r\n");
      for (var k = 0; k < 2; k++) {
        step(k ? "再重启一次板子" : "重启板子");
        await this._cycle();
        if (!this.port) return false;
        if (await rebootAndCatch()) return true;
      }
      return false;
    }

    // USB 转串口芯片（CH340 这类）的板子没有这种卡法，关了重开、复位一次就够
    step("重新打开串口");
    await this._cycle();
    if (this.connected() && await prompt(1500)) return true;
    if (!this.port) return false;
    step("重启板子");
    return rebootAndCatch();
  };

  // 进下载模式：照搬 esptool 给芯片内置 USB 串口/JTAG 用的那串信号（USBJTAGSerialReset）。
  // 四步的先后顺序、每一步里先动哪根线都不能改——这块 USB 是按“线的变化顺序”来判断要不要复位、
  // 复位后进哪个模式的，跟 hardReset 一样要拆成一根一根地设。
  MplSerial.prototype.enterDownload = async function () {
    if (!this.port) throw new Error("还没连上掌控板。");
    var port = this.port;
    function sig(s) {
      return Promise.race([port.setSignals(s), sleep(2000).then(function () { throw new Error("设置复位信号超时"); })]);
    }
    await sig({ requestToSend: false }); await sig({ dataTerminalReady: false }); await sleep(100);
    await sig({ dataTerminalReady: true }); await sig({ requestToSend: false }); await sleep(100);
    await sig({ requestToSend: true }); await sig({ dataTerminalReady: false }); await sig({ requestToSend: true });
    await sleep(100);
    await sig({ dataTerminalReady: false }); await sig({ requestToSend: false });
  };

  // 关掉串口再用同一个设备对象打开：授权还在，不弹选择框。
  // 关的过程中任何一步都可能因为通道堵着而挂住，所以每一步都设上限。
  MplSerial.prototype._cycle = async function () {
    var port = this.port;
    if (!port) return;
    this.port = null;   // 先摘掉：读循环看到 port 没了会自己退出
    try { if (this.reader) await Promise.race([this.reader.cancel(), sleep(1000)]); } catch (e) {}
    // cancel 返回时读循环还没来得及 releaseLock，这时 close 会因为“读通道还锁着”直接被拒——
    // 端口根本没关，后面的 open 又把“已经开着”当成成功，等于什么都没做（真机上就这么白跑过两轮）
    await Promise.race([this._loop || Promise.resolve(), sleep(1000)]);
    try { await Promise.race([port.close(), sleep(2000).then(function () { throw new Error("关闭超时"); })]); }
    catch (e) { this.onText("\n[重开串口时没关上：" + (e && e.message || e) + "]\n"); }
    await sleep(300);
    for (var i = 0; i < 3 && !this.port; i++) {
      try { await this._open(port); return; }
      catch (e) { this.port = null; await sleep(500); }
    }
    this.onStatus(this._wantConnected ? "waiting" : "disconnected");
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
