<template>
  <el-card shadow="never">
    <template #header>
      <div class="head">
        <span>应急工具</span>
        <span class="warn">操作不可撤销</span>
      </div>
    </template>
    <el-tabs v-model="tab">
      <el-tab-pane label="全部强制下课" name="end">
        <div><Hint block>将所有教师、所有班级的进行中课堂设置为下课（未进课的学生记为缺席）。一般用于出错后通过正常的下课、强制下课都无法解决时。</Hint></div>
        <el-popconfirm title="确定让所有进行中的课堂下课？" width="240" @confirm="endAll">
          <template #reference><el-button type="danger">全部强制下课</el-button></template>
        </el-popconfirm>
      </el-tab-pane>

      <el-tab-pane label="消除缺勤记录" name="absence">
        <div><Hint block>删除选中的缺席/请假记录，该节课不再计入学生的出勤统计（例如课后补交了假条、学生后来才转入本班）。消除后无法恢复。</Hint></div>
        <div class="bar">
          <el-select v-model="yearId" placeholder="年级" class="w140" @change="loadClasses">
            <el-option v-for="y in years" :key="y.id" :label="y.label" :value="y.id" />
          </el-select>
          <el-select v-model="classId" placeholder="班级" class="w140" :disabled="!yearId">
            <el-option v-for="k in classes" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
          <el-select v-model="termId" clearable placeholder="全部学期" class="w180">
            <el-option v-for="t in terms" :key="t.id" :label="t.label" :value="t.id" />
          </el-select>
          <el-button type="primary" :disabled="!classId" @click="loadAbsences">查询</el-button>
          <el-popconfirm :title="`消除选中的 ${selected.length} 条记录？`" width="240" @confirm="clearAbsences">
            <template #reference><el-button type="danger" :disabled="!selected.length">消除选中（{{ selected.length }}）</el-button></template>
          </el-popconfirm>
        </div>
        <el-table :data="absences" size="small" stripe max-height="480" empty-text="请选择班级后查询" @selection-change="(v: any[]) => (selected = v)">
          <el-table-column type="selection" width="36" />
          <el-table-column prop="studentNo" label="学号" width="70" />
          <el-table-column prop="name" label="姓名" width="90" />
          <el-table-column prop="courseName" label="课程" min-width="160" />
          <el-table-column prop="teacherName" label="教师" width="90" />
          <el-table-column label="上课时间" width="150"><template #default="{ row }: { row: any }">{{ fmtTime(row.startedAt) }}</template></el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }: { row: any }">
              <el-tag size="small" :type="row.status === 'LEAVE' ? 'warning' : 'danger'">{{ row.status === 'LEAVE' ? '请假' : '缺席' }}</el-tag>
              <span v-if="row.leaveReason" class="muted"> {{ row.leaveReason }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="学期修复" name="term">
        <div><Hint block>调整学期设置后，可能有课堂记录没有划分学期（报表按学期筛选时查不到）。选中这些记录并指定学期。</Hint></div>
        <div class="bar">
          <el-button @click="loadNoTerm">查询未划分学期的课堂</el-button>
          <el-select v-model="fixTermId" placeholder="选择学期" class="w180">
            <el-option v-for="t in terms" :key="t.id" :label="t.label" :value="t.id" />
          </el-select>
          <el-button type="primary" :disabled="!termSel.length || !fixTermId" @click="assignTerm">划分到该学期（{{ termSel.length }}）</el-button>
        </div>
        <el-table :data="noTerm" size="small" stripe max-height="480" empty-text="点击查询" @selection-change="(v: any[]) => (termSel = v)">
          <el-table-column type="selection" width="36" />
          <el-table-column prop="courseName" label="课程" min-width="160" />
          <el-table-column prop="className" label="班级" width="120" />
          <el-table-column prop="teacherName" label="教师" width="100" />
          <el-table-column label="上课时间" width="160"><template #default="{ row }: { row: any }">{{ fmtTime(row.startedAt) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="强制删除课程" name="course">
        <div><Hint block>删除已经有开课记录的课程，连同它的全部课堂和学习记录（考勤、作品、测验等）。教师自己只能删除没有开课记录的课程。删除后无法恢复，建议先备份数据库。</Hint></div>
        <div class="bar">
          <el-input v-model="keyword" placeholder="课程名或教师名" clearable class="w220" @keyup.enter="loadCourses" />
          <el-button type="primary" @click="loadCourses">查询</el-button>
        </div>
        <el-table :data="courses" size="small" stripe max-height="480" empty-text="输入关键字查询">
          <el-table-column prop="name" label="课程" min-width="180" />
          <el-table-column prop="teacherName" label="教师" width="100" />
          <el-table-column prop="lessonCount" label="开课次数" width="90" />
          <el-table-column label="创建时间" width="150"><template #default="{ row }: { row: any }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }: { row: any }">
              <el-popconfirm :title="`删除《${row.name}》及 ${row.lessonCount} 次开课的全部记录？`" width="280" @confirm="deleteCourse(row)">
                <template #reference><el-button link type="danger">强制删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'

const tab = ref('end')
const years = ref<any[]>([])
const classes = ref<any[]>([])
const terms = ref<any[]>([])
const yearId = ref<number>()
const classId = ref<number>()
const termId = ref<number>()
const absences = ref<any[]>([])
const selected = ref<any[]>([])
const keyword = ref('')
const courses = ref<any[]>([])

async function run<T>(fn: () => Promise<T>, ok?: (r: T) => string) {
  try {
    const r = await fn()
    if (ok) ElMessage.success(ok(r))
    return r
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

const noTerm = ref<any[]>([])
const termSel = ref<any[]>([])
const fixTermId = ref<number>()

async function loadNoTerm() { noTerm.value = (await run(() => api.get('/admin/tools/lessons-without-term'))) || [] }

async function assignTerm() {
  await run(() => api.post<number>('/admin/tools/lessons-without-term', { lessonIds: termSel.value.map(r => r.id), termId: fixTermId.value }), n => `已划分 ${n} 条课堂记录`)
  await loadNoTerm()
}

const endAll = () => run(() => api.post<number>('/admin/tools/end-all'), n => `已让 ${n} 个课堂下课`)

async function loadClasses() {
  classId.value = undefined
  classes.value = await api.get('/admin/classes', { yearId: yearId.value })
}

async function loadAbsences() {
  absences.value = (await run(() => api.get('/admin/tools/absences', { classId: classId.value, termId: termId.value || undefined }))) || []
}

async function clearAbsences() {
  const items = selected.value.map(r => ({ lessonId: r.lessonId, studentId: r.studentId }))
  await run(() => api.post<number>('/admin/tools/absences/clear', { items }), n => `已消除 ${n} 条记录`)
  await loadAbsences()
}

async function loadCourses() {
  courses.value = (await run(() => api.get('/admin/tools/courses', { keyword: keyword.value || undefined }))) || []
}

async function deleteCourse(row: any) {
  await run(() => api.del<number>(`/admin/tools/courses/${row.id}`), n => `已删除《${row.name}》（含 ${n} 次开课记录）`)
  await loadCourses()
}

onMounted(async () => {
  const [ys, ts] = await Promise.all([api.get('/admin/school-years'), api.get('/admin/terms')])
  years.value = ys
  terms.value = ts.map((t: any) => ({ ...t, label: t.label || `${t.yearName} ${t.termName}` }))
})
</script>

<style scoped>
.head { display: flex; align-items: baseline; gap: 12px; }
.warn { color: var(--el-color-danger); font-size: 12px; }
.desc { color: var(--el-text-color-regular); font-size: 13px; line-height: 1.7; margin: 0 0 12px; }
.bar { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.w140 { width: 140px; }
.w180 { width: 180px; }
.w220 { width: 220px; }
</style>
