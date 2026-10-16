<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>学习情况查询</span>
        <div class="filters">
          <el-select v-model="classId" placeholder="选择班级" class="w160" @change="load">
            <el-option v-for="k in classes" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
          <el-select v-model="termId" clearable placeholder="全部学期" class="w180" @change="load">
            <el-option v-for="t in terms" :key="t.id" :label="t.label" :value="t.id" />
          </el-select>
          <el-button :disabled="!classId" @click="exportXlsx">导出 Excel</el-button>
        </div>
      </div>
    </template>
    <el-tabs v-model="tab" @tab-change="loadDetail">
      <el-tab-pane label="期末统计" name="SUMMARY">
        <div class="weights">
          <span>总评权重：</span>
          <span v-for="(w, i) in weightNames" :key="w" class="wt">
            {{ w }} <el-input-number v-model="weights[i]" :min="0" :max="100" size="small" controls-position="right" class="w90" />
          </span>
          <el-button size="small" type="primary" :disabled="!classId" @click="loadSummary">重新计算</el-button>
          <Hint>各项按百分制折算后加权平均，缺项（如没布置过测验）不计权重</Hint>
        </div>
        <el-table :data="summary" size="small" stripe max-height="560" :default-sort="{ prop: 'studentNo', order: 'ascending' }" empty-text="请选择班级">
          <el-table-column prop="studentNo" label="班内学号" width="84" sortable fixed />
          <el-table-column label="姓名" width="90" fixed>
            <template #default="{ row }: { row: any }"><el-link type="primary" @click="openStudent(row)">{{ row.name }}</el-link></template>
          </el-table-column>
          <el-table-column label="出席" align="center">
            <el-table-column label="出席/课次" width="90"><template #default="{ row }: { row: any }">{{ row.present }}/{{ row.lessons }}</template></el-table-column>
            <el-table-column prop="leave" label="请假" width="60" />
            <el-table-column prop="attendancePct" label="出席率%" width="90" sortable />
            <el-table-column prop="lessonScore" label="课堂表现" width="90" sortable />
          </el-table-column>
          <el-table-column label="作品" align="center">
            <el-table-column prop="works" label="份数" width="70" sortable />
            <el-table-column prop="essence" label="精华" width="60" />
            <el-table-column prop="workPct" label="得分%" width="80" sortable />
          </el-table-column>
          <el-table-column label="操作题" align="center">
            <el-table-column prop="python" label="题数" width="70" />
            <el-table-column prop="pythonPct" label="得分%" width="80" sortable />
          </el-table-column>
          <el-table-column label="课后作业" align="center">
            <el-table-column label="已交" width="70"><template #default="{ row }: { row: any }">{{ row.homeworkDone }}/{{ row.homeworkTotal }}</template></el-table-column>
            <el-table-column prop="homeworkPct" label="得分%" width="80" sortable />
          </el-table-column>
          <el-table-column label="测验" align="center">
            <el-table-column label="已考" width="70"><template #default="{ row }: { row: any }">{{ row.testDone }}/{{ row.testTotal }}</template></el-table-column>
            <el-table-column prop="testPct" label="得分%" width="80" sortable />
          </el-table-column>
          <el-table-column prop="total" label="总评" width="80" sortable />
          <el-table-column prop="rank" label="名次" width="70" sortable />
        </el-table>
      </el-tab-pane>
      <el-tab-pane v-for="k in kinds" :key="k.kind" :label="k.label" :name="k.kind">
        <el-table :data="detail?.rows || []" size="small" stripe border max-height="560" empty-text="暂无数据">
          <el-table-column prop="studentNo" label="班内学号" width="84" fixed />
          <el-table-column prop="name" label="姓名" width="90" fixed />
          <el-table-column v-for="c in detail?.columns || []" :key="c.key" :label="c.title" min-width="110" align="center">
            <template #default="{ row }: { row: any }">
              <template v-if="k.kind === 'ATTENDANCE'">
                <el-tag v-if="row.cells[c.key]" size="small" :type="attendanceTag[row.cells[c.key].value]">{{ attendanceLabel[row.cells[c.key].value] }}</el-tag>
                <span v-else class="muted">—</span>
              </template>
              <template v-else>
                <span v-if="!row.cells[c.key]" class="miss">未交</span>
                <span v-else-if="row.cells[c.key].value && row.cells[c.key].value !== 'SUBMITTED'" class="muted">{{ attemptLabel[row.cells[c.key].value] }}</span>
                <span v-else-if="row.cells[c.key].score == null" class="muted">待评</span>
                <span v-else>{{ row.cells[c.key].score }}<span v-if="row.cells[c.key].essence" class="star">★</span></span>
              </template>
            </template>
          </el-table-column>
          <el-table-column v-if="k.kind !== 'ATTENDANCE'" label="完成" width="70" fixed="right">
            <template #default="{ row }: { row: any }">{{ doneCount(row) }}/{{ detail?.columns.length }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="studentDlg" :title="`${student?.name || ''} 的学习情况`" width="760px" destroy-on-close>
      <StudentReportCard :data="studentData" />
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { attendanceLabel, attendanceTag } from '../../utils/format'
import StudentReportCard from '../../components/StudentReportCard.vue'
import { attemptLabel } from '../quiz/labels'

const classes = ref<any[]>([])
const terms = ref<any[]>([])
const classId = ref<number>()
const termId = ref<number>()
const tab = ref('SUMMARY')
const summary = ref<any[]>([])
const detail = ref<any>(null)
const weightNames = ['出席', '作品', '操作题', '课后作业', '测验']
const weights = ref([10, 30, 20, 20, 20])
const kinds = [
  { kind: 'ATTENDANCE', label: '出席情况' },
  { kind: 'WORKS', label: '作品情况' },
  { kind: 'PYTHON', label: '操作题情况' },
  { kind: 'HOMEWORK', label: '课后作业情况' },
  { kind: 'TEST', label: '测验情况' }
]
const studentDlg = ref(false)
const student = ref<any>()
const studentData = ref<any>(null)

/** 已完成：作品/操作题有记录即算，作业/测验须已交卷 */
const doneCount = (row: any) => Object.values(row.cells).filter((c: any) => !c.value || c.value === 'SUBMITTED').length
const params = () => ({ classId: classId.value, termId: termId.value || undefined, w: weights.value.join(',') })

async function loadSummary() {
  if (!classId.value) return
  try {
    summary.value = await api.get('/teacher/reports/class/summary', params())
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function loadDetail() {
  if (!classId.value || tab.value === 'SUMMARY') return
  detail.value = null
  try {
    detail.value = await api.get('/teacher/reports/class/detail', { classId: classId.value, termId: termId.value || undefined, kind: tab.value })
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function load() {
  await loadSummary()
  await loadDetail()
}

async function openStudent(row: any) {
  student.value = row
  studentData.value = null
  studentDlg.value = true
  try {
    studentData.value = await api.get(`/teacher/reports/students/${row.studentId}`, { termId: termId.value || undefined, w: weights.value.join(',') })
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function exportXlsx() {
  try {
    await api.download('/teacher/reports/class/export', undefined, params())
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => {
  const [ks, ts] = await Promise.all([api.get('/teacher/classes'), api.get('/teacher/terms')])
  classes.value = ks
  terms.value = ts
  termId.value = ts.find((t: any) => t.current)?.id
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; align-items: center; }
.weights { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; font-size: 13px; }
.wt { white-space: nowrap; }
.w90 { width: 90px; }
.w160 { width: 160px; }
.w180 { width: 180px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.miss { color: var(--el-color-danger); font-size: 12px; }
.star { color: var(--el-color-warning); margin-left: 2px; }
</style>
