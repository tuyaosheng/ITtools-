<template>
  <div>
    <div class="bar">
      <span class="muted">学生在各页面的停留时间（分:秒）与访问次数，由学生课堂页面心跳累计，每 20 秒刷新。</span>
      <div class="actions">
        <el-checkbox v-model="showVisits" size="small">显示访问次数</el-checkbox>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </div>
    <el-table :data="data.rows" size="small" stripe border max-height="520" :default-sort="{ prop: 'studentNo', order: 'ascending' }" empty-text="还没有学生进入课堂">
      <el-table-column prop="studentNo" label="学号" width="70" sortable fixed />
      <el-table-column prop="name" label="姓名" width="90" fixed />
      <el-table-column v-for="p in data.pages" :key="p.id" :label="`${p.seq}. ${p.title}`" min-width="110" align="center">
        <template #default="{ row }: { row: any }">
          <template v-if="row.cells[p.id]">
            <span :class="{ weak: row.cells[p.id].seconds < 10 }">{{ mmss(row.cells[p.id].seconds) }}</span>
            <span v-if="showVisits" class="muted"> ×{{ row.cells[p.id].visits }}</span>
          </template>
          <span v-else class="none">未看</span>
        </template>
      </el-table-column>
      <el-table-column label="合计" prop="total" width="90" sortable fixed="right">
        <template #default="{ row }: { row: any }"><b>{{ mmss(row.total) }}</b></template>
      </el-table-column>
    </el-table>
    <div v-if="pageAvg.length" class="avg">
      <span class="muted">各页平均停留：</span>
      <el-tag v-for="a in pageAvg" :key="a.id" size="small" type="info" class="tag">{{ a.title }} {{ mmss(a.avg) }}（{{ a.viewed }}人看过）</el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ lesson: any }>()
const data = ref<any>({ pages: [], rows: [] })
const showVisits = ref(false)
let timer: number | undefined

const mmss = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`

const pageAvg = computed(() => data.value.pages.map((p: any) => {
  const cells = data.value.rows.map((r: any) => r.cells[p.id]).filter((c: any) => c && c.seconds > 0)
  const sum = cells.reduce((a: number, c: any) => a + c.seconds, 0)
  return { id: p.id, title: p.title, viewed: cells.length, avg: cells.length ? Math.round(sum / cells.length) : 0 }
}))

async function load() {
  try {
    data.value = await api.get(`/teacher/interact/lessons/${props.lesson.id}/dwell`)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(() => {
  load()
  if (props.lesson.status === 'ACTIVE') timer = window.setInterval(load, 20000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.actions { display: flex; align-items: center; gap: 8px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.none { color: var(--el-text-color-placeholder); font-size: 12px; }
.weak { color: var(--el-color-warning); }
.avg { margin-top: 10px; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.tag { margin: 0; }
</style>
