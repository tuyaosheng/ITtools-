<template>
  <el-tooltip placement="top" popper-class="hb-hint-pop" :show-after="150">
    <template #content><slot /></template>
    <div v-if="block" class="hb-hint-line" tabindex="0"><span class="hb-hint">i</span>{{ label || '这是做什么的？' }}</div>
    <span v-else class="hb-hint" tabindex="0" aria-label="说明">i</span>
  </el-tooltip>
</template>

<script setup lang="ts">
// 说明文字收进 ⓘ，鼠标悬停才显示；block 时单独占一行并带一句引导语
defineProps<{ block?: boolean; label?: string }>()
</script>

<style scoped>
.hb-hint {
  display: inline-grid; place-items: center; width: 16px; height: 16px; margin: 0 4px; vertical-align: -2px;
  border: 1.5px solid var(--el-text-color-secondary); border-radius: 50%;
  font: italic 700 11px/1 Georgia, serif; color: var(--el-text-color-secondary); cursor: help; user-select: none;
}
.hb-hint:hover, .hb-hint:focus { color: var(--el-color-primary); border-color: var(--el-color-primary); outline: none; }
.hb-hint-line {
  display: inline-flex; align-items: center; gap: 2px; margin: 0 0 12px; font-size: 13px;
  color: var(--el-text-color-secondary); cursor: help; outline: none;
}
.hb-hint-line:hover, .hb-hint-line:focus { color: var(--el-color-primary); }
.hb-hint-line:hover .hb-hint { color: var(--el-color-primary); border-color: var(--el-color-primary); }
.hb-hint-line .hb-hint { margin-left: 0; }
</style>
