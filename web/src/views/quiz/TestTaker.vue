<template>
  <div v-if="data" class="taker">
    <div class="bar">
      <div>
        <b>{{ data.test.title }}</b>
        <span class="muted">满分 {{ data.test.totalScore }} · {{ data.questions.length }} 题</span>
        <el-tag v-if="data.test.status === 'CLOSED'" type="info" size="small">已停止回收</el-tag>
      </div>
      <div v-if="!submitted && data.attempt && data.test.timeLimit" class="timer">剩余 {{ remainText }}</div>
      <div v-if="submitted" class="result">得分 <b>{{ data.attempt.score }}</b> / {{ data.test.totalScore }}</div>
    </div>
    <el-alert v-if="!data.attempt && data.test.status === 'CLOSED'" type="warning" :closable="false" title="测验已停止回收，你没有参加本次测验" />
    <el-alert v-if="submitted && !data.answerRevealed" type="success" :closable="false"
      title="已交卷。老师停止回收后将公布答案与解析。" class="mb" />

    <QuestionItem v-for="(q, i) in data.questions" :key="q.id" v-model="answers[q.id]" :q="q" :index="i"
      :readonly="submitted || data.test.status !== 'OPEN'" :show-key="data.answerRevealed" />

    <div v-if="!submitted && data.test.status === 'OPEN'" class="ops">
      <span class="muted">已答 {{ answeredCount }}/{{ data.questions.length }}</span>
      <el-button :loading="saving" @click="save(false)">暂存</el-button>
      <el-button type="primary" :loading="saving" @click="submit">交卷</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import QuestionItem from './QuestionItem.vue'

const props = defineProps<{ loadUrl: string }>()
const data = ref<any>(null)
const answers = reactive<Record<number, any>>({})
const saving = ref(false)
const now = ref(Date.now())
let timer: number | undefined

const submitted = computed(() => data.value?.attempt?.status === 'SUBMITTED')
const answeredCount = computed(() => Object.values(answers).filter(v => (Array.isArray(v) ? v.some(x => x) : v)).length)
const remainMs = computed(() => {
  if (!data.value?.attempt || !data.value.test.timeLimit) return 0
  return new Date(data.value.attempt.started_at).getTime() + data.value.test.timeLimit * 60000 - now.value
})
const remainText = computed(() => {
  const s = Math.max(0, Math.floor(remainMs.value / 1000))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
})

function apply(d: any) {
  data.value = d
  Object.keys(answers).forEach(k => delete answers[Number(k)])
  d.questions.forEach((q: any) => { if (q.given != null) answers[q.id] = q.given })
}

async function load() {
  apply(await api.get(props.loadUrl))
}

async function save(submit: boolean) {
  saving.value = true
  try {
    const list = data.value.questions.map((q: any) => ({ questionId: q.id, answer: answers[q.id] ?? null }))
    apply(await api.put(`/student/quiz/tests/${data.value.test.id}`, { answers: list, submit }))
    ElMessage.success(submit ? '已交卷' : '已暂存')
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

async function submit() {
  const left = data.value.questions.length - answeredCount.value
  try {
    await ElMessageBox.confirm(left > 0 ? `还有 ${left} 题未作答，确定交卷？` : '确定交卷？交卷后不能修改。', '交卷')
    await save(true)
  } catch {
    /* cancelled */
  }
}

watch(remainMs, v => {
  if (data.value?.test.timeLimit && !submitted.value && v <= 0 && data.value?.attempt) save(true)
})

watch(() => props.loadUrl, load)
onMounted(async () => {
  await load()
  timer = window.setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; padding: 10px 0 14px; gap: 10px; flex-wrap: wrap; }
.muted { color: #909399; font-size: 12px; margin: 0 8px; }
.timer { font-weight: 600; color: #e6a23c; }
.result b { font-size: 22px; color: var(--el-color-primary); }
.ops { display: flex; justify-content: flex-end; align-items: center; gap: 10px; margin-top: 10px; }
.mb { margin-bottom: 12px; }
</style>
