// 第 3 关 · 智能门——按 A 键开门，按 B 键关门。
// 新东西是“事件”：前两关都是“一直重复 + 如果”在不停地问传感器；这一关换成
// “当按键 A 被按下时”——不用一直问，按键自己会喊。两种写法都算对：
// 学生用“一直重复 + 如果 按键 A 正被按着”拼出来，一样能开门，没必要卡他。
(function () {
  "use strict";
  var lock = MplKit.lock;

  // 这一句“告诉网页”是由按哪个键触发的；不是按键触发的返回 null
  function buttonOf(send) {
    var ts = MplKit.triggers(send);
    for (var i = 0; i < ts.length; i++) {
      var t = ts[i];
      if (t.kind === "event" && t.block.type === "mpl_on_button" && t.block.getFieldValue("EVT") === "pressed")
        return t.block.getFieldValue("BTN");
      if (t.kind === "if" && t.cond && t.cond.type === "mpl_button" && t.cond.getFieldValue("STATE") === "down" &&
          MplKit.inMain(send) && MplKit.inForever(send))
        return t.cond.getFieldValue("BTN");
    }
    return null;
  }

  MplKit.register({
    id: "demo3",
    color: "#0EA5A4", icon: "🚪",   // 关卡条、任务卡片、“下一关”按钮都用这个颜色
    short: "智能门",
    starterVersion: "1",
    title: "智能门",
    brief: "按掌控板上的 A 键，网页里的门打开；按 B 键，门关上。",
    hint: "去“输入”分类找“当按键 A 被按下时”，拖到空白处（不用接在别的积木下面），再把“告诉网页”放进去。B 键同理。",
    sends: ["open", "close"],
    need: ["open", "close"],
    doneHint: "都放对了！点“刷入”，然后按一下 A 键、再按一下 B 键。",

    // 家门口的走廊：墙纸、门牌、门边壁灯、鞋柜和雨伞、门口地垫。门打开后露出屋里：暖色的客厅、窗户和一盏灯，
    // 还有一只探头的小狗——门开了总得看见点什么，学生才会想再关上、再打开。
    scene:
      '<div class="wall"></div><div class="floor"></div>' +
      '<div class="sconce"></div><div class="plate">301</div><div class="bell"></div>' +
      '<div class="frame"><div class="room"><i class="rwin"></i><i class="rlamp"></i><i class="dog"></i></div>' +
      '<div class="door"><i class="pan a"></i><i class="pan b"></i><span class="knob"></span></div></div>' +
      '<div class="mat">WELCOME</div>' +
      '<div class="cab"><i class="shoe s1"></i><i class="shoe s2"></i></div>' +
      '<div class="umb"></div>',
    css:
      '.scene.s-demo3{background:#E7F0F7}' +
      '.s-demo3 .wall{position:absolute;inset:0;background:repeating-linear-gradient(90deg,#DCEBF5 0 22px,#D2E4F1 22px 24px)}' +
      '.s-demo3 .floor{position:absolute;left:0;right:0;bottom:0;height:40px;background:repeating-linear-gradient(90deg,#9B6B43 0 46px,#8C5F3A 46px 48px)}' +
      '.s-demo3 .frame{position:absolute;left:50%;bottom:40px;width:110px;height:160px;margin-left:-55px;' +
      '  border:7px solid #F2F2F2;border-bottom:none;perspective:500px;box-shadow:0 0 0 2px #C9D6E0}' +
      '.s-demo3 .room{position:absolute;inset:0;background:linear-gradient(#FFE6A8,#F7C66B);overflow:hidden}' +
      '.s-demo3 .rwin{position:absolute;left:14px;top:18px;width:36px;height:30px;border:3px solid #fff;background:#8FD3F5}' +
      '.s-demo3 .rlamp{position:absolute;right:16px;top:0;width:3px;height:30px;background:#7A5A3A}' +
      '.s-demo3 .rlamp::after{content:"";position:absolute;left:-9px;top:28px;width:20px;height:12px;border-radius:10px 10px 0 0;' +
      '  background:#FF8FA3;box-shadow:0 8px 16px 6px rgba(255,240,180,.8)}' +
      '.s-demo3 .dog{position:absolute;left:30px;bottom:0;width:44px;height:40px}' +
      '.s-demo3 .dog::before{content:"";position:absolute;left:6px;bottom:0;width:32px;height:30px;border-radius:50% 50% 40% 40%;background:#C98A4B}' +
      '.s-demo3 .dog::after{content:"";position:absolute;left:0;top:2px;width:44px;height:14px;' +
      '  background:radial-gradient(circle at 10px 7px,#8C5A2E 7px,transparent 7.5px),radial-gradient(circle at 34px 7px,#8C5A2E 7px,transparent 7.5px)}' +
      '.s-demo3 .door{position:absolute;inset:0;background:linear-gradient(90deg,#14A3A2,#0E8D8C);transform-origin:left center;' +
      '  transition:transform .7s cubic-bezier(.3,.7,.3,1);box-shadow:inset 0 0 0 5px rgba(0,0,0,.08)}' +
      '.s-demo3 .pan{position:absolute;left:14px;right:14px;border:3px solid rgba(255,255,255,.35);border-radius:4px}' +
      '.s-demo3 .pan.a{top:14px;height:52px}.s-demo3 .pan.b{top:78px;height:62px}' +
      '.s-demo3 .knob{position:absolute;right:10px;top:50%;width:11px;height:11px;border-radius:50%;background:#FFD45C;box-shadow:0 0 0 2px #E0A82E}' +
      '.s-demo3.open .door{transform:rotateY(-78deg)}' +
      '.s-demo3 .plate{position:absolute;left:calc(50% + 66px);top:36px;background:#1F2937;color:#FFD45C;font:800 12px/1 var(--mono);' +
      '  padding:4px 6px;border-radius:4px}' +
      '.s-demo3 .bell{position:absolute;left:calc(50% + 72px);top:78px;width:14px;height:20px;border-radius:4px;background:#fff;' +
      '  box-shadow:inset 0 0 0 3px #CBD5E1,0 0 0 1px #94A3B8}' +
      '.s-demo3 .sconce{position:absolute;left:calc(50% - 92px);top:44px;width:18px;height:26px;border-radius:9px 9px 3px 3px;' +
      '  background:#FFE7A0;box-shadow:0 0 18px 6px rgba(255,220,120,.6)}' +
      '.s-demo3 .mat{position:absolute;left:50%;bottom:6px;width:120px;margin-left:-60px;height:24px;border-radius:5px;' +
      '  background:repeating-linear-gradient(45deg,#E2574C 0 6px,#D24B41 6px 12px);color:#FFF4D6;font:800 11px/24px var(--font);' +
      '  text-align:center;letter-spacing:2px}' +
      '.s-demo3 .cab{position:absolute;left:5%;bottom:40px;width:70px;height:44px;background:#F7F3EA;border-radius:4px;' +
      '  box-shadow:inset 0 -14px 0 #E9E1CF,0 0 0 2px #D8CDB6}' +
      '.s-demo3 .shoe{position:absolute;top:-10px;width:24px;height:10px;border-radius:10px 10px 2px 2px}' +
      '.s-demo3 .s1{left:6px;background:#3B82F6}.s-demo3 .s2{left:36px;background:#F59E0B}' +
      '.s-demo3 .umb{position:absolute;right:8%;bottom:40px;width:26px;height:70px;border-radius:12px 12px 4px 4px;' +
      '  background:linear-gradient(90deg,#8B5CF6 0 33%,#EC4899 33% 66%,#F59E0B 66%);clip-path:polygon(50% 0,100% 30%,70% 100%,30% 100%,0 30%)}',

    starter: function () {
      // 事件积木靠中断触发，主程序得一直活着；这一圈空转先替学生写好、锁住
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
      var got = { open: null, close: null }, loose = false;
      workspace.getAllBlocks(false).forEach(function (b) {
        if (b.type !== "mpl_send") return;
        var k = b.getFieldValue("KIND");
        var root = b.getRootBlock().type;
        if (root !== "mpl_main" && root !== "mpl_on_button") { loose = true; return; }
        var btn = buttonOf(b);
        if (got[k] === null || btn) got[k] = btn || "none";
      });
      function note(k, want) {
        if (got[k] === want) return "";
        if (got[k] && got[k] !== "none") return "现在是按 " + got[k].toUpperCase() + " 键才" + (k === "open" ? "开门" : "关门");
        if (got[k] === "none") return "有这块了，但它没放在“当按键 " + want.toUpperCase() + " 被按下时”里面";
        return loose ? "“告诉网页”要放进按键积木里面，单独摆着不会执行" : "还没有";
      }
      return [
        { label: "按 A 键 → 告诉网页“开门”", ok: got.open === "a", note: note("open", "a") },
        { label: "按 B 键 → 告诉网页“关门”", ok: got.close === "b", note: note("close", "b") }
      ];
    },

    onEvent: function (type, scene) {
      if (type === "open" || type === "close") scene.classList.toggle("open", type === "open");
      return scene.classList.contains("open") ? "门：开着" : "门：关着";
    }
  });
})();
