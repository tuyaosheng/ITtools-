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
    </div>
    <template v-else>
      <el-alert v-if="!data.attempt && data.test.status === 'CLOSED'" type="warning" :closable="false" title="已停止回收，你没有参加" />
      <el-alert v-if="data.attempt?.status === 'RETURNED'" type="error" :closable="false" show-icon class="mb"
        :title="`老师发还了你的答卷，请修改后重新提交${data.attempt.returnedNote ? '：' + data.attempt.returnedNote : ''}`" />
      <el-alert v-if="data.test.status === 'STANDBY' && data.attempt?.status === 'IN_PROGRESS'" type="warning" :closable="false" class="mb"
        title="老师暂停了作答" />
      <el-alert v-if="submitted && !data.scoreVisible" type="success" :closable="false" class="mb"
        title="已提交" />
      <el-alert v-else-if="submitted && !data.answerRevealed" type="success" :closable="false" class="mb"
        title="已交卷" />

      <template v-if="data.netGate">
        <el-alert v-if="data.netGate.passed && data.netGate.missingPrograms?.length" type="warning" :closable="false" show-icon class="mb"
          :title="`测验已达标（≥ ${data.netGate.passScore} 分），还差：${data.netGate.missingPrograms.join('、')}，都完成后自动开放网络`" />
        <el-alert v-else-if="data.netGate.passed && data.netGate.requireGroup && !data.netGate.groupNo" type="warning" :closable="false" show-icon class="mb"
          title="你已经完成了，但还没有加入小组，进组后全组都完成才开放网络" />
        <el-alert v-else-if="data.netGate.passed && data.netGate.requireGroup && !data.netGate.open" type="warning" :closable="false" show-icon class="mb"
          :title="`你已经完成了，还在等第 ${data.netGate.groupNo} 组：${groupWaitText}，全组都完成后一起开放网络`" />
        <el-alert v-else-if="data.netGate.passed" type="success" :closable="false" show-icon class="mb"
          :title="`已达标（≥ ${data.netGate.passScore} 分），网络已开放`" />
        <el-alert v-else-if="submitted" :type="data.netGate.left > 0 ? 'warning' : 'error'" :closable="false" show-icon class="mb"
          :title="data.netGate.left > 0
            ? `还没达标（需要 ≥ ${data.netGate.passScore} 分），你还有 ${data.netGate.left} 次机会`
            : `没达标（需要 ≥ ${data.netGate.passScore} 分），机会用完了，请找老师`">
          <el-button v-if="data.netGate.left > 0" type="primary" size="small" :loading="saving" @click="retake">重新作答</el-button>
        </el-alert>
        <el-alert v-else type="info" :closable="false" class="mb"
          :title="`达标分数 ${data.netGate.passScore} 分，达标后开放网络；共 ${data.netGate.allowed} 次机会，已用 ${data.netGate.tries} 次`" />
      </template>

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
const waitingText = computed(() => (data.value?.netGate?.waiting || []).map((w: any) => `${w.name}（差${w.missing.join('、') || '老师确认'}）`).join('、'))
// 作品组内共享时，组里任何一人交了就算，单独列出本组还差的作品
const groupWaitText = computed(() => {
  const gm: string[] = data.value?.netGate?.groupMissing || []
  return [waitingText.value && `组员 ${waitingText.value}`, gm.length && `本组还差作品 ${gm.join('、')}（组里任何一人交了都算）`].filter(Boolean).join('；') || '老师确认'
})
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

async function retake() {
  saving.value = true
  try {
    apply(await api.post(`/student/quiz/tests/${data.value.test.id}/retake`))
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
    // 成绩开网：已交卷未达标时，及时看到老师加的机会 / 放行
    const gateWaiting = d.netGate && (!d.netGate.passed || (d.netGate.requireGroup && !d.netGate.open)) && submitted.value
    if (d.test.status === 'STANDBY' || (d.test.mode === 'CONTROL' && now.value % 15000 < 3000) || (gateWaiting && now.value % 6000 < 3000)) load(true)
  }, 3000)
})
onBeforeUnmount(() => { window.clearInterval(timer); window.clearInterval(poll) })
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; padding: 10px 0 14px; gap: 10px; flex-wrap: wrap; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; margin: 0 8px; }
.timer { font-weight: 600; color: var(--el-color-warning); }
.result b { font-size: 22px; color: var(--el-color-primary); }
.ops { display: flex; justify-content: flex-end; align-items: center; gap: 10px; margin-top: 10px; }
.mb { margin-bottom: 12px; }
.waiting { text-align: center; padding: 48px 0; color: var(--el-text-color-regular); }
.waiting .big { font-size: 40px; margin-bottom: 8px; }
</style>
