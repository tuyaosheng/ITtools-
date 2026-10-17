<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <div class="left">
          <span>我的班级</span>
          <el-select v-model="classId" placeholder="选择班级" class="w200" @change="load">
            <el-option v-for="k in classes" :key="k.id" :label="`${k.name}（${k.studentCount}人）${k.bound ? ' · 已绑定' : ''}`" :value="k.id" />
          </el-select>
        </div>
        <el-button type="primary" :disabled="!classId" @click="addVisible = true">添加学生</el-button>
      </div>
    </template>
    <el-empty v-if="!classId" description="请选择班级" />
    <el-table v-else v-loading="loading" :data="students" stripe>
      <el-table-column label="照片" width="70">
        <template #default="{ row }: { row: any }"><UserAvatar :user-id="row.hasAvatar ? row.id : undefined" :name="row.name" :size="36" /></template>
      </el-table-column>
      <el-table-column prop="studentNo" label="班内学号" width="84" />
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column prop="gender" label="性别" width="60" />
      <el-table-column prop="xjh" label="学号" width="140" />
      <el-table-column prop="presentCount" label="出席次数" width="100" />
      <el-table-column prop="absentCount" label="缺席次数" width="100" />
      <el-table-column prop="avgScore" label="平均课堂分" width="110" />
      <el-table-column label="操作" min-width="200">
        <template #default="{ row }: { row: any }">
          <el-button link type="primary" @click="showRecords(row)">课堂记录</el-button>
          <el-button link type="primary" @click="resetPwd(row)">重置密码</el-button>
          <el-upload :show-file-list="false" accept="image/*" :http-request="(o: any) => uploadPhoto(row, o)" class="inline">
            <el-button link type="primary">{{ row.hasAvatar ? '换照片' : '传照片' }}</el-button>
          </el-upload>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="addVisible" title="添加学生" width="400px" append-to-body>
      <el-form :model="addForm" label-width="84px">
        <el-form-item label="姓名"><el-input v-model="addForm.name" /></el-form-item>
        <el-form-item label="学号" required><el-input v-model="addForm.xjh" placeholder="全校唯一" /></el-form-item>
        <el-form-item label="班内学号"><el-input v-model="addForm.studentNo" placeholder="可不填" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="addForm.password" placeholder="默认 123456" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="addStudent">添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recordsVisible" :title="`${recordsOf?.name} 的课堂记录`" width="760px" append-to-body>
      <el-table :data="records" size="small" max-height="460">
        <el-table-column prop="courseName" label="课程" />
        <el-table-column prop="teacherName" label="教师" width="80" />
        <el-table-column label="时间" width="140"><template #default="{ row }: { row: any }">{{ fmtTime(row.startedAt) }}</template></el-table-column>
        <el-table-column label="考勤" width="70">
          <template #default="{ row }: { row: any }"><el-tag size="small" :type="attendanceTag[row.status]">{{ attendanceLabel[row.status] }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="pcName" label="机位" width="80" />
        <el-table-column prop="score" label="得分" width="60" />
        <el-table-column prop="comment" label="评语" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { attendanceLabel, attendanceTag, fmtTime } from '../../utils/format'
import { refreshAvatars } from '../../utils/avatar'
import UserAvatar from '../../components/UserAvatar.vue'

const classes = ref<any[]>([])
const classId = ref<number>()
const students = ref<any[]>([])
const loading = ref(false)
const addVisible = ref(false)
const addForm = reactive({ name: '', xjh: '', studentNo: '', password: '' })
const recordsVisible = ref(false)
const recordsOf = ref<any>(null)
const records = ref<any[]>([])

async function uploadPhoto(row: any, o: any) {
  try {
    await api.upload(`/teacher/classes/${classId.value}/students/${row.id}/avatar`, o.file)
    refreshAvatars()
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function load() {
  if (!classId.value) return
  loading.value = true
  try {
    students.value = await api.get(`/teacher/classes/${classId.value}/students`)
  } finally {
    loading.value = false
  }
}

async function addStudent() {
  try {
    await api.post(`/teacher/classes/${classId.value}/students`, addForm)
    ElMessage.success('已添加')
    addVisible.value = false
    Object.assign(addForm, { name: '', xjh: '', studentNo: '', password: '' })
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function resetPwd(row: any) {
  try {
    const { value } = await ElMessageBox.prompt(`为 ${row.name} 设置新密码`, '重置密码', { inputValue: '123456' })
    await api.post(`/teacher/students/${row.id}/reset-password`, { password: value })
    ElMessage.success('密码已重置')
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(errMsg(err))
  }
}

async function showRecords(row: any) {
  recordsOf.value = row
  records.value = await api.get(`/teacher/students/${row.id}/records`)
  recordsVisible.value = true
}

onMounted(async () => {
  classes.value = await api.get('/teacher/classes')
  if (classes.value.length) {
    classId.value = classes.value[0].id
    await load()
  }
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.left { display: flex; align-items: center; gap: 12px; }
.w200 { width: 220px; }
.inline { display: inline-block; margin-left: 12px; }
</style>
