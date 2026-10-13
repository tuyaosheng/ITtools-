<template>
  <div v-if="data">
    <div v-if="s" class="tiles">
      <div class="tile"><b>{{ s.total ?? '—' }}</b><span>总评</span></div>
      <div class="tile"><b>{{ s.rank ?? '—' }}<small>/{{ data.classSize }}</small></b><span>班级名次</span></div>
      <div class="tile"><b>{{ pct(s.attendancePct) }}</b><span>出席率 {{ s.present }}/{{ s.lessons }}</span></div>
      <div class="tile"><b>{{ pct(s.workPct) }}</b><span>作品 {{ s.works }} 份 · 精华 {{ s.essence }}</span></div>
      <div class="tile"><b>{{ pct(s.pythonPct) }}</b><span>操作题 {{ s.python }} 道</span></div>
      <div class="tile"><b>{{ pct(s.homeworkPct) }}</b><span>课后作业 {{ s.homeworkDone }}/{{ s.homeworkTotal }}</span></div>
      <div class="tile"><b>{{ pct(s.testPct) }}</b><span>测验 {{ s.testDone }}/{{ s.testTotal }}</span></div>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane v-for="k in kinds" :key="k.kind" :label="`${k.label}（${(data.details[k.kind] || []).length}）`" :name="k.kind">
        <el-table :data="data.details[k.kind]" size="small" stripe max-height="360" empty-text="暂无记录">
          <el-table-column prop="title" :label="k.col" min-width="200" />
          <el-table-column label="情况" width="160">
            <template #default="{ row }: { row: any }">
              <template v-if="k.kind === 'ATTENDANCE'">
                <el-tag v-if="row.cell" size="small" :type="attendanceTag[row.cell.value]">{{ attendanceLabel[row.cell.value] }}</el-tag>
                <el-tag v-else size="small" type="info">未进课</el-tag>
              </template>
              <template v-else>
                <span v-if="!row.cell" class="muted">未完成</span>
                <span v-else-if="row.cell.value && row.cell.value !== 'SUBMITTED'" class="muted">{{ attemptLabel[row.cell.value] }}</span>
                <span v-else-if="row.cell.score == null" class="muted">已交 · 未评分</span>
                <span v-else>{{ row.cell.score }}<span v-if="Number(row.full)" class="muted"> / {{ row.full }}</span></span>
                <el-tag v-if="row.cell?.essence" size="small" type="warning" class="ml">精华</el-tag>
              </template>
            </template>
          </el-table-column>
          <el-table-column v-if="k.kind === 'ATTENDANCE'" label="课堂表现" width="90">
            <template #default="{ row }: { row: any }">{{ row.cell?.score ?? '' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { attendanceLabel, attendanceTag } from '../utils/format'
import { attemptLabel } from '../views/quiz/labels'

const props = defineProps<{ data: any }>()
const s = computed(() => props.data?.summary)
const tab = ref('ATTENDANCE')
const kinds = [
  { kind: 'ATTENDANCE', label: '出席', col: '课堂' },
  { kind: 'WORKS', label: '作品', col: '作品' },
  { kind: 'PYTHON', label: '操作题', col: '题目' },
  { kind: 'HOMEWORK', label: '课后作业', col: '作业' },
  { kind: 'TEST', label: '测验', col: '测验' }
]
const pct = (v: any) => (v == null ? '—' : `${v}%`)
</script>

<style scoped>
.tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 8px; margin-bottom: 12px; }
.tile { background: var(--el-fill-color-light); border-radius: 6px; padding: 10px; display: flex; flex-direction: column; gap: 2px; }
.tile b { font-size: 20px; }
.tile small { font-size: 12px; color: var(--el-text-color-secondary); font-weight: normal; }
.tile span { font-size: 12px; color: var(--el-text-color-secondary); }
.muted { color: var(--el-text-color-secondary); }
.ml { margin-left: 4px; }
</style>
