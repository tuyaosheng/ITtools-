# ITtools掌控板助手（学生机）

平台图形化编程“Mind+ 方式”上传用：在学生机本机用 Mind+ 自带的 arduino-cli 编译、写进掌控板。只用 Python 标准库。

打包（PyInstaller 要在和源码同一个盘的目录里跑，否则报 path is on mount）：

```
copy mpagent.py <临时目录>\
cd <临时目录>
python -m PyInstaller --onefile --noconsole --name "ITtools掌控板助手" mpagent.py --clean --noconfirm
```

成品 `dist\ITtools掌控板助手.exe` 放进交付包 `学生机脚本\`。

- 后台运行：勾“开机在后台自动运行”会在 `HKCU\...\Run` 写入 `"<exe>" --background`，开机后只显示托盘图标（ctypes 调 Shell_NotifyIcon，无第三方依赖）；关窗口=藏到托盘，托盘右键“退出助手”才退出；已运行时再启动会通过隐藏窗口类 `ITtoolsMpAgentTray` 把窗口叫出来。旧版写的不带 `--background` 的自启项启动时会自动改写。
- 配置：`%LOCALAPPDATA%\ITtools掌控板助手\config.json`（`server` = 教师机地址:端口）；也可 `ITtools掌控板助手.exe --server 192.168.1.10:8300`。
- 找 Mind+：卸载信息里登记的安装目录和常见位置下的 `apps\deps\mind-link\tool\arduino-cli.exe`；编译包在 `%LOCALAPPDATA%\mind+\Arduino\packages\mindplus\hardware\esp32`。
- 找掌控板：注册表里 VID 为 1A86/10C4/303A/0403 的 USB 串口。
- 平台接口：`/api/mpagent/agent/poll`（长轮询领任务）、`/api/mpagent/agent/jobs/{id}`（回报进度）、`/api/mpagent/agent/mindplus/{name}`（交 Mind+ 的 main.js，平台只收登记的 SHA-256）。
