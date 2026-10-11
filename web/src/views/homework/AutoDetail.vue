<template>
  <div v-if="items.length" class="auto-detail">
    <div v-for="(d, i) in items" :key="i" class="row" :class="{ ok: d.passed }">
      <el-icon class="icon"><CircleCheckFilled v-if="d.passed" /><CircleCloseFilled v-else /></el-icon>
      <span class="name">{{ d.name }}<span v-if="d.note" class="note">（{{ d.note }}）</span></span>
      <span class="msg">{{ d.message }}</span>
      <span class="score">{{ d.score }}/{{ d.max }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'

const props = defineProps<{ detail?: string | null }>()
const items = computed<any[]>(() => {
  try {
    return props.detail ? JSON.parse(props.detail) : []
  } catch {
    return []
  }
})
</script>

<style scoped>
.auto-detail { border: 1px solid #ebeef5; border-radius: 8px; overflow: hidden; }
.row { display: flex; align-items: center; gap: 8px; padding: 6px 10px; font-size: 13px; border-bottom: 1px solid #f2f3f5; color: #f56c6c; }
.row:last-child { border-bottom: none; }
.row.ok { color: #67c23a; }
.name { color: #303133; min-width: 150px; }
.note { color: #909399; }
.msg { flex: 1; color: #606266; }
.score { font-weight: 600; }
</style>
