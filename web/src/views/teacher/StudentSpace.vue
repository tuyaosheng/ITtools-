<template>
  <el-card shadow="never">
    <template #header>学生空间管理</template>
    <el-tabs v-model="tab">
      <el-tab-pane label="学生网盘" name="disk">
        <div class="bar">
          <el-select v-model="classId" placeholder="选择班级" class="w160" @change="loadDisks">
            <el-option v-for="k in classes" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
          <Hint>可以删除与学习无关的文件，勾选“扣除空间”后被删文件的大小继续计入该生已用空间作为惩罚；也可以单独关闭某个学生的网盘。</Hint>
        </div>
        <el-table :data="disks" size="small" stripe max-height="460" empty-text="请选择班级" highlight-current-row @current-change="openStudent">
          <el-table-column prop="studentNo" label="学号" width="70" />
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column prop="files" label="文件数" width="80" />
          <el-table-column label="已用" width="100"><template #default="{ row }: { row: any }">{{ fmtSize(row.used) }}</template></el-table-column>
          <el-table-column label="扣除空间" width="100"><template #default="{ row }: { row: any }">{{ row.penaltyBytes ? fmtSize(row.penaltyBytes) : '' }}</template></el-table-column>
          <el-table-column label="网盘" width="160">
            <template #default="{ row }: { row: any }">
              <el-switch :model-value="!row.disabled" active-text="开启" inactive-text="关闭" inline-prompt @click.stop
                @change="(v: boolean) => setPolicy(row, { disabled: !v })" />
              <el-button v-if="row.penaltyBytes" link type="primary" size="small" @click.stop="setPolicy(row, { resetPenalty: true })">清除扣除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <template v-if="student">
          <el-divider content-position="left">{{ student.name }} 的网盘文件</el-divider>
          <el-checkbox v-model="penalty" class="mb">删除时扣除网盘空间</el-checkbox>
          <el-table :data="files" size="small" stripe empty-text="网盘是空的">
            <el-table-column prop="title" label="名称" min-width="160" />
            <el-table-column prop="fileName" label="文件" min-width="160" />
            <el-table-column label="大小" width="90"><template #default="{ row }: { row: any }">{{ fmtSize(row.fileSize) }}</template></el-table-column>
            <el-table-column label="上传时间" width="150"><template #default="{ row }: { row: any }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }: { row: any }">
                <el-button link type="primary" @click="download(row)">下载</el-button>
                <el-popconfirm title="删除该文件？" @confirm="removeFile(row)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>
      <el-tab-pane label="学生网站" name="sites" lazy>
        <ClassSites :classes="classes" />
      </el-tab-pane>
      <el-tab-pane label="指法英雄榜" name="typing" lazy>
        <TypingLeaderboard :classes="classes" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtSize, fmtTime } from '../../utils/format'
import ClassSites from '../student/ClassSites.vue'
import TypingLeaderboard from '../student/TypingLeaderboard.vue'

const tab = ref('disk')
const classes = ref<any[]>([])
const classId = ref<number>()
const disks = ref<any[]>([])
const student = ref<any>(null)
const files = ref<any[]>([])
const penalty = ref(false)

async function loadDisks() {
  student.value = null
  disks.value = await api.get(`/teacher/disks/classes/${classId.value}`)
}

async function openStudent(row: any) {
  if (!row) return
  student.value = row
  files.value = await api.get(`/teacher/disks/students/${row.studentId}`)
}

async function setPolicy(row: any, body: any) {
  try {
    await api.put(`/teacher/disks/students/${row.studentId}/policy`, body)
    const keep = student.value?.studentId
    disks.value = await api.get(`/teacher/disks/classes/${classId.value}`)
    if (keep) student.value = disks.value.find(d => d.studentId === keep) || null
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

function download(f: any) {
  api.download(`/teacher/disks/students/${student.value.studentId}/files/${f.id}`, f.fileName)
}

async function removeFile(f: any) {
  try {
    await api.del(`/teacher/disks/students/${student.value.studentId}/files/${f.id}?penalty=${penalty.value}`)
    ElMessage.success(penalty.value ? '已删除，并扣除相应空间' : '已删除')
    const keep = student.value
    disks.value = await api.get(`/teacher/disks/classes/${classId.value}`)
    await openStudent(disks.value.find(d => d.studentId === keep.studentId) || keep)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => { classes.value = await api.get('/teacher/classes') })
</script>

<style scoped>
.bar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 10px; }
.w160 { width: 160px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.mb { margin-bottom: 8px; }
</style>
