<template>
  <el-card shadow="never">
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="学期维护" name="terms">
        <div class="ops"><el-button type="primary" size="small" @click="openTerm()">新增学期</el-button></div>
        <el-table :data="terms" stripe>
          <el-table-column prop="yearName" label="学年" />
          <el-table-column prop="termName" label="学期" />
          <el-table-column prop="displayOrder" label="排序" width="80" />
          <el-table-column label="当前学期" width="110">
            <template #default="{ row }: { row: any }">
              <el-tag v-if="row.current" type="success">当前</el-tag>
              <el-button v-else link type="primary" @click="setCurrent(row)">设为当前</el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }: { row: any }">
              <el-button link type="primary" @click="openTerm(row)">编辑</el-button>
              <el-popconfirm title="删除该学期？" @confirm="delTerm(row)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="班级绑定教师" name="bindings">
        <el-table :data="bindings" stripe>
          <el-table-column prop="className" label="班级" />
          <el-table-column label="绑定教师">
            <template #default="{ row }: { row: any }">
              <el-select v-model="row.teacherId" clearable placeholder="未绑定" size="small" @change="bind(row)">
                <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="当前课堂" name="active">
        <el-table :data="active" stripe>
          <el-table-column prop="className" label="班级" />
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="teacherName" label="教师" />
          <el-table-column label="开始时间"><template #default="{ row }: { row: any }">{{ fmtTime(row.startedAt) }}</template></el-table-column>
          <el-table-column label="出席"><template #default="{ row }: { row: any }">{{ row.presentCount }}/{{ row.studentCount }}</template></el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }: { row: any }">
              <el-popconfirm title="强制结束该课堂？" @confirm="forceEnd(row)">
                <template #reference><el-button link type="danger">强制下课</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="termDialog" :title="termForm.id ? '编辑学期' : '新增学期'" width="400px" append-to-body>
      <el-form :model="termForm" label-width="70px">
        <el-form-item label="学年"><el-input v-model="termForm.yearName" placeholder="如 2025-2026" /></el-form-item>
        <el-form-item label="学期"><el-input v-model="termForm.termName" placeholder="如 第一学期" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="termForm.displayOrder" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="termDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTerm">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'

const tab = ref('terms')
const terms = ref<any[]>([])
const bindings = ref<any[]>([])
const teachers = ref<any[]>([])
const active = ref<any[]>([])
const termDialog = ref(false)
const termForm = reactive<any>({})

async function load() {
  if (tab.value === 'terms') terms.value = await api.get('/admin/terms')
  if (tab.value === 'bindings') {
    bindings.value = await api.get('/admin/class-bindings')
    teachers.value = await api.get('/admin/users', { role: 'TEACHER' })
  }
  if (tab.value === 'active') active.value = await api.get('/admin/lessons/active')
}

function openTerm(row?: any) {
  Object.keys(termForm).forEach(k => delete termForm[k])
  Object.assign(termForm, row ? { ...row } : { yearName: '', termName: '', displayOrder: terms.value.length + 1 })
  termDialog.value = true
}

async function saveTerm() {
  try {
    if (termForm.id) await api.put(`/admin/terms/${termForm.id}`, termForm)
    else await api.post('/admin/terms', termForm)
    termDialog.value = false
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function delTerm(row: any) {
  try { await api.del(`/admin/terms/${row.id}`); await load() } catch (err) { ElMessage.error(errMsg(err)) }
}

async function setCurrent(row: any) {
  await api.post(`/admin/terms/${row.id}/current`)
  await load()
}

async function bind(row: any) {
  try {
    await api.put(`/admin/class-bindings/${row.classId}`, { teacherId: row.teacherId ?? null })
    ElMessage.success('已保存')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function forceEnd(row: any) {
  await api.post(`/admin/lessons/${row.id}/force-end`)
  ElMessage.success('已结束')
  await load()
}

onMounted(load)
</script>

<style scoped>
.ops { margin-bottom: 10px; }
.tip { color: var(--el-text-color-secondary); font-size: 13px; margin: 0 0 10px; }
</style>
