// 关卡的公共工具。每个 demoN.js 调 MplKit.register({...}) 登记一关，按登记顺序就是第 1、2、3… 关。
//
// 一关要写的东西：
//   id / short（关卡条上的两三个字）/ title / brief / hint
//   sends   这一关“告诉网页”能说的几句（MPL_SENDS 里的暗号）
//   need    通关要求网页真的收到过哪几句——光把积木放对不算，板子上真跑起来了才算
//   doneHint 积木全放对、还没通关时，告诉学生下一步做什么
//   scene / css  左边网页场景的 HTML 和样式；onEvent(type, sceneEl) 返回场景下方的说明文字
//   starter(cfg) 初始积木；check(workspace) 返回 [{label, ok, note}]
(function () {
  "use strict";
  window.MPL_DEMOS = window.MPL_DEMOS || [];

  function inMain(b) { return b.getRootBlock().type === "mpl_main"; }

  window.MplKit = {
    register: function (d) { window.MPL_DEMOS.push(d); },

    lock: function (block) {
      block.deletable = false;
      block.movable = false;
      return block;
    },

    inMain: inMain,

    // 按组号出固定的随机数：同一组每次打开都是同一份题，不同组不一样（看同桌的屏幕没用）
    rng: function (seed) {
      var a = seed >>> 0;
      return function () {
        a = (a + 0x6D2B79F5) >>> 0;
        var t = Math.imul(a ^ (a >>> 15), 1 | a);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
      };
    },
    shuffle: function (arr, r) {
      for (var i = arr.length - 1; i > 0; i--) { var j = Math.floor(r() * (i + 1)); var t = arr[i]; arr[i] = arr[j]; arr[j] = t; }
      return arr;
    },
    pick: function (arr, r) { return arr[Math.floor(r() * arr.length)]; },
    seed: function () {
      var g = String((window.MPL_RUNTIME && window.MPL_RUNTIME.group) || "");
      var n = parseInt(g.replace(/\D/g, ""), 10);
      return n > 0 ? n : 1;
    },

    // 某个语句槽里（只看这一层）放了哪些“告诉网页”；槽里有别的积木但没有“告诉网页”时返回 ["other"]
    sendsIn: function (block, inputName) {
      if (!block) return [];
      var first = block.getInputTargetBlock(inputName), out = [];
      for (var b = first; b; b = b.getNextBlock()) if (b.type === "mpl_send") out.push(b.getFieldValue("KIND"));
      return out.length ? out : (first ? ["other"] : []);
    },

    // 这块积木是不是在“一直重复”里面：放在外面只会判断一次，学生会以为“程序不灵”
    inForever: function (b) {
      for (var p = b.getSurroundParent(); p; p = p.getSurroundParent()) if (p.type === "mpl_forever") return true;
      return false;
    },

    // 一块语句积木是被什么“触发”的：在哪个“如果”的哪个分支里，那个分支的条件积木是什么；
    // 或者在哪个事件积木（当按键…时）里。返回 [{kind:"if", cond:Block}|{kind:"event", block:Block}]，由内到外。
    triggers: function (b) {
      var out = [], child = b;
      for (var p = b.getSurroundParent(); p; child = p, p = p.getSurroundParent()) {
        if (p.type === "controls_if") {
          for (var i = 0; p.getInput("DO" + i); i++) {
            var top = p.getInputTargetBlock("DO" + i);
            if (top && isInChain(top, child)) { out.push({ kind: "if", cond: p.getInputTargetBlock("IF" + i) }); break; }
          }
        } else if (p.type === "mpl_on_button" || p.type === "mpl_on_touch") {
          out.push({ kind: "event", block: p });
        }
      }
      return out;
    }
  };

  function isInChain(top, b) {
    for (var x = top; x; x = x.getNextBlock()) if (x === b) return true;
    return false;
  }
})();
