<template>
  <div class="results">
    <div class="muted">共 {{ results.respondents }} 人参与<span v-if="total"> · 应参与 {{ total }} 人</span></div>
    <div v-for="(it, i) in items" :key="it.id" class="res-item">
      <div class="stem">{{ i + 1 }}. {{ it.stem }} <el-tag size="small" type="info">{{ typeLabel[it.qtype] }}</el-tag></div>
      <template v-if="it.qtype === 'FILL'">
        <div v-if="!stat(it.id)?.texts?.length" class="muted">暂无填写</div>
        <ul v-else class="texts">
          <li v-for="(t, k) in stat(it.id).texts" :key="k"><b v-if="t.name">{{ t.name }}：</b>{{ t.text }}</li>
        </ul>
      </template>
      <template v-else>
        <div v-for="(label, key) in optionLabels(it)" :key="key" class="bar-row">
          <span class="opt">{{ label }}</span>
          <el-progress :percentage="pct(it.id, key)" :stroke-width="14" class="bar" :format="() => `${count(it.id, key)} 人 · ${pct(it.id, key)}%`" />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ items: any[]; results: any; total?: number }>()
const typeLabel: Record<string, string> = { JUDGE: '判断', SINGLE: '单选', MULTI: '多选', FILL: '填空' }

const stat = (id: string) => props.results.items.find((x: any) => x.id === id)
const count = (id: string, key: string) => stat(id)?.counts?.[key] ?? 0
const pct = (id: string, key: string) => {
  const n = stat(id)?.count || 0
  return n ? Math.round((count(id, key) * 100) / n) : 0
}
function optionLabels(it: any): Record<string, string> {
  if (it.qtype === 'JUDGE') return { T: '对 / 是', F: '错 / 否' }
  const m: Record<string, string> = {}
  ;(it.options || []).forEach((o: string, i: number) => { m[String.fromCharCode(65 + i)] = `${String.fromCharCode(65 + i)}. ${o}` })
  return m
}
</script>

<style scoped>
.results { display: flex; flex-direction: column; gap: 14px; }
.stem { font-weight: 600; margin-bottom: 6px; }
.bar-row { display: flex; align-items: center; gap: 8px; margin: 3px 0; }
.opt { width: 160px; flex-shrink: 0; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar { flex: 1; max-width: 420px; }
.texts { margin: 0; padding-left: 20px; font-size: 13px; line-height: 1.8; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
