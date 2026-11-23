// 积木 → 掌控板 MicroPython。每个积木的意思照 Mind+ 2.0.7 上传模式生成的 Arduino 代码（docs/Mind+积木清单），
// 写法换成掌控板 mPython 固件的 MicroPython：from mpython import *（button_a、touchpad_p、light、sound、
// accelerometer、oled 或彩屏 gui、rgb、music、MPythonPin），联网用 network + umqtt.simple。
// 只在用到的时候才加辅助函数；屏幕函数在板子上自己判断是 2.x 的 OLED 还是 3.0 的彩屏。
(function () {
  'use strict';
  var IND = '    ';
  var H = 'dev-DFRobot-handpyEsp32@0.0.6_';
  var W = 'ext-DFRobot-WIFI@0.0.5_';
  var M = 'ext-DFRobot-MQTT@0.0.4_';

  function short(type) { return type.replace(/^(dev|ext)-DFRobot-[^_]+_/, ''); }
  function indent(code, n) {
    var pad = new Array((n || 1) + 1).join(IND);
    return code.split('\n').map(function (l) { return l ? pad + l : l; }).join('\n');
  }
  function pyStr(s) {
    s = String(s == null ? '' : s);
    return "'" + s.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n').replace(/\r/g, '\\r') + "'";
  }
  function isNumText(s) { return /^\s*-?(\d+(\.\d*)?|\.\d+)(e[-+]?\d+)?\s*$/i.test(String(s)); }
  function numLit(s) {
    s = String(s).trim();
    if (!isNumText(s)) return null;
    var n = Number(s);
    return String(n);
  }
  var PIN_NO = { P: 27, Y: 14, T: 12, H: 13, O: 15, N: 4 };
  var TOUCH_NAME = { '27': 'p', '14': 'y', '12': 't', '13': 'h', '15': 'o', '4': 'n' };
  function pinNo(v) {
    v = String(v);
    if (/^P\d+$/.test(v)) return Number(v.slice(1));
    if (TOUCH_NAME[v]) return { '27': 'P', '14': 'Y', '12': 'T', '13': 'H', '15': 'O', '4': 'N' }[v];
    return v;
  }

  // ---------------- 辅助函数（按需加入）----------------
  var HELPERS = {
    mpython: 'from mpython import *',
    time: 'import time',
    math: 'import math',
    random: 'import random',
    music: 'import music',
    machine: 'import machine',
    num:
      'def _num(v):\n' +
      '    # 像 Mind+ 一样：能当数字的文字按数字算\n' +
      '    if isinstance(v, (int, float)):\n' +
      '        return v\n' +
      '    try:\n' +
      '        f = float(v)\n' +
      '        return int(f) if f == int(f) and "." not in str(v) else f\n' +
      '    except Exception:\n' +
      '        return 0',
    cmp:
      'def _cmp(a, b):\n' +
      '    # 比较：两边都能当数字就按数字比，否则按文字比\n' +
      '    try:\n' +
      '        a2, b2 = float(a), float(b)\n' +
      '        return (a2 > b2) - (a2 < b2)\n' +
      '    except Exception:\n' +
      '        a2, b2 = str(a), str(b)\n' +
      '        return (a2 > b2) - (a2 < b2)',
    str:
      'def _str(v):\n' +
      '    # 显示数字时整数不带 .0\n' +
      '    if isinstance(v, float) and v == int(v) and abs(v) < 1e15:\n' +
      '        return str(int(v))\n' +
      '    return str(v)',
    map:
      'def _map(v, a, b, c, d):\n' +
      '    # Mind+ 的“映射”是整数映射（Arduino map）\n' +
      '    v, a, b, c, d = _num(v), _num(a), _num(b), _num(c), _num(d)\n' +
      '    if b == a:\n' +
      '        return c\n' +
      '    return int((v - a) * (d - c) / (b - a) + c)',
    substr:
      'def _substr(s, m1, a, m2, b):\n' +
      '    # “获取第 a 个字符到第 b 个字符”，第 / 倒数第，从 1 数起，包含两头\n' +
      '    s = _str(s)\n' +
      '    n = len(s)\n' +
      '    i = int(_num(a)) if m1 == "first" else n - int(_num(a)) + 1\n' +
      '    j = int(_num(b)) if m2 == "first" else n - int(_num(b)) + 1\n' +
      '    if i > j:\n' +
      '        i, j = j, i\n' +
      '    return s[max(0, i - 1):max(0, j)]',
    find:
      'def _find(sub, s, last):\n' +
      '    # 查找出现位置，从 1 数起，找不到是 0\n' +
      '    s, sub = _str(s), _str(sub)\n' +
      '    i = s.rfind(sub) if last else s.find(sub)\n' +
      '    return i + 1',
    listfind:
      'def _lfind(l, v):\n' +
      '    for i in range(len(l)):\n' +
      '        if _str(l[i]) == _str(v):\n' +
      '            return i + 1\n' +
      '    return 0',
    pins:
      '_pins = {}\n' +
      'def _pin(n, mode):\n' +
      '    k = (n, mode)\n' +
      '    if k not in _pins:\n' +
      '        _pins[k] = MPythonPin(n, mode)\n' +
      '    return _pins[k]',
    screen:
      '# 屏幕：掌控板 2.x 是 128x64 的 OLED（oled），3.0 是彩屏（lv_gui）\n' +
      '_lw = 1\n' +
      'try:\n' +
      '    oled\n' +
      '    _scr = "oled"\n' +
      'except NameError:\n' +
      '    _scr = "gui"\n' +
      '    from lv_gui import *\n' +
      '    _gui = GUI()\n' +
      'def _show():\n' +
      '    if _scr == "oled":\n' +
      '        oled.show()\n' +
      '    else:\n' +
      '        _gui.update()\n' +
      'def _disp_line(t, line):\n' +
      '    line = int(_num(line))\n' +
      '    if _scr == "oled":\n' +
      '        oled.fill_rect(0, (line - 1) * 16, 128, 16, 0)\n' +
      '        oled.DispChar(_str(t), 0, (line - 1) * 16, 1)\n' +
      '    else:\n' +
      '        _gui.draw_label(text=_str(t), row=line, color=0xffffff, wrap=False)\n' +
      '    _show()\n' +
      'def _clear_line(line):\n' +
      '    line = int(_num(line))\n' +
      '    if _scr == "oled":\n' +
      '        oled.fill_rect(0, (line - 1) * 16, 128, 16, 0)\n' +
      '    else:\n' +
      '        _gui.draw_label(text=" " * 30, row=line, color=0xffffff, wrap=False)\n' +
      '    _show()\n' +
      'def _disp_xy(t, x, y):\n' +
      '    if _scr == "oled":\n' +
      '        oled.DispChar(_str(t), int(_num(x)), int(_num(y)), 1)\n' +
      '    else:\n' +
      '        _gui.draw_label(text=_str(t), x=int(_num(x)) * 2, y=int(_num(y)) * 2, width=320, color=0xffffff, wrap=False)\n' +
      '    _show()\n' +
      'def _fill(c):\n' +
      '    if _scr == "oled":\n' +
      '        oled.fill(c)\n' +
      '    else:\n' +
      '        _gui.fill(type=0)\n' +
      '        _gui.set_bg_color(0xffffff if c else 0x000000)\n' +
      '    _show()\n' +
      'def _rotate(deg):\n' +
      '    try:\n' +
      '        oled.rotation(1 if deg == 180 else 0)\n' +
      '    except Exception:\n' +
      '        try:\n' +
      '            oled.rotate(1 if deg == 180 else 0)\n' +
      '        except Exception:\n' +
      '            pass\n' +
      '    _show()\n' +
      'def _point(x, y):\n' +
      '    if _scr == "oled":\n' +
      '        oled.pixel(int(_num(x)), int(_num(y)), 1)\n' +
      '    _show()\n' +
      'def _line(x1, y1, x2, y2):\n' +
      '    x1, y1, x2, y2 = int(_num(x1)), int(_num(y1)), int(_num(x2)), int(_num(y2))\n' +
      '    if _scr == "oled":\n' +
      '        for d in range(_lw):\n' +
      '            if abs(x2 - x1) >= abs(y2 - y1):\n' +
      '                oled.line(x1, y1 + d, x2, y2 + d, 1)\n' +
      '            else:\n' +
      '                oled.line(x1 + d, y1, x2 + d, y2, 1)\n' +
      '    else:\n' +
      '        _gui.draw_line(x1 * 2, y1 * 2, x2 * 2, y2 * 2, size=_lw, color=0xffffff, draw=True)\n' +
      '    _show()\n' +
      'def _circle(x, y, r, fill):\n' +
      '    x, y, r = int(_num(x)), int(_num(y)), int(_num(r))\n' +
      '    if _scr == "oled":\n' +
      '        if fill:\n' +
      '            oled.fill_circle(x, y, r, 1)\n' +
      '        else:\n' +
      '            for d in range(_lw):\n' +
      '                oled.circle(x, y, max(0, r - d), 1)\n' +
      '    else:\n' +
      '        _gui.draw_round(x * 2, y * 2, r * 2, _lw, 0xffffff, 0xffffff, 1 if fill else 0, draw=True)\n' +
      '    _show()\n' +
      'def _rect(x, y, w, h, fill):\n' +
      '    x, y, w, h = int(_num(x)), int(_num(y)), int(_num(w)), int(_num(h))\n' +
      '    if _scr == "oled":\n' +
      '        if fill:\n' +
      '            oled.fill_rect(x, y, w, h, 1)\n' +
      '        else:\n' +
      '            for d in range(_lw):\n' +
      '                oled.rect(x + d, y + d, max(0, w - 2 * d), max(0, h - 2 * d), 1)\n' +
      '    else:\n' +
      '        _gui.draw_rect(x * 2, y * 2, w * 2, h * 2, _lw, 0xffffff, 0xffffff, 0, 1 if fill else 0, 1)\n' +
      '    _show()\n' +
      'def _bitmap(x, y, data, w, h):\n' +
      '    if _scr == "oled":\n' +
      '        oled.Bitmap(int(_num(x)), int(_num(y)), data, w, h, 1)\n' +
      '    _show()',
    rgb:
      '_rgb_b = 9\n' +
      '_rgb_c = [0, 0, 0]\n' +
      'def _rgb_apply(i):\n' +
      '    c = _rgb_c[i]\n' +
      '    k = _rgb_b / 9\n' +
      '    rgb[i] = (int(((c >> 16) & 255) * k), int(((c >> 8) & 255) * k), int((c & 255) * k))\n' +
      'def _rgb_set(i, c):\n' +
      '    # 灯号 0/1/2，-1 是全部；颜色是 0xRRGGBB\n' +
      '    c = int(_num(c))\n' +
      '    for j in (range(3) if int(_num(i)) < 0 else [int(_num(i))]):\n' +
      '        _rgb_c[j] = c\n' +
      '        _rgb_apply(j)\n' +
      '    rgb.write()\n' +
      'def _rgb_bright(b):\n' +
      '    global _rgb_b\n' +
      '    _rgb_b = max(0, min(9, int(_num(b))))\n' +
      '    for j in range(3):\n' +
      '        _rgb_apply(j)\n' +
      '    rgb.write()',
    tone:
      '_bpm = 120\n' +
      '_beat_ticks = 4\n' +
      '_buzz_pin = None\n' +
      'def _music_kw():\n' +
      '    return {} if _buzz_pin is None else {"pin": _buzz_pin}\n' +
      'def _tone(freq, beat):\n' +
      '    # 一拍的长度 = 60000 / 每分钟节拍数（毫秒）\n' +
      '    ms = int(_num(beat) * 60000 / _bpm)\n' +
      '    music.pitch(int(_num(freq)), ms, **_music_kw())\n' +
      'def _tone_bg(freq):\n' +
      '    try:\n' +
      '        music.pitch(int(_num(freq)), -1, wait=False, **_music_kw())\n' +
      '    except TypeError:\n' +
      '        music.pitch(int(_num(freq)), -1, **_music_kw())',
    touch:
      '_touch_th = {}\n' +
      '_touch_pads = {"p": touchpad_p, "y": touchpad_y, "t": touchpad_t, "h": touchpad_h, "o": touchpad_o, "n": touchpad_n}\n' +
      'def _touched(k):\n' +
      '    # 设置过阈值就用 读数 < 阈值，否则用固件自带的判断\n' +
      '    if k in _touch_th:\n' +
      '        return _touch_pads[k].read() < _touch_th[k]\n' +
      '    return _touch_pads[k].is_pressed()',
    accel:
      '_acc_off = [0, 0, 0]\n' +
      'def _acc(axis):\n' +
      '    # 单位 毫 g（m-g），和 Mind+ 一样是整数\n' +
      '    x = accelerometer.get_x() * 1000 - _acc_off[0]\n' +
      '    y = accelerometer.get_y() * 1000 - _acc_off[1]\n' +
      '    z = accelerometer.get_z() * 1000 - _acc_off[2]\n' +
      '    if axis == "X":\n' +
      '        return int(x)\n' +
      '    if axis == "Y":\n' +
      '        return int(y)\n' +
      '    if axis == "Z":\n' +
      '        return int(z)\n' +
      '    return int(math.sqrt(x * x + y * y + z * z))',
    gesture:
      '_shake_last = [0, 0, 0]\n' +
      'def _gesture(g):\n' +
      '    x, y, z = accelerometer.get_x(), accelerometer.get_y(), accelerometer.get_z()\n' +
      '    if g == "TiltForward":\n' +
      '        return y < -0.5\n' +
      '    if g == "TiltBack":\n' +
      '        return y > 0.5\n' +
      '    if g == "TiltLeft":\n' +
      '        return x > 0.5\n' +
      '    if g == "TiltRight":\n' +
      '        return x < -0.5\n' +
      '    if g == "ScreenUp":\n' +
      '        return z < -0.8\n' +
      '    if g == "ScreenDown":\n' +
      '        return z > 0.8\n' +
      '    if g == "Shake":\n' +
      '        a = _shake_last\n' +
      '        d = abs(x - a[0]) + abs(y - a[1]) + abs(z - a[2])\n' +
      '        a[0], a[1], a[2] = x, y, z\n' +
      '        return d > 1.5\n' +
      '    return False',
    btn:
      'def _btn(which, pressed):\n' +
      '    a, b = button_a.is_pressed(), button_b.is_pressed()\n' +
      '    v = a if which == "A" else b if which == "B" else (a and b)\n' +
      '    return v if pressed else not v',
    serial:
      'import sys\n' +
      'try:\n' +
      '    import uselect as _sel\n' +
      'except ImportError:\n' +
      '    import select as _sel\n' +
      '_uarts = {}\n' +
      'def _ser_ready(port):\n' +
      '    if port in _uarts:\n' +
      '        return _uarts[port].any() > 0\n' +
      '    p = _sel.poll()\n' +
      '    p.register(sys.stdin, _sel.POLLIN)\n' +
      '    return bool(p.poll(0))\n' +
      'def _ser_read(port):\n' +
      '    if port in _uarts:\n' +
      '        b = _uarts[port].read(1)\n' +
      '        return b.decode() if b else ""\n' +
      '    return sys.stdin.read(1) if _ser_ready(port) else ""\n' +
      'def _ser_readline(port):\n' +
      '    if port in _uarts:\n' +
      '        b = _uarts[port].readline()\n' +
      '        return b.decode().strip() if b else ""\n' +
      '    return sys.stdin.readline().strip()\n' +
      'def _ser_parse(port, kind):\n' +
      '    s = _ser_readline(port)\n' +
      '    if kind == "string":\n' +
      '        return s\n' +
      '    try:\n' +
      '        return int(float(s)) if kind == "int" else float(s)\n' +
      '    except Exception:\n' +
      '        return 0\n' +
      'def _ser_send(port, way, v, nl):\n' +
      '    if way == "HEX output":\n' +
      '        try:\n' +
      '            s = hex(int(_num(v)))[2:].upper()\n' +
      '        except Exception:\n' +
      '            s = _str(v)\n' +
      '    else:\n' +
      '        s = _str(v)\n' +
      '    if port in _uarts:\n' +
      '        _uarts[port].write(s + ("\\r\\n" if nl else ""))\n' +
      '    else:\n' +
      '        print(s, end="\\n" if nl else "")\n' +
      'def _ser_map(port, rx, tx, baud):\n' +
      '    from machine import UART, Pin\n' +
      '    _uarts[port] = UART(1 if port == "SerialPort1" else 2, baudrate=baud, rx=Pin(rx), tx=Pin(tx))',
    wifi:
      'import network\n' +
      '_wlan = network.WLAN(network.STA_IF)\n' +
      '_wifi_dbg = []\n' +
      'def _wifi_debug(msg, code):\n' +
      '    for f in _wifi_dbg:\n' +
      '        f(msg, code)\n' +
      'def _wifi_connect(ssid, pwd):\n' +
      '    # 和 Mind+ 一样一直等到连上；连不上时屏幕/串口有提示\n' +
      '    _wlan.active(True)\n' +
      '    if _wlan.isconnected():\n' +
      '        _wlan.disconnect()\n' +
      '    _wifi_debug("Wi-Fi 开始连接 " + _str(ssid), 0)\n' +
      '    _wlan.connect(_str(ssid), _str(pwd))\n' +
      '    t = time.ticks_ms()\n' +
      '    while not _wlan.isconnected():\n' +
      '        time.sleep_ms(200)\n' +
      '        if time.ticks_diff(time.ticks_ms(), t) > 15000:\n' +
      '            print("Wi-Fi 还没连上，检查热点名称和密码：", ssid)\n' +
      '            _wifi_debug("Wi-Fi 连接超时", 1)\n' +
      '            t = time.ticks_ms()\n' +
      '    _wifi_debug("Wi-Fi 已连接 " + _wlan.ifconfig()[0], 0)\n' +
      'def _wifi_ip(k):\n' +
      '    c = _wlan.ifconfig()\n' +
      '    return {"getWiFiLocalIP": c[0], "getWiFiNetmask": c[1], "getGateway": c[2], "getDNS": c[3]}.get(k, c[0])\n' +
      '_ap = None\n' +
      'def _wifi_ap(ssid, pwd):\n' +
      '    global _ap\n' +
      '    _ap = network.WLAN(network.AP_IF)\n' +
      '    _ap.active(True)\n' +
      '    _ap.config(essid=_str(ssid), password=_str(pwd), authmode=3 if len(_str(pwd)) >= 8 else 0)\n' +
      'def _wifi_ap_ip():\n' +
      '    return _ap.ifconfig()[0] if _ap else "0.0.0.0"',
    mqtt:
      'import ujson\n' +
      'import ubinascii\n' +
      'import machine\n' +
      'from umqtt.simple import MQTTClient\n' +
      'class _MQ:\n' +
      '    # Mind+ 的 MQTT：初始化参数里有服务器、账号和 Topic_0~Topic_4；连接后自动订阅这些主题\n' +
      '    def __init__(self):\n' +
      '        self.c = None\n' +
      '        self.ok = False\n' +
      '        self.cfg = None\n' +
      '        self.topics = ["", "", "", "", ""]\n' +
      '        self.subs = []\n' +
      '        self.handlers = []\n' +
      '    def init(self, settings):\n' +
      '        s = ujson.loads(settings)\n' +
      '        p = s.get("params", {})\n' +
      '        self.cfg = (p.get("siotwww") or p.get("server") or "127.0.0.1", int(p.get("port") or 1883),\n' +
      '                    p.get("siotid") or p.get("iotid") or "", p.get("siotpwd") or p.get("iotpwd") or "")\n' +
      '        self.topics = [p.get("topic", ""), p.get("topic_1", ""), p.get("topic_2", ""), p.get("topic_3", ""), p.get("topic_4", "")]\n' +
      '    def topic(self, k):\n' +
      '        if isinstance(k, str) and k.startswith("Topic_"):\n' +
      '            return self.topics[int(k[6:])]\n' +
      '        return _str(k)\n' +
      '    def _cb(self, t, m):\n' +
      '        t = t.decode()\n' +
      '        try:\n' +
      '            m = m.decode()\n' +
      '        except Exception:\n' +
      '            m = str(m)\n' +
      '        for topic, want, f in self.handlers:\n' +
      '            if self.topic(topic) == t and (want is None or _str(want) == m):\n' +
      '                f(m)\n' +
      '    def connect(self):\n' +
      '        if not self.cfg:\n' +
      '            print("先用“MQTT 初始化参数”设置服务器")\n' +
      '            return\n' +
      '        host, port, user, pwd = self.cfg\n' +
      '        cid = "handpy" + ubinascii.hexlify(machine.unique_id()).decode()\n' +
      '        try:\n' +
      '            self.c = MQTTClient(cid, host, port, user or None, pwd or None, keepalive=60)\n' +
      '            self.c.set_callback(self._cb)\n' +
      '            self.c.connect()\n' +
      '            for t in [x for x in self.topics if x] + self.subs:\n' +
      '                self.c.subscribe(t.encode())\n' +
      '            self.ok = True\n' +
      '            # 收消息靠后台每 20 毫秒查一次（Mind+ 里这是自动的）\n' +
      '            if self.poll not in _bg_tasks:\n' +
      '                _bg_tasks.append(self.poll)\n' +
      '            _bg_need()\n' +
      '        except Exception as e:\n' +
      '            self.ok = False\n' +
      '            print("MQTT 连接失败：", e)\n' +
      '    def disconnect(self):\n' +
      '        try:\n' +
      '            self.c.disconnect()\n' +
      '        except Exception:\n' +
      '            pass\n' +
      '        self.ok = False\n' +
      '    def publish(self, topic, msg):\n' +
      '        if not self.ok:\n' +
      '            print("MQTT 还没连接，消息没发出去")\n' +
      '            return\n' +
      '        try:\n' +
      '            # 转成字节再发：umqtt 按字数算长度，中文会被截断\n' +
      '            self.c.publish(self.topic(topic).encode(), _str(msg).encode())\n' +
      '        except Exception as e:\n' +
      '            self.ok = False\n' +
      '            print("MQTT 发送失败：", e)\n' +
      '    def subscribe(self, topic):\n' +
      '        t = self.topic(topic)\n' +
      '        if t not in self.subs:\n' +
      '            self.subs.append(t)\n' +
      '        if self.ok:\n' +
      '            self.c.subscribe(t.encode())\n' +
      '    def on(self, topic, want, f):\n' +
      '        self.handlers.append((topic, want, f))\n' +
      '    def poll(self):\n' +
      '        if self.ok:\n' +
      '            try:\n' +
      '                self.c.check_msg()\n' +
      '            except Exception as e:\n' +
      '                self.ok = False\n' +
      '                print("MQTT 断开了：", e)\n' +
      '_mq = _MQ()',
    bg:
      '# 后台：收 MQTT 消息、检查姿态和 A+B 键（Mind+ 里这些是自动进行的）\n' +
      '_bg_tasks = []\n' +
      '_bg_on = False\n' +
      'def _bg_once():\n' +
      '    for f in _bg_tasks:\n' +
      '        try:\n' +
      '            f()\n' +
      '        except Exception as e:\n' +
      '            print("后台出错：", e)\n' +
      'def _bg_run():\n' +
      '    while True:\n' +
      '        _bg_once()\n' +
      '        time.sleep_ms(20)\n' +
      'def _bg_need():\n' +
      '    global _bg_on\n' +
      '    if _bg_on:\n' +
      '        return\n' +
      '    _bg_on = True\n' +
      '    try:\n' +
      '        import _thread\n' +
      '        _thread.start_new_thread(_bg_run, ())\n' +
      '    except Exception:\n' +
      '        from machine import Timer\n' +
      '        Timer(1).init(period=50, mode=Timer.PERIODIC, callback=lambda t: _bg_once())'
  };
  // 辅助函数之间的依赖
  var NEEDS = {
    num: [], cmp: [], str: [], map: ['num'], substr: ['str', 'num'], find: ['str'], listfind: ['str'],
    pins: ['mpython'], screen: ['mpython', 'num', 'str'], rgb: ['mpython', 'num'], tone: ['music', 'num'],
    touch: ['mpython'], accel: ['mpython', 'math'], gesture: ['mpython'], btn: ['mpython'],
    serial: ['num', 'str'], wifi: ['time', 'str'], mqtt: ['str', 'bg', 'time', 'machine'], bg: ['time']
  };
  var ORDER = ['mpython', 'machine', 'time', 'math', 'random', 'music', 'num', 'cmp', 'str', 'map', 'substr', 'find', 'listfind', 'pins',
    'screen', 'rgb', 'tone', 'touch', 'accel', 'gesture', 'btn', 'serial', 'bg', 'wifi', 'mqtt'];

  function Gen(ws) {
    this.ws = ws;
    this.use_ = {};
    this.varNames = {};
    this.procs = {};
    this.argNames = null;
    this.bgTasks = [];
    this.warnings = [];
  }
  Gen.prototype.use = function (k) {
    if (this.use_[k]) return;
    this.use_[k] = true;
    var self = this;
    (NEEDS[k] || []).forEach(function (d) { self.use(d); });
  };

  // ---------------- 变量 ----------------
  Gen.prototype.varName = function (id, kind) {
    if (this.varNames[id]) return this.varNames[id];
    var v = this.ws.getVariableById(id);
    var base = v ? v.name : 'v';
    var ascii = /^[A-Za-z_][A-Za-z0-9_]*$/.test(base) ? base : '';
    var prefix = kind === 'list' ? 'mind_l_' : kind === 'string' ? 'mind_s_' : 'mind_n_';
    var n = ascii ? prefix + ascii : prefix + (Object.keys(this.varNames).length + 1);
    this.varNames[id] = n;
    return n;
  };
  Gen.prototype.fieldVar = function (block, field, kind) {
    var f = block.getField(field);
    var id = f && f.getValue();
    return id ? this.varName(id, kind) : (kind === 'list' ? 'mind_l_none' : 'mind_n_none');
  };

  // ---------------- 取值 ----------------
  // 返回 [代码, 类型]：类型 num / str / bool / any
  Gen.prototype.value = function (block, name, def) {
    var t = block.getInputTargetBlock(name);
    if (!t) return def === undefined ? ['0', 'num'] : def;
    return this.expr(t);
  };
  Gen.prototype.v = function (block, name) { return this.value(block, name)[0]; };
  Gen.prototype.n = function (block, name) {
    var r = this.value(block, name);
    if (r[1] === 'num') return r[0];
    this.use('num');
    return '_num(' + r[0] + ')';
  };
  Gen.prototype.s = function (block, name) {
    var r = this.value(block, name, ["''", 'str']);
    if (r[1] === 'str') return r[0];
    this.use('str');
    return '_str(' + r[0] + ')';
  };
  Gen.prototype.b = function (block, name) {
    var t = block.getInputTargetBlock(name);
    if (!t) return 'False';
    return this.expr(t)[0];
  };

  Gen.prototype.literal = function (block) {
    var type = block.type;
    if (/^math_/.test(type) || type === 'math_slider') {
      var raw = block.getFieldValue('NUM');
      var n = numLit(raw);
      return n !== null ? [n, 'num'] : [pyStr(raw), 'str'];
    }
    if (type === 'text' || type === 'mp_text_q') {
      var tx = block.getFieldValue('TEXT');
      var nn = numLit(tx);
      // 只有写法本身就是规范数字（如 50、3.5）才当数字；"007"、"00123456"（密码）要保持文字
      return nn !== null && nn === String(tx).trim() ? [nn, 'num'] : [pyStr(tx), 'str'];
    }
    if (type === 'piano') return [String(Number(block.getFieldValue('PIANO')) || 0), 'num'];
    if (type === 'colour_palette' || type === 'colour_picker') {
      var c = String(block.getFieldValue('COLOUR') || '#000000').replace('#', '');
      if (c.length === 3) c = c.split('').map(function (x) { return x + x; }).join('');
      return ['0x' + c.toUpperCase(), 'num'];
    }
    if (/_menu_/.test(type)) {
      var fieldName = type.replace(/^.*_menu_/, '');
      var mv = block.getFieldValue(fieldName);
      if (mv === 'ALL') return ['-1', 'num'];
      var m2 = numLit(mv);
      return m2 !== null ? [m2, 'num'] : [pyStr(mv), 'str'];
    }
    return null;
  };

  // ---------------- 表达式积木 ----------------
  Gen.prototype.expr = function (block) {
    var lit = this.literal(block);
    if (lit) return lit;
    var t = block.type, s = short(t), self = this;
    var f = function (n) { return block.getFieldValue(n); };
    switch (t) {
      // 运算符
      case 'operator_add': return ['(' + this.n(block, 'NUM1') + ' + ' + this.n(block, 'NUM2') + ')', 'num'];
      case 'operator_subtract': return ['(' + this.n(block, 'NUM1') + ' - ' + this.n(block, 'NUM2') + ')', 'num'];
      case 'operator_multiply': return ['(' + this.n(block, 'NUM1') + ' * ' + this.n(block, 'NUM2') + ')', 'num'];
      case 'operator_divide': return ['(' + this.n(block, 'NUM1') + ' / ' + this.n(block, 'NUM2') + ')', 'num'];
      case 'operator_mod': return ['(int(' + this.n(block, 'NUM1') + ') % int(' + this.n(block, 'NUM2') + '))', 'num'];
      case 'operator_random':
        this.use('random');
        return ['random.randint(int(' + this.n(block, 'FROM') + '), int(' + this.n(block, 'TO') + '))', 'num'];
      case 'operator_gt': case 'operator_lt': case 'operator_equals': case 'operator_gt_equals': case 'operator_lt_equals': {
        var op = { operator_gt: '>', operator_lt: '<', operator_equals: '==', operator_gt_equals: '>=', operator_lt_equals: '<=' }[t];
        var a = this.value(block, 'OPERAND1'), b = this.value(block, 'OPERAND2');
        if (a[1] === 'num' && b[1] === 'num') return ['(' + a[0] + ' ' + op + ' ' + b[0] + ')', 'bool'];
        this.use('cmp');
        return ['(_cmp(' + a[0] + ', ' + b[0] + ') ' + op + ' 0)', 'bool'];
      }
      case 'operator_and': return ['(' + this.b(block, 'OPERAND1') + ' and ' + this.b(block, 'OPERAND2') + ')', 'bool'];
      case 'operator_or': return ['(' + this.b(block, 'OPERAND1') + ' or ' + this.b(block, 'OPERAND2') + ')', 'bool'];
      case 'operator_not': return ['(not ' + this.b(block, 'OPERAND') + ')', 'bool'];
      case 'operator_join': return ['(' + this.s(block, 'STRING1') + ' + ' + this.s(block, 'STRING2') + ')', 'str'];
      case 'operator_letter_of':
        return ['(' + this.s(block, 'STRING') + ')[int(' + this.n(block, 'LETTER') + ') - 1:int(' + this.n(block, 'LETTER') + ')]', 'str'];
      case 'operator_length': return ['len(' + this.s(block, 'STRING') + ')', 'num'];
      case 'operator_contains': return ['(' + this.s(block, 'STRING2') + ' in ' + this.s(block, 'STRING1') + ')', 'bool'];
      case 'operator_textsrobotseven':
        this.use('substr');
        return ['_substr(' + this.v(block, 'STRING1') + ', ' + pyStr(f('LIST1')) + ', ' + this.v(block, 'STRING2') + ', ' + pyStr(f('LIST2')) + ', ' + this.v(block, 'STRING3') + ')', 'str'];
      case 'operator_textsrobotsevenNew':
        this.use('find');
        return ['_find(' + this.v(block, 'STRING1') + ', ' + this.v(block, 'STRING2') + ', ' + (f('LIST1') === 'last' ? 'True' : 'False') + ')', 'num'];
      case 'operator_round': return ['round(' + this.n(block, 'NUM') + ')', 'num'];
      case 'operator_stringToNum':
        this.use('num');
        return [f('TYPE') === 'Integer' ? 'int(_num(' + this.v(block, 'STRING') + '))' : 'float(_num(' + this.v(block, 'STRING') + '))', 'num'];
      case 'operator_mathop': {
        this.use('math');
        var x = this.n(block, 'NUM');
        var o = f('OPERATOR').trim();
        var map = {
          abs: 'abs(' + x + ')', floor: 'math.floor(' + x + ')', ceiling: 'math.ceil(' + x + ')', sqrt: 'math.sqrt(' + x + ')',
          sin: 'math.sin(' + x + ' * math.pi / 180)', cos: 'math.cos(' + x + ' * math.pi / 180)', tan: 'math.tan(' + x + ' * math.pi / 180)',
          asin: '(math.asin(' + x + ') * 180 / math.pi)', acos: '(math.acos(' + x + ') * 180 / math.pi)', atan: '(math.atan(' + x + ') * 180 / math.pi)',
          ln: 'math.log(' + x + ')', log: '(math.log(' + x + ') / math.log(10))', 'e ^': 'math.exp(' + x + ')', '10 ^': 'math.pow(10, ' + x + ')'
        };
        return [map[o] || x, 'num'];
      }
      case 'operator_numToASCII': return ['chr(int(' + this.n(block, 'NUM') + '))', 'str'];
      case 'operator_stringToASCII': return ['ord((' + this.s(block, 'STRING') + ' or "\\0")[0])', 'num'];
      case 'operator_numToString': this.use('str'); return ['_str(' + this.v(block, 'NUM') + ')', 'str'];
      case 'operator_map':
        this.use('map');
        return ['_map(' + ['VALUE', 'FROMLOW', 'FROMHIGH', 'TOLOW', 'TOHIGH'].map(function (k) { return self.v(block, k); }).join(', ') + ')', 'num'];
      case 'operator_constrain':
        return ['max(' + this.n(block, 'MIN') + ', min(' + this.n(block, 'MAX') + ', ' + this.n(block, 'VALUE') + '))', 'num'];
      // 变量
      case 'data_variable': return [this.fieldVar(block, 'VARIABLE', 'number'), 'num'];
      case 'data_string': return [this.fieldVar(block, 'STRING', 'string'), 'str'];
      case 'data_stringStartWith': return [this.fieldVar(block, 'STRING', 'string') + '.startswith(' + this.s(block, 'VALUE') + ')', 'bool'];
      case 'data_listcontents': this.use('str'); return ['" ".join([_str(x) for x in ' + this.fieldVar(block, 'LIST', 'list') + '])', 'str'];
      case 'data_itemoflist': return [this.fieldVar(block, 'LIST', 'list') + '[int(' + this.n(block, 'INDEX') + ') - 1]', 'any'];
      case 'data_itemnumoflist': this.use('listfind'); return ['_lfind(' + this.fieldVar(block, 'LIST', 'list') + ', ' + this.v(block, 'ITEM') + ')', 'num'];
      case 'data_lengthoflist': return ['len(' + this.fieldVar(block, 'LIST', 'list') + ')', 'num'];
      case 'data_listcontainsitem':
        this.use('listfind');
        return ['(_lfind(' + this.fieldVar(block, 'LIST', 'list') + ', ' + this.v(block, 'ITEM') + ') > 0)', 'bool'];
      // 函数参数
      case 'argument_reporter_string_number': case 'argument_reporter_boolean': {
        var nm = f('VALUE');
        return [this.argNames && this.argNames[nm] ? this.argNames[nm] : '0', 'any'];
      }
    }
    // 掌控板
    switch (s) {
      case 'esp32_btnIsPressed': this.use('btn'); return ['_btn(' + pyStr(f('BUTTON') === 'A+B' ? 'AB' : f('BUTTON')) + ', ' + (f('BUTTONSTATE') === 'Pressed' ? 'True' : 'False') + ')', 'bool'];
      case 'esp32_pinIsConnected': {
        this.use('touch');
        var k = TOUCH_NAME[f('PIN')] || 'p';
        return [(f('TOUCHSTATE') === 'Connected' ? '' : 'not ') + '_touched(' + pyStr(k) + ')', 'bool'];
      }
      case 'esp32_readTouchValue': this.use('mpython'); return ['touchpad_' + (TOUCH_NAME[f('PIN')] || 'p') + '.read()', 'num'];
      case 'isGesture': this.use('gesture'); return ['_gesture(' + pyStr(f('GESTURE')) + ')', 'bool'];
      case 'esp32_getSoundIntensity': this.use('mpython'); return ['sound.read()', 'num'];
      case 'esp32_readBrightness': this.use('mpython'); return ['light.read()', 'num'];
      case 'esp32_acceleration': this.use('accel'); return ['_acc(' + pyStr(f('TYPE') === 'strength' ? 'S' : f('TYPE')) + ')', 'num'];
      case 'esp32_rgbToColor':
        this.use('num');
        return ['((int(_num(' + this.v(block, 'RED') + ')) & 255) << 16 | (int(_num(' + this.v(block, 'GREEN') + ')) & 255) << 8 | (int(_num(' + this.v(block, 'BLUE') + ')) & 255))', 'num'];
      case 'esp32_getBrightness': this.use('rgb'); return ['_rgb_b', 'num'];
      case 'esp32_havenumberread': this.use('serial'); return ['_ser_ready(' + pyStr(f('SERIAL')) + ')', 'bool'];
      case 'esp32_readserialdata': this.use('serial'); return ['_ser_read(' + pyStr(f('SERIAL')) + ')', 'str'];
      case 'esp32_readserialIntfloat': this.use('serial'); return ['_ser_parse(' + pyStr(f('SERIAL')) + ', ' + pyStr(f('READTYPE')) + ')', 'any'];
      case 'esp32_getNumOfFloat': this.use('num'); return ['round(float(_num(' + this.v(block, 'TARGET') + ')), int(_num(' + this.v(block, 'NNUMS') + ')))', 'num'];
      case 'esp32_digitalRead': return [this.pinRead(f('PIN'), 'digital'), 'num'];
      case 'esp32_analogRead': return [this.pinRead(f('PIN'), 'analog'), 'num'];
      case 'esp32_timer': this.use('time'); return ['time.ticks_ms()', 'num'];
      // Wi-Fi
      case 'wifiConnected': this.use('wifi'); return ['_wlan.isconnected()', 'bool'];
      case 'wifiGetLocalIP': this.use('wifi'); return ['_wifi_ip(' + pyStr(f('CONFIGURATION')) + ')', 'str'];
      case 'wifiGetSoftAPIP': this.use('wifi'); return ['_wifi_ap_ip()', 'str'];
      case 'wifi_whenDebugInfoDebug_eventHeadInner_': return ['_dbg_msg', 'str'];
      case 'wifi_whenDebugInfoError_eventHeadInner_': return ['_dbg_code', 'num'];
      // MQTT
      case 'mqtt_mqttConnected': this.use('mqtt'); return ['_mq.ok', 'bool'];
      case 'mqtt_argumentReporterStringNumber_eventHeadInner_': return ['_mqtt_msg', 'str'];
    }
    this.warnings.push('不认识的积木：' + t);
    return ['0', 'num'];
  };

  Gen.prototype.pinRead = function (pin, kind) {
    this.use('pins');
    this.use('machine');
    var n = pinNo(pin);
    if (typeof n === 'string') {
      // 触摸键当普通引脚用：直接用 GPIO
      var g = PIN_NO[n];
      return kind === 'analog' ? 'machine.ADC(machine.Pin(' + g + ')).read()' : 'machine.Pin(' + g + ', machine.Pin.IN).value()';
    }
    return kind === 'analog' ? '_pin(' + n + ', PinMode.ANALOG).read_analog()' : '_pin(' + n + ', PinMode.IN).read_digital()';
  };

  // ---------------- 语句 ----------------
  Gen.prototype.stack = function (block) {
    var out = '';
    while (block) {
      if (!block.isShadow()) out += this.stmt(block);
      block = block.getNextBlock();
    }
    return out;
  };
  Gen.prototype.sub = function (block, name) {
    var t = block.getInputTargetBlock(name);
    var code = t ? this.stack(t) : '';
    return indent(code || 'pass\n');
  };

  Gen.prototype.stmt = function (block) {
    var t = block.type, s = short(t), self = this;
    var f = function (n) { return block.getFieldValue(n); };
    switch (t) {
      case 'control_wait': this.use('time'); return 'time.sleep(' + this.n(block, 'DURATION') + ')\n';
      case 'control_repeat': return 'for _i in range(int(' + this.n(block, 'TIMES') + ')):\n' + this.sub(block, 'SUBSTACK');
      case 'control_forever': return 'while True:\n' + this.sub(block, 'SUBSTACK');
      case 'control_if': return 'if ' + this.b(block, 'CONDITION') + ':\n' + this.sub(block, 'SUBSTACK');
      case 'control_if_else':
        return 'if ' + this.b(block, 'CONDITION') + ':\n' + this.sub(block, 'SUBSTACK') + 'else:\n' + this.sub(block, 'SUBSTACK2');
      case 'control_if_else_special': {
        var code = 'if ' + this.b(block, 'IF0') + ':\n' + this.sub(block, 'DO0');
        for (var i = 1; block.getInput('IF' + i); i++) code += 'elif ' + this.b(block, 'IF' + i) + ':\n' + this.sub(block, 'DO' + i);
        if (block.getInput('ELSE')) code += 'else:\n' + this.sub(block, 'ELSE');
        return code;
      }
      case 'control_wait_until': this.use('time'); return 'while not ' + this.b(block, 'CONDITION') + ':\n' + IND + 'time.sleep_ms(10)\n';
      case 'control_repeat_until': return 'while not ' + this.b(block, 'CONDITION') + ':\n' + this.sub(block, 'SUBSTACK');
      case 'data_setvariableto': return this.fieldVar(block, 'VARIABLE', 'number') + ' = ' + this.n(block, 'VALUE') + '\n';
      case 'data_changevariableby': {
        var vn = this.fieldVar(block, 'VARIABLE', 'number');
        return vn + ' = ' + vn + ' + ' + this.n(block, 'VALUE') + '\n';
      }
      case 'data_setStringto': return this.fieldVar(block, 'STRING', 'string') + ' = ' + this.s(block, 'VALUE') + '\n';
      case 'data_addtolist': return this.fieldVar(block, 'LIST', 'list') + '.append(' + this.v(block, 'ITEM') + ')\n';
      case 'data_deleteoflist': {
        var l1 = this.fieldVar(block, 'LIST', 'list'), i1 = 'int(' + this.n(block, 'INDEX') + ') - 1';
        return 'if 0 <= ' + i1 + ' < len(' + l1 + '):\n' + IND + 'del ' + l1 + '[' + i1 + ']\n';
      }
      case 'data_deletealloflist': return this.fieldVar(block, 'LIST', 'list') + '.clear()\n';
      case 'data_insertatlist': return this.fieldVar(block, 'LIST', 'list') + '.insert(int(' + this.n(block, 'INDEX') + ') - 1, ' + this.v(block, 'ITEM') + ')\n';
      case 'data_replaceitemoflist': {
        var l2 = this.fieldVar(block, 'LIST', 'list'), i2 = 'int(' + this.n(block, 'INDEX') + ') - 1';
        return 'if 0 <= ' + i2 + ' < len(' + l2 + '):\n' + IND + l2 + '[' + i2 + '] = ' + this.v(block, 'ITEM') + '\n';
      }
      case 'procedures_call': {
        var p = this.procs[block.getProcCode ? block.getProcCode() : ''];
        if (!p) return '';
        var ids = block.argumentIds_ || [];
        var args = ids.map(function (id) { return self.value(block, id, ['0', 'num'])[0]; });
        return p.name + '(' + args.join(', ') + ')\n';
      }
    }
    switch (s) {
      case 'esp32_setTouchThreshold': {
        this.use('touch');
        var pad = f('PIN');
        var th = 'int(' + this.n(block, 'THRESHOLD') + ')';
        if (pad === 'ALL') return 'for _k in "python":\n' + IND + '_touch_th[_k] = ' + th + '\n';
        return '_touch_th[' + pyStr(TOUCH_NAME[pad] || 'p') + '] = ' + th + '\n';
      }
      case 'esp32_accelerationCalibration':
        this.use('accel');
        return '_acc_off[0], _acc_off[1], _acc_off[2] = ' + this.n(block, 'XOFFSET') + ', ' + this.n(block, 'YOFFSET') + ', ' + this.n(block, 'ZOFFSET') + '\n';
      case 'esp32_displayInLine': this.use('screen'); return '_disp_line(' + this.v(block, 'TEXT') + ', ' + this.v(block, 'LINE') + ')\n';
      case 'esp32_displayClearInLine': this.use('screen'); return '_clear_line(' + this.v(block, 'LINE') + ')\n';
      case 'esp32_displayInXY': this.use('screen'); return '_disp_xy(' + this.v(block, 'TEXT') + ', ' + this.v(block, 'X') + ', ' + this.v(block, 'Y') + ')\n';
      case 'esp32_imageDrawing': {
        this.use('screen');
        var img = block.getInputTargetBlock('IMAGE');
        var info = {};
        try { info = JSON.parse(img ? img.getFieldValue('ESP32_IMG') : '{}') || {}; } catch (e) { info = {}; }
        if (!info.bits) return '# 屏幕显示图片：还没选图片\n';
        return '_bitmap(' + this.v(block, 'X') + ', ' + this.v(block, 'Y') + ', bytearray(b"' +
          info.bits.replace(/(..)/g, '\\x$1') + '"), ' + info.w + ', ' + info.h + ')\n';
      }
      case 'esp32_fillScreen': this.use('screen'); return '_fill(' + (f('COLOR') === 'White' ? 1 : 0) + ')\n';
      case 'esp32_screenRotation': this.use('screen'); return '_rotate(' + Number(f('ROTATION')) + ')\n';
      case 'esp32_pointDrawing': this.use('screen'); return '_point(' + this.v(block, 'X') + ', ' + this.v(block, 'Y') + ')\n';
      case 'esp32_setLineWidth': this.use('screen'); return '_lw = max(1, int(' + this.n(block, 'LINEWIDTH') + '))\n';
      case 'esp32_lineDrawing': this.use('screen'); return '_line(' + ['X1', 'Y1', 'X2', 'Y2'].map(function (k) { return self.v(block, k); }).join(', ') + ')\n';
      case 'esp32_circleDrawing': this.use('screen'); return '_circle(' + this.v(block, 'X') + ', ' + this.v(block, 'Y') + ', ' + this.v(block, 'RADIUS') + ', ' + (f('FILL') === 'FILL' ? 'True' : 'False') + ')\n';
      case 'esp32_rectangleDrawing':
        this.use('screen');
        return '_rect(' + ['X', 'Y', 'WIDTH', 'HEIGHT'].map(function (k) { return self.v(block, k); }).join(', ') + ', ' + (f('FILL') === 'FILL' ? 'True' : 'False') + ')\n';
      case 'esp32_setLightsColor': this.use('rgb'); return '_rgb_set(' + this.v(block, 'LIGHTS') + ', ' + this.v(block, 'COLOR') + ')\n';
      case 'esp32_rgbDisable': {
        this.use('rgb');
        var idx = { all: -1, 'No.0': 0, 'No.1': 1, 'No.2': 2 }[f('INDEX')];
        return '_rgb_set(' + (idx === undefined ? -1 : idx) + ', 0)\n';
      }
      case 'esp32_setBrightness': this.use('rgb'); return '_rgb_bright(' + this.v(block, 'BRIGHTVALUE') + ')\n';
      case 'esp32_playMusic': {
        this.use('tone');
        var rep = f('REPEAT');
        var wait = rep === 'Once' || rep === 'Forever' ? 'True' : 'False';
        var loop = rep === 'Forever' || rep === 'ForeverInBackground' ? 'True' : 'False';
        return 'music.play(music.' + f('MUSIC') + ', wait=' + wait + ', loop=' + loop + ', **_music_kw())\n';
      }
      case 'esp32_musicSetTempo':
        this.use('tone');
        return '_beat_ticks, _bpm = int(' + this.n(block, 'TICKS') + '), int(' + this.n(block, 'TEMPO') + ')\nmusic.set_tempo(ticks=_beat_ticks, bpm=_bpm)\n';
      case 'esp32_setBuzzerFreq': {
        this.use('tone');
        var beat = block.getInputTargetBlock('BEAT');
        var bv = beat && /_menu_/.test(beat.type) ? String(beat.getFieldValue('beatMenu')) : null;
        var beatCode = bv ? (bv.indexOf('/') > 0 ? '(' + bv.replace('/', ' / ') + ')' : bv) : this.n(block, 'BEAT');
        return '_tone(' + this.v(block, 'NOTE') + ', ' + beatCode + ')\n';
      }
      case 'esp32_backgroundSetBuzzerFreq': this.use('tone'); return '_tone_bg(' + this.v(block, 'NOTE') + ')\n';
      case 'esp32_buzzerSwitch': this.use('tone'); return 'music.stop(**_music_kw())\n';
      case 'esp32_buzzerRedirect': {
        this.use('tone');
        var bp = pinNo(f('PIN'));
        this.use('machine');
        return '_buzz_pin = machine.Pin(' + this.gpio(bp) + ')\n';
      }
      case 'esp32_serialSend':
        this.use('serial');
        return '_ser_send(' + pyStr(f('SERIAL')) + ', ' + pyStr(f('WAY')) + ', ' + this.v(block, 'STRING') + ', ' + (f('LINE') === 'Wrap' ? 'True' : 'False') + ')\n';
      case 'esp32_setSerial': return '# 串口0 就是 USB 线，波特率固定 115200（设置为 ' + f('BAUD') + ' 不影响）\n';
      case 'esp32_seriaRemapping': {
        this.use('serial');
        var rx = pinNo(f('RX')), tx = pinNo(f('TX'));
        return '_ser_map(' + pyStr(f('SERIAL')) + ', ' + this.gpio(rx) + ', ' + this.gpio(tx) + ', ' + Number(f('BAUD')) + ')\n';
      }
      case 'esp32_set_pinMode': {
        this.use('pins');
        this.use('machine');
        var pm = pinNo(f('PIN'));
        var mode = { OUTPUT: 'PinMode.OUT', INPUT: 'PinMode.IN', INPUT_PULLUP: 'PinMode.IN' }[f('PINMODE')];
        if (typeof pm === 'string') return 'machine.Pin(' + PIN_NO[pm] + ', machine.Pin.' + (f('PINMODE') === 'OUTPUT' ? 'OUT' : 'IN') + (f('PINMODE') === 'INPUT_PULLUP' ? ', machine.Pin.PULL_UP' : '') + ')\n';
        return '_pin(' + pm + ', ' + mode + ')\n';
      }
      case 'esp32_digitalWrite': {
        this.use('pins');
        this.use('machine');
        var dp = pinNo(f('PIN')), val = f('VALUE') === 'HIGH' ? 1 : 0;
        if (typeof dp === 'string') return 'machine.Pin(' + PIN_NO[dp] + ', machine.Pin.OUT).value(' + val + ')\n';
        return '_pin(' + dp + ', PinMode.OUT).write_digital(' + val + ')\n';
      }
      case 'esp32_analogWrite': {
        this.use('pins');
        this.use('machine');
        var ap = pinNo(f('PIN'));
        var av = 'int(' + this.n(block, 'VALUE') + ')';
        if (typeof ap === 'string') return 'machine.PWM(machine.Pin(' + PIN_NO[ap] + ')).duty(' + av + ')\n';
        return '_pin(' + ap + ', PinMode.PWM).write_analog(' + av + ')\n';
      }
      // Wi-Fi
      case 'wifiConnectByPwd': this.use('wifi'); return '_wifi_connect(' + this.v(block, 'ACCOUNT') + ', ' + this.v(block, 'PASSWORD') + ')\n';
      case 'wifiDisConnected': this.use('wifi'); return '_wlan.disconnect()\n';
      case 'wifiSetSoftAP': this.use('wifi'); return '_wifi_ap(' + this.v(block, 'ACCOUNT2') + ', ' + this.v(block, 'PASSWORD2') + ')\n';
      // MQTT
      case 'mqtt_initial': {
        this.use('mqtt');
        var st = block.getInputTargetBlock('SETTING');
        var json = st ? st.getFieldValue('MQTT') : '{}';
        return '_mq.init(' + pyStr(json) + ')\n';
      }
      case 'mqtt_mqttConnect': this.use('mqtt'); return '_mq.connect()\n';
      case 'mqtt_mqttDisconnect': this.use('mqtt'); return '_mq.disconnect()\n';
      case 'mqtt_mqttSend': case 'mqtt_mqttSend_save':
        this.use('mqtt');
        return '_mq.publish(' + pyStr(f('TOPIC')) + ', ' + this.v(block, 'MSG') + ')\n';
      case 'mqtt_mqttSendToTopic': case 'mqtt_mqttSendToTopic_save':
        this.use('mqtt');
        return '_mq.publish(' + this.s(block, 'TOPIC') + ', ' + this.v(block, 'MSG') + ')\n';
      case 'mqtt_subscribe': this.use('mqtt'); return '_mq.subscribe(' + this.s(block, 'TOPIC') + ')\n';
    }
    // 不能单独放的（表达式积木放在了语句位置）就当表达式算一下
    if (block.outputConnection) return '';
    this.warnings.push('不认识的积木：' + t);
    return '';
  };
  Gen.prototype.gpio = function (p) {
    if (typeof p === 'string') return String(PIN_NO[p]);
    // MPython 的 P 引脚 → GPIO（掌控板 2.x 引脚表）
    var MAP = { 0: 33, 1: 32, 2: 35, 3: 34, 4: 39, 5: 0, 6: 16, 7: 17, 8: 26, 9: 25, 10: 36, 11: 2, 13: 18, 14: 19, 15: 21, 16: 5, 19: 22, 20: 23 };
    return String(MAP[p] !== undefined ? MAP[p] : p);
  };

  // ---------------- 事件积木（帽子）→ 函数 ----------------
  Gen.prototype.globalsLine = function () {
    var names = Object.keys(this.varNames).map(function (k) { return this.varNames[k]; }, this);
    return names.length ? IND + 'global ' + names.join(', ') + '\n' : '';
  };

  Gen.prototype.hat = function (block, n) {
    var s = short(block.type), f = function (k) { return block.getFieldValue(k); };
    var body = this.stack(block.getNextBlock());
    var fn = '_on_' + n;
    var reg = '';
    var params = '';
    switch (s) {
      case 'esp32_buttonPress': {
        var btn = f('REFERENCE'), evt = f('BUTTONSTATE') === 'Pressed' ? 'pressed' : 'released';
        params = '_=None';
        if (btn === 'A+B') {
          this.use('btn'); this.use('bg');
          var key = '_ab_' + n;
          reg = key + ' = [False]\n' +
            'def ' + key + '_chk():\n' + IND + 'now = _btn("AB", True)\n' + IND + 'if now != ' + key + '[0]:\n' + IND + IND + key + '[0] = now\n' +
            IND + IND + 'if now == ' + (evt === 'pressed' ? 'True' : 'False') + ':\n' + IND + IND + IND + fn + '()\n' +
            '_bg_tasks.append(' + key + '_chk)\n_bg_need()\n';
        } else {
          this.use('mpython');
          reg = 'button_' + btn.toLowerCase() + '.event_' + evt + ' = ' + fn + '\n';
        }
        break;
      }
      case 'esp32_padConnected': {
        this.use('mpython');
        params = '_=None';
        reg = 'touchpad_' + (TOUCH_NAME[f('REFERENCE')] || 'p') + '.event_' + (f('TOUCHSTATE') === 'Connected' ? 'pressed' : 'released') + ' = ' + fn + '\n';
        break;
      }
      case 'onGesture': {
        this.use('gesture'); this.use('bg');
        var gk = '_g_' + n;
        reg = gk + ' = [False]\n' +
          'def ' + gk + '_chk():\n' + IND + 'now = _gesture(' + pyStr(f('GESTURE')) + ')\n' + IND + 'if now and not ' + gk + '[0]:\n' + IND + IND + fn + '()\n' + IND + gk + '[0] = now\n' +
          '_bg_tasks.append(' + gk + '_chk)\n_bg_need()\n';
        break;
      }
      case 'whenDebugInfo':
        this.use('wifi');
        params = '_dbg_msg, _dbg_code';
        reg = '_wifi_dbg.append(' + fn + ')\n';
        break;
      case 'mqtt_whenReceivedData': case 'mqtt_whenReceivedTopicData':
        this.use('mqtt');
        params = '_mqtt_msg';
        reg = '_mq.on(' + (s === 'mqtt_whenReceivedData' ? pyStr(f('TOPIC')) : this.s(block, 'TOPIC')) + ', None, ' + fn + ')\n';
        break;
      case 'mqtt_acceptance_meaage': case 'mqtt_acceptance_topic_meaage':
        this.use('mqtt');
        params = '_mqtt_msg=None';
        reg = '_mq.on(' + (s === 'mqtt_acceptance_meaage' ? pyStr(f('TOPIC')) : this.s(block, 'TOPIC')) + ', ' + this.v(block, 'MSG') + ', ' + fn + ')\n';
        break;
      default:
        return null;
    }
    return { def: 'def ' + fn + '(' + params + '):\n' + '@@GLOBALS@@' + indent(body || 'pass\n'), reg: reg };
  };

  // ---------------- 函数（自定义模块）----------------
  Gen.prototype.collectProcs = function (tops) {
    var self = this, n = 0;
    tops.forEach(function (b) {
      if (b.type !== 'procedures_definition') return;
      var proto = b.getInputTargetBlock('custom_block');
      if (!proto) return;
      n++;
      var names = (proto.displayNames_ || proto.argumentNames_ || []).slice();
      self.procs[proto.getProcCode()] = { name: 'mind_f_' + n, def: b, proto: proto, args: names };
    });
  };
  Gen.prototype.procDef = function (p) {
    var self = this;
    var argMap = {};
    var params = p.args.map(function (nm, i) { argMap[nm] = 'a' + (i + 1); return 'a' + (i + 1); });
    this.argNames = argMap;
    var body = this.stack(p.def.getNextBlock());
    this.argNames = null;
    return 'def ' + p.name + '(' + params.join(', ') + '):  # ' + p.proto.getProcCode().replace(/%[sbn]/g, '()') + '\n@@GLOBALS@@' + indent(body || 'pass\n');
  };

  // ---------------- 整个程序 ----------------
  window.MPCodegen = {
    generate: function (ws) {
      var g = new Gen(ws);
      var tops = ws.getTopBlocks(true);
      g.collectProcs(tops);
      var procDefs = [], hats = [], main = '', hasMain = false, n = 0;
      Object.keys(g.procs).forEach(function (k) { procDefs.push(g.procDef(g.procs[k])); });
      tops.forEach(function (b) {
        if (b.type === H + 'start') {
          if (hasMain) return;  // 只认第一个主程序（Mind+ 也只能有一个）
          hasMain = true;
          main = g.stack(b.getNextBlock());
        } else if (b.previousConnection === null && b.outputConnection === null && b.type !== 'procedures_definition') {
          var h = g.hat(b, ++n);
          if (h) hats.push(h);
        }
      });
      // 变量初值：数字 0、字符串 ''、列表 []（用到的才写）
      var allVars = ws.getAllVariables();
      var varLines = [];
      allVars.forEach(function (v) {
        var kind = v.type === 'list' ? 'list' : v.type === 'string' ? 'string' : 'number';
        if (v.type === 'broadcast_msg') return;
        var nm = g.varName(v.getId(), kind);
        varLines.push(nm + ' = ' + (kind === 'list' ? '[]' : kind === 'string' ? "''" : '0'));
      });
      var globals = g.globalsLine();
      var head = [];
      ORDER.forEach(function (k) { if (g.use_[k]) head.push(HELPERS[k]); });
      var parts = [head.join('\n')];
      if (varLines.length) parts.push(varLines.join('\n'));
      procDefs.forEach(function (d) { parts.push(d.replace('@@GLOBALS@@', globals)); });
      hats.forEach(function (h) { parts.push(h.def.replace('@@GLOBALS@@', globals) + h.reg); });
      if (main) parts.push(main.trim());
      // 生成的代码里不留注释（只删整行注释，字符串里的内容不受影响）
      var code = parts.filter(function (x) { return x; }).join('\n\n').split('\n')
        .filter(function (l) { return !/^\s*#/.test(l); }).join('\n');
      return { code: code.replace(/\n{3,}/g, '\n\n').replace(/^\n+/, '') + '\n', warnings: g.warnings };
    }
  };
})();
