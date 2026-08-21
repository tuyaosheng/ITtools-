<template>
  <AdminCrudPanel
    title="班级管理"
    dialog-title="班级"
    :dialog-visible="dialogVisible"
    :confirm-loading="saving"
    @update:dialog-visible="dialogVisible = $event"
    @dialog-confirm="submit"
    @dialog-cancel="closeDialog"
  >
    <template #filters>
      <span class="filter-label">学年</span>
      <el-select v-model="yearId" placeholder="请选择学年" class="filter-select" @change="load">
        <el-option v-for="y in years" :key="y.id" :label="y.label" :value="y.id" />
      </el-select>
    </template>

    <template #actions>
      <el-button type="primary" :disabled="!yearId" @click="openCreate">新增班级</el-button>
    </template>

    <el-table v-loading="loading" :data="classes" stripe class="full-width">
      <el-table-column prop="name" label="班级名称" min-width="160" />
      <el-table-column prop="yearLabel" label="所属学年" width="140" />
      <el-table-column prop="displayOrder" label="排序" width="100" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }: { row: any }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该班级？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <template #dialog>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="form.name" placeholder="如 高一(1)班" />
        </el-form-item>
        <el-form-item label="排序" prop="displayOrder">
          <el-input-number v-model="form.displayOrder" :min="0" />
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
  ElPopconfirm,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElSelect,
  ElOption,
  ElMessage,
  type FormInstance,
  type FormRules
} from 'element-plus'
import AdminCrudPanel from '../../components/admin/AdminCrudPanel.vue'
import { adminApi, type ClassItem, type SchoolYearItem } from '../../api/admin'

const years = ref<SchoolYearItem[]>([])
const yearId = ref<number | undefined>(undefined)
const classes = ref<ClassItem[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const formRef = ref<FormInstance>()
const form = reactive({ name: '', displayOrder: 0 })
const rules: FormRules = {
  name: [{ required: true, message: '请输入班级名称', trigger: 'blur' }]
}

async function loadYears() {
  years.value = await adminApi.years.list()
  if (!yearId.value && years.value.length) {
    yearId.value = years.value[0].id
  }
}

async function load() {
  if (!yearId.value) {
    classes.value = []
    return
  }
  loading.value = true
  try {
    classes.value = await adminApi.classes.list(yearId.value)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadYears()
  await load()
})

function openCreate() {
  editingId.value = null
  form.name = ''
  form.displayOrder = 0
  dialogVisible.value = true
}

function openEdit(row: ClassItem) {
  editingId.value = row.id
  form.name = row.name
  form.displayOrder = row.displayOrder
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
}

async function submit() {
  if (!formRef.value || !yearId.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const body = { schoolYearId: yearId.value, name: form.name, displayOrder: form.displayOrder }
    if (editingId.value === null) {
      await adminApi.classes.create(body)
    } else {
      await adminApi.classes.update(editingId.value, body)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: ClassItem) {
  try {
    await adminApi.classes.remove(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '操作失败')
  }
}
</script>

<style scoped>
.full-width {
  width: 100%;
}

.filter-label {
  color: #606266;
  font-size: 14px;
}

.filter-select {
  width: 200px;
}
</style>
