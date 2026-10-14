掌控板实验室（mpython-lite）前端，原样取自
https://gitee.com/jarvan-c/information_teaching_system  分支 feat/course-flow-and-attendance  提交 afdb833
（课件/掌控板公开课/mpython-lite/static）。

ITtools 的改动只有：
  - 新增 mpl/bridge.js：把原来访问 server.py 的接口（/api/code /event /stream /api/state /api/starter
    /api/config /api/settings）转到 ITtools 后端 /api/{student|teacher}/mpy/...，并带上登录会话与 CSRF 头；
  - index.html / teacher.html 在最前面引入 bridge.js；teacher.html 支持按人显示名字。
原 server.py 的功能由 interact-core 的 MpyService / MpyController 实现。

许可：串口协议部分（mpl/serial.js）参照 BIPES（GPL-3.0，https://github.com/BIPES/BIPES）。
教学自用无限制；对外分发需附源码并沿用 GPL-3.0。3D 农场模型为 Kenney（CC0）。
