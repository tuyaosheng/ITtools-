<template>
  <div>
    <div class="ops">
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="0">总排行榜</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="7">近 7 天</el-radio-button>
      </el-radio-group>
      <el-select v-model="yearId" clearable placeholder="全部年级" size="small" class="w140" @change="load">
        <el-option v-for="y in years" :key="y.id" :label="y.label" :value="y.id" />
      </el-select>
    </div>
    <el-empty v-if="!list.length" description="还没有精华作品" />
    <el-table v-else :data="list" size="small" stripe>
      <el-table-column label="名次" width="70">
        <template #default="{ $index }: { $index: number }"><span :class="['rank', 'r' + ($index + 1)]">{{ $index + 1 }}</span></template>
      </el-table-column>
      <el-table-column prop="name" label="姓名" width="110" />
      <el-table-column prop="className" label="班级" width="140" />
      <el-table-column prop="count" label="精华作品数" width="110" />
      <el-table-column label="最近一次"><template #default="{ row }: { row: any }">{{ fmtTime(row.lastAt) }}</template></el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import { fmtTime } from '../../utils/format'

const days = ref(0)
const yearId = ref<number>()
const years = ref<any[]>([])
const list = ref<any[]>([])

async function load() {
  list.value = await api.get('/homework/essence-ranking', { yearId: yearId.value, days: days.value || undefined })
}

onMounted(async () => {
  years.value = await api.get('/public/school-years').catch(() => [])
  await load()
})
</script>

<style scoped>
.ops { display: flex; gap: 8px; margin-bottom: 10px; }
.w140 { width: 140px; }
.rank { display: inline-block; width: 24px; height: 24px; line-height: 24px; text-align: center; border-radius: 50%; background: #f0f2f5; font-weight: 600; }
.rank.r1 { background: #f7ba2a; color: #fff; }
.rank.r2 { background: #c0c4cc; color: #fff; }
.rank.r3 { background: #d9a066; color: #fff; }
</style>
