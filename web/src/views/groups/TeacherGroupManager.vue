<template>
  <div v-if="data" class="mgr">
    <div class="bar">
      <span class="muted">组数</span>
      <el-input-number v-model="count" :min="0" :max="30" size="small" controls-position="right" class="w100" />
      <el-button size="small" @click="saveCount">设置</el-button>
      <el-divider direction="vertical" />
      <span class="muted">自由加入</span>
      <el-switch :model-value="data.joinOpen" @change="(v: any) => settings({ joinOpen: v })" />
      <span class="muted">组长推选</span>
      <el-switch :model-value="data.voteOpen" @change="(v: any) => settings({ voteOpen: v })" />
      <el-button size="small" @click="autoLeaders">按票数设组长</el-button>
      <el-divider direction="vertical" />
      <el-popconfirm title="把已进课的学生随机均分到当前组数？现有分组和组长会被清空" @confirm="randomize">
        <template #reference><el-button size="small">随机分组</el-button></template>
      </el-popconfirm>
      <el-button size="small" @click="openHistory">加载历史分组</el-button>
      <el-popconfirm title="清空本节课所有分组？" @confirm="clearAll">
        <template #reference><el-button size="small" type="danger" plain>清空</el-button></template>
      </el-popconfirm>
    </div>

    <div class="board">
      <div v-for="g in data.groups" :key="g.groupNo" class="group">
        <div class="g-head">
          <b>第 {{ g.groupNo }} 组</b>
          <span class="muted">{{ g.members.length }} 人</span>
          <span v-if="g.topic" class="topic" :title="g.topic">「{{ g.topic }}」</span>
        </div>
        <div v-if="!g.members.length" class="muted empty">暂无组员</div>
        <div v-for="m in g.members" :key="m.studentId" class="member">
          <el-dropdown trigger="click" @command="(c: string) => command(c, m, g)">
            <span class="m-name" :class="{ absent: !m.joined }">
              <span v-if="m.studentId === g.leaderId" class="crown" title="组长">★</span>{{ m.name }}
              <span v-if="!m.joined" class="tag">未进课</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="m.studentId !== g.leaderId" command="leader">设为组长</el-dropdown-item>
                <el-dropdown-item v-else command="unleader">取消组长</el-dropdown-item>
                <el-dropdown-item v-for="n in moveTargets(g.groupNo)" :key="n" :command="'move:' + n">移到第 {{ n }} 组</el-dropdown-item>
                <el-dropdown-item command="move:0" divided>移出小组</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span v-if="data.voteOpen || m.votes > 0" class="votes">{{ m.votes }} 票</span>
          <span v-if="m.task" class="muted task" :title="m.task">{{ m.task }}</span>
        </div>
      </div>
    </div>

    <div class="ungrouped">
      <div class="muted">未分组（{{ data.ungrouped.length }}）：点击学生选择小组</div>
      <el-dropdown v-for="m in data.ungrouped" :key="m.studentId" trigger="click" @command="(c: string) => command(c, m, null)">
        <el-tag :type="m.joined ? undefined : 'info'" class="u-tag">{{ m.name }}{{ m.joined ? '' : '（未进课）' }}</el-tag>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="n in moveTargets(0)" :key="n" :command="'move:' + n">加入第 {{ n }} 组</el-dropdown-item>
            <el-dropdown-item v-if="!moveTargets(0).length" disabled>请先设置组数</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-dialog v-model="historyVisible" title="加载历史分组" width="560px" append-to-body>
      <p class="muted">把本班以往某节课的分组和组长加载到本节课。尚未进课的学生会在进课时自动加入对应小组。</p>
      <el-table :data="history" size="small" empty-text="本班还没有分组记录" highlight-current-row @current-change="(r: any) => (historyPick = r?.id)">
        <el-table-column prop="courseName" label="课程" />
        <el-table-column label="上课时间" width="150"><template #default="{ row }">{{ fmtTime(row.startedAt) }}</template></el-table-column>
        <el-table-column prop="groupCount" label="组数" width="70" />
      </el-table>
      <template #footer>
        <el-button @click="historyVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!historyPick" @click="loadHistory">加载</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useLessonChannel } from '../../api/realtime'
import { fmtTime } from '../../utils/format'

const props = defineProps<{ lessonId: number }>()
const base = `/teacher/groups/lessons/${props.lessonId}`
const data = ref<any>(null)
const count = ref(0)
const historyVisible = ref(false)
const history = ref<any[]>([])
const historyPick = ref<number>()
let timer: number | undefined
let poll: number | undefined

function apply(d: any) {
  data.value = d
  count.value = d.groupCount
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
    apply(await p)
    if (ok) ElMessage.success(ok)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

const settings = (body: any) => run(api.put(`${base}/settings`, body))
const saveCount = () => run(api.put(`${base}/settings`, { groupCount: count.value }), '已设置组数')
const autoLeaders = () => run(api.post(`${base}/auto-leaders`), '已按票数确定组长')
const clearAll = () => run(api.post(`${base}/clear`), '已清空分组')

function randomize() {
  if (!count.value) { ElMessage.warning('请先填写组数'); return }
  run(api.post(`${base}/random`, { groupCount: count.value }), '已随机分组')
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
.bar { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }
.bar .el-button + .el-button { margin-left: 0; }
.muted { color: #909399; font-size: 12px; }
.w100 { width: 100px; }
.board { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 10px; }
.group { border: 1px solid #e4e7ed; border-radius: 6px; padding: 8px 10px; background: #fafbfc; }
.g-head { display: flex; gap: 6px; align-items: baseline; margin-bottom: 6px; }
.topic { font-size: 12px; color: #409eff; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.member { display: flex; align-items: center; gap: 6px; padding: 2px 0; font-size: 13px; }
.m-name { cursor: pointer; }
.m-name:hover { color: #409eff; }
.m-name.absent { color: #a8abb2; }
.crown { color: #e6a23c; margin-right: 2px; }
.tag { font-size: 11px; color: #a8abb2; margin-left: 2px; }
.votes { font-size: 11px; color: #e6a23c; }
.task { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 90px; }
.empty { padding: 6px 0; }
.ungrouped { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.u-tag { cursor: pointer; }
</style>
