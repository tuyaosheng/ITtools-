<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head" :class="{ mobile: isMobile }">
        <div class="left">
          <span v-if="!isMobile">我的班级</span>
          <el-select v-model="classId" placeholder="选择班级" class="w200" @change="load">
            <el-option v-for="k in classes" :key="k.id" :label="`${k.name}（${k.studentCount}人）${k.bound ? ' · 已绑定' : ''}`" :value="k.id" />
          </el-select>
        </div>
        <el-button type="primary" :disabled="!classId" @click="addVisible = true">添加学生</el-button>
      </div>
    </template>
    <el-empty v-if="!classId" description="请选择班级" />
    <!-- 手机：搜索 + 卡片，方便上课时直接重置密码 -->
    <div v-else-if="isMobile" v-loading="loading" class="m-list">
      <el-input v-model="kw" clearable size="large" placeholder="搜索姓名 / 学号" />
      <div v-for="row in filtered" :key="row.id" class="m-card">
        <UserAvatar :user-id="row.hasAvatar ? row.id : undefined" :name="row.name" :size="40" />
        <div class="m-who">
          <div class="m-name">{{ row.name }} <span class="m-no">{{ row.studentNo }}</span></div>
          <div class="m-meta">学号 {{ row.xjh || '—' }} · 出席 {{ row.presentCount ?? 0 }} · 均分 {{ row.avgScore ?? '—' }}</div>
        </div>
        <el-button type="warning" plain @click="resetPwd(row)">重置密码</el-button>
        <el-dropdown trigger="click" @command="(c: string) => c === 'records' ? showRecords(row) : kick(row)">
          <el-button text class="m-more">⋯</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="records">课堂记录</el-dropdown-item>
              <el-dropdown-item command="kick" :disabled="!row.onlineIp">强制下线{{ row.onlineIp ? `（${row.onlineIp}）` : '（不在线）' }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <el-empty v-if="!filtered.length" description="没有符合的学生" :image-size="60" />
    </div>
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
      <el-table-column label="在线" width="130">
        <template #default="{ row }: { row: any }">
          <span v-if="row.onlineIp" class="online">● {{ row.onlineIp }}</span><span v-else class="offline">不在线</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="200">
        <template #default="{ row }: { row: any }">
          <el-button link type="primary" @click="showRecords(row)">课堂记录</el-button>
          <el-button link type="primary" @click="resetPwd(row)">重置密码</el-button>
          <el-button link type="warning" :disabled="!row.onlineIp" @click="kick(row)">强制下线</el-button>
          <el-upload :show-file-list="false" accept="image/*" :http-request="(o: any) => uploadPhoto(row, o)" class="inline">
            <el-button link type="primary">{{ row.hasAvatar ? '换照片' : '传照片' }}</el-button>
          </el-upload>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="addVisible" title="添加学生" :width="isMobile ? '94%' : '400px'" append-to-body>
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

    <el-dialog v-model="recordsVisible" :title="`${recordsOf?.name} 的课堂记录`" :width="isMobile ? '96%' : '760px'" append-to-body>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useIsMobile } from '../../composables/useIsMobile'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { attendanceLabel, attendanceTag, fmtTime } from '../../utils/format'
import { refreshAvatars } from '../../utils/avatar'
import UserAvatar from '../../components/UserAvatar.vue'

const classes = ref<any[]>([])
const classId = ref<number>()
const students = ref<any[]>([])
const loading = ref(false)
const isMobile = useIsMobile()
const kw = ref('')
const filtered = computed(() => {
  const k = kw.value.trim()
  return k ? students.value.filter(s => [s.name, s.studentNo, s.xjh].some(v => String(v ?? '').includes(k))) : students.value
})
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
    const { value } = await ElMessageBox.prompt(`为 ${row.name}（${row.xjh || row.studentNo || ''}）设置新密码`, '重置密码',
      { inputValue: '123456', confirmButtonText: '重置', inputValidator: (v: string) => !!v?.trim() || '密码不能为空' })
    await api.post(`/teacher/students/${row.id}/reset-password`, { password: value })
    ElMessage.success('密码已重置')
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(errMsg(err))
  }
}

async function kick(row: any) {
  try {
    await ElMessageBox.confirm(`让 ${row.name} 在 ${row.onlineIp || '所有电脑'} 上的登录下线？下线后他可以在任意一台电脑重新登录。`, '强制下线',
      { type: 'warning', confirmButtonText: '下线' })
    const n = await api.post(`/teacher/students/${row.id}/kick`, {})
    ElMessage.success(n ? `${row.name} 已下线` : `${row.name} 当前不在线`)
    await load()
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
.online { color: var(--el-color-success); }
.offline { color: var(--el-text-color-secondary); }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.left { display: flex; align-items: center; gap: 12px; }
.w200 { width: 220px; }
.inline { display: inline-block; margin-left: 12px; }
.card-head.mobile .left { flex: 1; min-width: 0; }
.card-head.mobile .w200 { width: 100%; }
.card-head.mobile { gap: 8px; }
.m-list { display: flex; flex-direction: column; gap: 8px; }
.m-card { display: flex; align-items: center; gap: 10px; padding: 10px; border-radius: 12px; border: 1.5px solid var(--hb-line); background: var(--hb-paper); }
.m-who { flex: 1; min-width: 0; }
.m-name { font-weight: 800; font-size: 16px; color: var(--hb-ink-deep); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.m-no { font-weight: normal; font-size: 12px; color: var(--el-text-color-secondary); }
.m-meta { font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.m-more { padding: 4px 6px; font-size: 18px; }
</style>
