<template>
  <div class="rollcall">
    <div class="stage" :class="{ dark }">
      <!-- 抽中一个小组 -->
      <template v-if="!rolling && pickedGroup">
        <div class="name">第 {{ pickedGroup.groupNo }} 组</div>
        <div class="multi">
          <div v-for="m in pickedGroup.members" :key="m.studentId" class="multi-item">
            <UserAvatar :user-id="m.studentId" :name="m.name" :size="48" />
            <div class="multi-name small">{{ m.name }}</div>
          </div>
        </div>
      </template>
      <!-- 每组各抽 n 人 -->
      <template v-else-if="!rolling && groupedPicks.length">
        <div class="group-rows">
          <div v-for="g in groupedPicks" :key="g.groupNo" class="group-row">
            <el-tag effect="dark" :type="dark ? 'info' : 'primary'">第 {{ g.groupNo }} 组</el-tag>
            <span v-for="p in g.picks" :key="p.studentId" class="group-pick">
              <UserAvatar :user-id="p.studentId" :name="p.name" :size="36" />{{ p.name }}
            </span>
          </div>
        </div>
      </template>
      <template v-else-if="!rolling && picks.length > 1">
        <div class="multi">
          <div v-for="p in picks" :key="p.studentId" class="multi-item">
            <UserAvatar :user-id="p.studentId" :name="p.name" :size="64" />
            <div class="multi-name">{{ p.name }}</div>
            <div class="sub">{{ p.studentNo }}<template v-if="p.groupNo"> · {{ p.groupNo }} 组</template></div>
          </div>
        </div>
      </template>
      <template v-else>
        <UserAvatar v-if="!rolling && picked" :user-id="picked.studentId" :name="picked.name" :size="96" class="pick-avatar" />
        <div class="name" :class="{ rolling }">{{ display || '准备点名' }}</div>
        <div v-if="!rolling && picked" class="sub">{{ picked.studentNo }} · {{ picked.pcName || '' }}<template v-if="picked.groupNo"> · {{ picked.groupNo }} 组</template></div>
      </template>
      <div v-if="rolling && kind === 'person' && (count > 1 || scope === 'EACH')" class="sub">正在抽取…</div>

      <div class="ctrl">
        <el-button v-if="!rolling" type="primary" size="large" :disabled="!active || !canDraw" @click="start('person')">开始点名</el-button>
        <el-button v-if="!rolling" size="large" :disabled="!active || !groupNos.length" @click="start('group')">抽一个组</el-button>
        <el-button v-if="rolling" type="danger" size="large" @click="stop">停 止</el-button>
      </div>
      <div class="ctrl">
        <span :class="{ light: dark }">范围
          <el-select v-model="scope" size="small" class="w130" :disabled="rolling">
            <el-option label="全班" value="ALL" />
            <el-option label="指定小组" value="GROUP" :disabled="!groupNos.length" />
            <el-option label="每个小组各抽" value="EACH" :disabled="!groupNos.length" />
          </el-select>
        </span>
        <el-select v-if="scope === 'GROUP'" v-model="groupNo" size="small" placeholder="选择小组" class="w110" :disabled="rolling">
          <el-option v-for="g in groupNos" :key="g" :label="`第 ${g} 组`" :value="g" />
        </el-select>
        <span :class="{ light: dark }">{{ scope === 'EACH' ? '每组抽' : '一次抽' }}
          <el-input-number v-model="count" :min="1" :max="maxCount" size="small" :disabled="rolling" /> 人
        </span>
        <el-checkbox v-model="repeat" :disabled="rolling" :class="{ light: dark }">允许重复点到</el-checkbox>
        <el-button link :class="{ light: dark }" @click="dark = !dark">{{ dark ? '开灯' : '关灯' }}</el-button>
      </div>
      <div v-if="!groupNos.length" class="sub">出席的学生还没有分组时，只能在全班范围内抽人（分组在课堂分组里设置）</div>
      <div v-if="!active" class="sub">课堂已结束，不能点名</div>
    </div>
    <div class="cols">
      <div class="col">
        <div class="h">已点到（{{ data.called.length }}）</div>
        <el-tag v-for="(c, i) in data.called" :key="i" size="small" class="tag">{{ c.name }}</el-tag>
      </div>
      <div class="col">
        <div class="h">缺席 / 请假（{{ absent.length }}）</div>
        <el-table :data="absent" size="small" empty-text="全部到齐">
          <el-table-column prop="name" label="姓名" width="90" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }: { row: any }"><el-tag size="small" :type="row.status === 'LEAVE' ? 'warning' : 'danger'">{{ row.status === 'LEAVE' ? '请假' : '缺席' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="原因">
            <template #default="{ row }: { row: any }">
              <el-select :model-value="row.status === 'LEAVE' ? row.leaveReason || '其他原因' : ''" size="small" placeholder="登记请假原因" clearable
                class="w150" @change="(v: string) => setLeave(row, v)">
                <el-option v-for="r in reasons" :key="r" :label="r" :value="r" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import UserAvatar from '../../components/UserAvatar.vue'

const props = defineProps<{ lesson: any }>()
const reasons = ['身体不适', '被教师留住', '参加社团', '体育生训练', '参加竞赛', '家中有事', '请假外出', '其他原因']
const data = ref<any>({ students: [], called: [] })
const display = ref('')
const picked = ref<any>(null)
const picks = ref<any[]>([])
const groupedPicks = ref<any[]>([])
const pickedGroup = ref<any>(null)
const rolling = ref(false)
const kind = ref<'person' | 'group'>('person')
const repeat = ref(false)
const count = ref(1)
const scope = ref<'ALL' | 'GROUP' | 'EACH'>('ALL')
const groupNo = ref<number | undefined>(undefined)
const dark = ref(false)
const active = computed(() => props.lesson.status === 'ACTIVE')
const present = computed(() => data.value.students.filter((s: any) => s.status === 'PRESENT'))
const absent = computed(() => data.value.students.filter((s: any) => s.status !== 'PRESENT'))
// 有出席学生的小组号
const groupNos = computed<number[]>(() => {
  const set = new Set<number>(present.value.filter((s: any) => s.groupNo > 0).map((s: any) => s.groupNo as number))
  return [...set].sort((a, b) => a - b)
})
const candidates = computed(() => scope.value === 'GROUP'
  ? present.value.filter((s: any) => s.groupNo === groupNo.value) : present.value)
const canDraw = computed(() => candidates.value.length > 0 && (scope.value !== 'GROUP' || !!groupNo.value))
const maxCount = computed(() => {
  if (scope.value === 'EACH') {
    return Math.max(1, ...groupNos.value.map(g => present.value.filter((s: any) => s.groupNo === g).length))
  }
  return Math.max(1, candidates.value.length)
})
let spin: number | undefined

watch(groupNos, (v) => {
  if (groupNo.value && !v.includes(groupNo.value)) groupNo.value = undefined
  if (!v.length) scope.value = 'ALL'
})
watch(maxCount, (m) => { if (count.value > m) count.value = m })

async function load() {
  data.value = await api.get(`/teacher/activity/lessons/${props.lesson.id}/roll-call`)
}

function reset() {
  picked.value = null
  picks.value = []
  groupedPicks.value = []
  pickedGroup.value = null
}

function start(k: 'person' | 'group') {
  reset()
  kind.value = k
  rolling.value = true
  if (k === 'group') {
    const nos = groupNos.value
    spin = window.setInterval(() => { display.value = `第 ${nos[Math.floor(Math.random() * nos.length)]} 组` }, 60)
  } else {
    const names = (scope.value === 'EACH' ? present.value.filter((s: any) => s.groupNo > 0) : candidates.value).map((s: any) => s.name)
    spin = window.setInterval(() => { display.value = names[Math.floor(Math.random() * names.length)] }, 60)
  }
}

async function stop() {
  try {
    const base = `/teacher/activity/lessons/${props.lesson.id}/roll-call`
    if (kind.value === 'group') {
      const r = await api.post(`${base}/group`)
      window.clearInterval(spin)
      rolling.value = false
      pickedGroup.value = r
      display.value = `第 ${r.groupNo} 组`
      return
    }
    let url = `${base}?repeat=${repeat.value}&count=${count.value}&scope=${scope.value}`
    if (scope.value === 'GROUP') url += `&groupNo=${groupNo.value}`
    const r = await api.post(url)
    window.clearInterval(spin)
    rolling.value = false
    picked.value = r
    picks.value = r.picks || [r]
    groupedPicks.value = scope.value === 'EACH' ? r.groups || [] : []
    display.value = r.name
    await load()
  } catch (err) {
    window.clearInterval(spin)
    rolling.value = false
    display.value = ''
    ElMessage.error(errMsg(err))
  }
}

async function setLeave(row: any, reason: string) {
  try {
    await api.put(`/teacher/lessons/${props.lesson.id}/attendance/${row.studentId}`,
      reason ? { status: 'LEAVE', leaveReason: reason } : { status: 'ABSENT', leaveReason: null })
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(load)
onBeforeUnmount(() => window.clearInterval(spin))
</script>

<style scoped>
.stage { border-radius: 10px; background: var(--el-fill-color-light); padding: 28px 16px; text-align: center; transition: background .3s; }
.stage.dark { background: #1d1e1f; color: #fff; }
.name { font-size: 56px; font-weight: 700; letter-spacing: 6px; min-height: 76px; }
.name.rolling { color: var(--el-color-primary); }
.sub { color: var(--el-text-color-secondary); margin-top: 4px; }
.ctrl { margin-top: 16px; display: flex; justify-content: center; align-items: center; gap: 12px; flex-wrap: wrap; }
.light { color: #ddd; }
.cols { display: grid; grid-template-columns: 1fr 1.4fr; gap: 16px; margin-top: 14px; }
.h { font-weight: 600; margin-bottom: 6px; }
.tag { margin: 0 4px 4px 0; }
.w150 { width: 150px; }
.w130 { width: 130px; }
.w110 { width: 110px; }
@media (max-width: 900px) { .cols { grid-template-columns: 1fr; } }
.pick-avatar { margin-bottom: 8px; }
.multi { display: flex; flex-wrap: wrap; justify-content: center; gap: 16px 24px; min-height: 76px; }
.multi-item { display: flex; flex-direction: column; align-items: center; min-width: 90px; }
.multi-name { font-size: 28px; font-weight: 700; letter-spacing: 2px; margin-top: 4px; }
.multi-name.small { font-size: 18px; }
.group-rows { display: flex; flex-direction: column; gap: 10px; align-items: center; }
.group-row { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 8px 18px; font-size: 22px; font-weight: 700; }
.group-pick { display: inline-flex; align-items: center; gap: 6px; }
</style>
