<template>
  <el-card shadow="never">
    <template #header>我的作品</template>
    <el-table :data="works" stripe>
      <el-table-column label="课程" min-width="150"><template #default="{ row }: { row: any }">《{{ row.courseName }}》</template></el-table-column>
      <el-table-column prop="pageTitle" label="作品页" min-width="110" />
      <el-table-column label="文件" min-width="160">
        <template #default="{ row }: { row: any }"><el-link type="primary" @click="download(row)">{{ row.fileName }}</el-link></template>
      </el-table-column>
      <el-table-column label="提交时间" width="150"><template #default="{ row }: { row: any }">{{ fmtTime(row.submittedAt) }}</template></el-table-column>
      <el-table-column label="自动分" width="90">
        <template #default="{ row }: { row: any }">
          <el-popover v-if="row.autoDetail" trigger="hover" :width="520">
            <template #reference><el-link>{{ row.autoScore }}/{{ row.autoMax }}</el-link></template>
            <AutoDetail :detail="row.autoDetail" />
          </el-popover>
        </template>
      </el-table-column>
      <el-table-column label="老师评分" width="90"><template #default="{ row }: { row: any }">{{ row.teacherScore ?? '待批阅' }}</template></el-table-column>
      <el-table-column prop="peerAvg" label="互评" width="60" />
      <el-table-column prop="teacherComment" label="评语" min-width="140" show-overflow-tooltip />
      <el-table-column label="" width="70"><template #default="{ row }: { row: any }"><el-tag v-if="row.essence" type="warning" size="small">精华</el-tag></template></el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import { fmtTime } from '../../utils/format'
import AutoDetail from '../homework/AutoDetail.vue'

const works = ref<any[]>([])
function download(row: any) { api.download(`/homework/submissions/${row.id}/file`, row.fileName) }
onMounted(async () => { works.value = await api.get('/student/homework/mine') })
</script>
