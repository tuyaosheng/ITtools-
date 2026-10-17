<template>
  <div v-if="test" class="detail">
    <div class="head">
      <div>
        <b class="title">{{ test.title }}</b>
        <el-tag size="small" effect="plain" class="ml">{{ test.lessonId ? '课堂测验' : kindLabel[test.kind] }} · {{ modeLabel[test.mode] }}</el-tag>
        <div class="muted">
          {{ test.className }} · 满分 {{ test.totalScore }} · 已交 {{ test.submittedCount }}/{{ test.studentCount }}
          <template v-if="test.returnedCount"> · 发还中 {{ test.returnedCount }}</template> · 平均 {{ test.avgScore ?? '—' }}
          <template v-if="test.timeLimit"> · 限时 {{ test.timeLimit }} 分钟</template>
        </div>
      </div>
      <div class="ops">
        <el-tag :type="(testStatus(test).type as any)">{{ testStatus(test).text }}</el-tag>
        <template v-if="!test.finalizedAt">
          <template v-if="test.mode === 'CONTROL'">
            <el-button-group>
              <el-button size="small" :type="test.status === 'STANDBY' ? 'warning' : ''" @click="setStatus('STANDBY')">待机</el-button>
              <el-button size="small" :type="test.status === 'OPEN' ? 'success' : ''" @click="setStatus('OPEN')">开考</el-button>
              <el-popconfirm title="结束考试？未交卷的学生将自动交卷" @confirm="setStatus('CLOSED')">
                <template #reference><el-button size="small" :type="test.status === 'CLOSED' ? 'info' : ''">结束</el-button></template>
              </el-popconfirm>
            </el-button-group>
          </template>
          <template v-else>
            <el-popconfirm v-if="test.status === 'OPEN'" title="停止回收？未交卷的学生将自动交卷" @confirm="setStatus('CLOSED')">
              <template #reference><el-button size="small" type="warning">停止回收</el-button></template>
            </el-popconfirm>
            <el-button v-else size="small" @click="setStatus('OPEN')">重新开放</el-button>
          </template>
          <el-popconfirm v-if="test.status === 'CLOSED'" title="结分后成绩锁定，不能再改分或发还，确定？" @confirm="finalize">
            <template #reference><el-button size="small" type="primary">结分</el-button></template>
          </el-popconfirm>
        </template>
        <el-checkbox :model-value="test.showAnswer" @change="(v: any) => setShowAnswer(!!v)">公布答案</el-checkbox>
        <el-dropdown @command="onMore">
          <el-button size="small">更多 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="refresh">刷新</el-dropdown-item>
              <el-dropdown-item command="export">导出成绩</el-dropdown-item>
              <el-dropdown-item command="practice">设为本班刷题题库</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    <el-alert v-if="test.mode === 'CONTROL' && test.status === 'STANDBY'" type="warning" :closable="false" show-icon class="mb"
      title="待机中，点「开考」后学生才能作答" />

    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="成绩" name="results">
        <el-table :data="test.results" size="small" stripe>
          <el-table-column prop="studentNo" label="班内学号" width="84" />
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }: { row: any }">
              <el-tag size="small" :type="((row.status ? attemptTag[row.status] : 'info') as any)">{{ row.status ? attemptLabel[row.status] : '未交' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="得分" width="80" />
          <el-table-column prop="correctCount" label="对" width="55" />
          <el-table-column prop="wrongCount" label="错" width="55" />
          <el-table-column label="交卷时间" width="150"><template #default="{ row }: { row: any }">{{ fmtTime(row.submittedAt) }}</template></el-table-column>
          <el-table-column label="操作" min-width="150">
            <template #default="{ row }: { row: any }">
              <el-button v-if="row.attemptId" link type="primary" @click="openAttempt(row.attemptId)">{{ test.finalizedAt ? '查看' : '查看/改分' }}</el-button>
              <el-button v-if="row.status === 'SUBMITTED' && !test.finalizedAt" link type="warning" @click="returnAttempt(row)">发还重改</el-button>
              <span v-if="row.status === 'RETURNED' && row.returnedNote" class="muted">{{ row.returnedNote }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="hasFill" label="填空批改" name="fill">
        <el-empty v-if="!fillGroups.length" description="还没有学生提交" />
        <div v-for="q in fillGroups" :key="q.id" class="fq">
          <div class="fq-head">
            <span v-html="safeHtml(q.stem)" class="fq-stem" />
            <span class="muted">标准答案：{{ (q.answer || []).join(' / ') }} · 满分 {{ q.score }}</span>
          </div>
          <el-table :data="q.groups" size="small">
            <el-table-column label="学生答案" min-width="160">
              <template #default="{ row }: { row: any }"><code>{{ row.blanks.length ? row.blanks.join(' | ') : '（未答）' }}</code></template>
            </el-table-column>
            <el-table-column prop="count" label="人数" width="60" />
            <el-table-column label="学生" min-width="160" show-overflow-tooltip><template #default="{ row }: { row: any }">{{ row.students }}</template></el-table-column>
            <el-table-column label="当前得分" width="90">
              <template #default="{ row }: { row: any }">{{ row.minScore === row.maxScore ? row.minScore : `${row.minScore}~${row.maxScore}` }}<el-tag v-if="row.adjusted" size="small" type="warning" class="ml">人工</el-tag></template>
            </el-table-column>
            <el-table-column label="统一给分" width="230">
              <template #default="{ row }: { row: any }">
                <el-input-number v-model="row.newScore" :min="0" :max="q.score" :precision="1" size="small" :disabled="!!test.finalizedAt" class="w100" />
                <el-button size="small" :disabled="!!test.finalizedAt || row.newScore == null" @click="gradeGroup(q, row)">应用</el-button>
                <el-button size="small" link :disabled="!!test.finalizedAt" @click="row.newScore = q.score; gradeGroup(q, row)">判对</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="逐题分析" name="analysis">
        <div v-for="(q, i) in analysis" :key="q.id" class="aq">
          <div class="aq-head">
            <b>{{ i + 1 }}.</b> <span v-html="safeHtml(q.stem)" class="aq-stem" /><span v-if="!q.stem && q.hasImage" class="muted">（图片题）</span>
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

      <el-tab-pane v-if="test.batchKey" label="年级统计" name="grade">
        <el-table v-if="grade" :data="[...grade.classes, { className: '年级合计', ...gradeOverall }]" size="small" border>
          <el-table-column prop="className" label="班级" width="140" />
          <el-table-column label="提交" width="90"><template #default="{ row }: { row: any }">{{ row.submitted ?? row.n }}<template v-if="row.studentCount">/{{ row.studentCount }}</template></template></el-table-column>
          <el-table-column prop="avg" label="平均分" width="80" />
          <el-table-column prop="max" label="最高" width="70" />
          <el-table-column prop="min" label="最低" width="70" />
          <el-table-column label="及格率" width="90"><template #default="{ row }: { row: any }">{{ row.passRate == null ? '—' : row.passRate + '%' }}</template></el-table-column>
          <el-table-column label="优秀率" width="90"><template #default="{ row }: { row: any }">{{ row.goodRate == null ? '—' : row.goodRate + '%' }}</template></el-table-column>
        </el-table>
        <div><Hint block>及格 = 得分 ≥ 60%，优秀 = 得分 ≥ 85%（满分 {{ grade?.totalScore }}）</Hint></div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="attemptVisible" :title="`${attempt?.studentName} 的答卷 · 得分 ${attempt?.score ?? '—'}`" width="760px" append-to-body>
      <div v-if="attempt">
        <div v-for="(q, i) in attempt.questions" :key="q.id" class="att-q">
          <QuestionItem :q="q" :index="i" :model-value="q.given" readonly show-key />
          <div v-if="!test.finalizedAt" class="adjust">
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime, safeHtml } from '../../utils/format'
import QuestionItem from './QuestionItem.vue'
import { attemptLabel, attemptTag, kindLabel, modeLabel, testStatus } from './labels'

const props = defineProps<{ loadUrl: string }>()
const test = ref<any>(null)
const tab = ref('results')
const analysis = ref<any[]>([])
const fillGroups = ref<any[]>([])
const hasFill = ref(false)
const grade = ref<any>(null)
const attempt = ref<any>(null)
const attemptVisible = ref(false)
const gradeOverall = computed(() => grade.value?.overall || {})

async function reload() {
  test.value = await api.get(props.loadUrl)
  await onTab()
}

async function onTab() {
  const id = test.value.id
  if (tab.value === 'analysis') analysis.value = await api.get(`/teacher/quiz/tests/${id}/analysis`)
  if (tab.value === 'fill') await loadFill()
  if (tab.value === 'grade') grade.value = await api.get(`/teacher/quiz/tests/${id}/grade-stats`)
}

async function loadFill() {
  fillGroups.value = (await api.get(`/teacher/quiz/tests/${test.value.id}/fill-groups`)).map((q: any) => ({
    ...q, groups: q.groups.map((g: any) => ({ ...g, newScore: undefined }))
  }))
}

async function setStatus(status: string) {
  try {
    await api.put(`/teacher/quiz/tests/${test.value.id}/status`, { status })
    await reload()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function setShowAnswer(v: boolean) {
  await api.put(`/teacher/quiz/tests/${test.value.id}/status`, { status: test.value.status, showAnswer: v })
  await reload()
}

async function finalize() {
  try {
    await api.post(`/teacher/quiz/tests/${test.value.id}/finalize`)
    ElMessage.success('已结分')
    await reload()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function returnAttempt(row: any) {
  try {
    const { value } = await ElMessageBox.prompt(`发还给 ${row.name} 重新修改，可以写一句说明（可选）`, '发还重改', { inputPlaceholder: '如：第 3 题再检查一下' })
    await api.post(`/teacher/quiz/attempts/${row.attemptId}/return`, { note: value || null })
    ElMessage.success('已发还')
    await reload()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(errMsg(err))
  }
}

async function gradeGroup(q: any, g: any) {
  try {
    const n = await api.put(`/teacher/quiz/tests/${test.value.id}/fill-groups`, { questionId: q.id, answer: g.answer, score: g.newScore })
    ElMessage.success(`已给 ${n} 名学生打分`)
    test.value = await api.get(props.loadUrl)
    await loadFill()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function onMore(cmd: string) {
  if (cmd === 'refresh') await reload()
  if (cmd === 'export') api.download(`/teacher/quiz/tests/${test.value.id}/export`)
  if (cmd === 'practice') {
    try {
      await api.post(`/teacher/quiz/tests/${test.value.id}/practice-set`)
      ElMessage.success('已设为本班刷题题库')
    } catch (err) {
      ElMessage.error(errMsg(err))
    }
  }
}

async function openAttempt(id: number) {
  attempt.value = await api.get(`/teacher/quiz/attempts/${id}`)
  attemptVisible.value = true
}

async function adjust(q: any) {
  try {
    attempt.value = await api.put(`/teacher/quiz/attempts/${attempt.value.id}/questions/${q.id}`, { score: q.gotScore })
    ElMessage.success('已改分')
    test.value = await api.get(props.loadUrl)
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

onMounted(async () => {
  await reload()
  const paper = await api.get(`/teacher/quiz/papers/${test.value.paperId}`).catch(() => null)
  hasFill.value = !!paper?.questions?.some((q: any) => q.qtype === 'FILL')
})
defineExpose({ reload })
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.title { font-size: 16px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.ml { margin-left: 6px; }
.mt { margin-top: 8px; }
.mb { margin-bottom: 10px; }
.w100 { width: 100px; margin-right: 6px; }
.ops { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.fq { margin-bottom: 16px; }
.fq-head { display: flex; gap: 12px; align-items: baseline; flex-wrap: wrap; margin-bottom: 6px; }
.fq-stem :deep(p) { margin: 0; }
.aq { padding: 10px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.aq-head { display: flex; gap: 6px; }
.aq-stem :deep(p) { margin: 0; }
.aq-stat { display: flex; align-items: center; gap: 12px; margin: 6px 0; flex-wrap: wrap; }
.prog { width: 260px; }
.dist { display: flex; flex-wrap: wrap; gap: 8px; }
.dist-item { font-size: 12px; background: var(--el-fill-color-light); padding: 2px 8px; border-radius: 10px; }
.att-q { margin-bottom: 6px; }
.adjust { display: flex; align-items: center; gap: 8px; font-size: 13px; margin: -4px 0 12px; }
</style>
