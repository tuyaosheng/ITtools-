<template>
  <div class="rollcall">
    <div class="stage" :class="{ dark }">
      <UserAvatar v-if="!rolling && picked" :user-id="picked.studentId" :name="picked.name" :size="96" class="pick-avatar" />
      <div class="name" :class="{ rolling }">{{ display || '准备点名' }}</div>
      <div v-if="!rolling && picked" class="sub">{{ picked.studentNo }} · {{ picked.pcName || '' }}</div>
      <div class="ctrl">
        <el-button v-if="!rolling" type="primary" size="large" :disabled="!active || !present.length" @click="start">开始点名</el-button>
        <el-button v-else type="danger" size="large" @click="stop">停 止</el-button>
        <el-checkbox v-model="repeat" :disabled="rolling" class="ml" :class="{ light: dark }">允许重复点到</el-checkbox>
        <el-button link :class="{ light: dark }" @click="dark = !dark">{{ dark ? '开灯' : '关灯' }}</el-button>
      </div>
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import UserAvatar from '../../components/UserAvatar.vue'

const props = defineProps<{ lesson: any }>()
const reasons = ['身体不适', '被教师留住', '参加社团', '体育生训练', '参加竞赛', '家中有事', '请假外出', '其他原因']
const data = ref<any>({ students: [], called: [] })
const display = ref('')
const picked = ref<any>(null)
const rolling = ref(false)
const repeat = ref(false)
const dark = ref(false)
const active = computed(() => props.lesson.status === 'ACTIVE')
const present = computed(() => data.value.students.filter((s: any) => s.status === 'PRESENT'))
const absent = computed(() => data.value.students.filter((s: any) => s.status !== 'PRESENT'))
let spin: number | undefined

async function load() {
  data.value = await api.get(`/teacher/activity/lessons/${props.lesson.id}/roll-call`)
}

function start() {
  picked.value = null
  rolling.value = true
  const names = present.value.map((s: any) => s.name)
  spin = window.setInterval(() => { display.value = names[Math.floor(Math.random() * names.length)] }, 60)
}

async function stop() {
  try {
    const r = await api.post(`/teacher/activity/lessons/${props.lesson.id}/roll-call?repeat=${repeat.value}`)
    window.clearInterval(spin)
    rolling.value = false
    picked.value = r
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
.ml { margin-left: 4px; }
.w150 { width: 150px; }
@media (max-width: 900px) { .cols { grid-template-columns: 1fr; } }
.pick-avatar { margin-bottom: 8px; }
</style>
