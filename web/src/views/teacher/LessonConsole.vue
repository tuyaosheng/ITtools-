<template>
  <div v-if="lesson" class="console">
    <el-card shadow="never" class="head">
      <div class="head-row">
        <div>
          <div class="title">《{{ lesson.courseName }}》 <el-tag :type="active ? 'success' : 'info'" size="small">{{ active ? '上课中' : '已下课' }}</el-tag></div>
          <div class="meta">
            {{ lesson.className }} · {{ fmtTime(lesson.startedAt) }}{{ lesson.endedAt ? ' ~ ' + fmtTime(lesson.endedAt) : '' }}
            · 分组模型：{{ lesson.groupModelName || '不分组' }}
          </div>
        </div>
        <div class="stats">
          <div class="stat"><b>{{ present }}</b><span>出席</span></div>
          <div class="stat"><b>{{ notYet }}</b><span>未到</span></div>
          <div class="stat"><b>{{ leave }}</b><span>请假</span></div>
          <el-button v-if="active" type="danger" @click="endLesson">下课</el-button>
          <el-button v-else @click="router.push('/teacher/lessons')">返回记录</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="学生与考勤" name="students">
          <div class="tab-ops">
            <el-select v-model="groupModelId" clearable placeholder="不分组" size="small" class="w180">
              <el-option v-for="g in groupModels" :key="g.id" :label="g.name" :value="g.id" />
            </el-select>
            <el-button size="small" @click="loadGroups">按机位重新分组</el-button>
            <el-button size="small" @click="refresh">刷新</el-button>
            <span class="muted">每 5 秒自动刷新</span>
          </div>
          <el-table :data="attendance" size="small" stripe>
            <el-table-column prop="studentNo" label="学号" width="70" />
            <el-table-column prop="name" label="姓名" width="90" />
            <el-table-column label="考勤" width="130">
              <template #default="{ row }: { row: any }">
                <el-select :model-value="row.status" size="small" placeholder="未进课" @change="(v: string) => patch(row, { status: v })">
                  <el-option label="出席" value="PRESENT" />
                  <el-option label="缺席" value="ABSENT" />
                  <el-option label="请假" value="LEAVE" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="pcName" label="机位" width="90" />
            <el-table-column label="进课时间" width="140">
              <template #default="{ row }: { row: any }">{{ fmtTime(row.joinedAt) }}</template>
            </el-table-column>
            <el-table-column label="小组" width="110">
              <template #default="{ row }: { row: any }">
                <el-input-number :model-value="row.groupNo" :min="0" :max="50" size="small" controls-position="right"
                  class="w90" @change="(v: number) => patch(row, { groupNo: v })" />
              </template>
            </el-table-column>
            <el-table-column label="课堂得分" width="120">
              <template #default="{ row }: { row: any }">
                <el-input-number :model-value="row.score" :min="0" :max="100" size="small" controls-position="right"
                  class="w90" @change="(v: number) => patch(row, { score: v })" />
              </template>
            </el-table-column>
            <el-table-column label="评语" min-width="160">
              <template #default="{ row }: { row: any }">
                <el-input :model-value="row.comment" size="small" placeholder="课堂评语"
                  @change="(v: string) => patch(row, { comment: v })" />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="座位图" name="seats">
          <el-empty v-if="!seatMap?.room" description="该班级未配置上课机房（管理员在“固定机位”中设置）" />
          <template v-else>
            <div class="podium">讲 台 · {{ seatMap.room.name }}</div>
            <div class="grid" :style="{ gridTemplateColumns: `repeat(${seatMap.room.cols}, minmax(84px, 1fr))` }">
              <template v-for="r in seatMap.room.rows" :key="'r' + r">
                <div v-for="c in seatMap.room.cols" :key="`${r}-${c}`" class="seat" :class="seatClass(r, c)">
                  <template v-if="seatAt(r, c)">
                    <div class="pc">{{ seatAt(r, c).pcName }}</div>
                    <div class="who">{{ studentAt(seatAt(r, c).pcName)?.name || '空闲' }}</div>
                    <div v-if="studentAt(seatAt(r, c).pcName)?.groupNo" class="grp">第 {{ studentAt(seatAt(r, c).pcName).groupNo }} 组</div>
                    <div v-if="studentAt(seatAt(r, c).pcName)" class="flags">
                      <span class="star" :class="{ on: studentAt(seatAt(r, c).pcName).attention }" title="关注"
                        @click="toggleAttention(studentAt(seatAt(r, c).pcName))">★</span>
                      <template v-if="flagOf(studentAt(seatAt(r, c).pcName))">
                        <span v-if="flagOf(studentAt(seatAt(r, c).pcName)).top10" class="fl top">前10</span>
                        <span v-if="flagOf(studentAt(seatAt(r, c).pcName)).essence > 0" class="fl ess">精品</span>
                        <span v-if="flagOf(studentAt(seatAt(r, c).pcName)).ungraded > 0" class="fl todo">待阅</span>
                        <span v-else-if="flagOf(studentAt(seatAt(r, c).pcName)).works > 0" class="fl read">已阅</span>
                      </template>
                    </div>
                  </template>
                </div>
              </template>
            </div>
            <div class="legend">
              <span class="dot on" />有学生登录 <span class="dot" />空闲机位
              <span class="fl top">前10</span>课堂总分前 10 名 <span class="fl ess">精品</span>有精华作品
              <span class="fl todo">待阅</span>有作品未批阅 <span class="fl read">已阅</span>作品已全部批阅 <span class="star on">★</span>关注（点击切换）
            </div>
            <div v-if="seatAbsent.length" class="absent">
              <b>缺席 / 请假（{{ seatAbsent.length }}）：</b>
              <el-tag v-for="a in seatAbsent" :key="a.studentId" size="small" :type="a.status === 'LEAVE' ? 'warning' : 'danger'" class="atag">
                {{ a.name }}{{ a.status === 'LEAVE' ? `（请假${a.leaveReason ? '·' + a.leaveReason : ''}）` : '' }}
              </el-tag>
            </div>
          </template>
        </el-tab-pane>

        <el-tab-pane label="课堂页面" name="pages">
          <div class="pages-layout">
            <div class="page-list">
              <div v-for="(p, i) in lesson.pages" :key="p.id" class="page-row" :class="{ active: p.id === currentPage?.id }" @click="openPage(p)">
                <span class="seq">{{ i + 1 }}</span>
                <div class="pinfo">
                  <div>{{ p.title }}</div>
                  <div class="muted">{{ pageTypes[p.pageType]?.label }}</div>
                </div>
                <el-switch :model-value="p.open" size="small" inline-prompt active-text="开" inactive-text="关"
                  @click.stop @change="(v: boolean) => togglePage(p, v)" />
              </div>
            </div>
            <div class="page-view">
              <el-empty v-if="!currentPage" description="点击左侧页面查看内容与学生完成情况" />
              <template v-else>
                <h3>{{ currentPage.title }}</h3>
                <PageContent :page="currentPage" />
                <component :is="pageTypes[currentPage.pageType]?.teacher" v-if="pageTypes[currentPage.pageType]?.teacher"
                  :lesson-id="lesson.id" :page="currentPage" :lesson="lesson" />
              </template>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane v-for="t in consoleTabs" :key="t.name" :label="t.label" :name="t.name" lazy>
          <component :is="t.component" :lesson="lesson" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'
import { pageTypes } from '../../modules/pageTypes'
import { consoleTabs } from '../../modules/consoleTabs'
import PageContent from '../../components/PageContent.vue'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const lesson = ref<any>(null)
const attendance = ref<any[]>([])
const seatMap = ref<any>(null)
const groupModels = ref<any[]>([])
const groupModelId = ref<number | undefined>()
const tab = ref('students')
const currentPage = ref<any>(null)
let timer: number | undefined

const active = computed(() => lesson.value?.status === 'ACTIVE')
const present = computed(() => attendance.value.filter(a => a.status === 'PRESENT').length)
const leave = computed(() => attendance.value.filter(a => a.status === 'LEAVE').length)
const notYet = computed(() => attendance.value.filter(a => !a.status || a.status === 'ABSENT').length)

async function loadLesson() {
  lesson.value = await api.get(`/teacher/lessons/${id}`)
  groupModelId.value = lesson.value.groupModelId ?? undefined
}

async function refresh() {
  attendance.value = await api.get(`/teacher/lessons/${id}/attendance`)
  if (tab.value === 'seats') await loadSeats()
}

async function loadSeats() {
  const [m, f] = await Promise.all([api.get(`/teacher/lessons/${id}/seat-map`), api.get(`/teacher/lessons/${id}/seat-flags`).catch(() => [])])
  seatMap.value = m
  seatFlags.value = Object.fromEntries((f as any[]).map(x => [x.studentId, x]))
}

const seatFlags = ref<Record<number, any>>({})
const flagOf = (a: any) => seatFlags.value[a.studentId]
const seatAbsent = computed(() => (seatMap.value?.attendance || []).filter((a: any) => a.status !== 'PRESENT'))

async function toggleAttention(a: any) {
  await patch(a, { attention: !a.attention })
}

async function patch(row: any, body: any) {
  try {
    await api.put(`/teacher/lessons/${id}/attendance/${row.studentId}`, body)
    Object.assign(row, body)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function loadGroups() {
  attendance.value = await api.post(`/teacher/lessons/${id}/load-groups`, null, groupModelId.value ? { groupModelId: groupModelId.value } : undefined)
  await loadLesson()
  ElMessage.success('已按机位重新分组')
}

async function togglePage(p: any, open: boolean) {
  await api.put(`/teacher/lessons/${id}/pages/${p.id}`, { open })
  p.open = open
}

async function openPage(p: any) {
  const full = await api.get(`/teacher/courses/${lesson.value.courseId}`)
  currentPage.value = full.pages.find((x: any) => x.id === p.id)
}

async function endLesson() {
  try {
    const { value } = await ElMessageBox.prompt('下课后未进课的学生将记为缺席。可填写本课小结（可选）：', '确认下课',
      { inputType: 'textarea', confirmButtonText: '下课' })
    await api.post(`/teacher/lessons/${id}/end`, { summary: value })
    ElMessage.success('已下课')
    await loadLesson()
    await refresh()
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(errMsg(err))
  }
}

const seatAt = (r: number, c: number) => seatMap.value?.room?.seats.find((s: any) => s.row === r && s.col === c)
const studentAt = (pc: string) => seatMap.value?.attendance.find((a: any) => a.pcName === pc && a.status === 'PRESENT')
const seatClass = (r: number, c: number) => {
  const s = seatAt(r, c)
  if (!s) return 'empty'
  return studentAt(s.pcName) ? 'on' : 'idle'
}

onMounted(async () => {
  await loadLesson()
  groupModels.value = await api.get('/teacher/lab/group-models')
  await loadSeats()
  await refresh()
  timer = window.setInterval(() => { if (active.value) refresh() }, 5000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.console { display: flex; flex-direction: column; gap: 16px; }
.head-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.title { font-size: 18px; font-weight: 600; }
.meta { color: #909399; font-size: 13px; margin-top: 6px; }
.stats { display: flex; align-items: center; gap: 18px; }
.stat { display: flex; flex-direction: column; align-items: center; }
.stat b { font-size: 22px; color: var(--el-color-primary); }
.stat span { font-size: 12px; color: #909399; }
.tab-ops { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.w180 { width: 180px; }
.w90 { width: 90px; }
.muted { color: #a8abb2; font-size: 12px; }
.podium { text-align: center; background: #e5eaf3; border-radius: 6px; padding: 6px; margin-bottom: 12px; color: #606266; }
.grid { display: grid; gap: 8px; }
.seat { border-radius: 8px; padding: 8px; min-height: 64px; text-align: center; font-size: 12px; }
.seat.empty { background: transparent; }
.seat.idle { background: #f4f4f5; color: #909399; border: 1px dashed #dcdfe6; }
.seat.on { background: #f0f9eb; border: 1px solid #b3e19d; color: #303133; }
.pc { font-weight: 600; }
.who { margin-top: 4px; font-size: 13px; }
.grp { color: var(--el-color-primary); margin-top: 2px; }
.legend { margin-top: 10px; font-size: 12px; color: #909399; display: flex; align-items: center; gap: 6px; }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; background: #f4f4f5; border: 1px dashed #dcdfe6; margin-left: 12px; }
.dot.on { background: #f0f9eb; border: 1px solid #b3e19d; margin-left: 0; }
.pages-layout { display: grid; grid-template-columns: 280px 1fr; gap: 16px; }
.page-row { display: flex; align-items: center; gap: 10px; padding: 8px; border-radius: 8px; cursor: pointer; }
.page-row:hover { background: #f5f7fa; }
.page-row.active { background: var(--el-color-primary-light-9); }
.seq { width: 22px; height: 22px; border-radius: 50%; background: #e5eaf3; display: flex; align-items: center; justify-content: center; font-size: 12px; }
.pinfo { flex: 1; }
.page-view h3 { margin-top: 0; }
@media (max-width: 1000px) { .pages-layout { grid-template-columns: 1fr; } }
.flags { display: flex; gap: 3px; justify-content: center; align-items: center; margin-top: 2px; flex-wrap: wrap; }
.star { cursor: pointer; color: #c0c4cc; font-size: 13px; }
.star.on { color: #e6a23c; }
.fl { font-size: 10px; padding: 0 3px; border-radius: 3px; line-height: 15px; }
.fl.top { background: #fdf6ec; color: #e6a23c; }
.fl.ess { background: #fef0f0; color: #f56c6c; }
.fl.todo { background: #ecf5ff; color: #409eff; }
.fl.read { background: #f0f9eb; color: #67c23a; }
.absent { margin-top: 10px; font-size: 13px; }
.atag { margin: 0 4px 4px 0; }
</style>
