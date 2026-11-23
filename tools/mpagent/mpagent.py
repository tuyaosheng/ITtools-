# -*- coding: utf-8 -*-
"""
掌控板助手：放在学生机上运行。平台的图形化编程点“上传”时，像 Mind+ 上传模式一样，
用这台电脑上 Mind+ 自带的编译工具（arduino-cli）把程序编译好、写进掌控板。

- 它主动连教师机上的平台领任务（浏览器不用做任何设置），平台按电脑 IP 把这台电脑上学生发的任务交给它；
- 只读 Mind+ 的文件，不改 Mind+ 的设置；编译用的临时文件放在 %LOCALAPPDATA%\\ITtools掌控板助手；
- 只用 Python 自带的模块（打包成一个 exe）。
"""
import json
import os
import queue
import re
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import winreg

VERSION = '1.0.0'
APP = 'ITtools掌控板助手'
FQBN = 'mindplus:esp32:mpython:FlashMode=dio,FlashFreq=80,UploadSpeed=1500000,DebugLevel=none'
MINDPLUS_FILES = {
    'dev-DFRobot-handpyEsp32@0.0.6': ('devices', 'dev-DFRobot-handpyEsp32@0.0.6'),
    'ext-DFRobot-MQTT@0.0.4': ('extensions', 'ext-DFRobot-MQTT@0.0.4'),
    'ext-DFRobot-WIFI@0.0.5': ('extensions', 'ext-DFRobot-WIFI@0.0.5'),
}
# 掌控板常见的 USB 串口芯片：CH340/CH9102、CP210x、乐鑫原生 USB（掌控板 3.0）、FTDI
BOARD_VIDS = ('1A86', '10C4', '303A', '0403')
NO_WINDOW = 0x08000000

LOCAL = os.environ.get('LOCALAPPDATA') or os.path.expanduser('~')
WORK = os.path.join(LOCAL, APP)
CONFIG = os.path.join(WORK, 'config.json')


# ---------------- 配置 ----------------
def load_config():
    try:
        with open(CONFIG, encoding='utf-8-sig') as f:
            return json.load(f)
    except Exception:
        return {}


def save_config(cfg):
    os.makedirs(WORK, exist_ok=True)
    with open(CONFIG, 'w', encoding='utf-8') as f:
        json.dump(cfg, f, ensure_ascii=False, indent=1)


def norm_server(s):
    s = (s or '').strip()
    s = re.sub(r'^https?://', '', s).split('/')[0]
    return s


def set_autostart(on):
    key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, r'Software\Microsoft\Windows\CurrentVersion\Run', 0, winreg.KEY_SET_VALUE)
    try:
        if on:
            exe = sys.executable if getattr(sys, 'frozen', False) else '"%s" "%s"' % (sys.executable, os.path.abspath(__file__))
            winreg.SetValueEx(key, APP, 0, winreg.REG_SZ, '"%s"' % exe if getattr(sys, 'frozen', False) else exe)
        else:
            try:
                winreg.DeleteValue(key, APP)
            except FileNotFoundError:
                pass
    finally:
        winreg.CloseKey(key)


def get_autostart():
    try:
        key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, r'Software\Microsoft\Windows\CurrentVersion\Run')
        try:
            winreg.QueryValueEx(key, APP)
            return True
        finally:
            winreg.CloseKey(key)
    except OSError:
        return False


# ---------------- 找 Mind+ ----------------
def mindplus_install_dirs():
    """Mind+ 的安装目录候选：卸载信息里登记的 + 常见位置。"""
    dirs = []
    for root in (winreg.HKEY_LOCAL_MACHINE, winreg.HKEY_CURRENT_USER):
        for path in (r'Software\Microsoft\Windows\CurrentVersion\Uninstall', r'Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall'):
            try:
                k = winreg.OpenKey(root, path)
            except OSError:
                continue
            try:
                for i in range(winreg.QueryInfoKey(k)[0]):
                    try:
                        sub = winreg.OpenKey(k, winreg.EnumKey(k, i))
                        name = winreg.QueryValueEx(sub, 'DisplayName')[0]
                        if 'mind+' in str(name).lower() or 'mindplus' in str(name).lower():
                            for v in ('InstallLocation', 'DisplayIcon', 'UninstallString'):
                                try:
                                    val = str(winreg.QueryValueEx(sub, v)[0]).strip('"')
                                    d = val if os.path.isdir(val) else os.path.dirname(val.split('"')[0])
                                    if d:
                                        dirs.append(d)
                                except OSError:
                                    pass
                    except OSError:
                        pass
            finally:
                winreg.CloseKey(k)
    for drive in 'CDEFG':
        for sub in ('Mind+', 'mind+', r'Program Files\Mind+', r'Program Files (x86)\Mind+', r'Mind+\Mind+'):
            dirs.append('%s:\\%s' % (drive, sub))
    seen, out = set(), []
    for d in dirs:
        d = os.path.normpath(d)
        if d.lower() not in seen:
            seen.add(d.lower())
            out.append(d)
    return out


CLI_REL = os.path.join('apps', 'deps', 'mind-link', 'tool', 'arduino-cli.exe')


def cli_in(folder):
    """folder 是 Mind+ 安装目录（或里面的 Mind+.exe 所在目录）时返回 arduino-cli 路径。"""
    if not folder:
        return None
    p = os.path.join(folder, CLI_REL)
    return p if os.path.isfile(p) else None


def slow_search_dirs():
    """注册表里没登记（直接复制过来的 Mind+）时再找：正在运行的 Mind+、桌面/开始菜单快捷方式、各盘根目录和下一层。"""
    dirs = []
    ps = ("$o=@(); Get-Process -Name 'Mind+' -EA 0 | % { $o += (Split-Path $_.Path) };"
          "$sh=New-Object -ComObject WScript.Shell;"
          "foreach($d in @([Environment]::GetFolderPath('Desktop'),[Environment]::GetFolderPath('CommonDesktopDirectory'),"
          "[Environment]::GetFolderPath('StartMenu'),[Environment]::GetFolderPath('CommonStartMenu'))){"
          "Get-ChildItem $d -Recurse -Filter *.lnk -EA 0 | ? { $_.Name -like '*mind*' } | % { $t=$sh.CreateShortcut($_.FullName).TargetPath; if($t){ $o += (Split-Path $t) } } };"
          "$o -join \"`n\"")
    try:
        out = subprocess.run(['powershell', '-NoProfile', '-Command', ps], capture_output=True, timeout=20, creationflags=NO_WINDOW)
        dirs += [l.strip() for l in out.stdout.decode('utf-8', 'replace').splitlines() if l.strip()]
    except Exception:
        pass
    import string
    for drive in string.ascii_uppercase[2:]:
        root = '%s:\\' % drive
        if not os.path.isdir(root):
            continue
        try:
            tops = [os.path.join(root, n) for n in os.listdir(root)]
        except OSError:
            continue
        for t in tops:
            if not os.path.isdir(t):
                continue
            if 'mind' in os.path.basename(t).lower():
                dirs.append(t)
            # 再看一层：D:\软件\Mind+、C:\Program Files\Mind+ 这样的
            try:
                for n in os.listdir(t):
                    if 'mind' in n.lower():
                        dirs.append(os.path.join(t, n))
            except OSError:
                pass
    return dirs


def find_toolchain(cfg, slow=False):
    """返回 (arduino-cli 路径, Mind+ 的 Arduino 数据目录, 说明)。找到的位置记进配置，下次直接用。"""
    cli = None
    if cfg.get('arduinoCli') and os.path.isfile(cfg['arduinoCli']):
        cli = cfg['arduinoCli']
    if not cli:
        for d in mindplus_install_dirs():
            cli = cli_in(d)
            if cli:
                break
    if not cli and slow:
        for d in slow_search_dirs():
            cli = cli_in(d) or cli_in(os.path.dirname(d))
            if cli:
                break
    if cli and cfg.get('arduinoCli') != cli:
        cfg['arduinoCli'] = cli
        try:
            save_config(cfg)
        except Exception:
            pass
    data = cfg.get('arduinoData') or os.path.join(LOCAL, 'mind+', 'Arduino')
    board = os.path.join(data, 'packages', 'mindplus', 'hardware', 'esp32')
    if not cli:
        return None, data, '没找到 Mind+：点上面的“选择 Mind+ 位置”，选 Mind+ 的安装文件夹（里面有 Mind+.exe）'
    if not os.path.isdir(board):
        return cli, None, '没找到 Mind+ 的掌控板编译包（%s）' % board
    return cli, data, 'Mind+ 编译工具正常'


def mindplus_file(name):
    kind, folder = MINDPLUS_FILES[name]
    return os.path.join(LOCAL, 'mind+', 'download', 'upload', kind, folder, 'main.js')


# ---------------- 找掌控板 ----------------
def board_ports():
    """当前插着的掌控板串口，例如 ['COM9']（看 USB 设备登记的端口名，再和正在用的串口对一下）。"""
    active = set()
    try:
        k = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r'HARDWARE\DEVICEMAP\SERIALCOMM')
        try:
            for i in range(winreg.QueryInfoKey(k)[1]):
                active.add(str(winreg.EnumValue(k, i)[1]).upper())
        finally:
            winreg.CloseKey(k)
    except OSError:
        return []
    found = []
    for enum_root in (r'SYSTEM\CurrentControlSet\Enum\USB', r'SYSTEM\CurrentControlSet\Enum\USBSER000', r'SYSTEM\CurrentControlSet\Enum\FTDIBUS'):
        try:
            usb = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, enum_root)
        except OSError:
            continue
        try:
            for i in range(winreg.QueryInfoKey(usb)[0]):
                dev = winreg.EnumKey(usb, i)
                if not any(('VID_' + v) in dev.upper() for v in BOARD_VIDS):
                    continue
                try:
                    dk = winreg.OpenKey(usb, dev)
                except OSError:
                    continue
                try:
                    for j in range(winreg.QueryInfoKey(dk)[0]):
                        try:
                            pk = winreg.OpenKey(dk, winreg.EnumKey(dk, j) + r'\Device Parameters')
                            port = str(winreg.QueryValueEx(pk, 'PortName')[0]).upper()
                            winreg.CloseKey(pk)
                            if port in active and port not in found:
                                found.append(port)
                        except OSError:
                            pass
                finally:
                    winreg.CloseKey(dk)
        finally:
            winreg.CloseKey(usb)
    return found


# ---------------- 和平台通信 ----------------
class Server:
    def __init__(self, addr):
        self.base = 'http://' + addr

    def _req(self, method, path, data=None, ctype='application/json', timeout=10):
        body = None
        if data is not None:
            body = data if isinstance(data, bytes) else json.dumps(data, ensure_ascii=False).encode('utf-8')
        r = urllib.request.Request(self.base + path, data=body, method=method, headers={'Content-Type': ctype})
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            txt = resp.read().decode('utf-8')
        j = json.loads(txt) if txt else {}
        if isinstance(j, dict) and j.get('code') not in (None, 0, 200):
            raise RuntimeError(j.get('message') or txt)
        return j.get('data') if isinstance(j, dict) and 'data' in j else j

    def poll(self, mindplus, port, note):
        q = urllib.parse.urlencode({'version': VERSION, 'mindplus': 'true' if mindplus else 'false', 'port': port or '', 'note': note or '', 'wait': 20})
        return self._req('GET', '/api/mpagent/agent/poll?' + q, timeout=35) or {}

    def progress(self, job, status=None, log=None, message=None):
        self._req('POST', '/api/mpagent/agent/jobs/' + job, {'status': status, 'log': log, 'message': message})

    def put_file(self, name, data):
        self._req('PUT', '/api/mpagent/agent/mindplus/' + urllib.parse.quote(name), data, ctype='application/octet-stream', timeout=30)


# ---------------- 编译、上传 ----------------
class Worker:
    def __init__(self, ui):
        self.ui = ui
        self.stop = False
        self.cfg = load_config()

    def toolchain(self):
        # 第一次（和用户手动选过之后）多花几秒做彻底的查找；以后用记下的位置
        r = find_toolchain(self.cfg, slow=not getattr(self, 'searched', False))
        self.searched = True
        return r

    def cli_yaml(self, data):
        os.makedirs(WORK, exist_ok=True)
        p = os.path.join(WORK, 'arduino-cli.yaml')
        txt = ('directories:\n    data: %s\n    downloads: %s\n    user: %s\n'
               'build_cache:\n    compilations_before_purge: 10\n    ttl: 720h0m0s\n'
               'updater:\n    enable_notification: false\n') % (data, os.path.join(data, 'staging'), os.path.join(data, 'user'))
        with open(p, 'w', encoding='utf-8') as f:
            f.write(txt)
        return p

    def run(self, srv, job_id, args, stage):
        """运行 arduino-cli，日志每半秒送回平台一次。"""
        proc = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, creationflags=NO_WINDOW, cwd=WORK)
        buf, last = [], time.time()
        q = queue.Queue()

        def reader():
            for line in iter(proc.stdout.readline, b''):
                q.put(line.decode('utf-8', 'replace'))
            q.put(None)
        threading.Thread(target=reader, daemon=True).start()
        done = False
        while not done:
            try:
                line = q.get(timeout=0.5)
                if line is None:
                    done = True
                else:
                    buf.append(line)
                    self.ui.log(line.rstrip())
            except queue.Empty:
                pass
            if buf and (time.time() - last > 0.5 or done):
                try:
                    srv.progress(job_id, stage, ''.join(buf))
                except Exception:
                    pass
                buf, last = [], time.time()
        return proc.wait()

    def do_job(self, srv, job):
        jid, code = job['id'], job['code']
        cli, data, why = self.toolchain()
        if not cli or not data:
            srv.progress(jid, 'FAILED', message=why)
            return
        ports = board_ports()
        if not ports:
            srv.progress(jid, 'FAILED', message='没找到掌控板：请插好数据线（换个 USB 口试试），再点上传')
            return
        port = ports[0]
        sk = os.path.join(WORK, 'sketch', 'sketch')
        os.makedirs(sk, exist_ok=True)
        with open(os.path.join(sk, 'sketch.ino'), 'w', encoding='utf-8') as f:
            f.write(code)
        build = os.path.join(WORK, 'build')
        yaml = self.cli_yaml(data)
        self.ui.state('编译中…')
        srv.progress(jid, 'COMPILING', message='正在编译（第一次要半分钟左右）', log='[编译] %s\n' % sk)
        t0 = time.time()
        rc = self.run(srv, jid, [cli, '--config-file', yaml, 'compile', '-b', FQBN, '--build-path', build, sk], 'COMPILING')
        if rc != 0:
            srv.progress(jid, 'FAILED', message='编译出错，看下面的输出')
            self.ui.state('编译出错')
            return
        self.ui.state('上传到 %s…' % port)
        srv.progress(jid, 'UPLOADING', message='编译好了（%d 秒），正在写进掌控板（%s）' % (time.time() - t0, port), log='\n[上传] %s\n' % port)
        rc = self.run(srv, jid, [cli, '--config-file', yaml, 'upload', '-b', FQBN, '-p', port, '--input-dir', build], 'UPLOADING')
        if rc != 0:
            srv.progress(jid, 'FAILED', message='写进掌控板失败：串口可能被网页或 Mind+ 占着（网页上点“断开”），或者按一下板子背面的复位键再试')
            self.ui.state('上传失败')
            return
        srv.progress(jid, 'DONE', message='上传完成（共 %d 秒），程序已经在掌控板上运行' % (time.time() - t0))
        self.ui.state('上传完成')

    def loop(self):
        while not self.stop:
            addr = norm_server(self.cfg.get('server'))
            if not addr:
                self.ui.server_state('还没有填教师机地址')
                time.sleep(1)
                self.cfg = load_config()
                continue
            srv = Server(addr)
            cli, data, why = self.toolchain()
            ports = board_ports()
            self.ui.env_state(why, ports)
            try:
                r = srv.poll(bool(cli and data), ','.join(ports), why)
                self.ui.server_state('已连上教师机 %s' % addr)
                for name in (r.get('need') or []):
                    p = mindplus_file(name) if name in MINDPLUS_FILES else None
                    if p and os.path.isfile(p):
                        try:
                            with open(p, 'rb') as f:
                                srv.put_file(name, f.read())
                            self.ui.log('已把 Mind+ 的 %s 交给平台' % name)
                        except Exception as e:
                            self.ui.log('交 %s 没成功：%s' % (name, e))
                job = r.get('job')
                if job:
                    self.ui.log('收到上传任务')
                    try:
                        self.do_job(srv, job)
                    except Exception as e:
                        try:
                            srv.progress(job['id'], 'FAILED', message='助手出错：%s' % e)
                        except Exception:
                            pass
                        self.ui.log('出错：%s' % e)
            except urllib.error.HTTPError as e:
                self.ui.server_state('教师机 %s 返回 %s（平台版本太旧？）' % (addr, e.code))
                time.sleep(5)
            except Exception as e:
                self.ui.server_state('连不上教师机 %s：%s' % (addr, str(e)[:60]))
                time.sleep(5)


# ---------------- 窗口 ----------------
class UI:
    def __init__(self):
        import tkinter as tk
        from tkinter import scrolledtext
        self.tk = tk
        self.root = tk.Tk()
        self.root.title('%s %s' % (APP, VERSION))
        self.root.geometry('560x400')
        self.q = queue.Queue()
        f = tk.Frame(self.root, padx=10, pady=8)
        f.pack(fill='both', expand=True)
        self.v_server = tk.StringVar(value='正在连接…')
        self.v_env = tk.StringVar(value='')
        self.v_state = tk.StringVar(value='等待上传')
        tk.Label(f, text='平台的图形化编程点“上传”时，用这台电脑上的 Mind+ 编译并写进掌控板。窗口可以最小化，不要关。',
                 wraplength=530, justify='left', fg='#555').pack(anchor='w')
        row = tk.Frame(f)
        row.pack(fill='x', pady=(6, 0))
        tk.Label(row, text='教师机地址').pack(side='left')
        self.e_server = tk.Entry(row, width=24)
        self.e_server.pack(side='left', padx=6)
        tk.Button(row, text='保存', command=self.save_server).pack(side='left')
        self.v_auto = tk.BooleanVar(value=get_autostart())
        tk.Checkbutton(row, text='开机自动打开', variable=self.v_auto, command=self.toggle_auto).pack(side='left', padx=10)
        tk.Button(row, text='选择 Mind+ 位置', command=self.pick_mindplus).pack(side='left')
        for v in (self.v_server, self.v_env, self.v_state):
            tk.Label(f, textvariable=v, anchor='w', justify='left').pack(fill='x', pady=1)
        self.txt = scrolledtext.ScrolledText(f, height=12, font=('Consolas', 9))
        self.txt.pack(fill='both', expand=True, pady=(6, 0))
        cfg = load_config()
        self.e_server.insert(0, cfg.get('server', ''))
        self.root.after(200, self.pump)

    def save_server(self):
        cfg = load_config()
        cfg['server'] = norm_server(self.e_server.get())
        save_config(cfg)
        self.worker.cfg = cfg
        self.log('已保存教师机地址：%s' % cfg['server'])

    def pick_mindplus(self):
        from tkinter import filedialog, messagebox
        d = filedialog.askdirectory(title='选择 Mind+ 的安装文件夹（里面有 Mind+.exe）')
        if not d:
            return
        d = os.path.normpath(d)
        cli = cli_in(d) or cli_in(os.path.dirname(d))
        if not cli:
            messagebox.showwarning(APP, '这个文件夹里没找到 Mind+ 的编译工具：\n' + os.path.join(d, CLI_REL))
            return
        cfg = load_config()
        cfg['arduinoCli'] = cli
        save_config(cfg)
        self.worker.cfg = cfg
        self.log('已记下 Mind+ 的位置：%s' % cli)

    def toggle_auto(self):
        try:
            set_autostart(self.v_auto.get())
        except Exception as e:
            self.log('设置开机自动打开失败：%s' % e)

    # 线程安全：别的线程只往队列里放
    def log(self, s):
        self.q.put(('log', s))

    def state(self, s):
        self.q.put(('state', s))

    def server_state(self, s):
        self.q.put(('server', s))

    def env_state(self, why, ports):
        self.q.put(('env', '%s；掌控板：%s' % (why, '、'.join(ports) if ports else '没插')))

    def pump(self):
        try:
            while True:
                k, s = self.q.get_nowait()
                if k == 'log':
                    self.txt.insert('end', time.strftime('%H:%M:%S ') + s + '\n')
                    if int(self.txt.index('end-1c').split('.')[0]) > 2000:
                        self.txt.delete('1.0', '500.0')
                    self.txt.see('end')
                elif k == 'state':
                    self.v_state.set(s)
                elif k == 'server':
                    self.v_server.set(s)
                elif k == 'env':
                    self.v_env.set(s)
        except queue.Empty:
            pass
        self.root.after(200, self.pump)


def single_instance():
    """同一台电脑只开一个助手。"""
    import ctypes
    ctypes.windll.kernel32.CreateMutexW(None, False, 'Global\\ITtoolsMpAgent')
    return ctypes.windll.kernel32.GetLastError() != 183


def main():
    args = sys.argv[1:]
    if '--server' in args:
        i = args.index('--server')
        if i + 1 < len(args):
            cfg = load_config()
            cfg['server'] = norm_server(args[i + 1])
            save_config(cfg)
    if not single_instance():
        return
    ui = UI()
    w = Worker(ui)
    ui.worker = w
    threading.Thread(target=w.loop, daemon=True).start()
    ui.root.mainloop()
    w.stop = True


if __name__ == '__main__':
    main()
