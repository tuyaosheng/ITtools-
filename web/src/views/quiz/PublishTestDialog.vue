<template>
  <el-dialog :model-value="modelValue" title="发布测验" width="460px" append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)" @open="onOpen">
    <el-form :model="form" label-width="90px">
      <el-form-item label="试卷">
        <el-select v-model="form.paperId" filterable class="full">
          <el-option v-for="p in papers" :key="p.id" :label="`${p.name}（${p.totalScore}分）`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="测验名称"><el-input v-model="form.title" placeholder="默认使用试卷名称" /></el-form-item>
      <el-form-item label="发布班级">
        <el-select v-model="form.classId" filterable class="full">
          <el-option v-for="k in classes" :key="k.id" :label="k.name" :value="k.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="限时(分钟)"><el-input-number v-model="form.timeLimit" :min="0" :max="300" /><span class="muted">0 为不限时</span></el-form-item>
      <el-form-item label="公布答案"><el-switch v-model="form.showAnswer" /><span class="muted">停止回收后向学生显示答案与解析</span></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :disabled="!form.paperId || !form.classId" @click="publish">发布</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ modelValue: boolean; paper?: any }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'published', t: any): void }>()
const router = useRouter()
const papers = ref<any[]>([])
const classes = ref<any[]>([])
const form = reactive<any>({ paperId: undefined, classId: undefined, title: '', timeLimit: 0, showAnswer: true })

async function onOpen() {
  const [p, k] = await Promise.all([api.get('/teacher/quiz/papers'), api.get('/teacher/classes')])
  papers.value = p
  classes.value = k
  form.paperId = props.paper?.id
  form.title = ''
}

async function publish() {
  try {
    const t = await api.post('/teacher/quiz/tests', { ...form, timeLimit: form.timeLimit || null })
    ElMessage.success('测验已发布')
    emit('update:modelValue', false)
    emit('published', t)
    router.push(`/teacher/tests?open=${t.id}`)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}
</script>

<style scoped>
.full { width: 100%; }
.muted { color: #909399; font-size: 12px; margin-left: 8px; }
</style>
