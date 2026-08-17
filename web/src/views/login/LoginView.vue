<template>
  <div class="login-view">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-header">
          <h1>ITtools 平台登录</h1>
        </div>
      </template>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        class="login-alert"
        @close="errorMessage = ''"
      />

      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="学生 · 班级姓名" name="student-name">
          <el-form ref="studentNameFormRef" :model="studentNameForm" :rules="studentNameRules" label-width="70px" @submit.prevent>
            <el-form-item label="学年" prop="yearId">
              <el-select v-model="studentNameForm.yearId" placeholder="请选择学年" class="full-width" @change="onYearChange">
                <el-option v-for="y in years" :key="y.id" :label="y.label" :value="y.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="班级" prop="classId">
              <el-select
                v-model="studentNameForm.classId"
                placeholder="请选择班级"
                class="full-width"
                :disabled="!studentNameForm.yearId"
                @change="onClassChange"
              >
                <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="姓名" prop="name">
              <el-select v-model="studentNameForm.name" placeholder="请选择姓名" class="full-width" :disabled="!studentNameForm.classId">
                <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.name" />
              </el-select>
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="studentNameForm.password" type="password" show-password placeholder="请输入密码" />
            </el-form-item>
            <el-button type="primary" class="full-width" :loading="loading" @click="submitStudentName">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="学生 · 学籍号" name="student-xjh">
          <el-form ref="studentXjhFormRef" :model="studentXjhForm" :rules="studentXjhRules" label-width="70px" @submit.prevent>
            <el-form-item label="学籍号" prop="xjh">
              <el-input v-model="studentXjhForm.xjh" placeholder="请输入学籍号" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="studentXjhForm.password" type="password" show-password placeholder="请输入密码" />
            </el-form-item>
            <el-button type="primary" class="full-width" :loading="loading" @click="submitStudentXjh">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="教师" name="teacher">
          <el-form ref="teacherFormRef" :model="teacherForm" :rules="credentialRules" label-width="70px" @submit.prevent>
            <el-form-item label="账号" prop="loginName">
              <el-input v-model="teacherForm.loginName" placeholder="请输入教师账号" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="teacherForm.password" type="password" show-password placeholder="请输入密码" />
            </el-form-item>
            <el-button type="primary" class="full-width" :loading="loading" @click="submitTeacher">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="管理员" name="admin">
          <el-form ref="adminFormRef" :model="adminForm" :rules="credentialRules" label-width="70px" @submit.prevent>
            <el-form-item label="账号" prop="loginName">
              <el-input v-model="adminForm.loginName" placeholder="请输入管理员账号" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="adminForm.password" type="password" show-password placeholder="请输入密码" />
            </el-form-item>
            <el-button type="primary" class="full-width" :loading="loading" @click="submitAdmin">登录</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElCard,
  ElAlert,
  ElTabs,
  ElTabPane,
  ElForm,
  ElFormItem,
  ElSelect,
  ElOption,
  ElInput,
  ElButton,
  type FormInstance,
  type FormRules
} from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import { publicApi, type SchoolYear, type SchoolClass, type StudentLite } from '../../api/auth'

const router = useRouter()
const auth = useAuthStore()

const activeTab = ref('student-name')
const loading = ref(false)
const errorMessage = ref('')

// --- 学生 · 班级姓名 ---
const years = ref<SchoolYear[]>([])
const classes = ref<SchoolClass[]>([])
const students = ref<StudentLite[]>([])

const studentNameFormRef = ref<FormInstance>()
const studentNameForm = reactive({
  yearId: undefined as number | undefined,
  classId: undefined as number | undefined,
  name: '',
  password: ''
})
const studentNameRules: FormRules = {
  yearId: [{ required: true, message: '请选择学年', trigger: 'change' }],
  classId: [{ required: true, message: '请选择班级', trigger: 'change' }],
  name: [{ required: true, message: '请选择姓名', trigger: 'change' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(async () => {
  years.value = await publicApi.years()
})

async function onYearChange(yearId: number) {
  studentNameForm.classId = undefined
  studentNameForm.name = ''
  classes.value = []
  students.value = []
  if (!yearId) return
  classes.value = await publicApi.classes(yearId)
}

async function onClassChange(classId: number) {
  studentNameForm.name = ''
  students.value = []
  if (!classId) return
  students.value = await publicApi.students(classId)
}

// --- 学生 · 学籍号 ---
const studentXjhFormRef = ref<FormInstance>()
const studentXjhForm = reactive({ xjh: '', password: '' })
const studentXjhRules: FormRules = {
  xjh: [{ required: true, message: '请输入学籍号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// --- 教师 / 管理员 ---
const teacherFormRef = ref<FormInstance>()
const teacherForm = reactive({ loginName: '', password: '' })
const adminFormRef = ref<FormInstance>()
const adminForm = reactive({ loginName: '', password: '' })
const credentialRules: FormRules = {
  loginName: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function doLogin(payload: Record<string, unknown>) {
  errorMessage.value = ''
  loading.value = true
  try {
    await auth.login(payload)
    routeByRole()
  } catch {
    errorMessage.value = '用户名或密码错误'
  } finally {
    loading.value = false
  }
}

function routeByRole() {
  const role = auth.user?.role
  if (role === 'ADMIN') router.push('/admin')
  else if (role === 'TEACHER') router.push('/teacher')
  else if (role === 'STUDENT') router.push('/student')
  else router.push('/login')
}

async function submitStudentName() {
  if (!studentNameFormRef.value) return
  try {
    await studentNameFormRef.value.validate()
  } catch {
    return
  }
  const year = years.value.find(y => y.id === studentNameForm.yearId)
  const klass = classes.value.find(c => c.id === studentNameForm.classId)
  await doLogin({
    loginType: 'STUDENT_NAME',
    yearCode: year?.yearCode,
    className: klass?.name,
    name: studentNameForm.name,
    password: studentNameForm.password
  })
}

async function submitStudentXjh() {
  if (!studentXjhFormRef.value) return
  try {
    await studentXjhFormRef.value.validate()
  } catch {
    return
  }
  await doLogin({
    loginType: 'STUDENT_XJH',
    xjh: studentXjhForm.xjh,
    password: studentXjhForm.password
  })
}

async function submitTeacher() {
  if (!teacherFormRef.value) return
  try {
    await teacherFormRef.value.validate()
  } catch {
    return
  }
  await doLogin({
    loginType: 'TEACHER',
    loginName: teacherForm.loginName,
    password: teacherForm.password
  })
}

async function submitAdmin() {
  if (!adminFormRef.value) return
  try {
    await adminFormRef.value.validate()
  } catch {
    return
  }
  await doLogin({
    loginType: 'ADMIN',
    loginName: adminForm.loginName,
    password: adminForm.password
  })
}
</script>

<style scoped>
.login-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e9f2 100%);
  padding: 16px;
}

.login-card {
  width: 100%;
  max-width: 420px;
}

.login-header h1 {
  margin: 0;
  font-size: 20px;
  text-align: center;
}

.login-alert {
  margin-bottom: 16px;
}

.full-width {
  width: 100%;
}
</style>
