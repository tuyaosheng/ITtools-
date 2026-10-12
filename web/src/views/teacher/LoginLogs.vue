<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>学生登入记录</span>
        <div class="filters">
          <el-select v-model="classId" placeholder="选择班级" class="w160">
            <el-option v-for="k in classes" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" class="w260" />
          <el-button type="primary" :disabled="!classId || !range" @click="load">查询</el-button>
        </div>
      </div>
    </template>
    <el-tabs v-model="tab">
      <el-tab-pane :label="`按学生汇总（${summary.length}）`" name="sum">
        <el-table :data="summary" size="small" stripe empty-text="请选择班级和日期后查询">
          <el-table-column prop="studentNo" label="学号" width="80" />
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column prop="count" label="登入次数" width="90" sortable />
          <el-table-column prop="minutes" label="在线分钟" width="100" sortable />
          <el-table-column label="最近登入"><template #default="{ row }: { row: any }">{{ fmtTime(row.last) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane :label="`明细（${logs.length}）`" name="detail">
        <el-table :data="logs" size="small" stripe max-height="520">
          <el-table-column prop="studentNo" label="学号" width="80" />
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column label="登入" width="150"><template #default="{ row }: { row: any }">{{ fmtTime(row.loginAt) }}</template></el-table-column>
          <el-table-column label="退出" width="150"><template #default="{ row }: { row: any }">{{ row.logoutAt ? fmtTime(row.logoutAt) : '未正常退出' }}</template></el-table-column>
          <el-table-column prop="minutes" label="时长(分)" width="90" />
          <el-table-column prop="ip" label="IP" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'

const classes = ref<any[]>([])
const classId = ref<number>()
const today = new Date()
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const range = ref<[string, string] | null>([iso(new Date(today.getTime() - 6 * 86400000)), iso(today)])
const logs = ref<any[]>([])
const tab = ref('sum')

const summary = computed(() => {
  const m = new Map<number, any>()
  for (const l of logs.value) {
    const s = m.get(l.studentId) || { studentNo: l.studentNo, name: l.name, count: 0, minutes: 0, last: l.loginAt }
    s.count++
    s.minutes = Math.round((s.minutes + Number(l.minutes || 0)) * 10) / 10
    if (l.loginAt > s.last) s.last = l.loginAt
    m.set(l.studentId, s)
  }
  return [...m.values()]
})

async function load() {
  try {
    logs.value = await api.get('/teacher/login-logs', { classId: classId.value, from: range.value![0], to: range.value![1] })
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => { classes.value = await api.get('/teacher/classes') })
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; align-items: center; }
.w160 { width: 160px; }
.w260 { width: 260px; }
</style>
