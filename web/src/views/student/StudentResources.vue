<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>学习资源</span>
        <div class="filters">
          <el-select v-model="category" placeholder="全部分类" clearable class="w140">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="term" placeholder="全部学期" clearable class="w160">
            <el-option v-for="t in termsList" :key="t" :label="t" :value="t" />
          </el-select>
          <el-input v-model="kw" placeholder="搜索标题" clearable class="w180" />
        </div>
      </div>
    </template>

    <el-empty v-if="!shown.length" description="暂无资源" />
    <div class="grid">
      <el-card v-for="r in shown" :key="r.id" shadow="hover">
        <div class="r-head">
          <b>{{ r.title }}</b>
          <el-tag v-if="r.category" size="small">{{ r.category }}</el-tag>
        </div>
        <div class="muted">{{ r.teacherName }} · {{ r.termLabel || '未分学期' }} · {{ fmtTime(r.createdAt) }}</div>
        <div v-if="reading[r.id]" class="read">已阅读 {{ minutes(reading[r.id].seconds) }} · 打开 {{ reading[r.id].views }} 次</div>
        <p v-if="r.description" class="desc">{{ r.description }}</p>
        <div class="ops">
          <template v-if="r.fileName">
            <el-button size="small" type="primary" @click="download(r)">下载 {{ fmtSize(r.fileSize) }}</el-button>
            <el-button v-if="previewable(r.fileName)" size="small" @click="openReader(r)">在线阅读</el-button>
          </template>
          <el-button v-if="r.linkUrl" size="small" tag="a" :href="r.linkUrl" target="_blank" @click="markOpen(r)">打开链接</el-button>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="readerVisible" :title="current?.title" fullscreen append-to-body destroy-on-close @closed="stopTimer">
      <div class="reader-bar">
        <span class="muted">本次阅读计时：{{ minutes(sessionSeconds) }}（页面切到后台时暂停）</span>
        <span v-if="current && reading[current.id]" class="muted">累计 {{ minutes(reading[current.id].seconds) }}</span>
      </div>
      <iframe v-if="current" class="reader" :src="fileUrl(`/resources/${current.id}/file?inline=true`)" />
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg, fileUrl } from '../../api/client'
import { fmtSize, fmtTime } from '../../utils/format'

const list = ref<any[]>([])
const category = ref<string>()
const term = ref<string>()
const kw = ref('')

const categories = computed(() => [...new Set(list.value.map(r => r.category).filter(Boolean))])
const termsList = computed(() => [...new Set(list.value.map(r => r.termLabel).filter(Boolean))])
const shown = computed(() => list.value.filter(r =>
  (!category.value || r.category === category.value) &&
  (!term.value || r.termLabel === term.value) &&
  (!kw.value || r.title.includes(kw.value))))

function previewable(name: string) {
  return /\.(pdf|png|jpe?g|gif|bmp|webp|svg|mp4|webm|mp3|txt|html?)$/i.test(name)
}

// ---- 阅读计时（原“阅读时间 分钟”）：打开记一次，阅读中每 20 秒上报，服务器按实际间隔累计 ----
const reading = ref<Record<number, any>>({})
const readerVisible = ref(false)
const current = ref<any>(null)
const sessionSeconds = ref(0)
let beatTimer: number | undefined
let tickTimer: number | undefined

const minutes = (s: number) => (s < 60 ? `${s} 秒` : `${Math.floor(s / 60)} 分 ${s % 60} 秒`)

async function markOpen(r: any) {
  try {
    reading.value[r.id] = await api.post(`/student/resources/${r.id}/open`)
  } catch { /* 计时失败不影响阅读 */ }
}

async function openReader(r: any) {
  current.value = r
  sessionSeconds.value = 0
  readerVisible.value = true
  await markOpen(r)
  beatTimer = window.setInterval(async () => {
    if (document.hidden || !current.value) return
    try { reading.value[current.value.id] = await api.post(`/student/resources/${current.value.id}/read`) } catch { /* ignore */ }
  }, 20000)
  tickTimer = window.setInterval(() => { if (!document.hidden) sessionSeconds.value++ }, 1000)
}

async function stopTimer() {
  window.clearInterval(beatTimer)
  window.clearInterval(tickTimer)
  if (current.value) {
    try { reading.value[current.value.id] = await api.post(`/student/resources/${current.value.id}/read`) } catch { /* ignore */ }
  }
  current.value = null
}
onBeforeUnmount(() => { window.clearInterval(beatTimer); window.clearInterval(tickTimer) })

async function download(r: any) {
  try {
    await api.download(`/resources/${r.id}/file`, r.fileName)
  } catch (err) {
    ElMessage.error(errMsg(err, '下载失败'))
  }
}

onMounted(async () => {
  list.value = await api.get('/student/resources')
  reading.value = await api.get('/student/resources/reading')
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.w140 { width: 140px; }
.w160 { width: 160px; }
.w180 { width: 180px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }
.r-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.desc { font-size: 13px; color: var(--el-text-color-regular); margin: 8px 0; white-space: pre-wrap; }
.ops { margin-top: 8px; display: flex; gap: 6px; flex-wrap: wrap; }
.read { color: var(--el-color-success); font-size: 12px; margin-top: 2px; }
.reader-bar { display: flex; justify-content: space-between; margin-bottom: 8px; }
.reader { width: 100%; height: calc(100vh - 140px); border: 1px solid var(--el-border-color); border-radius: 6px; }
</style>
