<template>
  <div class="blocks-page">
    <div class="tip">
      随时练习：程序自动保存，换电脑登录也能接着做；“另存为”可以存成 .mpcode（Mind+ 也能打开）。课上的作品请在课堂里对应的页面做、点“提交作品”。
    </div>
    <!-- 同源 iframe：串口权限要显式放行，否则 MicroPython 方式连不上掌控板 -->
    <iframe src="/mpblocks/index.html?free=1" class="frame" allow="serial; fullscreen; clipboard-write" />
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.blocks-page { display: flex; flex-direction: column; height: calc(100vh - 110px); min-height: 520px; gap: 8px; }
.tip { font-size: 13px; color: var(--el-text-color-secondary); }
.frame { flex: 1; width: 100%; border: 1px solid var(--el-border-color); border-radius: 8px; background: #fff; }
</style>
