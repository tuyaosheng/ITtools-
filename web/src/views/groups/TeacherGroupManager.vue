<template>
  <div v-if="data" class="mgr" :class="{ mobile: isMobile }">
    <!-- ① 组数与人数 ② 开放 / 关闭自由选组 -->
    <div class="ctrl">
      <div class="setup">
        <div class="field">
          <span class="lbl">一共几组</span>
          <el-input-number v-model="count" :min="0" :max="30" :size="isMobile ? 'large' : 'default'" class="num" />
        </div>
        <div class="field">
          <span class="lbl">每组几人 <Hint>0 表示不限人数。学生加入已满的组会被拒绝；你手动调人不受限制。</Hint></span>
          <el-input-number v-model="maxSize" :min="0" :max="100" :size="isMobile ? 'large' : 'default'" class="num" />
        </div>
        <el-button type="primary" plain :disabled="!dirty" class="save" @click="saveCount">{{ dirty ? '保存设置' : '已保存' }}</el-button>
      </div>

      <div class="switcher" :class="{ open: data.joinOpen }">
        <div class="sw-text">
          <div class="sw-title">{{ data.joinOpen ? '自由选组 · 开放中' : '自由选组 · 已关闭' }}</div>
          <div class="sw-sub">
            <template v-if="data.locked">本节课开网要求全组完成，学生进组后不能自己换组</template>
            <template v-else-if="data.joinOpen">学生可以自己选组、随时换组</template>
            <template v-else>学生不能加入或换组，只有你能调整</template>
          </div>
        </div>
        <el-button :type="data.joinOpen ? 'danger' : 'success'" :size="isMobile ? 'large' : 'default'" class="sw-btn"
          :disabled="!count && !data.joinOpen" @click="toggleJoin">
          {{ data.joinOpen ? '关闭选组' : '开放选组' }}
        </el-button>
      </div>
    </div>

    <div class="summary">
      <span class="pill">全班 <b>{{ total }}</b> 人</span>
      <span class="pill ok">已分组 <b>{{ total - data.ungrouped.length }}</b></span>
      <span class="pill" :class="{ warn: data.ungrouped.length }">未分组 <b>{{ data.ungrouped.length }}</b></span>
      <span v-if="data.maxSize" class="pill">可容纳 <b>{{ data.groupCount * data.maxSize }}</b></span>
      <span class="grow" />
      <span class="vote">
        组长推选 <el-switch :model-value="data.voteOpen" size="small" @change="(v: any) => settings({ voteOpen: v })" />
      </span>
      <el-dropdown trigger="click" @command="moreCmd">
        <el-button size="small">更多操作 ▾</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="autoLeaders">按票数设组长</el-dropdown-item>
            <el-dropdown-item command="random">随机分组</el-dropdown-item>
            <el-dropdown-item command="history">加载历史分组</el-dropdown-item>
            <el-dropdown-item command="clear" divided><span class="danger">清空全部分组</span></el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-alert v-if="tooFew" type="warning" :closable="false" show-icon class="mb"
      :title="`全班 ${total} 人，每组最多 ${data.maxSize} 人，至少要设 ${Math.ceil(total / data.maxSize)} 组`" />
    <el-empty v-if="!data.groups.length" description="先设置组数，再开放自由选组" :image-size="80" />

    <div class="board">
      <div v-for="g in data.groups" :key="g.groupNo" class="group" :style="{ '--gc': groupColor(g.groupNo) }">
        <div class="g-head">
          <span class="g-no">{{ g.groupNo }}</span>
          <b>第 {{ g.groupNo }} 组</b>
          <span class="grow" />
          <span class="g-count" :class="{ full: g.full }">{{ g.members.length }}{{ data.maxSize ? ` / ${data.maxSize}` : '' }} 人</span>
        </div>
        <div v-if="data.maxSize" class="bar"><i :style="{ width: Math.min(100, g.members.length / data.maxSize * 100) + '%' }" /></div>
        <div v-if="g.topic" class="topic" :title="g.topic">「{{ g.topic }}」</div>
        <div class="chips">
          <span v-if="!g.members.length" class="muted">暂无组员</span>
          <el-dropdown v-for="m in g.members" :key="m.studentId" trigger="click" @command="(c: string) => command(c, m, g)">
            <span class="chip" :class="{ absent: !m.joined, leader: m.studentId === g.leaderId }">
              <span v-if="m.studentId === g.leaderId" class="crown">★</span>{{ m.name }}
              <small v-if="data.voteOpen || m.votes > 0" class="votes">{{ m.votes }}票</small>
              <small v-if="!m.joined" class="tag">未进课</small>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ m.name }}{{ m.task ? ' · ' + m.task : '' }}</el-dropdown-item>
                <el-dropdown-item v-if="m.studentId !== g.leaderId" command="leader" divided>设为组长</el-dropdown-item>
                <el-dropdown-item v-else command="unleader" divided>取消组长</el-dropdown-item>
                <el-dropdown-item v-for="n in moveTargets(g.groupNo)" :key="n" :command="'move:' + n">移到第 {{ n }} 组</el-dropdown-item>
                <el-dropdown-item command="move:0" divided>移出小组</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <div v-if="data.ungrouped.length" class="ungrouped">
      <div class="u-title">还没分组（{{ data.ungrouped.length }}）<span class="muted">点名字可直接放进某组</span></div>
      <div class="chips">
        <el-dropdown v-for="m in data.ungrouped" :key="m.studentId" trigger="click" @command="(c: string) => command(c, m, null)">
          <span class="chip loose" :class="{ absent: !m.joined }">{{ m.name }}<small v-if="!m.joined" class="tag">未进课</small></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="n in moveTargets(0)" :key="n" :command="'move:' + n">加入第 {{ n }} 组</el-dropdown-item>
              <el-dropdown-item v-if="!moveTargets(0).length" disabled>请先设置组数</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <el-dialog v-model="historyVisible" title="加载历史分组" :width="isMobile ? '94%' : '560px'" append-to-body>
      <el-table :data="history" size="small" empty-text="本班还没有分组记录" highlight-current-row @current-change="(r: any) => (historyPick = r?.id)">
        <el-table-column prop="courseName" label="课程" />
        <el-table-column label="上课时间" width="140"><template #default="{ row }">{{ fmtTime(row.startedAt) }}</template></el-table-column>
        <el-table-column prop="groupCount" label="组数" width="56" />
      </el-table>
      <template #footer>
        <el-button @click="historyVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!historyPick" @click="loadHistory">加载</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useLessonChannel } from '../../api/realtime'
import { fmtTime } from '../../utils/format'
import { useIsMobile } from '../../composables/useIsMobile'
import { groupColor } from './groupColors'
import Hint from '../../components/Hint.vue'

const props = defineProps<{ lessonId: number }>()
const base = `/teacher/groups/lessons/${props.lessonId}`
const isMobile = useIsMobile()
const data = ref<any>(null)
const count = ref(0)
const maxSize = ref(0)
const dirty = computed(() => !!data.value && (count.value !== data.value.groupCount || maxSize.value !== data.value.maxSize))
const total = computed(() => (data.value?.ungrouped.length || 0) + (data.value?.groups || []).reduce((n: number, g: any) => n + g.members.length, 0))
const tooFew = computed(() => data.value?.maxSize > 0 && total.value > data.value.groupCount * data.value.maxSize)
const historyVisible = ref(false)
const history = ref<any[]>([])
const historyPick = ref<number>()
let timer: number | undefined
let poll: number | undefined

function apply(d: any, force = false) {
  // 正在编辑但没保存的数字不被定时刷新冲掉（操作后的结果 force 覆盖）
  const keep = dirty.value && !force
  data.value = d
  if (!keep) {
    count.value = d.groupCount
    maxSize.value = d.maxSize
  }
}

async function load() {
  try {
    apply(await api.get(base))
  } catch (err) {
    ElMessage.error(errMsg(err, '加载分组失败'))
  }
}

async function run(p: Promise<any>, ok?: string) {
  try {
    apply(await p, true)
    if (ok) ElMessage.success(ok)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

const settings = (body: any) => run(api.put(`${base}/settings`, body))
const saveCount = () => run(api.put(`${base}/settings`, { groupCount: count.value, maxSize: maxSize.value }), '已保存')

async function toggleJoin() {
  const open = !data.value.joinOpen
  const body: any = { joinOpen: open }
  // 开放时顺带保存还没点保存的组数 / 人数
  if (open && dirty.value) Object.assign(body, { groupCount: count.value, maxSize: maxSize.value })
  if (open && !(body.groupCount ?? data.value.groupCount)) { ElMessage.warning('请先设置组数'); return }
  await run(api.put(`${base}/settings`, body), open ? '已开放自由选组' : '已关闭，学生不能再加入或换组')
}

async function confirm(msg: string) {
  try {
    await ElMessageBox.confirm(msg, '确认', { type: 'warning' })
    return true
  } catch {
    return false
  }
}

async function moreCmd(c: string) {
  if (c === 'autoLeaders') run(api.post(`${base}/auto-leaders`), '已按票数确定组长')
  else if (c === 'history') openHistory()
  else if (c === 'random') {
    if (!count.value) { ElMessage.warning('请先填写组数'); return }
    if (await confirm(`把已进课的学生随机均分到 ${count.value} 个组？现有分组和组长会被清空`)) {
      run(api.post(`${base}/random`, { groupCount: count.value }), '已随机分组')
    }
  } else if (c === 'clear') {
    if (await confirm('清空本节课所有分组？')) run(api.post(`${base}/clear`), '已清空分组')
  }
}

function moveTargets(current: number) {
  const n = Math.max(data.value?.groupCount || 0, data.value?.groups.length || 0)
  return Array.from({ length: n }, (_, i) => i + 1).filter(x => x !== current)
}

function command(c: string, m: any, g: any) {
  if (c === 'leader') run(api.put(`${base}/groups/${g.groupNo}/leader`, { studentId: m.studentId }))
  else if (c === 'unleader') run(api.put(`${base}/groups/${g.groupNo}/leader`, { studentId: null }))
  else if (c.startsWith('move:')) run(api.put(`${base}/members/${m.studentId}`, { groupNo: Number(c.slice(5)) }))
}

async function openHistory() {
  historyPick.value = undefined
  history.value = await api.get(`${base}/history`)
  historyVisible.value = true
}

async function loadHistory() {
  await run(api.post(`${base}/load`, { fromLessonId: historyPick.value }), '已加载历史分组')
  historyVisible.value = false
}

useLessonChannel(props.lessonId, true, e => {
  if (e.type === 'GROUPS_CHANGED') {
    window.clearTimeout(timer)
    timer = window.setTimeout(load, 300)
  }
})
onMounted(() => {
  load()
  // 学生进课签到没有实时事件，定时刷新以显示新进课的学生
  poll = window.setInterval(load, 10000)
})
onBeforeUnmount(() => window.clearInterval(poll))
</script>

<style scoped>
.mgr { display: flex; flex-direction: column; gap: 12px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.danger { color: var(--el-color-danger); }
.grow { flex: 1; }
.mb { margin-bottom: 0; }

.ctrl { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 12px; }
.setup, .switcher { border: 1.5px solid var(--hb-line); border-radius: 12px; background: var(--hb-paper); padding: 12px 14px; box-shadow: 3px 3px 0 var(--hb-line); }
.setup { display: flex; align-items: flex-end; gap: 14px; flex-wrap: wrap; }
.field { display: flex; flex-direction: column; gap: 4px; }
.lbl { font-size: 13px; color: var(--el-text-color-secondary); display: flex; align-items: center; gap: 2px; }
.num { width: 130px; }
.save { margin-left: auto; }
.switcher { display: flex; align-items: center; gap: 12px; background: var(--el-fill-color-light); transition: background .2s, border-color .2s; }
.switcher.open { background: var(--el-color-success-light-9); border-color: var(--el-color-success-light-5); }
.sw-text { flex: 1; min-width: 0; }
.sw-title { font-family: var(--hb-title-font); font-size: 20px; color: var(--hb-ink-deep); }
.switcher.open .sw-title { color: var(--el-color-success-dark-2); }
.sw-sub { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }
.sw-btn { flex: none; min-width: 96px; font-weight: 700; }

.summary { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.pill { font-size: 12px; padding: 3px 10px; border-radius: 999px; background: var(--el-fill-color); color: var(--el-text-color-regular); }
.pill b { font-size: 14px; color: var(--hb-ink-deep); }
.pill.ok { background: var(--el-color-success-light-9); }
.pill.warn { background: var(--el-color-warning-light-9); }
.vote { font-size: 13px; color: var(--el-text-color-secondary); display: flex; align-items: center; gap: 4px; margin-right: 6px; }

.board { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.group { border: 1.5px solid var(--hb-line); border-top: 5px solid var(--gc); border-radius: 12px; padding: 10px 12px; background: var(--hb-paper); }
.g-head { display: flex; gap: 8px; align-items: center; }
.g-no { width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center; background: var(--gc); color: #fff; font-weight: 800; font-size: 13px; }
.g-count { font-size: 12px; color: var(--el-text-color-secondary); }
.g-count.full { color: var(--el-color-danger); font-weight: 700; }
.bar { height: 5px; border-radius: 3px; background: var(--el-fill-color); margin: 8px 0 2px; overflow: hidden; }
.bar i { display: block; height: 100%; background: var(--gc); border-radius: 3px; transition: width .3s; }
.topic { font-size: 12px; color: var(--el-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 4px; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.chip { display: inline-flex; align-items: center; gap: 3px; padding: 4px 10px; border-radius: 999px; font-size: 13px; cursor: pointer;
  background: var(--el-fill-color-light); border: 1px solid var(--el-border-color-lighter); color: var(--hb-ink-deep); user-select: none; }
.chip:hover { border-color: var(--gc, var(--el-color-primary)); }
.chip.leader { background: var(--el-color-warning-light-9); border-color: var(--el-color-warning-light-5); font-weight: 700; }
.chip.absent { color: var(--el-text-color-placeholder); border-style: dashed; }
.chip.loose { background: var(--hb-paper); }
.crown { color: var(--el-color-warning); }
.votes { color: var(--el-color-warning-dark-2); font-size: 11px; }
.tag { font-size: 11px; color: var(--el-text-color-placeholder); }

.ungrouped { border: 1.5px dashed var(--hb-line); border-radius: 12px; padding: 10px 12px; }
.u-title { font-weight: 700; color: var(--hb-ink-deep); display: flex; gap: 8px; align-items: baseline; }

/* 手机：上下排，按钮放大，组卡片单列 */
.mgr.mobile .ctrl { grid-template-columns: 1fr; }
.mgr.mobile .setup { gap: 10px; }
.mgr.mobile .field { flex: 1 1 40%; }
.mgr.mobile .num { width: 100%; }
.mgr.mobile .save { width: 100%; margin-left: 0; }
.mgr.mobile .board { grid-template-columns: 1fr; }
.mgr.mobile .chip { padding: 6px 12px; font-size: 14px; }
@media (max-width: 1100px) { .ctrl { grid-template-columns: 1fr; } }
</style>
