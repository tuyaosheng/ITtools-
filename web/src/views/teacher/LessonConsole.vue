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
            
          </div>
          <el-table :data="attendance" size="small" stripe>
            <el-table-column prop="studentNo" label="班内学号" width="84" />
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
          <el-empty v-if="!seatMap?.room" description="该班级还没有配置机房" />
          <template v-else>
            <!-- 平时只看；“管理”才能点选座位，底部批量暂停 / 恢复网络 -->
            <div class="seatbar">
              <template v-if="!managing">
                <span class="chip">{{ seatMap.room.name }}</span>
                <span class="chip ok">{{ seatOnline }} / {{ seatList.length }} 台已登录</span>
                <span v-if="net.enabled" class="chip" :class="net.reachable ? 'ok' : 'bad'">
                  {{ net.reachable ? `网络控制器已连接 · ${seatPowered} 台开机` : '连不上网络控制器' }}
                </span>
                <el-button v-if="net.enabled && active" size="small" type="primary" @click="setManaging(true)">管理</el-button>
              </template>
              <template v-else>
                <el-button size="small" @click="selectSeats(() => true)">全选</el-button>
                <el-button size="small" @click="selectSeats(s => !!studentAt(s.pcName))">已登录</el-button>
                <el-button size="small" @click="selectSeats(s => !studentAt(s.pcName))">未登录</el-button>
                <el-button size="small" @click="selectSeats(s => netOf(s) === 'off')">已断网</el-button>
                <el-button size="small" @click="selected = new Set()">清空</el-button>
                <span class="muted">点座位选中，可多选</span>
              </template>
              <span class="grow" />
              <el-button v-if="net.enabled" link type="primary" size="small" @click="loadNet">刷新网络状态</el-button>
            </div>

            <div class="podium">讲 台</div>
            <div class="seatgrid" :class="{ managing }"
              :style="{ gridTemplateColumns: `repeat(${seatMap.room.cols}, minmax(96px, 1fr))` }">
              <template v-for="r in seatMap.room.rows" :key="'r' + r">
                <template v-for="c in seatMap.room.cols" :key="`${r}-${c}`">
                  <div v-if="seatAt(r, c)" class="seat" :class="seatClass(seatAt(r, c))"
                    :title="`${seatAt(r, c).pcName}  IP ${seatAt(r, c).ipAddress}  第${r}排第${c}列`"
                    @click="toggleSel(seatAt(r, c))">
                    <div class="no"><span>{{ r }}-{{ c }}</span><span>{{ ipTail(seatAt(r, c)) }}</span></div>
                    <template v-if="studentAt(seatAt(r, c).pcName)">
                      <div class="who">
                        <UserAvatar :user-id="studentAt(seatAt(r, c).pcName).hasAvatar ? studentAt(seatAt(r, c).pcName).studentId : undefined"
                          :name="studentAt(seatAt(r, c).pcName).name" :size="26" />
                        <span class="nm">{{ studentAt(seatAt(r, c).pcName).name }}</span>
                        <span class="star" :class="{ on: studentAt(seatAt(r, c).pcName).attention }" title="关注（点击切换）"
                          @click.stop="toggleAttention(studentAt(seatAt(r, c).pcName))">★</span>
                      </div>
                      <div class="sub">
                        <span>{{ seatAt(r, c).pcName }}</span>
                        <span v-if="studentAt(seatAt(r, c).pcName).groupNo" class="grp">第{{ studentAt(seatAt(r, c).pcName).groupNo }}组</span>
                      </div>
                      <div class="flags">
                        <template v-if="flagOf(studentAt(seatAt(r, c).pcName))">
                          <span v-if="flagOf(studentAt(seatAt(r, c).pcName)).top10" class="fl top">前10</span>
                          <span v-if="flagOf(studentAt(seatAt(r, c).pcName)).essence > 0" class="fl ess">精品</span>
                          <span v-if="flagOf(studentAt(seatAt(r, c).pcName)).ungraded > 0" class="fl todo">待阅</span>
                          <span v-else-if="flagOf(studentAt(seatAt(r, c).pcName)).works > 0" class="fl read">已阅</span>
                        </template>
                        <span class="grow" />
                        <span v-if="net.enabled" class="net" :class="netOf(seatAt(r, c))">{{ netText(seatAt(r, c)) }}</span>
                      </div>
                    </template>
                    <template v-else>
                      <div class="nm idle-nm">{{ seatAt(r, c).pcName }}</div>
                      <div class="sub">未登录</div>
                      <div v-if="net.enabled" class="flags"><span class="grow" /><span class="net" :class="netOf(seatAt(r, c))">{{ netText(seatAt(r, c)) }}</span></div>
                    </template>
                  </div>
                  <div v-else class="seat gap" />
                </template>
              </template>
            </div>
            <div class="legend">
              <span class="fl top">前10</span>课堂总分前 10 <span class="fl ess">精品</span>有精华作品
              <span class="fl todo">待阅</span>有作品未批阅 <span class="fl read">已阅</span>已全部批阅 <span class="star on">★</span>关注
              <template v-if="net.enabled">· 🌐已开 ⛔已断 —没下发过</template>
              <Hint>灰色虚线的座位还没有学生登录；点 ★ 切换关注。<template v-if="net.enabled">网络状态是本平台最后一次给这台电脑下发的指令，不是实时探测；上课中点“管理”可以批量暂停 / 恢复网络。</template></Hint>
            </div>
            <div v-if="seatAbsent.length" class="absent">
              <b>缺席 / 请假（{{ seatAbsent.length }}）：</b>
              <el-tag v-for="a in seatAbsent" :key="a.studentId" size="small" :type="a.status === 'LEAVE' ? 'warning' : 'danger'" class="atag">
                {{ a.name }}{{ a.status === 'LEAVE' ? `（请假${a.leaveReason ? '·' + a.leaveReason : ''}）` : '' }}
              </el-tag>
            </div>

            <div v-if="managing" class="batchbar">
              <b>已选 {{ selected.size }} 台</b>
              <el-button size="small" :disabled="!selected.size" @click="batchNet('cut')">暂停网络</el-button>
              <el-button size="small" type="primary" :disabled="!selected.size" @click="batchNet('open')">恢复网络</el-button>
              <span class="grow" />
              <el-button size="small" @click="setManaging(false)">完成</el-button>
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
              <el-empty v-if="!currentPage" description="选择左侧页面" />
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
import UserAvatar from '../../components/UserAvatar.vue'
import Hint from '../../components/Hint.vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
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
const seatList = computed<any[]>(() => seatMap.value?.room?.seats || [])
const seatOnline = computed(() => seatList.value.filter(s => studentAt(s.pcName)).length)
const ipTail = (s: any) => (s?.ipAddress ? '.' + s.ipAddress.split('.').pop() : '')

// ---- 座位图：网络状态 + “管理”多选批量暂停 / 恢复网络 ----
const managing = ref(false)
const selected = ref<Set<string>>(new Set())
const net = reactive<{ enabled: boolean; reachable: boolean; status: Record<string, any>; last: Record<string, boolean> }>(
  { enabled: false, reachable: false, status: {}, last: {} })
const seatPowered = computed(() => seatList.value.filter(s => net.status[s.ipAddress]?.online).length)
function netOf(s: any): 'on' | 'off' | 'unk' {
  const v = net.last[s?.ipAddress]
  return v === true ? 'on' : v === false ? 'off' : 'unk'
}
const netText = (s: any) => ({ on: '🌐已开', off: '⛔已断', unk: '—' }[netOf(s)])
const seatClass = (s: any) => ({
  idle: !studentAt(s.pcName),
  on: !!studentAt(s.pcName),
  cut: netOf(s) === 'off',
  sel: selected.value.has(s.ipAddress),
})

function setManaging(v: boolean) {
  managing.value = v
  selected.value = new Set()
}

function toggleSel(s: any) {
  if (!managing.value || !s?.ipAddress) return
  const n = new Set(selected.value)
  if (n.has(s.ipAddress)) n.delete(s.ipAddress)
  else n.add(s.ipAddress)
  selected.value = n
}

function selectSeats(fn: (s: any) => boolean) {
  selected.value = new Set(seatList.value.filter(fn).map(s => s.ipAddress))
}

async function loadNet() {
  try {
    Object.assign(net, await api.get(`/teacher/lessons/${id}/network/status`))
  } catch {
    net.enabled = false
  }
}

async function batchNet(action: 'open' | 'cut') {
  const list = seatList.value.filter(s => selected.value.has(s.ipAddress))
  const label = action === 'open' ? '恢复网络' : '暂停网络'
  const names = list.map(s => studentAt(s.pcName)?.name || s.pcName)
  try {
    await ElMessageBox.confirm(`对选中的 ${list.length} 台执行「${label}」？` + (list.length <= 8 ? `\n${names.join('、')}` : ''), label, { type: 'warning' })
  } catch {
    return
  }
  try {
    const r: any = await api.post(`/teacher/lessons/${id}/network/batch`, { action, ips: list.map(s => s.ipAddress) })
    if (r.failed?.length) ElMessage.warning(`已完成 ${r.count} 台，失败 ${r.failed.length} 台：${r.failed.join('、')}`)
    else ElMessage.success(`已完成 ${r.count} 台`)
    await loadNet()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => {
  await loadLesson()
  groupModels.value = await api.get('/teacher/lab/group-models')
  await loadSeats()
  await refresh()
  await loadNet()
  timer = window.setInterval(() => { if (active.value) refresh() }, 5000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.console { display: flex; flex-direction: column; gap: 16px; }
.head-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.title { font-size: 18px; font-weight: 600; }
.meta { color: var(--el-text-color-secondary); font-size: 13px; margin-top: 6px; }
.stats { display: flex; align-items: center; gap: 18px; }
.stat { display: flex; flex-direction: column; align-items: center; }
.stat b { font-size: 22px; color: var(--el-color-primary); }
.stat span { font-size: 12px; color: var(--el-text-color-secondary); }
.tab-ops { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.w180 { width: 180px; }
.w90 { width: 90px; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.pages-layout { display: grid; grid-template-columns: 280px 1fr; gap: 16px; }
.page-row { display: flex; align-items: center; gap: 10px; padding: 8px; border-radius: 8px; cursor: pointer; }
.page-row:hover { background: var(--el-fill-color-light); }
.page-row.active { background: var(--el-color-primary-light-9); }
.seq { width: 22px; height: 22px; border-radius: 50%; background: var(--el-fill-color); display: flex; align-items: center; justify-content: center; font-size: 12px; }
.pinfo { flex: 1; }
.page-view h3 { margin-top: 0; }
@media (max-width: 1000px) { .pages-layout { grid-template-columns: 1fr; } }
/* 座位图：与管理员端机房座位图（LabRoomView）同一套座位卡 */
.podium { text-align: center; background: var(--el-fill-color); border-radius: 6px; padding: 6px; margin-bottom: 12px; color: var(--el-text-color-regular); letter-spacing: 8px; }
.chip { font-size: 12px; padding: 2px 10px; border-radius: 999px; background: var(--el-fill-color); color: var(--el-text-color-regular); }
.chip.ok { background: var(--el-color-success-light-9); color: var(--el-color-success-dark-2); }
.chip.bad { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.seatbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.seatbar .el-button + .el-button { margin-left: 0; }
.grow { flex: 1; }
.seatgrid { display: grid; gap: 8px; padding: 14px; overflow-x: auto; background: var(--el-fill-color-lighter);
  border: 1.5px dashed var(--hb-line); border-radius: 10px; }
.seat { position: relative; min-height: 84px; padding: 5px 7px; border: 2px solid var(--el-border-color); border-radius: 10px;
  background: var(--hb-paper); font-size: 12px; user-select: none; overflow: hidden; transition: border-color .15s, box-shadow .15s; }
.seat.gap { visibility: hidden; }
.seatgrid.managing .seat:not(.gap) { cursor: pointer; }
.seatgrid.managing .seat:not(.gap):hover { border-color: var(--el-border-color-darker); }
.seat .no { display: flex; justify-content: space-between; font-family: Consolas, monospace; font-size: 11px; color: var(--el-text-color-secondary); }
.seat .who { display: flex; align-items: center; gap: 5px; margin: 3px 0 1px; min-width: 0; }
.seat .nm { flex: 1; min-width: 0; font-size: 15px; font-weight: 800; color: var(--hb-ink-deep); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.seat .idle-nm { display: block; margin: 4px 0 1px; font-size: 14px; color: var(--el-text-color-placeholder); }
.seat .sub { display: flex; justify-content: space-between; gap: 4px; font-size: 11px; color: var(--el-text-color-secondary); white-space: nowrap; overflow: hidden; }
.seat .grp { color: var(--el-color-primary); font-weight: 700; }
.seat .net { font-weight: 700; font-size: 11px; }
.seat .net.on { color: var(--el-color-success-dark-2); }
.seat .net.off { color: var(--el-color-danger); }
.seat .net.unk { color: var(--el-text-color-placeholder); }
.seat.on { border-color: var(--el-color-success-light-5); background: var(--el-color-success-light-9); }
.seat.idle { border-style: dashed; background: var(--el-fill-color-light); color: var(--el-text-color-placeholder); }
.seat.cut { background: var(--el-color-danger-light-9); }
.seat.sel { border-color: var(--el-color-primary); border-style: solid; background: var(--el-color-primary-light-9); box-shadow: 0 0 0 3px var(--el-color-primary-light-5); }
.seat.sel .no::before { content: "✓"; color: var(--el-color-primary); font-weight: 800; }
.flags { display: flex; gap: 3px; align-items: center; margin-top: 3px; flex-wrap: wrap; min-height: 15px; }
.star { cursor: pointer; color: var(--el-text-color-placeholder); font-size: 14px; line-height: 1; }
.star:hover { color: var(--el-color-warning-light-3); }
.star.on { color: var(--el-color-warning); }
.fl { font-size: 10px; padding: 0 4px; border-radius: 3px; line-height: 15px; }
.fl.top { background: var(--el-color-warning-light-9); color: var(--el-color-warning-dark-2); }
.fl.ess { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.fl.todo { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.fl.read { background: var(--el-color-success-light-9); color: var(--el-color-success-dark-2); }
.legend { margin-top: 8px; font-size: 12px; line-height: 1.8; color: var(--el-text-color-secondary); display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.legend .fl { margin-left: 6px; }
.batchbar { position: sticky; bottom: 12px; z-index: 5; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 14px;
  padding: 10px 16px; border-radius: 12px; background: var(--hb-ink-deep); color: var(--hb-paper); box-shadow: 4px 4px 0 var(--hb-line); }
.batchbar .el-button + .el-button { margin-left: 0; }
.absent { margin-top: 10px; font-size: 13px; }
.atag { margin: 0 4px 4px 0; }
</style>
