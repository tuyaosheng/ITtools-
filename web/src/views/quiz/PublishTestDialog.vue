<template>
  <el-dialog :model-value="modelValue" :title="form.kind === 'HOMEWORK' ? '布置课后作业' : '发布测验'" width="560px" append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)" @open="onOpen">
    <el-form :model="form" label-width="90px">
      <el-form-item label="类型">
        <el-radio-group v-model="form.kind" @change="onKind">
          <el-radio-button value="HOMEWORK">课后作业</el-radio-button>
          <el-radio-button value="TEST">测验</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="试卷">
        <el-select v-model="form.paperId" filterable class="full">
          <el-option v-for="p in papers" :key="p.id" :label="`${p.name}（${p.totalScore}分）`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称"><el-input v-model="form.title" placeholder="默认使用试卷名称" /></el-form-item>
      <el-form-item label="班级">
        <el-checkbox-group v-model="form.classIds">
          <el-checkbox v-for="k in classes" :key="k.id" :value="k.id">{{ k.name }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="考试方式">
        <el-radio-group v-model="form.mode">
          <el-radio v-if="form.kind === 'TEST'" value="CONTROL">控制模式</el-radio>
          <el-radio value="AUTO">自动模式</el-radio>
          <el-radio value="INSTANT">提交即批改</el-radio>
        </el-radio-group>
        <div class="muted">{{ modeHelp }}</div>
      </el-form-item>
      <el-form-item label="限时(分钟)"><el-input-number v-model="form.timeLimit" :min="0" :max="300" /><span class="muted ml">0 为不限时</span></el-form-item>
      <el-form-item label="公布答案"><el-switch v-model="form.showAnswer" /><span class="muted ml">{{ form.kind === 'HOMEWORK' ? '结分后' : '停止回收后' }}向学生显示答案与解析</span></el-form-item>
      <el-form-item label="每人乱序">
        <el-checkbox v-model="form.shuffleQuestions">题目顺序</el-checkbox>
        <el-checkbox v-model="form.shuffleOptions">单选/多选的选项顺序</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :disabled="!form.paperId || !form.classIds.length" @click="publish">发布</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ modelValue: boolean; paper?: any; kind?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'published', t: any): void }>()
const router = useRouter()
const papers = ref<any[]>([])
const classes = ref<any[]>([])
const form = reactive<any>({ kind: 'HOMEWORK', mode: 'AUTO', paperId: undefined, classIds: [], title: '', timeLimit: 0, showAnswer: true, shuffleQuestions: false, shuffleOptions: false })

const modeHelp = computed(() => ({
  CONTROL: '学生先“等待开考”，由你发送开考、结束指令',
  AUTO: '发布后学生即可作答，停止回收后统一批改',
  INSTANT: '学生交卷后立即看到得分（和答案）'
} as Record<string, string>)[form.mode])

function onKind() {
  if (form.kind === 'HOMEWORK' && form.mode === 'CONTROL') form.mode = 'AUTO'
}

async function onOpen() {
  const [p, k] = await Promise.all([api.get('/teacher/quiz/papers'), api.get('/teacher/classes')])
  papers.value = p
  classes.value = k
  Object.assign(form, { kind: props.kind || 'HOMEWORK', mode: 'AUTO', paperId: props.paper?.id, classIds: [], title: '', shuffleQuestions: false, shuffleOptions: false })
}

async function publish() {
  try {
    const list = await api.post('/teacher/quiz/tests', { ...form, timeLimit: form.timeLimit || null })
    ElMessage.success(`已发布到 ${list.length} 个班级`)
    emit('update:modelValue', false)
    emit('published', list[0])
    router.push(`/teacher/tests?open=${list[0].id}`)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}
</script>

<style scoped>
.full { width: 100%; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; width: 100%; }
.ml { margin-left: 8px; width: auto; }
</style>
