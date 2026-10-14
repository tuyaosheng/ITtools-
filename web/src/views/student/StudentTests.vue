<template>
  <el-card shadow="never">
    <template v-if="current">
      <el-button link @click="back">← 返回列表</el-button>
      <TestTaker :load-url="`/student/quiz/tests/${current}`" />
    </template>
    <el-tabs v-else v-model="tab" @tab-change="onTab">
      <el-tab-pane v-for="k in ['HOMEWORK', 'TEST']" :key="k" :label="k === 'HOMEWORK' ? '课后作业' : '测验'" :name="k">
        <el-empty v-if="!tests.length" :description="k === 'HOMEWORK' ? '暂无课后作业' : '暂无测验'" />
        <el-table v-else :data="tests" stripe>
          <el-table-column prop="title" label="名称" min-width="180" />
          <el-table-column prop="teacherName" label="老师" width="100" />
          <el-table-column prop="totalScore" label="满分" width="70" />
          <el-table-column label="限时" width="80"><template #default="{ row }: { row: any }">{{ row.timeLimit ? row.timeLimit + '分钟' : '不限' }}</template></el-table-column>
          <el-table-column label="回收状态" width="130">
            <template #default="{ row }: { row: any }"><el-tag :type="(testStatus(row).type as any)" size="small">{{ testStatus(row).text }}</el-tag></template>
          </el-table-column>
          <el-table-column label="我的情况" width="140">
            <template #default="{ row }: { row: any }">
              <el-tag v-if="row.myStatus === 'RETURNED'" type="danger" size="small">被发还，需重改</el-tag>
              <el-tag v-else-if="row.myStatus === 'SUBMITTED'" type="success" size="small">{{ row.myScore != null ? `已交 ${row.myScore} 分` : '已交，待批改' }}</el-tag>
              <el-tag v-else-if="row.myStatus === 'IN_PROGRESS'" type="warning" size="small">作答中</el-tag>
              <span v-else class="muted">{{ row.status === 'CLOSED' ? '未交' : '待完成' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }: { row: any }">
              <el-button link type="primary" @click="current = row.id">{{ actionText(row) }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="错题本" name="wrong">
        <div class="wb-bar">
          <el-radio-group v-model="wrongKind" size="small" @change="loadWrong">
            <el-radio-button value="HOMEWORK">课后作业错题</el-radio-button>
            <el-radio-button value="TEST">测验错题</el-radio-button>
          </el-radio-group>
          <el-checkbox v-model="showMastered" @change="loadWrong">显示已掌握的题</el-checkbox>
          <Hint>老师公布答案后，答错的题会收集到这里</Hint>
        </div>
        <el-empty v-if="!wrong.length" description="没有错题，继续保持！" />
        <div v-for="(q, i) in wrong" :key="q.id" class="wq">
          <div class="src">
            来自：{{ q.source }}
            <el-tag v-if="q.mastered" size="small" type="success">已掌握</el-tag>
            <el-button v-if="!q.mastered" link type="success" size="small" @click="setMastered(q, true)">我已经掌握</el-button>
            <el-button v-else link size="small" @click="setMastered(q, false)">取消掌握</el-button>
          </div>
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
import { testStatus } from '../quiz/labels'

const tab = ref('HOMEWORK')
const tests = ref<any[]>([])
const wrong = ref<any[]>([])
const wrongKind = ref('HOMEWORK')
const showMastered = ref(false)
const current = ref<number | null>(null)

function actionText(row: any) {
  if (row.myStatus === 'RETURNED') return '修改'
  if (row.myStatus === 'SUBMITTED' || row.status === 'CLOSED' || row.finalizedAt) return '查看'
  return row.status === 'STANDBY' ? '进入' : '开始作答'
}

async function load() {
  if (tab.value === 'wrong') return loadWrong()
  tests.value = await api.get('/student/quiz/tests', { kind: tab.value })
}
async function loadWrong() {
  wrong.value = await api.get('/student/quiz/wrong-book', { kind: wrongKind.value, includeMastered: showMastered.value })
}
async function onTab() { await load() }
async function back() { current.value = null; await load() }

async function setMastered(q: any, v: boolean) {
  await api.put(`/student/quiz/wrong-book/${q.id}`, { mastered: v })
  await loadWrong()
}

onMounted(load)
</script>

<style scoped>
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.wb-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.src { font-size: 12px; color: var(--el-text-color-secondary); margin: 4px 0; display: flex; align-items: center; gap: 8px; }
</style>
