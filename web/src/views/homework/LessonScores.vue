<template>
  <div>
    <div class="bar">
      <div><Hint block>本节课各测验页、作品提交页、Python 页的得分加上课堂表现分（原“测验 + 操作组合统分”）</Hint></div>
      <el-button size="small" @click="load">刷新</el-button>
      <el-button size="small" type="primary" @click="api.download(`/teacher/lessons/${lesson.id}/scores/export`)">导出 Excel</el-button>
    </div>
    <el-empty v-if="data && !data.columns.length" description="本节课还没有可计分的测验、作品或 Python 成绩" />
    <el-table v-else-if="data" :data="data.rows" size="small" stripe border max-height="560">
      <el-table-column prop="studentNo" label="学号" width="70" fixed />
      <el-table-column prop="name" label="姓名" width="90" fixed />
      <el-table-column v-for="c in data.columns" :key="c.key" :label="`${c.title}（${Number(c.full)}）`" min-width="110">
        <template #header><div class="col-h">{{ c.title }}<span class="muted">{{ typeLabel[c.type] }} · 满分 {{ Number(c.full) }}</span></div></template>
        <template #default="{ row }: { row: any }">{{ row.scores[c.key] == null ? '—' : Number(row.scores[c.key]) }}</template>
      </el-table-column>
      <el-table-column label="课堂表现" width="80"><template #default="{ row }: { row: any }">{{ row.lessonScore ?? '—' }}</template></el-table-column>
      <el-table-column prop="total" label="合计" width="80" sortable fixed="right" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'

const props = defineProps<{ lesson: any }>()
const typeLabel: Record<string, string> = { QUIZ: '测验', HOMEWORK: '作品', PYTHON: 'Python' }
const data = ref<any>(null)

async function load() { data.value = await api.get(`/teacher/lessons/${props.lesson.id}/scores`) }
onMounted(load)
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.bar .muted { flex: 1; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.col-h { display: flex; flex-direction: column; line-height: 1.3; }
</style>
