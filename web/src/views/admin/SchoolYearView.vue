<template>
  <AdminCrudPanel
    title="年级管理"
    dialog-title="学年"
    :dialog-visible="dialogVisible"
    :confirm-loading="saving"
    @update:dialog-visible="dialogVisible = $event"
    @dialog-confirm="submit"
    @dialog-cancel="closeDialog"
  >
    <template #actions>
      <el-button type="primary" @click="openCreate">新增学年</el-button>
    </template>

    <el-table v-loading="loading" :data="years" stripe class="full-width">
      <el-table-column prop="yearCode" label="学年代码" width="140" />
      <el-table-column prop="label" label="学年名称" min-width="160" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }: { row: any }">
          <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }: { row: any }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该学年？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <template #dialog>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="学年代码" prop="yearCode">
          <el-input v-model="form.yearCode" placeholder="如 2021" />
        </el-form-item>
        <el-form-item label="学年名称" prop="label">
          <el-input v-model="form.label" placeholder="如 2021级" />
        </el-form-item>
        <el-form-item label="启用" prop="active">
          <el-switch v-model="form.active" />
        </el-form-item>
      </el-form>
    </template>
  </AdminCrudPanel>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import {
  ElTable,
  ElTableColumn,
  ElButton,
  ElTag,
  ElPopconfirm,
  ElForm,
  ElFormItem,
  ElInput,
  ElSwitch,
  ElMessage,
  type FormInstance,
  type FormRules
} from 'element-plus'
import AdminCrudPanel from '../../components/admin/AdminCrudPanel.vue'
import { adminApi, type SchoolYearItem } from '../../api/admin'

const years = ref<SchoolYearItem[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const formRef = ref<FormInstance>()
const form = reactive({ yearCode: '', label: '', active: true })
const rules: FormRules = {
  yearCode: [{ required: true, message: '请输入学年代码', trigger: 'blur' }],
  label: [{ required: true, message: '请输入学年名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    years.value = await adminApi.years.list()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editingId.value = null
  form.yearCode = ''
  form.label = ''
  form.active = true
  dialogVisible.value = true
}

function openEdit(row: SchoolYearItem) {
  editingId.value = row.id
  form.yearCode = row.yearCode
  form.label = row.label
  form.active = row.active
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const body = { yearCode: form.yearCode, label: form.label, active: form.active }
    if (editingId.value === null) {
      await adminApi.years.create(body)
    } else {
      await adminApi.years.update(editingId.value, body)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: SchoolYearItem) {
  await adminApi.years.remove(row.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<style scoped>
.full-width {
  width: 100%;
}
</style>
