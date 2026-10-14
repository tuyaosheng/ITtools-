// 第 2 关 · 声控楼道灯——拍一下手，楼道灯亮 5 秒再自己熄灭。
// 这一关换学生自己找“传感器”：判断骨架和“告诉网页”的位置都留空，
// 要他们想明白“该用哪个传感器的数值去比”。第 1 关是光线，这里换成声音，正好对照。
// “亮 5 秒再灭”交给网页做，不让学生在板子上写计时：一节课里只练一件新事。
(function () {
  "use strict";
  var lock = MplKit.lock;
  var LIT_MS = 5000;

  MplKit.register({
    id: "demo2",
    color: "#8B5CF6", icon: "🔊",   // 关卡条、任务卡片、“下一关”按钮都用这个颜色
    short: "声控灯",
    starterVersion: "1",
    title: "声控楼道灯",
    brief: "拍一下手，楼道里的灯就亮起来，过 5 秒自己熄灭——就像学校楼梯间的声控灯。",
    hint: "“如果”的比较左边空着：声音大小该用哪个传感器去读？去“输入”分类里找。然后把“告诉网页”放进“执行”。",
    sends: ["loud"],
    need: ["loud"],
    doneHint: "都放对了！点“刷入”，然后对着掌控板拍一下手。灯不亮就看看“板子说”里声音的数值，调一调 2000 这个数。",

    // 学校楼梯间：上半截浅色墙、下半截绿色墙裙，台阶带黄色防滑条和扶手，墙上有“安全出口”灯牌和“轻声慢步”标语。
    // 和第 1 关一样盖一层夜色，灯一亮夜色褪掉。“安全出口”灯牌永远亮着（真的楼道里也是），在夜色上面一层。
    scene:
      '<div class="wall"></div><div class="dado"></div><div class="ground"></div>' +
      '<div class="poster">轻声慢步</div><div class="win"></div>' +
      '<div class="stair"><i></i><i></i><i></i><i></i><i></i></div>' +
      '<div class="rail"></div><div class="post p1"></div><div class="post p2"></div><div class="post p3"></div>' +
      '<div class="plant"><i class="lf a"></i><i class="lf b"></i><i class="lf c"></i><i class="pot"></i></div>' +
      '<div class="night"></div>' +
      '<div class="exit"><span>🏃</span>安全出口</div>' +
      '<div class="ceil"></div><div class="cord"></div><div class="cone"></div><div class="bulb2"></div>' +
      '<div class="count"></div>',
    css:
      '.scene.s-demo2{background:#DDE7E3}' +
      '.s-demo2 .wall{position:absolute;inset:0;background:linear-gradient(#EEF3EF,#DDE7E3)}' +
      '.s-demo2 .dado{position:absolute;left:0;right:0;bottom:24px;height:74px;background:#79B8A6;border-top:4px solid #5E9F8C}' +
      '.s-demo2 .ground{position:absolute;left:0;right:0;bottom:0;height:24px;background:repeating-linear-gradient(90deg,#C8B79A 0 38px,#BBA987 38px 40px)}' +
      '.s-demo2 .win{position:absolute;left:42%;top:22px;width:54px;height:44px;border:4px solid #fff;border-radius:4px;' +
      '  background:linear-gradient(#20305F,#3E4C8E);box-shadow:inset 0 0 0 2px #E1E8F0}' +
      '.s-demo2 .poster{position:absolute;left:8%;top:26px;padding:6px 8px;background:#FF8FA3;color:#fff;font-weight:800;' +
      '  font-size:13px;border-radius:6px;transform:rotate(-3deg);box-shadow:0 2px 0 #E66F86}' +
      '.s-demo2 .stair{position:absolute;left:4%;bottom:24px;width:58%;height:120px}' +
      '.s-demo2 .stair i{position:absolute;bottom:0;background:#E9D9BA;border-top:5px solid #F5B82E;box-shadow:inset 0 -3px 0 #D2BE98}' +
      '.s-demo2 .stair i:nth-child(1){left:0;width:100%;height:24px}.s-demo2 .stair i:nth-child(2){left:20%;width:80%;height:48px}' +
      '.s-demo2 .stair i:nth-child(3){left:40%;width:60%;height:72px}.s-demo2 .stair i:nth-child(4){left:60%;width:40%;height:96px}' +
      '.s-demo2 .stair i:nth-child(5){left:80%;width:20%;height:120px}' +
      '.s-demo2 .rail{position:absolute;left:4%;bottom:70px;width:62%;height:6px;border-radius:3px;background:#B0703E;' +
      '  transform-origin:left center;transform:rotate(-24deg)}' +
      '.s-demo2 .post{position:absolute;width:4px;background:#8C5A35}' +
      '.s-demo2 .p1{left:calc(4% + 10%);bottom:48px;height:38px}.s-demo2 .p2{left:calc(4% + 30%);bottom:96px;height:50px}' +
      '.s-demo2 .p3{left:calc(4% + 50%);bottom:144px;height:30px;display:none}' +
      '.s-demo2 .plant{position:absolute;right:18px;bottom:24px;width:34px;height:56px}' +
      '.s-demo2 .plant .pot{position:absolute;left:6px;bottom:0;width:22px;height:18px;background:#3F7FD0;clip-path:polygon(0 0,100% 0,85% 100%,15% 100%)}' +
      '.s-demo2 .plant .lf{position:absolute;bottom:16px;width:13px;height:32px;border-radius:50%/80% 80% 20% 20%;background:#3FAE5A}' +
      '.s-demo2 .plant .a{left:4px;transform:rotate(-30deg)}.s-demo2 .plant .b{left:11px;height:38px;background:#56C46F}' +
      '.s-demo2 .plant .c{left:17px;transform:rotate(30deg)}' +
      '.s-demo2 .night{position:absolute;inset:0;z-index:5;background:rgba(12,18,48,.74);transition:opacity .3s ease}' +
      '.s-demo2 .exit{position:absolute;left:8%;top:70px;z-index:7;background:#16A34A;color:#fff;font-size:11px;font-weight:800;' +
      '  padding:3px 7px;border-radius:4px;box-shadow:0 0 12px 2px rgba(34,197,94,.55);display:flex;gap:3px;align-items:center}' +
      '.s-demo2 .exit span{font-size:12px}' +
      '.s-demo2 .ceil{position:absolute;left:0;right:0;top:0;height:10px;background:#C9D4CF;z-index:7}' +
      '.s-demo2 .cord{position:absolute;right:90px;top:10px;width:2px;height:30px;background:#7B8796;z-index:7}' +
      '.s-demo2 .bulb2{position:absolute;right:76px;top:38px;width:30px;height:30px;border-radius:50%;background:#5A6475;z-index:7;' +
      '  transition:background .2s,box-shadow .2s;box-shadow:inset 0 -4px 0 rgba(0,0,0,.15)}' +
      '.s-demo2 .cone{position:absolute;right:-20px;top:56px;width:220px;height:170px;opacity:0;transition:opacity .25s;z-index:6;' +
      '  background:radial-gradient(ellipse at 50% 0,rgba(255,226,140,.75),rgba(255,226,140,0) 72%);clip-path:polygon(44% 0,56% 0,100% 100%,0 100%)}' +
      '.s-demo2.on .night{opacity:0}.s-demo2.on .cone{opacity:1}' +
      '.s-demo2.on .bulb2{background:#FFF3C4;box-shadow:0 0 28px 14px rgba(255,220,120,.85)}' +
      '.s-demo2 .count{position:absolute;right:16px;top:16px;color:#fff;font:800 26px/1 var(--mono);z-index:8;' +
      '  min-width:38px;text-align:center;border-radius:10px;background:rgba(139,92,246,.85);padding:6px 0}' +
      '.s-demo2 .count:empty{display:none}',

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
                inputs: {
                  IF0: { block: lock({
                    type: "logic_compare", fields: { OP: "GT" },
                    inputs: { B: { block: lock({ type: "math_number", fields: { NUM: 2000 } }) } }
                  }) }
                },
                next: { block: lock({ type: "mpl_sleep", fields: { SEC: 0.1 } }) }
              }) } }
            }) } }
          }]
        }
      };
    },

    check: function (workspace) {
      var ifBlock = workspace.getAllBlocks(false).filter(function (b) {
        return b.type === "controls_if" && MplKit.inMain(b);
      })[0];
      var cmp = ifBlock && ifBlock.getInputTargetBlock("IF0");
      var left = cmp && cmp.type === "logic_compare" ? cmp.getInputTargetBlock("A") : null;
      var sends = MplKit.sendsIn(ifBlock, "DO0");
      return [
        { label: "比较的左边 → 放“声音的响度”", ok: !!left && left.type === "mpl_sound",
          note: !left ? "还空着" : (left.type === "mpl_light" ? "这是光线传感器——拍手改变的是声音" : "这里放的不是“声音的响度”") },
        { label: "声音够大的时候 → 告诉网页“有声音”", ok: sends.indexOf("loud") >= 0,
          note: sends.length ? "这里放的不是“有声音”" : "还空着" }
      ];
    },

    // 每收到一次“有声音”就重新从 5 秒开始倒数，和真的声控灯一样：人一直在走动、灯就一直亮着
    onEvent: function (type, scene) {
      var st = scene._d2 || (scene._d2 = { until: 0, timer: null });
      var count = scene.querySelector(".count");
      function tick() {
        var left = Math.ceil((st.until - Date.now()) / 1000);
        if (left > 0) { count.textContent = left; st.timer = setTimeout(tick, 200); }
        else { count.textContent = ""; scene.classList.remove("on"); scene._cap && (scene._cap.textContent = "楼道灯：关"); }
      }
      if (type === "loud") {
        st.until = Date.now() + LIT_MS;
        scene.classList.add("on");
        clearTimeout(st.timer); tick();
      }
      return scene.classList.contains("on") ? "楼道灯：开（安静 5 秒后熄灭）" : "楼道灯：关";
    }
  });
})();
