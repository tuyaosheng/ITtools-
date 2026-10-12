<template>
  <el-card shadow="never">
    <template #header>我的作品</template>
    <div v-if="late.length" class="late">
      <div class="late-title">需要补交 / 重做的作品（{{ late.length }}）</div>
      <div v-for="l in late" :key="l.lessonId + '-' + l.pageId" class="late-row">
        <span>《{{ l.courseName }}》· {{ l.pageTitle }} <span class="muted">{{ fmtTime(l.startedAt) }}</span></span>
        <el-tag v-if="l.returned" type="danger" size="small">老师发回重做{{ l.returnNote ? '：' + l.returnNote : '' }}</el-tag>
        <el-tag v-else type="warning" size="small">未交</el-tag>
        <el-button size="small" type="primary" link @click="router.push(`/student/lessons/${l.lessonId}?page=${l.pageId}`)">去提交</el-button>
      </div>
    </div>
    <el-table :data="works" stripe>
      <el-table-column label="课程" min-width="150"><template #default="{ row }: { row: any }">《{{ row.courseName }}》</template></el-table-column>
      <el-table-column label="作品" min-width="130">
        <template #default="{ row }: { row: any }">{{ row.pageTitle }}<div v-if="row.title" class="muted">《{{ row.title }}》</div></template>
      </el-table-column>
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
      <el-table-column label="老师评分" width="100">
        <template #default="{ row }: { row: any }">{{ row.teacherScore ?? '待批阅' }}<span v-if="row.rating" class="rating">{{ ratingLabel[row.rating] }}</span></template>
      </el-table-column>
      <el-table-column prop="peerAvg" label="互评" width="60" />
      <el-table-column prop="teacherComment" label="评语" min-width="140" show-overflow-tooltip />
      <el-table-column label="" width="70"><template #default="{ row }: { row: any }"><el-tag v-if="row.essence" type="warning" size="small">精华</el-tag></template></el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api/client'
import { fmtTime } from '../../utils/format'
import AutoDetail from '../homework/AutoDetail.vue'

const router = useRouter()
const works = ref<any[]>([])
const late = ref<any[]>([])
const ratingLabel: Record<string, string> = { GOOD: '好评', OK: '中评', BAD: '差评' }
function download(row: any) { api.download(`/homework/submissions/${row.id}/file`, row.fileName) }
onMounted(async () => {
  ;[works.value, late.value] = await Promise.all([api.get('/student/homework/mine'), api.get('/student/homework/late')])
})
</script>

<style scoped>
.late { margin-bottom: 14px; padding: 10px 12px; background: #fdf6ec; border-radius: 6px; }
.late-title { font-weight: 600; margin-bottom: 6px; }
.late-row { display: flex; align-items: center; gap: 8px; padding: 3px 0; font-size: 13px; }
.muted { color: #909399; font-size: 12px; }
.rating { margin-left: 6px; font-size: 12px; color: #e6a23c; }
</style>
