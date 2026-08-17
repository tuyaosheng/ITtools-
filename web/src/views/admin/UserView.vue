<template>
  <AdminCrudPanel
    title="用户管理"
    dialog-title="用户"
    :dialog-visible="dialogVisible"
    :confirm-loading="saving"
    @update:dialog-visible="dialogVisible = $event"
    @dialog-confirm="submit"
    @dialog-cancel="closeDialog"
  >
    <template #filters>
      <span class="filter-label">角色</span>
      <el-select v-model="roleFilter" placeholder="全部角色" clearable class="filter-select" @change="load">
        <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
      </el-select>

      <span class="filter-label">学年</span>
      <el-select v-model="filterYearId" placeholder="全部学年" clearable class="filter-select" @change="onFilterYearChange">
        <el-option v-for="y in years" :key="y.id" :label="y.label" :value="y.id" />
      </el-select>

      <span class="filter-label">班级</span>
      <el-select
        v-model="classFilter"
        placeholder="全部班级"
        clearable
        class="filter-select"
        :disabled="!filterYearId"
        @change="load"
      >
        <el-option v-for="c in filterClasses" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </template>

    <template #actions>
      <el-button type="primary" @click="openCreate">新增用户</el-button>
    </template>

    <el-table v-loading="loading" :data="users" stripe class="full-width">
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }: { row: any }">{{ roleLabel(row.role) }}</template>
      </el-table-column>
      <el-table-column prop="loginName" label="登录名" width="140" />
      <el-table-column prop="studentNo" label="学号" width="120" />
      <el-table-column prop="xjh" label="学籍号" width="140" />
      <el-table-column prop="className" label="班级" width="140" />
      <el-table-column label="毕业" width="90">
        <template #default="{ row }: { row: any }">
          <el-switch :model-value="row.graduated" @change="(v: string | number | boolean) => toggleGraduate(row, Boolean(v))" />
        </template>
      </el-table-column>
      <el-table-column label="启用" width="90">
        <template #default="{ row }: { row: any }">
          <el-switch :model-value="row.enabled" @change="(v: string | number | boolean) => toggleEnabled(row, Boolean(v))" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }: { row: any }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="resetPassword(row)">重置密码</el-button>
          <el-popconfirm title="确认删除该用户？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <template #dialog>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" :disabled="editingId !== null" class="full-width" @change="onFormRoleChange">
            <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item v-if="form.role !== 'STUDENT'" label="登录名" prop="loginName">
          <el-input v-model="form.loginName" />
        </el-form-item>
        <template v-if="form.role === 'STUDENT'">
          <el-form-item label="学号" prop="studentNo">
            <el-input v-model="form.studentNo" />
          </el-form-item>
          <el-form-item label="学籍号" prop="xjh">
            <el-input v-model="form.xjh" />
          </el-form-item>
          <el-form-item label="学年" prop="enrollYearId">
            <el-select v-model="form.enrollYearId" class="full-width" @change="onFormYearChange">
              <el-option v-for="y in years" :key="y.id" :label="y.label" :value="y.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="班级" prop="classId">
            <el-select v-model="form.classId" class="full-width" :disabled="!form.enrollYearId">
              <el-option v-for="c in formClasses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item v-if="editingId === null" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
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
  ElSwitch,
  ElPopconfirm,
  ElForm,
  ElFormItem,
  ElInput,
  ElSelect,
  ElOption,
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules
} from 'element-plus'
import AdminCrudPanel from '../../components/admin/AdminCrudPanel.vue'
import { adminApi, type UserItem, type UserRole, type SchoolYearItem, type ClassItem } from '../../api/admin'

const roleOptions: { value: UserRole; label: string }[] = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'TEACHER', label: '教师' },
  { value: 'STUDENT', label: '学生' }
]

function roleLabel(role: string) {
  return roleOptions.find(r => r.value === role)?.label ?? role
}

const years = ref<SchoolYearItem[]>([])
const users = ref<UserItem[]>([])
const loading = ref(false)
const saving = ref(false)

// --- filters ---
const roleFilter = ref<UserRole | ''>('')
const filterYearId = ref<number | undefined>(undefined)
const classFilter = ref<number | undefined>(undefined)
const filterClasses = ref<ClassItem[]>([])

async function onFilterYearChange() {
  classFilter.value = undefined
  filterClasses.value = filterYearId.value ? await adminApi.classes.list(filterYearId.value) : []
  await load()
}

async function load() {
  loading.value = true
  try {
    users.value = await adminApi.users.list({
      role: roleFilter.value || undefined,
      classId: classFilter.value
    })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  years.value = await adminApi.years.list()
  await load()
})

// --- create / edit dialog ---
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const formClasses = ref<ClassItem[]>([])
const form = reactive({
  role: 'STUDENT' as UserRole,
  name: '',
  loginName: '',
  studentNo: '',
  xjh: '',
  classId: undefined as number | undefined,
  enrollYearId: undefined as number | undefined,
  password: ''
})
const rules: FormRules = {
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function resetForm() {
  form.role = 'STUDENT'
  form.name = ''
  form.loginName = ''
  form.studentNo = ''
  form.xjh = ''
  form.classId = undefined
  form.enrollYearId = undefined
  form.password = ''
  formClasses.value = []
}

function onFormRoleChange() {
  form.classId = undefined
  form.enrollYearId = undefined
  formClasses.value = []
}

async function onFormYearChange() {
  form.classId = undefined
  formClasses.value = form.enrollYearId ? await adminApi.classes.list(form.enrollYearId) : []
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: UserItem) {
  editingId.value = row.id
  form.role = row.role
  form.name = row.name
  form.loginName = row.loginName ?? ''
  form.studentNo = row.studentNo ?? ''
  form.xjh = row.xjh ?? ''
  form.classId = row.classId ?? undefined
  form.password = ''
  // UserView (the list endpoint) does not expose the student's current
  // enrollYearId, only classId/className - so the year select starts
  // empty and the admin must re-pick it to change the class. If they
  // submit without touching it, the pre-filled classId is still sent
  // (see submit()), but enrollYearId is omitted, which per the backend's
  // FULL-REPLACE semantics clears the stored enrollYearId.
  formClasses.value = []
  form.enrollYearId = undefined
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
    if (editingId.value === null) {
      await adminApi.users.create({
        role: form.role,
        name: form.name,
        loginName: form.role === 'STUDENT' ? undefined : form.loginName || undefined,
        studentNo: form.role === 'STUDENT' ? form.studentNo || undefined : undefined,
        xjh: form.role === 'STUDENT' ? form.xjh || undefined : undefined,
        classId: form.role === 'STUDENT' ? form.classId ?? undefined : undefined,
        enrollYearId: form.role === 'STUDENT' ? form.enrollYearId ?? undefined : undefined,
        password: form.password
      })
    } else {
      // FULL-REPLACE: always send the complete object, omitted class/enrollYear get cleared.
      await adminApi.users.update(editingId.value, {
        name: form.name,
        loginName: form.role === 'STUDENT' ? undefined : form.loginName || undefined,
        studentNo: form.role === 'STUDENT' ? form.studentNo || undefined : undefined,
        xjh: form.role === 'STUDENT' ? form.xjh || undefined : undefined,
        classId: form.role === 'STUDENT' ? form.classId ?? undefined : undefined,
        enrollYearId: form.role === 'STUDENT' ? form.enrollYearId ?? undefined : undefined
      })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: UserItem) {
  await adminApi.users.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

async function resetPassword(row: UserItem) {
  try {
    const { value } = await ElMessageBox.prompt(`为 ${row.name} 设置新密码`, '重置密码', {
      inputType: 'password',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValidator: (v: string) => (!!v && v.length >= 6) || '密码至少 6 位'
    })
    await adminApi.users.resetPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch {
    // cancelled
  }
}

async function toggleGraduate(row: UserItem, value: boolean) {
  await adminApi.users.graduate(row.id, value)
  row.graduated = value
  ElMessage.success('已更新')
}

async function toggleEnabled(row: UserItem, value: boolean) {
  await adminApi.users.enabled(row.id, value)
  row.enabled = value
  ElMessage.success('已更新')
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
  width: 160px;
}
</style>
