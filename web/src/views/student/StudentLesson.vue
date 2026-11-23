<template>
  <div v-if="lesson" class="lesson">
    <el-card shadow="never" class="bar">
      <div class="bar-row">
        <div>
          <span class="course">《{{ lesson.courseName }}》</span>
          <span class="meta">{{ lesson.teacherName }} · {{ lesson.className }}</span>
          <el-tag v-if="lesson.status !== 'ACTIVE'" type="info" size="small">已下课</el-tag>
        </div>
        <div class="meta">
          机位 {{ lesson.myAttendance?.pcName || '未登记' }}
          <template v-if="lesson.myAttendance?.groupNo"> · {{ lesson.myAttendance.groupLabel || `第 ${lesson.myAttendance.groupNo} 组` }}</template>
        </div>
      </div>
    </el-card>

    <!-- 掌控板编程要放下积木区和场景，右侧工具栏挪到下面 -->
    <div class="body" :class="{ wide: page?.pageType === 'MPYTHON' }">
      <el-card shadow="never" class="nav">
        <template #header>学习环节</template>
        <el-empty v-if="!pages.length" description="老师还没有开放页面" :image-size="50" />
        <div v-for="(p, i) in pages" :key="p.id" class="nav-item" :class="{ active: p.id === page?.id }" @click="openPage(p.id)">
          <span class="seq">{{ i + 1 }}</span>
          <div>
            <div>{{ p.title }}</div>
            <div class="type">{{ pageTypes[p.pageType]?.label }}</div>
          </div>
        </div>
        <el-button class="refresh" size="small" @click="loadPages">刷新</el-button>
      </el-card>

      <el-card ref="contentCard" shadow="never" class="content">
        <el-empty v-if="!page" description="请从左侧选择学习环节" />
        <template v-else>
          <h2 class="ptitle">{{ page.title }}</h2>
          <PageContent :page="page" />
          <component :is="pageTypes[page.pageType]?.student" v-if="pageTypes[page.pageType]?.student"
            :lesson-id="lesson.id" :page="page" :lesson="lesson" />
          <!-- 页面很长时（如任务单），做完不用滚回顶部找左边的环节列表 -->
          <div v-if="prevPage || nextPage" class="pager">
            <el-button v-if="prevPage" class="pager-prev" @click="openPage(prevPage.id)">
              ← 上一个：{{ prevPage.title }}
            </el-button>
            <span class="grow" />
            <el-button v-if="nextPage" type="primary" class="pager-next" @click="openPage(nextPage.id)">
              下一个：{{ nextPage.title }}<span class="pager-type">（{{ pageTypes[nextPage.pageType]?.label }}）</span> →
            </el-button>
          </div>
        </template>
      </el-card>

      <el-card shadow="never" class="tools">
        <el-tabs v-model="toolTab">
          <el-tab-pane v-for="t in tools" :key="t.name" :label="t.label" :name="t.name" lazy>
            <component :is="t.component" :lesson="lesson" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { pageTypes } from '../../modules/pageTypes'
import { closedFeatures, studentTools } from '../../modules/consoleTabs'
import PageContent from '../../components/PageContent.vue'

const route = useRoute()
const id = Number(route.params.id)
const lesson = ref<any>(null)
const pages = ref<any[]>([])
const page = ref<any>(null)
const toolTab = ref(studentTools[0]?.name)
// 老师设了“成绩开网”时，最前面多一个“完成情况”：自己和组员还差什么
const hasGate = ref(false)
const progressTool = { name: 'progress', label: '完成情况', component: defineAsyncComponent(() => import('../quiz/GroupProgress.vue')) }
// 教师关闭的功能（如课堂论坛）不显示对应标签
const tools = computed(() => [...(hasGate.value ? [progressTool] : []),
  ...studentTools.filter(t => !t.feature || !closedFeatures(lesson.value).includes(t.feature))])
async function checkGate() {
  try {
    const g = await api.get(`/student/lessons/${id}/net-gate/progress`)
    if (g?.enabled && !hasGate.value) { hasGate.value = true; toolTab.value = 'progress' }
    if (!g?.enabled) hasGate.value = false
  } catch { /* 忽略 */ }
}
let timer: number | undefined
let hbTimer: number | undefined

async function loadPages() {
  if (lesson.value?.status === 'ACTIVE') {
    // 顺带刷新教师对本堂课功能开关的改动
    api.get('/student/lessons/current').then((cur: any) => {
      if (cur && cur.id === id && lesson.value) lesson.value.disabledFeatures = cur.disabledFeatures
    }).catch(() => {})
  }
  pages.value = await api.get(`/student/lessons/${id}/pages`)
  if (page.value && !pages.value.some(p => p.id === page.value.id)) page.value = null
}

const contentCard = ref<any>(null)
const pageIndex = computed(() => pages.value.findIndex(p => p.id === page.value?.id))
const prevPage = computed(() => pageIndex.value > 0 ? pages.value[pageIndex.value - 1] : null)
const nextPage = computed(() => pageIndex.value >= 0 && pageIndex.value < pages.value.length - 1 ? pages.value[pageIndex.value + 1] : null)

async function openPage(pageId: number) {
  const switching = page.value != null && page.value.id !== pageId
  try {
    page.value = await api.get(`/student/lessons/${id}/pages/${pageId}`)
  } catch (err) {
    ElMessage.error(errMsg(err))
    return
  }
  // 从长页面底部切到别的环节时，回到新页面的开头
  if (switching) {
    await nextTick()
    const el: HTMLElement | undefined = contentCard.value?.$el
    if (el && el.getBoundingClientRect().top < 0) el.scrollIntoView({ block: 'start', behavior: 'smooth' })
  }
}

onMounted(async () => {
  checkGate()
  const cur = await api.get('/student/lessons/current')
  if (cur && cur.id === id) {
    lesson.value = cur.myAttendance?.status === 'PRESENT' ? cur : await api.post(`/student/lessons/${id}/join`)
  } else {
    // 已结束的课堂：只读查看
    const recs: any[] = await api.get('/student/records')
    const r = recs.find(x => x.lessonId === id)
    lesson.value = { id, courseName: r?.courseName, teacherName: r?.teacherName, status: 'ENDED', myAttendance: r }
  }
  await loadPages()
  // ?page= 指定打开的页面（如从“我的作品”补交入口跳转）
  const want = Number(route.query.page)
  if (pages.value.length) await openPage(pages.value.some(p => p.id === want) ? want : pages.value[0].id)
  timer = window.setInterval(() => { loadPages(); checkGate() }, 8000)
  // 在线监控心跳：上报当前查看的页面（教师"在线监控"与"学情监控·页面停留时间"使用）；切换页面时立即上报
  beat()
  hbTimer = window.setInterval(beat, 20000)
})
function beat() {
  if (lesson.value?.status === 'ACTIVE') {
    api.post(`/student/interact/lessons/${id}/heartbeat`, {
      pageId: page.value?.id ?? null, pageTitle: page.value?.title ?? '课堂首页', visible: !document.hidden
    }).catch(() => {})
  }
}
watch(() => page.value?.id, (v, old) => { if (v !== old && hbTimer) beat() })
// 切回/切走浏览器时立即上报，让后台时间不计入停留
document.addEventListener('visibilitychange', beat)
onBeforeUnmount(() => document.removeEventListener('visibilitychange', beat))
onBeforeUnmount(() => { window.clearInterval(timer); window.clearInterval(hbTimer) })
</script>

<style scoped>
.lesson { display: flex; flex-direction: column; gap: 12px; }
.bar :deep(.el-card__body) { padding: 12px 18px; }
.bar-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.course { font-size: 18px; font-weight: 600; margin-right: 10px; }
.meta { color: var(--el-text-color-secondary); font-size: 13px; margin-right: 8px; }
.body { display: grid; grid-template-columns: 220px 1fr 300px; gap: 12px; align-items: start; }
.nav-item { display: flex; gap: 10px; align-items: center; padding: 8px; border-radius: 8px; cursor: pointer; }
.nav-item:hover { background: var(--el-fill-color-light); }
.nav-item.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.seq { width: 22px; height: 22px; border-radius: 50%; background: var(--el-fill-color); display: flex; align-items: center; justify-content: center; font-size: 12px; flex-shrink: 0; }
.type { font-size: 12px; color: var(--el-text-color-secondary); }
.refresh { margin-top: 8px; width: 100%; }
.ptitle { margin-top: 0; }
.content { scroll-margin-top: 12px; }
.pager { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 24px; padding-top: 14px; border-top: 1.5px dashed var(--el-border-color); }
.pager .grow { flex: 1; }
.pager .el-button { max-width: 100%; white-space: normal; height: auto; min-height: 36px; text-align: left; line-height: 1.4; }
.pager-type { opacity: .8; font-size: 12px; }
/* 两栏时右侧工具栏排到下一行；左边“学习环节”占满两行，固定在屏幕上时不会盖住工具栏 */
.body.wide { grid-template-columns: 200px 1fr; }
.body.wide .nav { grid-row: span 2; }
.body.wide .tools { grid-column: 2; }
/* 左边“学习环节”跟着屏幕走：页面再长也能直接点到别的环节 */
@media (min-width: 761px) {
  .nav { position: sticky; top: 12px; max-height: calc(100vh - 24px); overflow-y: auto; }
}
@media (max-width: 1200px) { .body { grid-template-columns: 200px 1fr; } .nav { grid-row: span 2; } .tools { grid-column: 2; } }
@media (max-width: 760px) { .body { grid-template-columns: 1fr; } .nav { grid-row: auto; } .tools { grid-column: auto; } .pager .grow { display: none; } .pager .el-button { width: 100%; margin-left: 0; } }
</style>
