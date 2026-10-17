<template>
  <div class="mpy">
    <div class="bar">
      <span class="muted">每组的积木、刷入次数、通关情况实时更新；板子坏了的组可以在右侧“模拟”。</span>
      <span class="grow" />
      <el-button size="small" @click="fullscreen">全屏</el-button>
      <el-link :href="src" target="_blank" type="primary">新窗口打开（投屏）</el-link>
    </div>
    <div ref="wrap" class="frame-wrap">
      <iframe :src="src" class="frame" allow="serial; fullscreen" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

/** 掌控板编程（教师课堂控制台）：嵌入 web/public/mpython 的教师看板（原 mpython-lite teacher.html）。 */
const props = defineProps<{ lessonId: number; page: any }>()
const wrap = ref<HTMLElement>()
const src = computed(() => `/mpython/teacher.html?${new URLSearchParams({ page: String(props.page.id), lesson: String(props.lessonId), role: 'teacher' })}`)

function fullscreen() { wrap.value?.requestFullscreen?.() }
</script>

<style scoped>
.bar { display: flex; gap: 10px; align-items: center; margin-bottom: 8px; }
.grow { flex: 1; }
.muted { color: var(--el-text-color-secondary); font-size: 13px; }
.frame-wrap { width: 100%; height: 76vh; min-height: 560px; border: 1.5px solid var(--hb-line); border-radius: 8px; overflow: hidden; background: #fff; }
.frame-wrap:fullscreen { height: 100%; border: none; border-radius: 0; }
.frame { width: 100%; height: 100%; border: 0; }
</style>
