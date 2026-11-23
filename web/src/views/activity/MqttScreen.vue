<template>
  <div class="screen" :class="[theme, { compact }]" :style="{ '--fs': scale }">
    <header class="top">
      <div class="ttl">
        <div class="t1">物联网消息</div>
        <div class="t2">{{ data?.className }} {{ data?.courseName ? '·《' + data.courseName + '》' : '' }}</div>
      </div>
      <div class="proj">
        <span class="k">项目</span><b class="code">{{ data?.project || '—' }}</b>
        <span class="k">服务器</span><b class="code">{{ data?.server }}:{{ data?.port }}</b>
      </div>
      <div class="stat">
        <span class="pill">设备 <b>{{ shown.length }}</b></span>
        <span class="pill">在线连接 <b>{{ data?.clients ?? 0 }}</b></span>
        <span class="clock">{{ clock }}</span>
      </div>
    </header>

    <div class="bar" :class="{ hide: hideBar }">
      <el-radio-group v-model="mode" size="small">
        <el-radio-button value="device">按设备</el-radio-button>
        <el-radio-button value="pair" :disabled="!hasPairs">按配对</el-radio-button>
      </el-radio-group>
      <el-input v-model="filter" size="small" clearable placeholder="筛选设备名" class="flt" />
      <el-button size="small" @click="scale = Math.max(0.6, +(scale - 0.1).toFixed(1))">A-</el-button>
      <el-button size="small" @click="scale = Math.min(2.2, +(scale + 0.1).toFixed(1))">A+</el-button>
      <el-checkbox v-model="compact" size="small">只看最新一条</el-checkbox>
      <el-button size="small" @click="markNow">{{ since ? '显示全部消息' : '从现在开始看' }}</el-button>
      <el-button size="small" @click="theme = theme === 'dark' ? 'light' : 'dark'">{{ theme === 'dark' ? '浅色' : '深色' }}</el-button>
      <el-button size="small" type="primary" @click="toggleFull">{{ full ? '退出全屏' : '全屏' }}</el-button>
      <span class="hint">鼠标不动 5 秒后这一行自动隐藏</span>
    </div>

    <div v-if="data && !data.running" class="warn">内置 MQTT 服务器没有运行{{ data.error ? '：' + data.error : '' }}</div>
    <div v-if="data && !shown.length" class="empty">还没有设备发来消息<br><small>掌控板上传程序后，消息会出现在这里</small></div>

    <!-- 按设备 -->
    <div v-if="mode === 'device' || !hasPairs" class="grid">
      <DeviceTile v-for="d in shown" :key="d.device" :d="d" />
    </div>
    <!-- 按配对：同一对的设备排在一起 -->
    <div v-else class="pairs">
      <section v-for="p in pairBlocks" :key="p.key" class="pair">
        <h3>{{ p.title }}</h3>
        <div class="grid in-pair">
          <DeviceTile v-for="d in p.devices" :key="d.device" :d="d" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref, type PropType } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../../api/client'

/**
 * 物联网消息投屏大屏（像 SIoT 网页那样）：每台设备一块，最新消息放大显示，新消息闪一下；
 * 一直是数字的设备画出最近的变化曲线；按配对时 门铃3组、显示3组 的设备排在同一个框里。
 */
const route = useRoute()
const lessonId = route.params.id
const data = ref<any>(null)
const mode = ref<'device' | 'pair'>('device')
const filter = ref('')
const scale = ref(1)
const compact = ref(false)
const theme = ref<'dark' | 'light'>('dark')
const full = ref(false)
const since = ref<number | null>(null)
const clock = ref('')
const hideBar = ref(false)
const now = ref(Date.now())
const flashUntil = new Map<string, number>()
const lastSeen = new Map<string, string>()
let poll: any, tick: any, idle: any

const groupByNo = computed(() => {
  const m = new Map<number, any>()
  for (const g of data.value?.groups || []) m.set(g.groupNo, g)
  return m
})
const hasPairs = computed(() => (data.value?.groups || []).some((g: any) => g.pairNo))

/** 设备 → 组名、配对号：G03 是第 3 组；名字末尾有数字（Bell3、Door3）就当第 3 对。 */
function meta(device: string) {
  const gm = /^G0*(\d+)$/i.exec(device)
  if (gm) {
    const g = groupByNo.value.get(Number(gm[1]))
    return { label: g?.label || `第${gm[1]}组`, pair: g?.pairNo ?? null }
  }
  const tail = /(\d+)$/.exec(device)
  return { label: '', pair: tail ? Number(tail[1]) : null }
}

const shown = computed(() => {
  const kw = filter.value.trim().toLowerCase()
  return (data.value?.devices || [])
    .filter((d: any) => !kw || String(d.device).toLowerCase().includes(kw))
    .map((d: any) => {
      const msgs = since.value ? d.messages.filter((m: any) => new Date(m.createdAt).getTime() >= since.value!) : d.messages
      return { ...d, ...meta(d.device), messages: msgs, flash: (flashUntil.get(d.device) || 0) > now.value, now: now.value, compact: compact.value }
    })
    .filter((d: any) => !since.value || d.messages.length)
    .sort((a: any, b: any) => String(a.device).localeCompare(String(b.device), 'zh', { numeric: true }))
})

const pairBlocks = computed(() => {
  const m = new Map<string, { key: string; title: string; order: number; devices: any[] }>()
  for (const d of shown.value) {
    const key = d.pair == null ? 'other' : String(d.pair)
    if (!m.has(key)) {
      const names = (data.value?.groups || []).filter((g: any) => g.pairNo === d.pair).map((g: any) => g.label)
      m.set(key, { key, order: d.pair ?? 9999, title: d.pair == null ? '其他设备' : `第 ${d.pair} 对${names.length ? '　' + names.join(' ↔ ') : ''}`, devices: [] })
    }
    m.get(key)!.devices.push(d)
  }
  return [...m.values()].sort((a, b) => a.order - b.order)
})

async function load() {
  try {
    const d = await api.get(`/teacher/lessons/${lessonId}/mqtt`)
    for (const dev of d.devices || []) {
      const latest = dev.messages?.[0]?.createdAt
      const prev = lastSeen.get(dev.device)
      if (latest && prev !== undefined && latest !== prev) flashUntil.set(dev.device, Date.now() + 1500)
      if (latest) lastSeen.set(dev.device, latest)
    }
    data.value = d
  } catch { /* 下次再试 */ }
}

function markNow() { since.value = since.value ? null : Date.now() }

function toggleFull() {
  if (document.fullscreenElement) document.exitFullscreen()
  else document.documentElement.requestFullscreen?.()
}

function onMove() {
  hideBar.value = false
  clearTimeout(idle)
  idle = setTimeout(() => (hideBar.value = true), 5000)
}

onMounted(() => {
  document.title = '物联网消息 · 投屏'
  load()
  poll = setInterval(load, 1500)
  tick = setInterval(() => {
    now.value = Date.now()
    const d = new Date()
    const p = (n: number) => String(n).padStart(2, '0')
    clock.value = `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  }, 500)
  document.addEventListener('fullscreenchange', () => (full.value = !!document.fullscreenElement))
  window.addEventListener('mousemove', onMove)
  onMove()
})
onBeforeUnmount(() => {
  clearInterval(poll)
  clearInterval(tick)
  clearTimeout(idle)
  window.removeEventListener('mousemove', onMove)
})

// ---- 单台设备的卡片 ----
function ago(t: string, nowMs: number) {
  const s = Math.max(0, Math.round((nowMs - new Date(t).getTime()) / 1000))
  if (s < 60) return `${s} 秒前`
  if (s < 3600) return `${Math.floor(s / 60)} 分钟前`
  return `${Math.floor(s / 3600)} 小时前`
}
function hms(t: string) {
  const d = new Date(t)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
function spark(values: number[]) {
  const w = 220, hgt = 46
  const min = Math.min(...values), max = Math.max(...values)
  const span = max - min || 1
  const pts = values.map((v, i) => `${(i / Math.max(1, values.length - 1)) * w},${hgt - 4 - ((v - min) / span) * (hgt - 8)}`).join(' ')
  return h('svg', { viewBox: `0 0 ${w} ${hgt}`, class: 'spark', preserveAspectRatio: 'none' }, [
    h('polyline', { points: pts, fill: 'none', stroke: 'currentColor', 'stroke-width': 2.5, 'stroke-linejoin': 'round' })
  ])
}

const DeviceTile = defineComponent({
  props: { d: { type: Object as PropType<any>, required: true } },
  setup(props) {
    return () => {
      const d = props.d
      const latest = d.messages[0]
      const stale = !latest || props.d.now - new Date(latest.createdAt).getTime() > 60000
      const nums = d.messages.slice(0, 20).map((m: any) => String(m.payload).trim()).filter((s: string) => s !== '' && !isNaN(Number(s))).map(Number)
      const numeric = nums.length >= 3 && nums.length === Math.min(20, d.messages.length)
      return h('div', { class: ['tile', { flash: d.flash, stale }] }, [
        h('div', { class: 'tile-h' }, [
          h('b', { class: 'code dev' }, d.device),
          d.label ? h('span', { class: 'lbl' }, d.label) : null,
          h('span', { class: 'cnt' }, `${d.count} 条`)
        ]),
        h('div', { class: 'latest' }, latest ? String(latest.payload) : '—'),
        h('div', { class: 'when' }, latest ? `${hms(latest.createdAt)}　${ago(latest.createdAt, d.now)}` : ''),
        numeric && !d.compact ? spark(nums.slice().reverse()) : null,
        !d.compact && !numeric
          ? h('div', { class: 'hist' }, d.messages.slice(1, 6).map((m: any) => h('div', { class: 'row' }, [h('span', { class: 't' }, hms(m.createdAt)), h('span', { class: 'p' }, String(m.payload))])))
          : null
      ])
    }
  }
})
</script>

<style scoped>
.screen { --bg: #0f1724; --card: #172235; --ink: #e8eef8; --sub: #8fa3bf; --line: #24344d; --acc: #3ddc97; --flash: #ffd166;
  min-height: 100vh; background: var(--bg); color: var(--ink); padding: 14px 18px 24px; box-sizing: border-box; font-size: calc(16px * var(--fs)); }
.screen.light { --bg: #f4f6fa; --card: #ffffff; --ink: #1d2433; --sub: #6b778c; --line: #dfe4ec; --acc: #12a36d; --flash: #f2a900; }
.top { display: flex; align-items: center; gap: 24px; flex-wrap: wrap; border-bottom: 1px solid var(--line); padding-bottom: 10px; }
.t1 { font-size: 1.8em; font-weight: 800; letter-spacing: 2px; }
.t2 { color: var(--sub); font-size: .95em; }
.proj { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.k { color: var(--sub); font-size: .9em; }
.code { font-family: Consolas, 'Courier New', monospace; }
.proj .code { font-size: 1.5em; color: var(--acc); }
.stat { margin-left: auto; display: flex; gap: 10px; align-items: center; }
.pill { background: var(--card); border: 1px solid var(--line); border-radius: 999px; padding: 4px 12px; color: var(--sub); }
.pill b { color: var(--ink); font-size: 1.15em; }
.clock { font-family: Consolas, monospace; font-size: 1.6em; }
.bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin: 10px 0; transition: opacity .3s; }
.bar.hide { opacity: 0; pointer-events: none; }
.flt { width: 160px; }
.hint { color: var(--sub); font-size: 12px; }
.warn { background: #c0392b; color: #fff; padding: 8px 12px; border-radius: 8px; margin-bottom: 10px; }
.empty { text-align: center; color: var(--sub); font-size: 1.6em; margin-top: 18vh; line-height: 1.8; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(calc(280px * var(--fs)), 1fr)); gap: 14px; }
.pairs { display: flex; flex-direction: column; gap: 16px; }
.pair { border: 1px dashed var(--line); border-radius: 14px; padding: 8px 12px 12px; }
.pair h3 { margin: 2px 0 10px; font-size: 1.25em; color: var(--sub); font-weight: 700; }
.screen :deep(.tile) { background: var(--card); border: 2px solid var(--line); border-radius: 14px; padding: 12px 14px; display: flex; flex-direction: column; gap: 4px;
  transition: border-color .3s, box-shadow .3s, opacity .3s; min-width: 0; }
.screen :deep(.tile.flash) { border-color: var(--flash); box-shadow: 0 0 0 4px color-mix(in srgb, var(--flash) 35%, transparent); }
.screen :deep(.tile.stale) { opacity: .62; }
.screen :deep(.tile-h) { display: flex; align-items: baseline; gap: 8px; }
.screen :deep(.dev) { font-size: 1.25em; }
.screen :deep(.lbl) { color: var(--acc); font-weight: 700; }
.screen :deep(.cnt) { margin-left: auto; color: var(--sub); font-size: .85em; }
.screen :deep(.latest) { font-size: 2.6em; font-weight: 800; line-height: 1.15; word-break: break-all; color: var(--ink); }
.screen.compact :deep(.latest) { font-size: 3.2em; }
.screen :deep(.when) { color: var(--sub); font-size: .9em; font-family: Consolas, monospace; }
.screen :deep(.spark) { width: 100%; height: calc(46px * var(--fs)); color: var(--acc); margin-top: 4px; }
.screen :deep(.hist) { border-top: 1px dashed var(--line); margin-top: 4px; padding-top: 4px; }
.screen :deep(.row) { display: flex; gap: 8px; font-size: .95em; padding: 1px 0; }
.screen :deep(.row .t) { color: var(--sub); font-family: Consolas, monospace; flex: none; }
.screen :deep(.row .p) { word-break: break-all; }
</style>
