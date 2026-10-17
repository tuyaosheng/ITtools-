// 第 4 关 · 摇一摇骰子——晃一下掌控板，网页上飞行棋的骰子滚出一个点数。
// 这一关只给“一直重复”，判断要学生自己从“逻辑”里拖：前三关都是在别人拼好的“如果”里填空，
// 到这里该自己搭一次完整的“如果……就……”了。
(function () {
  "use strict";
  var lock = MplKit.lock;
  var PIPS = { 1: [5], 2: [1, 9], 3: [1, 5, 9], 4: [1, 3, 7, 9], 5: [1, 3, 5, 7, 9], 6: [1, 3, 4, 6, 7, 9] };

  function face(n) {
    var h = "";
    for (var i = 1; i <= 9; i++) h += '<i class="' + (PIPS[n].indexOf(i) >= 0 ? "p" : "") + '"></i>';
    return h;
  }

  MplKit.register({
    id: "demo4",
    color: "#EF476F", icon: "🎲",   // 关卡条、任务卡片、“下一关”按钮都用这个颜色
    short: "骰子",
    starterVersion: "1",
    title: "摇一摇骰子",
    brief: "拿起掌控板晃一晃，网页上的骰子就会滚一次，停在一个随机的点数上。",
    hint: "这次“如果”要你自己搭：去“逻辑”拖一个“如果”放进“一直重复”，条件用“输入”里的“掌控板被摇晃”，再放“告诉网页：摇了一下”。",
    sends: ["shake"],
    need: ["shake"],
    doneHint: "都放对了！点“刷入”，然后拿起掌控板用力晃一下（小心别扯掉 USB 线）。",

    // 儿童游戏室的书桌：中间一张飞行棋棋盘（四色营地 + 十字跑道 + 棋子），左边一摞字母积木，右边几支蜡笔。
    // 刻意不画绿绒布、筹码、骰盅那种赌桌——这是给初中生上课的，骰子在这里是“下棋走几步”。
    // 骰子停下时周围炸开一圈彩纸。
    scene:
      '<div class="desk"></div>' +
      '<div class="board"><i class="home h1"></i><i class="home h2"></i><i class="home h3"></i><i class="home h4"></i>' +
      '<i class="pawn w1"></i><i class="pawn w2"></i><i class="pawn w3"></i><i class="pawn w4"></i></div>' +
      '<div class="blocks"><i class="b1">A</i><i class="b2">B</i><i class="b3">C</i></div>' +
      '<div class="crayons"><i class="k1"></i><i class="k2"></i><i class="k3"></i></div>' +
      '<div class="dice">' + face(1) + '</div><div class="confetti"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div>' +
      '<div class="rolls"></div>',
    css:
      '.scene.s-demo4{background:#FFF3E0}' +
      '.s-demo4 .desk{position:absolute;inset:0;background:radial-gradient(circle,#FFE2B8 2px,transparent 2.5px) 0 0/22px 22px,' +
      '  linear-gradient(#FFF6E6,#FFE9C9)}' +
      '.s-demo4 .board{position:absolute;left:50%;top:50%;width:176px;height:176px;margin:-92px 0 0 -88px;border-radius:16px;' +
      '  border:5px solid #fff;box-shadow:0 6px 16px rgba(160,100,40,.25);transform:rotate(-4deg);' +
      '  background:repeating-linear-gradient(90deg,transparent 0 17px,rgba(0,0,0,.08) 17px 19px) center/38px 100% no-repeat,' +
      '  repeating-linear-gradient(0deg,transparent 0 17px,rgba(0,0,0,.08) 17px 19px) center/100% 38px no-repeat,' +
      '  linear-gradient(#FFFDF7,#FFFDF7) center/38px 100% no-repeat,linear-gradient(#FFFDF7,#FFFDF7) center/100% 38px no-repeat,' +
      '  conic-gradient(#FFB3B3 0 25%,#A9D6FF 0 50%,#B7EFC5 0 75%,#FFE59A 0)}' +
      '.s-demo4 .home{position:absolute;width:44px;height:44px;border-radius:50%;background:rgba(255,255,255,.75);' +
      '  box-shadow:inset 0 0 0 4px rgba(255,255,255,.9)}' +
      '.s-demo4 .h1{right:12px;top:12px}.s-demo4 .h2{right:12px;bottom:12px}.s-demo4 .h3{left:12px;bottom:12px}.s-demo4 .h4{left:12px;top:12px}' +
      '.s-demo4 .pawn{position:absolute;width:16px;height:16px;border-radius:50%;border:3px solid #fff;box-shadow:0 2px 3px rgba(0,0,0,.25)}' +
      '.s-demo4 .w1{right:26px;top:26px;background:#EF4444}.s-demo4 .w2{right:26px;bottom:26px;background:#3B82F6}' +
      '.s-demo4 .w3{left:26px;bottom:26px;background:#22C55E}.s-demo4 .w4{left:26px;top:26px;background:#F59E0B}' +
      '.s-demo4 .blocks{position:absolute;left:6%;bottom:52px;width:40px}' +
      '.s-demo4 .blocks i{display:grid;place-items:center;width:34px;height:30px;margin-top:2px;border-radius:5px;color:#fff;' +
      '  font:800 15px/1 var(--font);font-style:normal;box-shadow:inset 0 -4px 0 rgba(0,0,0,.15)}' +
      '.s-demo4 .b1{background:#8B5CF6;transform:rotate(-6deg) translateX(4px)}.s-demo4 .b2{background:#06B6D4}.s-demo4 .b3{background:#F97316}' +
      '.s-demo4 .crayons{position:absolute;right:7%;bottom:26px;width:60px;height:70px}' +
      '.s-demo4 .crayons i{position:absolute;bottom:0;width:11px;height:62px;border-radius:2px;transform-origin:bottom center;' +
      '  clip-path:polygon(50% 0,100% 14%,100% 100%,0 100%,0 14%)}' +
      '.s-demo4 .k1{left:6px;background:#EF4444;transform:rotate(-14deg)}.s-demo4 .k2{left:24px;background:#22C55E}' +
      '.s-demo4 .k3{left:42px;background:#3B82F6;transform:rotate(14deg)}' +
      '.s-demo4 .dice{position:absolute;left:50%;top:50%;width:84px;height:84px;margin:-50px 0 0 -42px;background:#fff;z-index:3;' +
      '  border-radius:16px;box-shadow:0 7px 0 #E4DED2,0 12px 20px rgba(120,70,20,.3);display:grid;' +
      '  grid-template:repeat(3,1fr)/repeat(3,1fr);padding:12px;gap:3px}' +
      '.s-demo4 .dice i{border-radius:50%;margin:3px}.s-demo4 .dice i.p{background:#EF476F}' +
      '.s-demo4 .dice.roll{animation:d4roll .7s cubic-bezier(.2,.8,.3,1)}' +
      '@keyframes d4roll{0%{transform:translateY(-40px) rotate(0)}60%{transform:translateY(6px) rotate(560deg)}100%{transform:none}}' +
      '.s-demo4 .confetti{position:absolute;left:50%;top:50%;z-index:2}' +
      '.s-demo4 .confetti i{position:absolute;width:8px;height:12px;border-radius:2px;opacity:0}' +
      '.s-demo4 .dice.roll~.confetti i{animation:d4pop .9s ease-out .45s}' +
      '.s-demo4 .confetti i:nth-child(1){background:#F59E0B;--dx:-90px;--dy:-60px}.s-demo4 .confetti i:nth-child(2){background:#3B82F6;--dx:80px;--dy:-70px}' +
      '.s-demo4 .confetti i:nth-child(3){background:#EF476F;--dx:-110px;--dy:10px}.s-demo4 .confetti i:nth-child(4){background:#8B5CF6;--dx:105px;--dy:20px}' +
      '.s-demo4 .confetti i:nth-child(5){background:#FFD45C;--dx:-60px;--dy:70px}.s-demo4 .confetti i:nth-child(6){background:#10B981;--dx:60px;--dy:65px}' +
      '.s-demo4 .confetti i:nth-child(7){background:#06B6D4;--dx:0px;--dy:-85px}.s-demo4 .confetti i:nth-child(8){background:#F97316;--dx:-20px;--dy:80px}' +
      '@keyframes d4pop{0%{opacity:1;transform:translate(0,0) rotate(0)}100%{opacity:0;transform:translate(var(--dx),var(--dy)) rotate(300deg)}}' +
      '.s-demo4 .rolls{position:absolute;right:16px;top:18px;color:#fff;font-weight:700;z-index:4;background:rgba(239,71,111,.9);' +
      '  padding:3px 10px;border-radius:999px;font-size:13px}' +
      '.s-demo4 .rolls:empty{display:none}',

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
      var ifs = workspace.getAllBlocks(false).filter(function (b) { return b.type === "controls_if" && MplKit.inMain(b); });
      var good = ifs.filter(function (b) {
        var c = b.getInputTargetBlock("IF0");
        return c && c.type === "mpl_shaken";
      })[0];
      var outside = good && !MplKit.inForever(good);
      var sends = MplKit.sendsIn(good, "DO0");
      return [
        { label: "如果“掌控板被摇晃”（放在“一直重复”里）", ok: !!good && !outside,
          note: outside ? "要放进“一直重复”里面，不然只在开机时检查一次" :
                (ifs.length ? "“如果”有了，条件还不是“掌控板被摇晃”" : "还没有“如果”") },
        { label: "就 → 告诉网页“摇了一下”", ok: sends.indexOf("shake") >= 0,
          note: !good ? "先把上一步搭好" : (sends.length ? "这里放的不是“摇了一下”" : "还空着") }
      ];
    },

    // 点数在网页上随机出：这一关练的是“事件 → 判断 → 通知”，不是随机数
    onEvent: function (type, scene) {
      var st = scene._d4 || (scene._d4 = { n: 0 });
      if (type === "shake") {
        var dice = scene.querySelector(".dice");
        var v = 1 + Math.floor(Math.random() * 6);
        st.n++;
        dice.classList.remove("roll"); void dice.offsetWidth; dice.classList.add("roll");
        setTimeout(function () { dice.innerHTML = face(v); }, 350);
        scene.querySelector(".rolls").textContent = "已经摇了 " + st.n + " 次";
        st.last = v;
      }
      return st.last ? "骰子：" + st.last + " 点" : "骰子：还没摇";
    }
  });
})();
