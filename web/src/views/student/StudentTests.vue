<template>
  <el-card shadow="never">
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="测验" name="tests">
        <template v-if="!current">
          <el-empty v-if="!tests.length" description="暂无测验" />
          <el-table v-else :data="tests" stripe>
            <el-table-column prop="title" label="测验" min-width="180" />
            <el-table-column prop="teacherName" label="老师" width="100" />
            <el-table-column prop="totalScore" label="满分" width="70" />
            <el-table-column label="限时" width="80"><template #default="{ row }: { row: any }">{{ row.timeLimit ? row.timeLimit + '分钟' : '不限' }}</template></el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }: { row: any }">
                <el-tag v-if="row.myStatus === 'SUBMITTED'" type="success" size="small">已交 {{ row.myScore }} 分</el-tag>
                <el-tag v-else-if="row.status === 'OPEN'" type="warning" size="small">{{ row.myStatus ? '作答中' : '待完成' }}</el-tag>
                <el-tag v-else type="info" size="small">已结束</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }: { row: any }">
                <el-button link type="primary" @click="current = row.id">{{ row.myStatus === 'SUBMITTED' || row.status !== 'OPEN' ? '查看' : '开始作答' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
        <template v-else>
          <el-button link @click="back">← 返回测验列表</el-button>
          <TestTaker :load-url="`/student/quiz/tests/${current}`" />
        </template>
      </el-tab-pane>
      <el-tab-pane label="错题本" name="wrong">
        <el-empty v-if="!wrong.length" description="没有错题，继续保持！" />
        <div v-for="(q, i) in wrong" :key="q.id">
          <div class="src">来自：{{ q.source }}</div>
          <QuestionItem :q="{ ...q, correct: false }" :index="i" :model-value="q.given" readonly show-key />
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import TestTaker from '../quiz/TestTaker.vue'
import QuestionItem from '../quiz/QuestionItem.vue'

const tab = ref('tests')
const tests = ref<any[]>([])
const wrong = ref<any[]>([])
const current = ref<number | null>(null)

async function load() { tests.value = await api.get('/student/quiz/tests') }
async function onTab() { if (tab.value === 'wrong') wrong.value = await api.get('/student/quiz/wrong-book') }
async function back() { current.value = null; await load() }
onMounted(load)
</script>

<style scoped>
.src { font-size: 12px; color: #909399; margin: 4px 0; }
</style>
