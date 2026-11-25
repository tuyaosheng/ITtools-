<template>
  <div v-if="kind !== 'none'" ref="boxRef" class="file-preview" :class="{ fullscreen: isFull, 'is-slides': kind === 'slides' }">
    <div class="fp-bar">
      <span class="fp-title">{{ kindLabel }}</span>
      <template v-if="kind === 'slides' && showMode">
        <el-button size="small" title="上一页" @click="showCmd('prevSlide')">«</el-button>
        <el-button size="small" @click="showCmd('prev')">上一步</el-button>
        <span v-if="showPos" class="fp-count">{{ showPos }}</span>
        <el-button size="small" type="primary" plain @click="showCmd('next')">下一步</el-button>
        <el-button size="small" title="下一页（跳过本页剩下的动画）" @click="showCmd('nextSlide')">»</el-button>
        <span class="fp-hint">点幻灯片或按 → / 空格播放下一步动画，PageUp / PageDown 翻页</span>
      </template>
      <template v-if="kind === 'slides' && !showMode && slideCount">
        <el-button size="small" :disabled="cur <= 1" @click="go(cur - 1)">上一张</el-button>
        <span class="fp-count">{{ cur }} / {{ slideCount }}</span>
        <el-button size="small" :disabled="cur >= slideCount" @click="go(cur + 1)">下一张</el-button>
      </template>
      <span class="fp-spacer" />
      <el-button v-if="kind === 'slides' && showMode" size="small" @click="switchMode('images')">图片模式</el-button>
      <el-button v-else-if="kind === 'slides' && showAvailable" size="small" @click="switchMode('show')">动画模式</el-button>
      <el-button size="small" @click="toggleFull">{{ isFull ? '退出全屏' : (kind === 'slides' ? '全屏播放' : '全屏') }}</el-button>
    </div>

    <div v-if="loading" class="fp-loading">正在生成预览，第一次打开大课件要等几秒…</div>
    <div v-else-if="error" class="fp-error">{{ error }}</div>

    <!-- PPT 动画模式：LibreOffice 转出的交互演示；服务器返回 CSP sandbox，它在隔离环境里只运行自带的播放脚本 -->
    <div v-else-if="kind === 'slides' && showMode" ref="showStageRef" class="fp-stage fp-show-stage">
      <!-- 演示按固定尺寸渲染，再整体缩放适应外框（框内页面不随外框变化重排，切换全屏也不变形） -->
      <iframe ref="showRef" class="fp-show" :src="`/api${previewApi}/show?v=${previewVer}`"
        :style="{ width: SHOW_W + 'px', height: showH + 'px', transform: `translate(-50%, -50%) scale(${showScale})` }"
        @load="focusShow" />
    </div>
    <!-- PPT 图片模式：一张张幻灯片 -->
    <div v-else-if="kind === 'slides'" class="fp-stage" tabindex="0" @keydown="onKey" @click="onStageClick">
      <img v-if="slideCount" class="fp-slide" :src="slideUrl(cur)" :alt="`第 ${cur} 张`" draggable="false" />
      <img v-if="cur < slideCount" class="fp-preload" :src="slideUrl(cur + 1)" alt="" />
    </div>
    <div v-if="kind === 'slides' && showNote && !loading" class="fp-note">{{ showNote }}</div>
    <div v-if="kind === 'slides' && !showMode && slideCount > 1 && !loading && !error && !isFull" class="fp-thumbs">
      <img v-for="n in slideCount" :key="n" :src="slideUrl(n)" :class="{ on: n === cur }" loading="lazy" @click="go(n)" />
    </div>

    <!-- PDF / 文本 / 网页 / 旧版 Word -->
    <iframe v-else-if="kind === 'frame' || kind === 'doc'" class="fp-frame" :src="frameSrc" />

    <!-- 图片 -->
    <div v-else-if="kind === 'image'" class="fp-image"><img :src="fileUrl" /></div>

    <!-- 音频 -->
    <audio v-else-if="kind === 'audio'" class="fp-audio" controls :src="fileUrl" />

    <!-- docx / 表格：浏览器内渲染 -->
    <div v-else-if="kind === 'docx'" ref="docxRef" class="fp-docx" />
    <div v-else-if="kind === 'sheet'" class="fp-sheet">
      <el-radio-group v-if="sheetNames.length > 1" v-model="sheetIdx" size="small" class="fp-sheet-tabs">
        <el-radio-button v-for="(n, i) in sheetNames" :key="n" :value="i">{{ n }}</el-radio-button>
      </el-radio-group>
      <div class="fp-sheet-body" v-html="sheetHtml" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import DOMPurify from 'dompurify'
import { http } from '../api/http'

/** 资料课件在线显示：PPT（带动画的演示，或按张出图）、Word、Excel、PDF、图片、文本等。 */
const props = defineProps<{
  fileName: string
  /** 原文件地址（inline） */
  fileUrl: string
  /** 服务端预览接口前缀（不含 /api），如 /classroom/pages/12/preview */
  previewApi: string
}>()

const ext = computed(() => (props.fileName || '').split('.').pop()?.toLowerCase() || '')
const kind = computed(() => {
  const e = ext.value
  if (['ppt', 'pptx', 'pps', 'ppsx'].includes(e)) return 'slides'
  if (e === 'doc') return 'doc'
  if (e === 'docx') return 'docx'
  if (['xlsx', 'xls', 'csv', 'et'].includes(e)) return 'sheet'
  if (['pdf', 'txt', 'html', 'htm'].includes(e)) return 'frame'
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg'].includes(e)) return 'image'
  if (['mp3', 'wav', 'ogg', 'm4a'].includes(e)) return 'audio'
  return 'none'
})
const kindLabel = computed(() => ({
  slides: '幻灯片', doc: 'Word 文档', docx: 'Word 文档', sheet: '表格', frame: ext.value === 'pdf' ? 'PDF' : '文档', image: '图片', audio: '音频'
} as Record<string, string>)[kind.value] || '')

const loading = ref(false)
const error = ref('')
const boxRef = ref<HTMLElement>()
const isFull = ref(false)

// ---- 幻灯片 ----
const slideCount = ref(0)
const cur = ref(1)
/** 服务器装了 LibreOffice 时 PPT 默认用动画模式（保留动画、切换效果）；没装或转换失败退回图片模式 */
const showMode = ref(false)
const showAvailable = ref(false)
const showNote = ref('')
const showRef = ref<HTMLIFrameElement>()
const showStageRef = ref<HTMLElement>()
const SHOW_W = 1280
const showRatio = ref(16 / 9)
const showH = computed(() => Math.round(SHOW_W / showRatio.value))
const showScale = ref(0.5)
let showObserver: ResizeObserver | null = null
function fitShow() {
  const st = showStageRef.value
  if (!st) return
  showScale.value = Math.min(st.clientWidth / SHOW_W, st.clientHeight / showH.value) || 0.5
}
watch(showStageRef, el => {
  showObserver?.disconnect()
  if (el) {
    showObserver = new ResizeObserver(fitShow)
    showObserver.observe(el)
    fitShow()
  }
})
let slideMode: 'show' | 'images' | undefined
function focusShow() {
  try { showRef.value?.focus(); showRef.value?.contentWindow?.focus() } catch { /* 沙箱文档 */ }
}
const showPos = ref('')
/** 演示框回传的进度（只认自己这个框发来的消息） */
function onShowMessage(e: MessageEvent) {
  if (!showRef.value || e.source !== showRef.value.contentWindow) return
  const st = e.data?.ittoolsSlidesState
  if (st && Number.isFinite(st.slide) && Number.isFinite(st.count)) showPos.value = `${st.slide} / ${st.count}`
}
/** 给演示框发指令（框是隔离的沙箱页面，只能用 postMessage） */
function showCmd(c: 'next' | 'prev' | 'first' | 'nextSlide' | 'prevSlide') {
  showRef.value?.contentWindow?.postMessage({ ittoolsSlides: c }, '*')
}
function switchMode(m: 'show' | 'images') {
  slideMode = m
  load()
}
const previewVer = ref('')
function slideUrl(n: number) { return `/api${props.previewApi}/slides/${n}?v=${previewVer.value}` }
function go(n: number) { if (n >= 1 && n <= slideCount.value) cur.value = n }
function onKey(e: KeyboardEvent) {
  if (kind.value !== 'slides') return
  if (showMode.value) {
    if (e.key === 'PageDown') { showCmd('nextSlide'); e.preventDefault() }
    else if (e.key === 'PageUp') { showCmd('prevSlide'); e.preventDefault() }
    else if (['ArrowRight', 'ArrowDown', ' ', 'Enter'].includes(e.key)) { showCmd('next'); e.preventDefault() }
    else if (['ArrowLeft', 'ArrowUp', 'Backspace'].includes(e.key)) { showCmd('prev'); e.preventDefault() }
    else if (e.key === 'Home') showCmd('first')
    return
  }
  if (['ArrowRight', 'ArrowDown', 'PageDown', ' ', 'Enter'].includes(e.key)) { go(cur.value + 1); e.preventDefault() }
  else if (['ArrowLeft', 'ArrowUp', 'PageUp', 'Backspace'].includes(e.key)) { go(cur.value - 1); e.preventDefault() }
  else if (e.key === 'Home') go(1)
  else if (e.key === 'End') go(slideCount.value)
}
/** 点幻灯片左边三分之一往回翻，其余往后翻（像放 PPT 一样） */
function onStageClick(e: MouseEvent) {
  const el = e.currentTarget as HTMLElement
  el.focus()
  const r = el.getBoundingClientRect()
  if (e.clientX - r.left < r.width / 3) go(cur.value - 1)
  else go(cur.value + 1)
}

// ---- 旧版 Word / iframe ----
const frameSrc = computed(() => kind.value === 'doc' ? `/api${props.previewApi}/doc?v=${previewVer.value}` : props.fileUrl)

// ---- docx ----
const docxRef = ref<HTMLElement>()
// ---- 表格 ----
const sheetNames = ref<string[]>([])
const sheetIdx = ref(0)
const workbook = shallowRef<any>(null)
let XLSX: any = null
const sheetHtml = computed(() => {
  const wb = workbook.value
  if (!wb || !XLSX) return ''
  const ws = wb.Sheets[sheetNames.value[sheetIdx.value]]
  return ws ? DOMPurify.sanitize(XLSX.utils.sheet_to_html(ws, { header: '', footer: '' })) : ''
})

/** Word 页面按原尺寸排版，容器窄时整页等比缩小（不压页宽，避免正文被挤成一窄条） */
let docxObserver: ResizeObserver | null = null
function fitDocx() {
  const box = docxRef.value
  const wrap = box?.querySelector<HTMLElement>('.docx-wrapper')
  const page = wrap?.querySelector<HTMLElement>('section.docx')
  if (!box || !wrap || !page) return
  wrap.style.zoom = ''
  const scale = Math.min(1, (box.clientWidth - 24) / (page.offsetWidth + 32))
  wrap.style.zoom = scale < 1 ? String(scale) : ''
}

async function fetchBlob() {
  const res = await fetch(props.fileUrl, { credentials: 'same-origin' })
  if (!res.ok) throw new Error('文件读取失败')
  return res.arrayBuffer()
}

async function load() {
  error.value = ''
  slideCount.value = 0
  cur.value = 1
  workbook.value = null
  sheetNames.value = []
  sheetIdx.value = 0
  const k = kind.value
  if (!['slides', 'doc', 'docx', 'sheet'].includes(k)) return
  loading.value = true
  try {
    if (k === 'slides') {
      showMode.value = false
      showPos.value = ''
      showNote.value = ''
      const { data } = await http.get(props.previewApi, { timeout: 240000, params: slideMode ? { mode: slideMode } : {} })
      previewVer.value = data.data.ver || ''
      if (data.data.kind === 'show') {
        showRatio.value = Number(data.data.ratio) || 16 / 9
        showMode.value = true
        showAvailable.value = true
      } else {
        slideCount.value = data.data.count || 0
        if (data.data.showError) showNote.value = data.data.showError
        else if (slideMode === 'images') showAvailable.value = true
        else showNote.value = '服务器没有安装 LibreOffice，PPT 按图片显示（不播放动画）'
      }
    } else if (k === 'doc') {
      const { data } = await http.get(props.previewApi, { timeout: 180000 })
      previewVer.value = data.data.ver || ''
    } else if (k === 'docx') {
      const [buf, lib] = await Promise.all([fetchBlob(), import('docx-preview')])
      loading.value = false
      await nextTick()
      if (docxRef.value) {
        docxRef.value.innerHTML = ''
        await lib.renderAsync(buf, docxRef.value, undefined, { inWrapper: true, ignoreLastRenderedPageBreak: true, experimental: true })
        requestAnimationFrame(fitDocx)
        docxObserver?.disconnect()
        docxObserver = new ResizeObserver(() => fitDocx())
        docxObserver.observe(docxRef.value)
      }
    } else if (k === 'sheet') {
      const [buf, lib] = await Promise.all([fetchBlob(), import('xlsx')])
      XLSX = lib
      const wb = lib.read(buf, { type: 'array', sheetRows: 2000 })
      sheetNames.value = wb.SheetNames
      workbook.value = wb
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || '无法在线预览，请下载后查看'
  } finally {
    loading.value = false
  }
}

function toggleFull() {
  if (document.fullscreenElement) { document.exitFullscreen(); return }
  if (isFull.value) { isFull.value = false; return }   // 网页内铺满模式
  const req = boxRef.value?.requestFullscreen?.()
  // 浏览器不允许全屏（内嵌窗口、策略限制等）时，退而铺满整个网页
  if (!req) enterPageFull()
  else req.catch(enterPageFull)
}
function enterPageFull() {
  isFull.value = true
  focusStage()
}
function focusStage() {
  nextTick(() => {
    if (showMode.value) focusShow()
    else boxRef.value?.querySelector<HTMLElement>('.fp-stage')?.focus()
  })
}
function onFsChange() {
  isFull.value = document.fullscreenElement === boxRef.value
  if (isFull.value) focusStage()
}
watch(isFull, full => {
  document.body.style.overflow = full ? 'hidden' : ''   // 网页内铺满时不显示外层页面的滚动条
  if (kind.value === 'docx') nextTick(fitDocx)
})
/** 全屏放映时不用先点幻灯片，键盘直接翻页；Esc 退出网页内铺满 */
function onDocKey(e: KeyboardEvent) {
  if (!isFull.value) return
  if (e.key === 'Escape' && !document.fullscreenElement) { isFull.value = false; return }
  if ((e.target as HTMLElement)?.closest?.('.fp-stage')) return   // 舞台自己处理
  onKey(e)
}

onMounted(() => {
  document.addEventListener('fullscreenchange', onFsChange)
  document.addEventListener('keydown', onDocKey)
  window.addEventListener('message', onShowMessage)
  load()
})
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFsChange)
  document.removeEventListener('keydown', onDocKey)
  window.removeEventListener('message', onShowMessage)
  if (isFull.value) document.body.style.overflow = ''
  docxObserver?.disconnect()
  showObserver?.disconnect()
})
watch(() => [props.fileUrl, props.fileName], load)
</script>

<style scoped>
.file-preview {
  border: 1.5px solid var(--el-border-color); border-radius: 10px; background: var(--el-bg-color);
  overflow: hidden; margin-top: 8px; display: flex; flex-direction: column; position: relative;
}
.fp-bar {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px; flex-wrap: wrap;
  border-bottom: 1px dashed var(--el-border-color); background: var(--el-fill-color-lighter);
}
.fp-title { font-weight: 600; color: var(--el-text-color-primary); font-size: 14px; }
.fp-count { font-variant-numeric: tabular-nums; font-size: 13px; min-width: 56px; text-align: center; }
.fp-spacer { flex: 1; }
.fp-loading, .fp-error { padding: 48px 16px; text-align: center; color: var(--el-text-color-secondary); font-size: 14px; }
.fp-error { color: var(--el-color-danger); }
.fp-stage {
  position: relative; background: #2b2b2b; display: flex; align-items: center; justify-content: center;
  aspect-ratio: 16 / 9; max-height: 70vh; cursor: pointer; outline: none; user-select: none;
}
.fp-slide { max-width: 100%; max-height: 100%; object-fit: contain; box-shadow: 0 2px 12px rgba(0,0,0,.4); }
.fp-preload { display: none; }
.fp-hint { font-size: 12px; color: var(--el-text-color-secondary); }
.fp-show-stage { cursor: default; background: #000; }
.fp-show { position: absolute; left: 50%; top: 50%; transform-origin: center center; border: 0; display: block; background: #000; }
.fp-note { padding: 6px 12px; font-size: 12px; color: var(--el-text-color-secondary); background: var(--el-fill-color-lighter); border-top: 1px dashed var(--el-border-color); }
.fp-thumbs { display: flex; gap: 8px; padding: 8px 12px; overflow-x: auto; background: var(--el-fill-color-lighter); }
.fp-thumbs img {
  height: 60px; border-radius: 4px; border: 2px solid transparent; cursor: pointer; opacity: .7; flex-shrink: 0;
}
.fp-thumbs img.on { border-color: var(--el-color-primary); opacity: 1; }
.fp-frame { width: 100%; height: 70vh; border: 0; background: #fff; }
.fp-image { text-align: center; padding: 8px; }
.fp-image img { max-width: 100%; max-height: 70vh; }
.fp-audio { width: 100%; margin: 12px 0; }
.fp-docx { max-height: 75vh; overflow: auto; background: #eee; }
.fp-docx :deep(.docx-wrapper) { padding: 16px; }
.fp-docx :deep(.docx-wrapper > section.docx) { box-shadow: 0 1px 6px rgba(0,0,0,.15); margin-bottom: 16px; }
.fp-sheet { padding: 8px 12px; }
.fp-sheet-tabs { margin-bottom: 8px; }
.fp-sheet-body { max-height: 70vh; overflow: auto; }
.fp-sheet-body :deep(table) { border-collapse: collapse; font-size: 13px; }
.fp-sheet-body :deep(td) { border: 1px solid var(--el-border-color); padding: 3px 8px; white-space: nowrap; }

/* 全屏：幻灯片铺满黑底，其它类型占满屏幕 */
.file-preview.fullscreen { border-radius: 0; border: 0; height: 100vh; position: fixed; inset: 0; z-index: 3000; margin: 0; }
.file-preview.fullscreen .fp-stage { aspect-ratio: auto; max-height: none; flex: 1; background: #000; }
.file-preview.fullscreen.is-slides .fp-bar { opacity: .15; transition: opacity .2s; position: absolute; top: 0; left: 0; right: 0; z-index: 2; }
.file-preview.fullscreen.is-slides .fp-bar:hover { opacity: 1; }
.file-preview.fullscreen .fp-frame, .file-preview.fullscreen .fp-docx, .file-preview.fullscreen .fp-sheet-body { height: calc(100vh - 50px); max-height: none; }
.file-preview.fullscreen .fp-image img { max-height: 100vh; }
</style>
