// 自由农场关 · 拓展任务：3D 智能农场——掌控板变成温室大棚的遥控器。
// 农场本身是另一个项目（three.js 温室大棚，原来靠 bridge.exe 读串口、走 WebSocket 喂数据），
// 原样拷进 static/farm/，嵌在左边的场景里。数据改由这个页面转：
// 板子每 0.1 秒打印一行 @@FARM:{...}，学生页从 USB 串口读到后 postMessage 给农场——
// 学生机上不用再开 bridge.exe，串口也不会被两个程序抢。
// 前四关是“挑一个传感器、做一件事”，这一关反过来：所有传感器一起上，看一整套系统怎么配合。
// 积木只要一块（放进循环），重点在刷进去之后玩：选设备、开关、自动模式、救活枯萎的植物。
(function () {
  "use strict";
  var lock = MplKit.lock;

  MplKit.register({
    id: "demo5",
    color: "#16A34A", icon: "🌱",   // 关卡条、任务卡片、“下一关”按钮都用这个颜色
    short: "3D 农场",
    extra: true,                     // 拓展任务：关卡条上标“拓展”
    starterVersion: "1",
    title: "智能农场（拓展）",
    brief: "掌控板变成温室大棚的遥控器：光线、按键、触摸键、倾斜、摇晃、指南针、声音全部实时发给 3D 农场。" +
           "大棚里的植物靠灌溉阀浇水——忘了浇，它们会缺水、发黄、最后枯萎。",
    hint: "去“网页”分类找“把全部传感器数据发给 3D 农场”，放进“一直重复”里。刷入后点场景右上角的 ⛶ 全屏玩。",
    sends: [],
    need: ["farm"],
    netBlocks: ["mpl_farm_send"],
    doneHint: "放对了！点“刷入”，然后：摸 P/Y/T/H 选设备、按 A 开关；摸 H 选到灌溉阀给植物浇水；按 B 切自动模式，挡住光线试试。",

    scene:
      '<iframe class="farm" src="farm/index.html" title="3D 智能农场" allow="microphone; fullscreen"></iframe>' +
      '<button class="farm-full" type="button" title="全屏打开 3D 农场（Esc 退出）">⛶ 全屏</button>',
    css:
      '.scene.s-demo5{height:380px;background:#0d0f14}' +
      '.s-demo5 .farm{position:absolute;inset:0;width:100%;height:100%;border:0;display:block}' +
      '.s-demo5 .farm-full{position:absolute;right:10px;bottom:10px;z-index:10;background:#16A34A;border-color:#16A34A;color:#fff;' +
      '  font-weight:700;padding:6px 12px;box-shadow:0 3px 10px rgba(0,0,0,.35)}' +
      '.s-demo5 .farm-full:hover{background:#12873D;border-color:#12873D}' +
      '.s-demo5 .caption{left:auto;right:96px;bottom:12px;font-size:12px}' +
      '.s-demo5:fullscreen{height:100%;border-radius:0}',

    starter: function () {
      return {
        blocks: {
          languageVersion: 0,
          blocks: [{
            type: "mpl_main", x: 24, y: 24, deletable: false,
            inputs: { DO: { block: lock({
              type: "mpl_forever",
              inputs: { DO: { block: lock({ type: "mpl_sleep", fields: { SEC: 0.1 } }) } }
            }) } }
          }]
        }
      };
    },

    check: function (workspace) {
      var b = workspace.getAllBlocks(false).filter(function (x) { return x.type === "mpl_farm_send"; });
      var good = b.filter(function (x) { return MplKit.inMain(x) && MplKit.inForever(x); })[0];
      var outside = !good && b.some(function (x) { return MplKit.inMain(x); });
      return [
        { label: "“一直重复”里 → 把全部传感器数据发给 3D 农场", ok: !!good,
          note: outside ? "要放进“一直重复”里面，不然只发一次，农场就不动了" :
                (b.length ? "这块还没接到程序里" : "还没有") }
      ];
    },

    // 场景第一次画出来时（type 为 null）接上全屏按钮；之后只更新说明文字
    onEvent: function (type, scene) {
      if (!scene._d5) {
        scene._d5 = true;
        var btn = scene.querySelector(".farm-full");
        btn.addEventListener("click", function () {
          if (document.fullscreenElement) document.exitFullscreen();
          else if (scene.requestFullscreen) scene.requestFullscreen().catch(function () {});
        });
      }
      return type === "farm" ? "3D 农场：数据已接通" : "3D 农场：等板子发数据";
    }
  });
})();
