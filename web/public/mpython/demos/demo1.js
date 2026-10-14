// 第 1 关 · 智能小夜灯——挡住光线传感器，网页里的台灯亮起来。
// 预设好的积木骨架整体锁住（拖不动、删不掉），只留“如果”和“否则”两个空槽让学生补：
// 第一次接触掌控板，要练的是“条件成立时做什么、不成立时做什么”，
// 不是循环怎么写——那些先替他们拼好。
(function () {
  "use strict";
  var lock = MplKit.lock;

  MplKit.register({
    id: "demo1",
    color: "#F59E0B", icon: "💡",   // 关卡条、任务卡片、“下一关”按钮都用这个颜色
    short: "小夜灯",
    // 改了初始积木就改这个号：学生本机存的旧积木会被换成新版
    starterVersion: "3",
    title: "智能小夜灯",
    brief: "用手挡住掌控板上的光线传感器，让左边房间里的台灯亮起来；手移开，台灯熄灭。",
    hint: "右边的程序已经帮你写好了一大半，只差两块积木。去“网页”分类里找。",
    sends: ["dark", "light"],
    need: ["dark", "light"],
    doneHint: "两块都放对了！点上面的“刷入”，然后用手挡住光线传感器、再移开试试。",

    // 一间孩子的卧室：窗外星空、窗帘、挂画、书架、书桌上的书和绿植、地毯上睡着的猫。
    // 东西都画成彩色的，再盖一层深蓝“夜色”：没开灯时整间屋子沉在暗里，
    // 台灯一亮夜色褪掉、屋里泛起暖光——学生一眼看出“灯真的亮了”，比只换一个灯泡颜色明显得多。
    // 台灯本身在夜色上面一层，不开灯时也看得见它在哪儿。
    scene:
      '<div class="wall"></div><div class="skirt"></div><div class="floor"></div>' +
      '<div class="win"><div class="sky"><i class="moon"></i></div><i class="cl"></i><i class="cr"></i></div>' +
      '<div class="pic"><i></i></div>' +
      '<div class="shelf"><i style="height:30px;background:#E5484D"></i><i style="height:24px;background:#3B82F6"></i>' +
      '<i style="height:34px;background:#F59E0B"></i><i style="height:22px;background:#10B981;transform:rotate(12deg)"></i></div>' +
      '<div class="rug"></div>' +
      '<div class="cat"><i class="body"></i><i class="head"></i><i class="tail"></i></div>' +
      '<div class="desk"><i class="leg l"></i><i class="leg r"></i></div>' +
      '<div class="books"><i></i><i></i></div><div class="mug"></div>' +
      '<div class="plant"><i class="lf a"></i><i class="lf b"></i><i class="lf c"></i><i class="pot"></i></div>' +
      '<div class="night"></div><div class="warm"></div><div class="glow"></div>' +
      '<div class="lamp"><div class="shade"></div><div class="bulb"></div><div class="pole"></div><div class="base"></div></div>',
    css:
      '.scene.s-demo1{background:#F3D9B1}' +
      '.s-demo1 .wall{position:absolute;inset:0 0 46px 0;background:repeating-linear-gradient(90deg,#F6DDB8 0 18px,#F1D3A8 18px 36px)}' +
      '.s-demo1 .skirt{position:absolute;left:0;right:0;bottom:46px;height:8px;background:#E7B98A}' +
      '.s-demo1 .floor{position:absolute;left:0;right:0;bottom:0;height:46px;' +
      '  background:repeating-linear-gradient(90deg,#B97A4E 0 58px,#A96D44 58px 60px)}' +
      '.s-demo1 .win{position:absolute;left:5%;top:18px;width:100px;height:84px}' +
      '.s-demo1 .sky{position:absolute;inset:0 8px;border:5px solid #fff;border-radius:6px;' +
      '  background:radial-gradient(1.5px 1.5px at 20% 30%,#fff 99%,transparent),radial-gradient(1.5px 1.5px at 70% 20%,#fff 99%,transparent),' +
      '  radial-gradient(1px 1px at 45% 65%,#fff 99%,transparent),radial-gradient(1.5px 1.5px at 30% 80%,#FFE9A8 99%,transparent),' +
      '  linear-gradient(#1B2A5C,#3B3F8F 70%,#6B4E9B);box-shadow:inset 0 0 0 2px #E7EEF8;animation:d1twinkle 3s ease-in-out infinite}' +
      '@keyframes d1twinkle{50%{filter:brightness(1.25)}}' +
      '.s-demo1 .sky .moon{position:absolute;right:10px;top:8px;width:16px;height:16px;border-radius:50%;background:#FFF6D0;' +
      '  box-shadow:-5px 0 0 0 #2A3170 inset,0 0 10px 2px rgba(255,246,208,.6)}' +
      '.s-demo1 .cl,.s-demo1 .cr{position:absolute;top:-6px;width:22px;height:96px;border-radius:0 0 10px 10px;' +
      '  background:repeating-linear-gradient(90deg,#EF6F57 0 5px,#E05A44 5px 9px)}' +
      '.s-demo1 .cl{left:-6px}.s-demo1 .cr{right:-6px}' +
      '.s-demo1 .pic{position:absolute;left:34%;top:22px;width:64px;height:46px;border:5px solid #8C5A35;border-radius:3px;' +
      '  background:linear-gradient(#9ED8F5,#D7F0FB 60%);overflow:hidden}' +
      '.s-demo1 .pic i{position:absolute;left:-10px;right:-10px;bottom:-14px;height:30px;border-radius:50% 50% 0 0;' +
      '  background:#5DBB63;box-shadow:22px 6px 0 #3E9E4A}' +
      '.s-demo1 .pic::after{content:"";position:absolute;right:8px;top:6px;width:10px;height:10px;border-radius:50%;background:#FFC83D}' +
      '.s-demo1 .shelf{position:absolute;left:33%;top:118px;width:84px;height:6px;background:#8C5A35;border-radius:2px}' +
      '.s-demo1 .shelf i{position:absolute;bottom:6px;width:10px;border-radius:2px 2px 0 0;transform-origin:bottom left}' +
      '.s-demo1 .shelf i:nth-child(1){left:6px}.s-demo1 .shelf i:nth-child(2){left:18px}' +
      '.s-demo1 .shelf i:nth-child(3){left:30px}.s-demo1 .shelf i:nth-child(4){left:42px}' +
      '.s-demo1 .rug{position:absolute;left:12%;bottom:6px;width:150px;height:26px;border-radius:50%;' +
      '  background:repeating-radial-gradient(ellipse,#2BB3A3 0 8px,#F7C948 8px 12px,#2BB3A3 12px 20px)}' +
      '.s-demo1 .cat{position:absolute;left:calc(12% + 42px);bottom:16px;width:70px;height:36px}' +
      '.s-demo1 .cat .body{position:absolute;left:10px;bottom:0;width:52px;height:24px;border-radius:50%;background:#F29E4C}' +
      '.s-demo1 .cat .head{position:absolute;left:0;bottom:6px;width:24px;height:22px;border-radius:50%;background:#F5AE5F}' +
      '.s-demo1 .cat .head::before,.s-demo1 .cat .head::after{content:"";position:absolute;top:-6px;width:9px;height:10px;' +
      '  background:#F5AE5F;clip-path:polygon(50% 0,100% 100%,0 100%)}' +
      '.s-demo1 .cat .head::before{left:2px}.s-demo1 .cat .head::after{right:2px}' +
      '.s-demo1 .cat .tail{position:absolute;right:-2px;bottom:2px;width:22px;height:8px;border-radius:8px;background:#E08A3A;transform:rotate(-20deg)}' +
      '.s-demo1 .desk{position:absolute;right:3%;bottom:40px;width:220px;height:12px;background:#A0673F;border-radius:3px}' +
      '.s-demo1 .desk .leg{position:absolute;top:12px;width:10px;height:28px;background:#8A5634}' +
      '.s-demo1 .desk .l{left:12px}.s-demo1 .desk .r{right:12px}' +
      '.s-demo1 .books{position:absolute;right:calc(3% + 20px);bottom:52px;width:46px}' +
      '.s-demo1 .books i{display:block;height:9px;border-radius:2px;margin-top:1px;background:#3B82F6}' +
      '.s-demo1 .books i+i{background:#E5484D;width:40px;margin-left:3px}' +
      '.s-demo1 .mug{position:absolute;right:calc(3% + 74px);bottom:52px;width:16px;height:18px;border-radius:0 0 4px 4px;' +
      '  background:#fff;box-shadow:inset 0 -5px 0 #F59E0B}' +
      '.s-demo1 .plant{position:absolute;right:calc(3% + 182px);bottom:52px;width:30px;height:50px}' +
      '.s-demo1 .plant .pot{position:absolute;left:5px;bottom:0;width:20px;height:16px;background:#D9653B;' +
      '  clip-path:polygon(0 0,100% 0,85% 100%,15% 100%)}' +
      '.s-demo1 .plant .lf{position:absolute;bottom:14px;width:12px;height:28px;border-radius:50%/80% 80% 20% 20%;background:#3FAE5A}' +
      '.s-demo1 .plant .a{left:3px;transform:rotate(-28deg)}.s-demo1 .plant .b{left:9px;height:34px;background:#56C46F}' +
      '.s-demo1 .plant .c{left:15px;transform:rotate(28deg)}' +
      // 夜色：没开灯时压一层深蓝；开灯后淡掉，换成以台灯为中心的暖光
      '.s-demo1 .night{position:absolute;inset:0;z-index:5;background:rgba(12,18,48,.72);transition:opacity .6s ease}' +
      '.s-demo1 .warm{position:absolute;inset:0;z-index:5;opacity:0;transition:opacity .6s ease;pointer-events:none;' +
      '  background:radial-gradient(circle at calc(97% - 135px) 45%,rgba(255,200,110,.35),rgba(255,170,80,.08) 55%,rgba(40,20,0,.18))}' +
      '.s-demo1 .lamp{position:absolute;right:calc(3% + 100px);bottom:52px;width:70px;height:110px;z-index:7}' +
      '.s-demo1 .lamp .pole{position:absolute;left:33px;bottom:0;width:4px;height:76px;background:#556071;transition:background .4s}' +
      '.s-demo1 .lamp .base{position:absolute;left:17px;bottom:0;width:36px;height:7px;border-radius:4px;background:#556071;transition:background .4s}' +
      '.s-demo1 .lamp .shade{position:absolute;left:6px;top:4px;width:58px;height:32px;background:#586479;' +
      '  clip-path:polygon(18% 0,82% 0,100% 100%,0 100%);transition:background .4s}' +
      '.s-demo1 .lamp .bulb{position:absolute;left:27px;top:34px;width:16px;height:11px;border-radius:0 0 10px 10px;' +
      '  background:#4A5466;transition:background .3s,box-shadow .3s}' +
      '.s-demo1 .glow{position:absolute;right:calc(3% + 30px);bottom:40px;width:210px;height:130px;z-index:6;opacity:0;' +
      '  transition:opacity .4s;background:radial-gradient(ellipse at 50% 12%,rgba(255,214,120,.8),rgba(255,214,120,0) 70%);' +
      '  clip-path:polygon(40% 0,60% 0,100% 100%,0 100%)}' +
      '.s-demo1.lit .night{opacity:0}.s-demo1.lit .warm,.s-demo1.lit .glow{opacity:1}' +
      '.s-demo1.lit .lamp .shade{background:#F7B733}.s-demo1.lit .lamp .pole,.s-demo1.lit .lamp .base{background:#C8892B}' +
      '.s-demo1.lit .lamp .bulb{background:#FFF4C2;box-shadow:0 0 24px 12px rgba(255,214,120,.85)}',

    starter: function () {
      return {
        blocks: {
          languageVersion: 0,
          blocks: [{
            type: "mpl_main", x: 24, y: 24, deletable: false,
            inputs: { DO: { block: lock({
              type: "mpl_forever",
              inputs: { DO: { block: lock({
                type: "controls_if",
                extraState: { hasElse: true },
                inputs: {
                  IF0: { block: lock({
                    type: "logic_compare", fields: { OP: "LT" },
                    inputs: {
                      A: { block: lock({ type: "mpl_light" }) },
                      B: { block: lock({ type: "math_number", fields: { NUM: 100 } }) }
                    }
                  }) }
                },
                next: { block: lock({ type: "mpl_sleep", fields: { SEC: 0.2 } }) }
              }) } }
            }) } }
          }]
        }
      };
    },

    // 只看结构不看代码文本：两个空槽里第一块是不是对的“告诉网页”。
    // 放反了单独提示——这是最常见的错，告诉他“反了”比只说“不对”有用得多。
    check: function (workspace) {
      var ifBlock = workspace.getAllBlocks(false).filter(function (b) {
        return b.type === "controls_if" && MplKit.inMain(b);
      })[0];
      var a = MplKit.sendsIn(ifBlock, "DO0")[0] || null;
      var b = MplKit.sendsIn(ifBlock, "ELSE")[0] || null;
      var swapped = a === "light" && b === "dark";
      return [
        { label: "光线暗的时候 → 告诉网页“变暗了”", ok: a === "dark",
          note: swapped ? "放反了：暗的时候应该告诉网页“变暗了”" : (a ? "这里放的不是“变暗了”" : "还空着") },
        { label: "否则 → 告诉网页“变亮了”", ok: b === "light",
          note: swapped ? "放反了：亮的时候应该告诉网页“变亮了”" : (b ? "这里放的不是“变亮了”" : "还空着") }
      ];
    },

    // 网页端的反应：收到“变暗了”就开灯——反着来的设计是故意的，
    // 物理世界变暗、网页反而亮起来，学生会愣一下，这一下就记住了“传感器→判断→执行”。
    onEvent: function (type, scene) {
      if (type === "dark" || type === "light") scene.classList.toggle("lit", type === "dark");
      return scene.classList.contains("lit") ? "台灯：开" : "台灯：关";
    }
  });
})();
