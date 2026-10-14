<template>
  <div>
    <div class="bar">
      <el-radio-group v-model="level" size="small" @change="load">
        <el-radio-button v-for="l in typingLevels" :key="l.value" :value="l.value">{{ l.label }}</el-radio-button>
      </el-radio-group>
      <el-select v-if="classes" v-model="classId" clearable placeholder="全校" size="small" class="w160" @change="load">
        <el-option v-for="k in classes" :key="k.id" :label="k.name" :value="k.id" />
      </el-select>
    </div>
    <el-table :data="rows" size="small" stripe max-height="460" empty-text="还没有人上榜">
      <el-table-column label="名次" width="70">
        <template #default="{ row }: { row: any }"><span :class="['rk', 'rk' + row.rank]">{{ row.rank }}</span></template>
      </el-table-column>
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="yearLabel" label="年级" width="100" />
      <el-table-column prop="className" label="班级" width="100" />
      <el-table-column prop="speed" label="速度（字符/分）" width="130" />
      <el-table-column label="日期"><template #default="{ row }: { row: any }">{{ fmtTime(row.date) }}</template></el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import { fmtTime } from '../../utils/format'
import { typingLevels } from './typingWords'

/** 全校指法英雄榜（原 paiming.aspx）。classes 传入时可按班级筛选（教师端）。 */
const props = defineProps<{ classes?: any[]; initialLevel?: string }>()
const level = ref(props.initialLevel || 'PRIMARY')
const classId = ref<number>()
const rows = ref<any[]>([])

async function load() {
  rows.value = await api.get('/typing/leaderboard', { level: level.value, classId: classId.value || undefined })
}

defineExpose({ load, level })
onMounted(load)
</script>

<style scoped>
.bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 10px; }
.w160 { width: 160px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.rk { display: inline-block; min-width: 24px; text-align: center; border-radius: 12px; font-weight: 600; }
.rk1 { background: #f5c518; color: #fff; }
.rk2 { background: #b0b7c3; color: #fff; }
.rk3 { background: #cd7f32; color: #fff; }
</style>
