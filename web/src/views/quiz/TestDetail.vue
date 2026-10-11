<template>
  <div v-if="test" class="detail">
    <div class="head">
      <div>
        <b class="title">{{ test.title }}</b>
        <span class="muted">{{ test.className }} · 满分 {{ test.totalScore }} · 已交 {{ test.submittedCount }}/{{ test.studentCount }} · 平均 {{ test.avgScore ?? '—' }}</span>
      </div>
      <div class="ops">
        <el-tag :type="test.status === 'OPEN' ? 'success' : 'info'">{{ test.status === 'OPEN' ? '回收中' : '已停止回收' }}</el-tag>
        <el-button v-if="test.status === 'OPEN'" size="small" type="warning" @click="setStatus('CLOSED')">停止回收</el-button>
        <el-button v-else size="small" @click="setStatus('OPEN')">重新开放</el-button>
        <el-checkbox :model-value="test.showAnswer" @change="(v: any) => setShowAnswer(!!v)">停止后公布答案</el-checkbox>
        <el-button size="small" @click="reload">刷新</el-button>
        <el-button size="small" @click="exportXlsx">导出成绩</el-button>
      </div>
    </div>
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="成绩" name="results">
        <el-table :data="test.results" size="small" stripe>
          <el-table-column prop="studentNo" label="学号" width="70" />
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }: { row: any }">
              <el-tag size="small" :type="row.status === 'SUBMITTED' ? 'success' : row.status ? 'warning' : 'info'">
                {{ row.status === 'SUBMITTED' ? '已交' : row.status ? '作答中' : '未交' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="得分" width="80" />
          <el-table-column prop="correctCount" label="对" width="60" />
          <el-table-column prop="wrongCount" label="错" width="60" />
          <el-table-column label="交卷时间"><template #default="{ row }: { row: any }">{{ fmtTime(row.submittedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }: { row: any }">
              <el-button v-if="row.attemptId" link type="primary" @click="openAttempt(row.attemptId)">查看/改分</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="逐题分析" name="analysis">
        <div v-for="(q, i) in analysis" :key="q.id" class="aq">
          <div class="aq-head">
            <b>{{ i + 1 }}.</b> <span v-html="safeHtml(q.stem)" class="aq-stem" />
          </div>
          <div class="aq-stat">
            <el-progress :percentage="rate(q)" :stroke-width="14" :format="() => `正确率 ${rate(q)}%`" class="prog" />
            <span class="muted">对 {{ q.rightCount }} · 错 {{ q.wrongCount }} · 均分 {{ q.avgScore }}/{{ q.score }} · 标准答案：{{ keyText(q) }}</span>
          </div>
          <div class="dist">
            <span v-for="d in q.distribution" :key="d.answer" class="dist-item">{{ distLabel(q, d.answer) }}：{{ d.n }} 人</span>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="attemptVisible" :title="`${attempt?.studentName} 的答卷 · 得分 ${attempt?.score}`" width="760px" append-to-body>
      <div v-if="attempt">
        <div v-for="(q, i) in attempt.questions" :key="q.id" class="att-q">
          <QuestionItem :q="q" :index="i" :model-value="q.given" readonly show-key />
          <div class="adjust">
            人工改分：
            <el-input-number v-model="q.gotScore" :min="0" :max="q.score" :precision="1" size="small" />
            <el-button size="small" @click="adjust(q)">保存</el-button>
            <el-tag v-if="q.adjusted" size="small" type="warning">已人工调整</el-tag>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime, safeHtml } from '../../utils/format'
import QuestionItem from './QuestionItem.vue'

const props = defineProps<{ loadUrl: string }>()
const test = ref<any>(null)
const tab = ref('results')
const analysis = ref<any[]>([])
const attempt = ref<any>(null)
const attemptVisible = ref(false)

async function reload() {
  test.value = await api.get(props.loadUrl)
  if (tab.value === 'analysis') analysis.value = await api.get(`/teacher/quiz/tests/${test.value.id}/analysis`)
}

async function onTab() {
  if (tab.value === 'analysis') analysis.value = await api.get(`/teacher/quiz/tests/${test.value.id}/analysis`)
}

async function setStatus(status: string) {
  await api.put(`/teacher/quiz/tests/${test.value.id}/status`, { status })
  await reload()
}

async function setShowAnswer(v: boolean) {
  await api.put(`/teacher/quiz/tests/${test.value.id}/status`, { status: test.value.status, showAnswer: v })
  await reload()
}

function exportXlsx() {
  api.download(`/teacher/quiz/tests/${test.value.id}/export`)
}

async function openAttempt(id: number) {
  attempt.value = await api.get(`/teacher/quiz/attempts/${id}`)
  attemptVisible.value = true
}

async function adjust(q: any) {
  try {
    attempt.value = await api.put(`/teacher/quiz/attempts/${attempt.value.id}/questions/${q.id}`, { score: q.gotScore })
    ElMessage.success('已改分')
    await reload()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

const rate = (q: any) => {
  const n = Number(q.rightCount) + Number(q.wrongCount)
  return n ? Math.round((Number(q.rightCount) * 100) / n) : 0
}
const keyText = (q: any) => (q.qtype === 'JUDGE' ? (q.answer === 'T' ? '对' : '错') : Array.isArray(q.answer) ? q.answer.join(' / ') : q.answer)
const distLabel = (q: any, a: string) => {
  if (!a) return '未答'
  if (q.qtype === 'FILL') { try { return JSON.parse(a).join(' / ') } catch { return a } }
  return a
}

onMounted(reload)
defineExpose({ reload })
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.title { font-size: 16px; }
.muted { color: #909399; font-size: 12px; margin-left: 8px; }
.ops { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.aq { padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.aq-head { display: flex; gap: 6px; }
.aq-stem :deep(p) { margin: 0; }
.aq-stat { display: flex; align-items: center; gap: 12px; margin: 6px 0; flex-wrap: wrap; }
.prog { width: 260px; }
.dist { display: flex; flex-wrap: wrap; gap: 8px; }
.dist-item { font-size: 12px; background: #f4f4f5; padding: 2px 8px; border-radius: 10px; }
.att-q { margin-bottom: 6px; }
.adjust { display: flex; align-items: center; gap: 8px; font-size: 13px; margin: -4px 0 12px; }
</style>
