<template>
  <div>
    <div class="bar">
      <el-select v-model="pageId" placeholder="选择课后操作题" class="w280" filterable>
        <el-option v-for="p in pages" :key="p.id" :label="`《${p.courseName}》${p.title}（${p.submissions}人练习）`" :value="p.id" />
      </el-select>
      <el-select v-model="classId" placeholder="选择班级" class="w160">
        <el-option v-for="k in classes" :key="k.id" :label="k.name" :value="k.id" />
      </el-select>
      <el-button type="primary" :disabled="!pageId || !classId" @click="load">查询</el-button>
    </div>
    <template v-if="report">
      <el-row :gutter="16">
        <el-col :md="14" :span="24">
          <el-table :data="report.students" size="small" stripe max-height="520">
            <el-table-column prop="studentNo" label="班内学号" width="84" />
            <el-table-column prop="name" label="姓名" width="90" />
            <el-table-column label="练习次数" width="90"><template #default="{ row }: { row: any }">{{ row.attempts || 0 }}</template></el-table-column>
            <el-table-column label="最近" width="90"><template #default="{ row }: { row: any }">{{ row.score ?? '' }}</template></el-table-column>
            <el-table-column label="最好" width="90"><template #default="{ row }: { row: any }"><b>{{ row.bestScore ?? '' }}</b></template></el-table-column>
            <el-table-column label="最近提交" min-width="140"><template #default="{ row }: { row: any }">{{ row.submittedAt ? fmtTime(row.submittedAt) : '未练习' }}</template></el-table-column>
          </el-table>
        </el-col>
        <el-col :md="10" :span="24">
          <div class="h">评分点通过率（各学生最近一次）</div>
          <div v-for="r in report.rules" :key="r.seq" class="rule">
            <div>{{ r.seq }}. {{ r.name }}<span v-if="r.note" class="muted">（{{ r.note }}）</span> <span class="muted">{{ r.passed }}/{{ r.total }}</span></div>
            <el-progress :percentage="r.rate ?? 0" :status="(r.rate ?? 0) < 60 ? 'exception' : 'success'" />
          </div>
          <el-empty v-if="!report.rules.length" description="还没有练习记录" :image-size="60" />
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'

const pages = ref<any[]>([])
const classes = ref<any[]>([])
const pageId = ref<number>()
const classId = ref<number>()
const report = ref<any>(null)

async function load() {
  try {
    report.value = await api.get(`/teacher/op-practice/pages/${pageId.value}`, { classId: classId.value })
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => {
  const [p, k] = await Promise.all([api.get('/teacher/op-practice/pages'), api.get('/teacher/classes')])
  pages.value = p
  classes.value = k
})
</script>

<style scoped>
.bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.w280 { width: 280px; }
.w160 { width: 160px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.h { font-weight: 600; margin-bottom: 8px; }
.rule { margin-bottom: 10px; font-size: 13px; }
</style>
