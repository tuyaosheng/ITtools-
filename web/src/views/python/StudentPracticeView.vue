<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span v-if="!current">Python 课后练习 <span class="muted">（老师开放的课后题库，可反复提交，记录最高分）</span></span>
        <el-button v-else link type="primary" @click="back">← 返回题目列表</el-button>
      </div>
    </template>

    <template v-if="!current">
      <el-table :data="list" stripe empty-text="老师还没有开放课后练习题">
        <el-table-column label="题目" min-width="220">
          <template #default="{ row }"><el-link type="primary" @click="open(row.id)">{{ row.title }}</el-link></template>
        </el-table-column>
        <el-table-column prop="teacherName" label="出题老师" width="110" />
        <el-table-column label="我的最高分" width="130">
          <template #default="{ row }">
            <template v-if="row.bestScore != null">
              <el-tag :type="Number(row.bestScore) >= Number(row.totalScore) ? 'success' : 'warning'" size="small">
                {{ Number(row.bestScore) }} / {{ row.totalScore }}
              </el-tag>
            </template>
            <span v-else class="muted">未完成（满分 {{ row.totalScore }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="提交次数" width="90">
          <template #default="{ row }">{{ row.submitCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="最近评分" width="150">
          <template #default="{ row }">{{ fmtTime(row.gradedAt) }}</template>
        </el-table-column>
      </el-table>
    </template>
    <PythonWorkbench v-else :view="current" :base-path="`/student/python/practice/${current.id}`" @update="v => (current = v)" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'
import PythonWorkbench from './PythonWorkbench.vue'

const list = ref<any[]>([])
const current = ref<any>(null)

async function load() { list.value = await api.get('/student/python/practice') }

async function open(id: number) {
  try {
    current.value = await api.get(`/student/python/practice/${id}`)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function back() {
  current.value = null
  await load()
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.muted { color: #909399; font-size: 12px; }
</style>
