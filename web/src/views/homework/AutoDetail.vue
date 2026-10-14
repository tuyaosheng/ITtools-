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
.auto-detail { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; overflow: hidden; }
.row { display: flex; align-items: center; gap: 8px; padding: 6px 10px; font-size: 13px; border-bottom: 1px solid var(--el-fill-color-light); color: var(--el-color-danger); }
.row:last-child { border-bottom: none; }
.row.ok { color: var(--el-color-success); }
.name { color: var(--el-text-color-primary); min-width: 150px; }
.note { color: var(--el-text-color-secondary); }
.msg { flex: 1; color: var(--el-text-color-regular); }
.score { font-weight: 600; }
</style>
