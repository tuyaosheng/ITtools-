<template>
  <div v-if="data" class="taker">
    <div class="bar">
      <div>
        <b>{{ data.test.title }}</b>
        <span class="muted">{{ kindLabel[data.test.kind] }} · 满分 {{ data.test.totalScore }}<template v-if="data.questions.length"> · {{ data.questions.length }} 题</template></span>
        <el-tag v-if="data.test.finalizedAt" type="info" size="small">已结分</el-tag>
        <el-tag v-else-if="data.test.status === 'CLOSED'" type="info" size="small">已停止回收</el-tag>
      </div>
      <div v-if="data.canEdit && data.attempt?.status === 'IN_PROGRESS' && data.test.timeLimit" class="timer">剩余 {{ remainText }}</div>
      <div v-if="submitted && data.scoreVisible" class="result">得分 <b>{{ data.attempt.score }}</b> / {{ data.test.totalScore }}</div>
    </div>

    <div v-if="waiting" class="waiting">
      <div class="big">⏳</div>
      <div>等待老师发出开考指令…</div>
      <div class="muted">开考后试题会自动出现，不用刷新页面</div>
    </div>
    <template v-else>
      <el-alert v-if="!data.attempt && data.test.status === 'CLOSED'" type="warning" :closable="false" title="已停止回收，你没有参加" />
      <el-alert v-if="data.attempt?.status === 'RETURNED'" type="error" :closable="false" show-icon class="mb"
        :title="`老师发还了你的答卷，请修改后重新提交${data.attempt.returnedNote ? '：' + data.attempt.returnedNote : ''}`" />
      <el-alert v-if="data.test.status === 'STANDBY' && data.attempt?.status === 'IN_PROGRESS'" type="warning" :closable="false" class="mb"
        title="老师暂停了作答，请等待继续指令（已填写的答案不会丢失）" />
      <el-alert v-if="submitted && !data.scoreVisible" type="success" :closable="false" class="mb"
        title="已提交。老师批改并结分后可以看到成绩和答案。" />
      <el-alert v-else-if="submitted && !data.answerRevealed" type="success" :closable="false" class="mb"
        :title="data.test.showAnswer ? '已交卷。老师结束后将公布答案与解析。' : '已交卷。'" />

      <QuestionItem v-for="(q, i) in data.questions" :key="q.id" v-model="answers[q.id]" :q="q" :index="i"
        :readonly="!data.canEdit" :show-key="data.answerRevealed" />

      <div v-if="data.canEdit" class="ops">
        <span class="muted">已答 {{ answeredCount }}/{{ data.questions.length }}</span>
        <el-button :loading="saving" @click="save(false)">暂存</el-button>
        <el-button type="primary" :loading="saving" @click="submit">{{ data.attempt?.status === 'RETURNED' ? '重新提交' : '交卷' }}</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import QuestionItem from './QuestionItem.vue'
import { kindLabel } from './labels'

const props = defineProps<{ loadUrl: string }>()
const data = ref<any>(null)
const answers = reactive<Record<number, any>>({})
const saving = ref(false)
const now = ref(Date.now())
let timer: number | undefined
let poll: number | undefined

const submitted = computed(() => data.value?.attempt?.status === 'SUBMITTED')
const waiting = computed(() => data.value?.test.status === 'STANDBY' && !data.value?.attempt)
const answeredCount = computed(() => Object.values(answers).filter(v => (Array.isArray(v) ? v.some(x => x) : v)).length)
const remainMs = computed(() => {
  if (!data.value?.attempt || !data.value.test.timeLimit) return 0
  return new Date(data.value.attempt.started_at).getTime() + data.value.test.timeLimit * 60000 - now.value
})
const remainText = computed(() => {
  const s = Math.max(0, Math.floor(remainMs.value / 1000))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
})

function apply(d: any, keepAnswers = false) {
  data.value = d
  if (keepAnswers) {
    d.questions.forEach((q: any) => { if (answers[q.id] === undefined && q.given != null) answers[q.id] = q.given })
    return
  }
  Object.keys(answers).forEach(k => delete answers[Number(k)])
  d.questions.forEach((q: any) => { if (q.given != null) answers[q.id] = q.given })
}

async function load(keepAnswers = false) {
  apply(await api.get(props.loadUrl), keepAnswers)
}

async function save(submit: boolean) {
  saving.value = true
  try {
    const list = data.value.questions.map((q: any) => ({ questionId: q.id, answer: answers[q.id] ?? null }))
    apply(await api.put(`/student/quiz/tests/${data.value.test.id}`, { answers: list, submit }))
    ElMessage.success(submit ? '已提交' : '已暂存')
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

async function submit() {
  const left = data.value.questions.length - answeredCount.value
  try {
    await ElMessageBox.confirm(left > 0 ? `还有 ${left} 题未作答，确定提交？` : '确定提交？提交后不能修改。', '提交')
    await save(true)
  } catch {
    /* cancelled */
  }
}

watch(remainMs, v => {
  if (data.value?.test.timeLimit && data.value?.attempt?.status === 'IN_PROGRESS' && data.value.canEdit && v <= 0) save(true)
})

watch(() => props.loadUrl, () => load())
onMounted(async () => {
  await load()
  timer = window.setInterval(() => { now.value = Date.now() }, 1000)
  // 控制模式：待机/暂停时每 3 秒检查一次老师是否开考，作答中每 15 秒同步一次状态（不覆盖正在填写的答案）
  poll = window.setInterval(() => {
    const d = data.value
    if (!d || d.test.finalizedAt || (submitted.value && d.test.status === 'CLOSED')) return
    if (d.test.status === 'STANDBY' || (d.test.mode === 'CONTROL' && now.value % 15000 < 3000)) load(true)
  }, 3000)
})
onBeforeUnmount(() => { window.clearInterval(timer); window.clearInterval(poll) })
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; padding: 10px 0 14px; gap: 10px; flex-wrap: wrap; }
.muted { color: #909399; font-size: 12px; margin: 0 8px; }
.timer { font-weight: 600; color: #e6a23c; }
.result b { font-size: 22px; color: var(--el-color-primary); }
.ops { display: flex; justify-content: flex-end; align-items: center; gap: 10px; margin-top: 10px; }
.mb { margin-bottom: 12px; }
.waiting { text-align: center; padding: 48px 0; color: #606266; }
.waiting .big { font-size: 40px; margin-bottom: 8px; }
</style>
