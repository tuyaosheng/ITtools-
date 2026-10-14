<template>
  <AdminCrudPanel
    title="机位管理"
    dialog-title="机位"
    :dialog-visible="dialogVisible"
    :confirm-loading="saving"
    @update:dialog-visible="dialogVisible = $event"
    @dialog-confirm="submit"
    @dialog-cancel="closeDialog"
  >
    <template #actions>
      <el-button @click="downloadTemplate">下载导入模板</el-button>
      <el-button @click="importVisible = true">批量导入</el-button>
      <el-button type="primary" @click="openCreate">新增机位</el-button>
    </template>

    <el-table v-loading="loading" :data="workstations" stripe class="full-width">
      <el-table-column prop="pcName" label="计算机名" min-width="160" />
      <el-table-column prop="ipAddress" label="IP地址" min-width="160" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }: { row: any }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该机位？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="importVisible" title="批量导入机位" width="520px" append-to-body>
      <div><Hint block label="表格怎么填？">表头需包含“计算机名”“IP地址”两列，支持 .xlsx / .xls / .csv；可先下载导入模板照着填</Hint></div>
      <div class="import-row">
        <input ref="fileInput" type="file" accept=".xlsx,.xls,.csv" @change="onFile" />
      </div>
      <el-checkbox v-model="deleteExisting">导入前清空已有机位（原“删除已有IP”）</el-checkbox>
      <div v-if="importResult" class="import-result">
        共 {{ importResult.total }} 行，成功 {{ importResult.imported }}，失败 {{ importResult.failed }}
        <ul><li v-for="e in importResult.errors" :key="e.row">第 {{ e.row }} 行：{{ e.message }}</li></ul>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="doImport">开始导入</el-button>
      </template>
    </el-dialog>

    <template #dialog>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="计算机名" prop="pcName">
          <el-input v-model="form.pcName" placeholder="如 PC-01" />
        </el-form-item>
        <el-form-item label="IP地址" prop="ipAddress">
          <el-input v-model="form.ipAddress" placeholder="如 192.168.1.101" />
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
  ElMessage,
  ElDialog,
  ElAlert,
  ElCheckbox,
  type FormInstance,
  type FormRules
} from 'element-plus'
import AdminCrudPanel from '../../components/admin/AdminCrudPanel.vue'
import { adminApi, type LabWorkstationItem } from '../../api/admin'
import { api, errMsg } from '../../api/client'

const importVisible = ref(false)
const importing = ref(false)
const importFile = ref<File | null>(null)
const deleteExisting = ref(false)
const importResult = ref<any>(null)

function onFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  importFile.value = f ?? null
}

async function doImport() {
  if (!importFile.value) return
  importing.value = true
  try {
    importResult.value = await api.upload('/admin/lab/workstations/import', importFile.value, { deleteExisting: deleteExisting.value })
    ElMessage.success('导入完成')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    importing.value = false
  }
}

function downloadTemplate() {
  api.download('/admin/lab/workstations/template', '机位导入模板.xlsx').catch(err => ElMessage.error(errMsg(err)))
}

const workstations = ref<LabWorkstationItem[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const formRef = ref<FormInstance>()
const form = reactive({ pcName: '', ipAddress: '' })
const rules: FormRules = {
  pcName: [{ required: true, message: '请输入计算机名', trigger: 'blur' }],
  ipAddress: [{ required: true, message: '请输入IP地址', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    workstations.value = await adminApi.labWorkstations.list()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editingId.value = null
  form.pcName = ''
  form.ipAddress = ''
  dialogVisible.value = true
}

function openEdit(row: LabWorkstationItem) {
  editingId.value = row.id
  form.pcName = row.pcName
  form.ipAddress = row.ipAddress
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
    const body = { pcName: form.pcName, ipAddress: form.ipAddress }
    if (editingId.value === null) {
      await adminApi.labWorkstations.create(body)
    } else {
      await adminApi.labWorkstations.update(editingId.value, body)
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

async function remove(row: LabWorkstationItem) {
  try {
    await adminApi.labWorkstations.remove(row.id)
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
.import-row {
  margin: 12px 0;
}
.import-result {
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
