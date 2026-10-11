<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>上课记录</span>
        <el-radio-group v-model="status" size="small" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="ACTIVE">进行中</el-radio-button>
          <el-radio-button value="ENDED">已结束</el-radio-button>
        </el-radio-group>
      </div>
    </template>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="课程" min-width="180">
        <template #default="{ row }: { row: any }">《{{ row.courseName }}》</template>
      </el-table-column>
      <el-table-column prop="className" label="班级" width="130" />
      <el-table-column label="上课时间" width="160">
        <template #default="{ row }: { row: any }">{{ fmtTime(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column label="出席/人数" width="100">
        <template #default="{ row }: { row: any }">{{ row.presentCount }}/{{ row.studentCount }}</template>
      </el-table-column>
      <el-table-column prop="absentCount" label="缺席" width="70" />
      <el-table-column prop="leaveCount" label="请假" width="70" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }: { row: any }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '上课中' : '已结束' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="summary" label="小结" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="100">
        <template #default="{ row }: { row: any }">
          <el-button link type="primary" @click="router.push(`/teacher/lessons/${row.id}`)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api/client'
import { fmtTime } from '../../utils/format'

const router = useRouter()
const list = ref<any[]>([])
const status = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await api.get('/teacher/lessons', status.value ? { status: status.value } : undefined)
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
</style>
