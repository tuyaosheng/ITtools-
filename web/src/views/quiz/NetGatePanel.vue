<template>
  <div class="gate">
    <el-alert v-if="!quizPages.length" type="info" :closable="false" title="这节课的页面里还没有测验页，先在课程里添加一个“课堂测验”页面" />
    <template v-else>
      <el-form inline class="rule" size="small">
        <el-form-item label="测验">
          <el-select v-model="form.pageId" class="w200" placeholder="选择测验页面" :disabled="saving">
            <el-option v-for="p in quizPages" :key="p.id" :label="p.title" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="达标分数">
          <el-input-number v-model="form.passScore" :min="0" :step="5" />
          <span v-if="quizTotal" class="unit">满分 {{ quizTotal }}</span>
        </el-form-item>
        <el-form-item label="还要完成的作品">
          <el-select v-model="form.pageIds" multiple collapse-tags collapse-tags-tooltip clearable class="w260" placeholder="不选则只看测验" :disabled="saving">
            <el-option v-for="p in homeworkPages" :key="p.id" :label="p.title" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="还要交的任务单">
          <el-select v-model="form.sheetIds" multiple collapse-tags collapse-tags-tooltip clearable class="w260" placeholder="不选则不看任务单" :disabled="saving">
            <el-option v-for="p in surveyPages" :key="p.id" :label="p.title" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.pageIds.length" label="每个作品达标分">
          <el-input-number v-model="form.homeworkPass" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="每人机会">
          <el-input-number v-model="form.maxTries" :min="1" :max="20" />
          <span class="unit">次</span>
        </el-form-item>
        <el-form-item label="小组全员完成才开网">
          <el-switch v-model="form.requireGroup" :disabled="saving" />
        </el-form-item>
        <el-form-item v-if="form.requireGroup && form.pageIds.length" label="作品组内有人交就算">
          <el-switch v-model="form.groupPrograms" :disabled="saving" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="!form.pageId || form.passScore == null || !active" @click="save">{{ rule ? '更新规则' : '启用成绩开网' }}</el-button>
          <el-button v-if="rule" :disabled="!active" @click="remove">取消并恢复网络</el-button>
        </el-form-item>
      </el-form>
      <Hint>启用后，没达标的学生会被断网，交卷分数 ≥ 达标分数、所选作品每个都达标就自动开网（作品得分优先用老师打的分，否则用自动评分；被发回的不算）；每人可交卷的次数 = 每人机会 + 你额外给的。
        没达标且还有机会的学生，在测验页点“重新作答”重做；机会用完需要你加机会或直接放行。下课后会自动恢复所有人的网络。需要先启用网络控制器。
        任务单（课堂调查页）只看交没交，不看对错。
        打开“小组全员完成才开网”后，同组出席的同学都达标才一起开网，没进组的不开网，学生进组后不能自己退出或换组；手动放行的学生算已完成，但只开他自己的网。
        再打开“作品组内有人交就算”：每个作品页组里有一位出席的同学达标就算全组完成，测验和任务单仍要每人完成。</Hint>

      <el-table v-if="rule" :data="students" size="small" stripe class="tbl">
        <el-table-column prop="studentNo" label="学号" width="70" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column v-if="rule.requireGroup" prop="groupNo" label="小组" width="76" sortable>
          <template #default="{ row }: { row: any }">{{ row.groupNo ? `第 ${row.groupNo} 组` : '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }: { row: any }">
            <el-tag size="small" :type="stateTag[row.state].type">{{ stateTag[row.state].text }}</el-tag>
            <el-tag v-if="row.override" size="small" effect="plain" class="ml">{{ row.override === 'OPEN' ? '已放行' : '手动断网' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交卷次数" width="90"><template #default="{ row }: { row: any }">{{ row.tries }} / {{ row.allowed }}</template></el-table-column>
        <el-table-column label="最好成绩" width="86"><template #default="{ row }: { row: any }">{{ row.bestScore ?? '—' }}</template></el-table-column>
        <el-table-column v-for="p in rule.pages" :key="p.id" :label="p.title" min-width="100">
          <template #default="{ row }: { row: any }">
            <template v-for="x in row.programs.filter((g: any) => g.pageId === p.id)" :key="x.pageId">
              <template v-if="p.pageType === 'SURVEY'">
                <span v-if="x.ok" class="ok">✓ 已交</span>
                <span v-else class="muted">未交</span>
              </template>
              <span v-else-if="x.ok" class="ok">✓ {{ x.score }}</span>
              <span v-else-if="x.groupOk" class="ok">组员已交</span>
              <span v-else-if="x.returned" class="off">已发回</span>
              <span v-else-if="x.score != null" class="off">{{ x.score }}</span>
              <span v-else class="muted">未达标</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column v-if="rule.groupPrograms" label="本组还差作品" min-width="120">
          <template #default="{ row }: { row: any }">
            <span v-if="row.groupMissing?.length" class="off">{{ row.groupMissing.join('、') }}</span>
            <span v-else-if="row.groupNo" class="ok">—</span>
          </template>
        </el-table-column>
        <el-table-column label="网络（实测）" width="110">
          <template #default="{ row }: { row: any }">
            <span v-if="row.net === 'on'" class="ok">🌐 已开</span>
            <span v-else-if="row.net === 'off'" class="off">⛔ 已断</span>
            <span v-else class="muted">{{ row.ip ? '电脑不在线' : '未进课' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="{ row }: { row: any }">
            <el-button link type="primary" size="small" :disabled="!active" @click="grant(row)">+1 次机会</el-button>
            <el-button v-if="row.override !== 'OPEN'" link type="success" size="small" :disabled="!active" @click="setOverride(row, 'OPEN')">放行</el-button>
            <el-button v-if="row.override" link size="small" :disabled="!active" @click="setOverride(row, null)">按成绩</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ lesson: any }>()
const lessonId = props.lesson.id
const active = computed(() => props.lesson.status === 'ACTIVE')
const quizPages = computed<any[]>(() => (props.lesson.pages || []).filter((p: any) => p.pageType === 'QUIZ'))
const homeworkPages = computed<any[]>(() => (props.lesson.pages || []).filter((p: any) => p.pageType === 'HOMEWORK'))
const surveyPages = computed<any[]>(() => (props.lesson.pages || []).filter((p: any) => p.pageType === 'SURVEY'))
const rule = ref<any>(null)
const students = ref<any[]>([])
const saving = ref(false)
const form = reactive<{ pageId?: number; passScore?: number; maxTries: number; pageIds: number[]; sheetIds: number[]; homeworkPass: number; requireGroup: boolean; groupPrograms: boolean }>({ pageId: undefined, passScore: undefined, maxTries: 2, pageIds: [], sheetIds: [], homeworkPass: 100, requireGroup: false, groupPrograms: false })
const papers = ref<any[]>([])
// 所选测验页关联试卷的总分
const quizTotal = computed<number | undefined>(() => {
  const refId = quizPages.value.find((p: any) => p.id === form.pageId)?.refId
  const total = papers.value.find((p: any) => p.id === refId)?.totalScore
  return total == null ? undefined : Number(total)
})
// 换测验时达标分默认取满分的 60%；已保存规则的测验保留原来的分数
watch(() => form.pageId, (id) => {
  if (id === undefined || rule.value?.pageId === id) return
  form.passScore = quizTotal.value ? Math.ceil(quizTotal.value * 60 / 100) : undefined
})
const stateTag: Record<string, { text: string; type: any }> = {
  PASS: { text: '已达标', type: 'success' },
  PROGRAM: { text: '差作品', type: 'warning' },
  SHEET: { text: '差任务单', type: 'warning' },
  GROUP: { text: '等组员', type: 'warning' },
  NO_GROUP: { text: '未进组', type: 'danger' },
  TRYING: { text: '答题中', type: 'warning' },
  NOT_STARTED: { text: '未开始', type: 'info' },
  EXHAUSTED: { text: '机会用完', type: 'danger' }
}
let timer: number | undefined

async function load() {
  const d = await api.get(`/teacher/quiz/lessons/${lessonId}/net-gate`)
  rule.value = d.rule
  students.value = d.students
  if (d.rule && !saving.value && form.pageId === undefined) {
    form.pageId = d.rule.pageId
    form.passScore = Number(d.rule.passScore)
    form.maxTries = d.rule.maxTries
    form.pageIds = d.rule.pages.filter((p: any) => p.pageType !== 'SURVEY').map((p: any) => p.id)
    form.sheetIds = d.rule.pages.filter((p: any) => p.pageType === 'SURVEY').map((p: any) => p.id)
    form.homeworkPass = Number(d.rule.homeworkPass)
    form.requireGroup = !!d.rule.requireGroup
    form.groupPrograms = !!d.rule.groupPrograms
  }
  // 只有一个测验页时直接选上
  if (!d.rule && form.pageId === undefined && quizPages.value.length === 1) form.pageId = quizPages.value[0].id
}

async function save() {
  saving.value = true
  try {
    await api.put(`/teacher/quiz/lessons/${lessonId}/net-gate`, { pageId: form.pageId, passScore: form.passScore, maxTries: form.maxTries, enabled: true, pageIds: [...form.pageIds, ...form.sheetIds], homeworkPass: form.homeworkPass, requireGroup: form.requireGroup, groupPrograms: form.requireGroup && form.groupPrograms })
    ElMessage.success('已启用，没达标的学生马上断网')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

async function remove() {
  try {
    await ElMessageBox.confirm('取消后，全班学生的网络都会恢复。确定？', '取消成绩开网', { type: 'warning' })
    await api.del(`/teacher/quiz/lessons/${lessonId}/net-gate`)
    rule.value = null
    students.value = []
    form.pageId = undefined
    form.pageIds = []
    form.sheetIds = []
    form.requireGroup = false
    form.groupPrograms = false
    await load()
    ElMessage.success('已取消，正在恢复网络')
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(errMsg(err))
  }
}

async function grant(row: any) {
  try {
    students.value = await api.post(`/teacher/quiz/lessons/${lessonId}/net-gate/students/${row.studentId}/tries`, { n: 1 })
    ElMessage.success(`${row.name} 加了 1 次机会`)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function setOverride(row: any, mode: 'OPEN' | null) {
  try {
    students.value = await api.put(`/teacher/quiz/lessons/${lessonId}/net-gate/students/${row.studentId}/override`, { mode })
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => {
  papers.value = await api.get('/teacher/quiz/papers').catch(() => [])
  await load()
  timer = window.setInterval(() => { if (rule.value) load() }, 5000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.w200 { width: 200px; }
.w260 { width: 260px; }
.unit { margin-left: 6px; color: var(--el-text-color-secondary); }
.tbl { margin-top: 10px; }
.ml { margin-left: 4px; }
.ok { color: var(--el-color-success-dark-2); }
.off { color: var(--el-color-danger); }
.muted { color: var(--el-text-color-placeholder); }
</style>
