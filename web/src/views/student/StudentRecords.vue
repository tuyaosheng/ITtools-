<template>
  <el-card shadow="never">
    <template #header>学习记录</template>
    <el-table :data="records" stripe>
      <el-table-column label="课程" min-width="180">
        <template #default="{ row }: { row: any }">
          <el-link type="primary" @click="router.push(`/student/lessons/${row.lessonId}`)">《{{ row.courseName }}》</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="teacherName" label="教师" width="100" />
      <el-table-column label="上课时间" width="160"><template #default="{ row }: { row: any }">{{ fmtTime(row.startedAt) }}</template></el-table-column>
      <el-table-column label="考勤" width="80">
        <template #default="{ row }: { row: any }"><el-tag size="small" :type="attendanceTag[row.status]">{{ attendanceLabel[row.status] }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="pcName" label="机位" width="90" />
      <el-table-column prop="score" label="课堂得分" width="90" />
      <el-table-column prop="comment" label="老师评语" min-width="160" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api/client'
import { attendanceLabel, attendanceTag, fmtTime } from '../../utils/format'

const router = useRouter()
const records = ref<any[]>([])
onMounted(async () => { records.value = await api.get('/student/records') })
</script>
