<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>我的课表</span>
        <div>
          <el-input-number v-model="periods" :min="4" :max="12" size="small" /> 节
          <el-button type="primary" size="small" @click="save">保存课表</el-button>
        </div>
      </div>
    </template>
    <table class="tt">
      <thead>
        <tr><th>节次</th><th v-for="(d, i) in days" :key="d" :class="{ today: i + 1 === todayWd }">{{ d }}</th></tr>
      </thead>
      <tbody>
        <tr v-for="p in periods" :key="p">
          <th>第 {{ p }} 节</th>
          <td v-for="w in 7" :key="w" :class="{ today: w === todayWd }">
            <input v-model="grid[`${p}-${w}`]" class="cell" placeholder="—" />
          </td>
        </tr>
      </tbody>
    </table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const days = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']
const todayWd = new Date().getDay() || 7
const periods = ref(8)
const grid = reactive<Record<string, string>>({})

async function load() {
  const cells: any[] = await api.get('/teacher/timetable')
  cells.forEach(c => {
    grid[`${c.period}-${c.weekday}`] = c.content
    if (c.period > periods.value) periods.value = c.period
  })
}

async function save() {
  const cells = Object.entries(grid)
    .filter(([, v]) => v && v.trim())
    .map(([k, v]) => { const [period, weekday] = k.split('-').map(Number); return { period, weekday, content: v } })
    .filter(c => c.period <= periods.value)
  try {
    await api.put('/teacher/timetable', { cells })
    ElMessage.success('课表已保存')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.tt { width: 100%; border-collapse: collapse; }
.tt th, .tt td { border: 1px solid #ebeef5; padding: 4px; text-align: center; }
.tt th { background: #fafafa; font-weight: 500; color: #606266; }
.tt .today { background: var(--el-color-primary-light-9); }
.cell { width: 100%; border: none; background: transparent; text-align: center; padding: 8px 2px; outline: none; font-size: 13px; }
.cell:focus { background: #fff; box-shadow: inset 0 0 0 1px var(--el-color-primary); border-radius: 4px; }
</style>
