<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>走班选课管理</span>
        <el-button type="primary" @click="open()">新建选课活动</el-button>
      </div>
    </template>

    <el-table :data="list" stripe empty-text="暂无选课活动">
      <el-table-column prop="name" label="活动名称" min-width="180" />
      <el-table-column label="面向年级" width="120">
        <template #default="{ row }">{{ row.yearLabel || '全部年级' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-tag :type="statusTag[row.status]" size="small">{{ statusLabel[row.status] }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="chosenCount" label="已选人数" width="90" />
      <el-table-column label="创建时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="330" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'OPEN'" link type="success" @click="setStatus(row, 'OPEN')">开放选课</el-button>
          <el-button v-else link type="warning" @click="setStatus(row, 'CLOSED')">结束选课</el-button>
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button link type="primary" @click="showRoster(row)">名单</el-button>
          <el-button link type="primary" @click="exportRoster(row)">导出</el-button>
          <el-popconfirm title="删除该活动及全部选课记录？" @confirm="remove(row)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑选课活动' : '新建选课活动'" width="900px" top="5vh" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="面向年级">
          <el-select v-model="form.yearId" clearable placeholder="全部年级" class="w200">
            <el-option v-for="y in years" :key="y.id" :label="y.label" :value="y.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="科目">
          <el-table :data="form.subjects" size="small" border class="full">
            <el-table-column label="科目名称" min-width="140">
              <template #default="{ row }"><el-input v-model="row.name" size="small" /></template>
            </el-table-column>
            <el-table-column label="人数上限" width="120">
              <template #default="{ row }"><el-input-number v-model="row.capacity" :min="1" size="small" controls-position="right" class="full" /></template>
            </el-table-column>
            <el-table-column label="任课教师" width="110">
              <template #default="{ row }"><el-input v-model="row.teacherName" size="small" /></template>
            </el-table-column>
            <el-table-column label="上课时间" width="150">
              <template #default="{ row }"><el-input v-model="row.schedule" size="small" placeholder="如 周三第7-8节" /></template>
            </el-table-column>
            <el-table-column label="简介" min-width="140">
              <template #default="{ row }"><el-input v-model="row.description" size="small" /></template>
            </el-table-column>
            <el-table-column width="60">
              <template #default="{ $index, row }">
                <el-button link type="danger" :disabled="row.chosen > 0" @click="form.subjects.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" class="mt8" @click="form.subjects.push({ name: '', capacity: 40, teacherName: '', schedule: '', description: '' })">+ 添加科目</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rosterVisible" :title="'选课名单 - ' + (rosterOf?.name || '')" width="760px" append-to-body>
      <div class="summary">
        <el-tag v-for="s in rosterOf?.subjects || []" :key="s.id" class="mr4">{{ s.name }}：{{ s.chosen }}/{{ s.capacity }}</el-tag>
      </div>
      <el-table :data="rosterOf?.roster || []" size="small" max-height="460" empty-text="还没有学生选课">
        <el-table-column prop="subjectName" label="科目" width="140" />
        <el-table-column prop="className" label="班级" width="120" />
        <el-table-column prop="studentNo" label="学籍号" width="140" />
        <el-table-column prop="studentName" label="姓名" width="100" />
        <el-table-column label="选课时间">
          <template #default="{ row }">{{ fmtTime(row.chosenAt) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'

const statusLabel: Record<string, string> = { DRAFT: '草稿', OPEN: '选课中', CLOSED: '已结束' }
const statusTag: Record<string, string> = { DRAFT: 'info', OPEN: 'success', CLOSED: 'warning' }

const list = ref<any[]>([])
const years = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({ subjects: [] })
const rosterVisible = ref(false)
const rosterOf = ref<any>(null)

async function load() { list.value = await api.get('/admin/electives') }

async function open(r?: any) {
  if (r) {
    const a = await api.get(`/admin/electives/${r.id}`)
    Object.assign(form, { id: a.id, name: a.name, description: a.description, yearId: a.yearId, subjects: a.subjects })
  } else {
    Object.assign(form, { id: null, name: '', description: '', yearId: null, subjects: [{ name: '', capacity: 40, teacherName: '', schedule: '', description: '' }] })
  }
  visible.value = true
}

async function save() {
  const body = {
    name: form.name, description: form.description, yearId: form.yearId || null,
    subjects: form.subjects.map((s: any) => ({ id: s.id ?? null, name: s.name, capacity: s.capacity, teacherName: s.teacherName, schedule: s.schedule, description: s.description }))
  }
  try {
    if (form.id) await api.put(`/admin/electives/${form.id}`, body)
    else await api.post('/admin/electives', body)
    visible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function setStatus(r: any, status: string) {
  await api.put(`/admin/electives/${r.id}/status`, { status })
  await load()
}

async function showRoster(r: any) {
  rosterOf.value = await api.get(`/admin/electives/${r.id}`)
  rosterVisible.value = true
}

async function exportRoster(r: any) {
  try {
    await api.download(`/admin/electives/${r.id}/export`)
  } catch (err) {
    ElMessage.error(errMsg(err, '导出失败'))
  }
}

async function remove(r: any) {
  await api.del(`/admin/electives/${r.id}`)
  await load()
}

onMounted(async () => {
  years.value = await api.get('/admin/school-years')
  await load()
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.w200 { width: 200px; }
.full { width: 100%; }
.mt8 { margin-top: 8px; }
.mr4 { margin-right: 4px; margin-bottom: 4px; }
.summary { margin-bottom: 10px; }
</style>
