// 积木锁：锁住 = 拖不动 + 删不掉（数字、下拉这类字段仍然能改，比如学生可以调光线阈值）。
// 锁住的积木左上角画一把小锁，学生一眼看得出“这块是老师定好的，不是我拖不动”；
// 教师编辑模式（?edit=1）下点小锁或右键菜单就能锁定/解锁。
(function () {
  "use strict";

  var TYPE = new Blockly.icons.IconType("mpl_lock");
  var dom = Blockly.utils.dom, Svg = Blockly.utils.Svg;

  class LockIcon extends Blockly.icons.Icon {
    getType() { return TYPE; }
    initView(pointerdownListener) {
      if (this.svgRoot) return;
      super.initView(pointerdownListener);
      var g = this.svgRoot;
      dom.createSvgElement(Svg.RECT, { x: 0, y: 0, width: 16, height: 16, rx: 4, fill: "rgba(0,0,0,0.30)" }, g);
      dom.createSvgElement(Svg.PATH, { d: "M5.3 7.3V5.7a2.7 2.7 0 0 1 5.4 0v1.6", fill: "none",
        stroke: "#fff", "stroke-width": 1.6, "stroke-linecap": "round" }, g);
      dom.createSvgElement(Svg.RECT, { x: 3.6, y: 7.1, width: 8.8, height: 6.2, rx: 1.4, fill: "#fff" }, g);
      if (window.MPL_EDIT) g.style.cursor = "pointer";
      this.setTooltip(window.MPL_EDIT ? "已锁定：学生拖不动、删不掉。点这把锁可以解锁"
                                      : "老师锁定的积木：不能拖动，也不能删除");
    }
    getSize() { return new Blockly.utils.Size(16, 16); }
    getWeight() { return -10; }          // 排在积木最前面
    isShownWhenCollapsed() { return true; }
    onClick() { if (window.MPL_EDIT) MplLock.set(this.sourceBlock, false); }
  }

  // 只读画布（教师看板）里 isMovable() 一律是 false，照它判断每块积木都会挂上锁；
  // 这时直接看积木自己记的“能不能动”（movable_，存档里的 movable:false 就落在这里）
  function isLocked(b) {
    if (b.workspace && b.workspace.options.readOnly) return b.movable_ === false;
    return !b.isMovable();
  }

  function sync(b) {
    if (b.isInFlyout) return;
    var has = b.hasIcon(TYPE);
    if (isLocked(b) && !has) b.addIcon(new LockIcon(b));
    else if (!isLocked(b) && has) b.removeIcon(TYPE);
  }

  var listeners = [];
  function changed() { listeners.forEach(function (f) { f(); }); }

  var MplLock = {
    isLocked: isLocked,
    sync: sync,
    syncAll: function (ws) { ws.getAllBlocks(false).forEach(sync); },
    // setMovable/setDeletable 不产生 Blockly 事件，存档和上传都不会自动触发，所以改完要通知页面
    set: function (b, on) {
      b.setMovable(!on);
      b.setDeletable(!on);
      sync(b);
      changed();
    },
    setAll: function (ws, on) {
      ws.getAllBlocks(false).forEach(function (b) {
        // “当掌控板启动时”本来就删不掉；全部解锁时也别让它变成可删除
        b.setMovable(!on);
        b.setDeletable(b.type === "mpl_main" ? false : !on);
        sync(b);
      });
      changed();
    },
    onChange: function (f) { listeners.push(f); }
  };

  Blockly.ContextMenuRegistry.registry.register({
    id: "mpl_lock_toggle",
    scopeType: Blockly.ContextMenuRegistry.ScopeType.BLOCK,
    weight: -1,
    displayText: function (scope) { return isLocked(scope.block) ? "🔓 解锁这块积木" : "🔒 锁住这块积木"; },
    preconditionFn: function (scope) { return window.MPL_EDIT && !scope.block.isInFlyout ? "enabled" : "hidden"; },
    callback: function (scope) { MplLock.set(scope.block, !isLocked(scope.block)); }
  });

  window.MplLock = MplLock;

  // ---- 让锁住的积木之间也能插入新积木 ----
  // Blockly 默认：插入位置下面那块不可移动，就拒绝插入（怕把它挤走）。于是学生想在锁住的
  // “一直重复”和“如果”之间加一块“等待”，怎么拖都放不进去。
  // 放开这一条：锁住的那块只会接到新积木下面、整体往下挪一格，骨架不散。
  // 但新积木（连同它下面整串）的最底下必须还有接口能接住那块锁住的积木——
  // 像“一直重复”这种下面没接口的，插进去就会把锁住的积木挤成游离的一块，这种照旧不让插。
  class MplConnectionChecker extends Blockly.ConnectionChecker {
    doDragChecks(a, b, distance) {
      var t = b.type === Blockly.ConnectionType.NEXT_STATEMENT ? b.targetBlock() : null;
      if (!t || t.isMovable() || t.isShadow()) return super.doDragChecks(a, b, distance);
      if (!a.getSourceBlock().lastConnectionInStack(true)) return false;
      // 父类那条“目标不可移动就拒绝”的判断只看 isMovable()，借这一次检查让它看到“可移动”，
      // 其余规则（距离、类型、插入标记、拖着的积木不能接自己）照常由父类判断
      t.isMovable = function () { return true; };
      try { return super.doDragChecks(a, b, distance); }
      finally { delete t.isMovable; }
    }
  }

  // ---- 拖走插进去的积木时，别把下面锁住的骨架一起拖走 ----
  // Blockly 拖一块语句积木，默认连同它下面整串一起拖。学生把插进去的“等待”拖出来，
  // 下面锁住的“如果”“等待”就跟着被拖跑了——锁形同虚设。
  // 只要下面有锁住的积木，就改成“只拖这一块，下面的自动接回原位”（Blockly 自带的 healStack）。
  class MplBlockDragger extends Blockly.BlockDragger {
    startDrag(xy, healStack) {
      for (var n = this.draggingBlock_.getNextBlock(); n; n = n.getNextBlock()) {
        if (!n.isMovable()) { healStack = true; break; }
      }
      super.startDrag(xy, healStack);
    }
  }

  window.MPL_PLUGINS = { connectionChecker: MplConnectionChecker, blockDragger: MplBlockDragger };
})();
