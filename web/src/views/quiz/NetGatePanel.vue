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
        </el-form-item>
        <el-form-item label="每人机会">
          <el-input-number v-model="form.maxTries" :min="1" :max="20" />
          <span class="unit">次</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="!form.pageId || !active" @click="save">{{ rule ? '更新规则' : '启用成绩开网' }}</el-button>
          <el-button v-if="rule" :disabled="!active" @click="remove">取消并恢复网络</el-button>
        </el-form-item>
      </el-form>
      <Hint>启用后，没达标的学生会被断网，交卷分数 ≥ 达标分数就自动开网；每人可交卷的次数 = 每人机会 + 你额外给的。
        没达标且还有机会的学生，在测验页点“重新作答”重做；机会用完需要你加机会或直接放行。下课后会自动恢复所有人的网络。需要先启用网络控制器。</Hint>

      <el-table v-if="rule" :data="students" size="small" stripe class="tbl">
        <el-table-column prop="studentNo" label="学号" width="70" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }: { row: any }">
            <el-tag size="small" :type="stateTag[row.state].type">{{ stateTag[row.state].text }}</el-tag>
            <el-tag v-if="row.override" size="small" effect="plain" class="ml">{{ row.override === 'OPEN' ? '已放行' : '手动断网' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交卷次数" width="90"><template #default="{ row }: { row: any }">{{ row.tries }} / {{ row.allowed }}</template></el-table-column>
        <el-table-column label="最好成绩" width="86"><template #default="{ row }: { row: any }">{{ row.bestScore ?? '—' }}</template></el-table-column>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ lesson: any }>()
const lessonId = props.lesson.id
const active = computed(() => props.lesson.status === 'ACTIVE')
const quizPages = computed<any[]>(() => (props.lesson.pages || []).filter((p: any) => p.pageType === 'QUIZ'))
const rule = ref<any>(null)
const students = ref<any[]>([])
const saving = ref(false)
const form = reactive<{ pageId?: number; passScore: number; maxTries: number }>({ pageId: undefined, passScore: 60, maxTries: 2 })
const stateTag: Record<string, { text: string; type: any }> = {
  PASS: { text: '已达标', type: 'success' },
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
  }
}

async function save() {
  saving.value = true
  try {
    await api.put(`/teacher/quiz/lessons/${lessonId}/net-gate`, { pageId: form.pageId, passScore: form.passScore, maxTries: form.maxTries, enabled: true })
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
  await load()
  timer = window.setInterval(() => { if (rule.value) load() }, 5000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.w200 { width: 200px; }
.unit { margin-left: 6px; color: var(--el-text-color-secondary); }
.tbl { margin-top: 10px; }
.ml { margin-left: 4px; }
.ok { color: var(--el-color-success-dark-2); }
.off { color: var(--el-color-danger); }
.muted { color: var(--el-text-color-placeholder); }
</style>
