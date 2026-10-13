<template>
  <div class="scratch">
    <el-empty v-if="!page.fileName" description="教师还没有上传 Scratch 作品" />
    <template v-else>
      <div class="bar">
        <el-button type="success" size="small" :disabled="!ready" @click="green">🚩 开始</el-button>
        <el-button type="danger" size="small" :disabled="!ready" @click="stop">⏹ 停止</el-button>
        <el-button size="small" :disabled="!ready" @click="fullscreen">全屏</el-button>
        <el-link type="primary" class="ml" @click="download">下载作品 {{ page.fileName }}</el-link>
        <span v-if="status" class="muted ml">{{ status }}</span>
      </div>
      <div ref="stageWrap" class="stage-wrap"><div ref="stage" class="stage" /></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../../api/client'

const props = defineProps<{ page: any; lessonId?: number; lesson?: any }>()
const stage = ref<HTMLElement>()
const stageWrap = ref<HTMLElement>()
const ready = ref(false)
const status = ref('')
let sc: any = null

/** TurboWarp Scaffolding（public/scratch，MPL-2.0）只加载一次，挂在全局 Scaffolding 上 */
function loadScaffolding(): Promise<any> {
  const w = window as any
  if (w.Scaffolding) return Promise.resolve(w.Scaffolding)
  if (w.__scaffoldingLoading) return w.__scaffoldingLoading
  w.__scaffoldingLoading = new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = `${import.meta.env.BASE_URL}scratch/scaffolding-with-music.js`
    s.onload = () => resolve(w.Scaffolding)
    s.onerror = () => reject(new Error('Scratch 播放器加载失败'))
    document.head.appendChild(s)
  })
  return w.__scaffoldingLoading
}

async function init() {
  if (!props.page.fileName) return
  status.value = '正在加载作品…'
  try {
    const [S, resp] = await Promise.all([loadScaffolding(), fetch(`/api/classroom/pages/${props.page.id}/file`, { credentials: 'same-origin' })])
    if (!resp.ok) throw new Error('作品下载失败')
    const data = await resp.arrayBuffer()
    sc = new S.Scaffolding()
    sc.width = 480
    sc.height = 360
    sc.resizeMode = 'preserve-ratio'
    sc.setup()
    sc.appendTo(stage.value)
    await sc.loadProject(data)
    ready.value = true
    status.value = '点击“开始”运行作品'
  } catch (e: any) {
    status.value = `无法播放：${e?.message || e}（可下载后用 Scratch 打开）`
  }
}

function green() { sc?.greenFlag(); status.value = '' }
function stop() { sc?.stopAll() }
function fullscreen() { stageWrap.value?.requestFullscreen?.() }
function download() { api.download(`/classroom/pages/${props.page.id}/file`, props.page.fileName) }

onMounted(init)
onBeforeUnmount(() => {
  try { sc?.stopAll(); sc?.vm?.quit?.() } catch { /* ignore */ }
  sc = null
})
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.stage-wrap { width: 100%; max-width: 640px; aspect-ratio: 4 / 3; background: #fff; border: 1px solid var(--el-border-color); border-radius: 6px; overflow: hidden; }
.stage-wrap:fullscreen { max-width: none; border: none; background: #000; }
.stage { width: 100%; height: 100%; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.ml { margin-left: 8px; }
</style>
