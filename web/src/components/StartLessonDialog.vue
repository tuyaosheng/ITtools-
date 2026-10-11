<template>
  <el-dialog :model-value="modelValue" title="开课" width="460px" append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)" @open="load">
    <el-form :model="form" label-width="90px">
      <el-form-item label="课程">
        <el-select v-model="form.courseId" filterable placeholder="选择课程" class="full">
          <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id">
            <span>{{ c.name }}</span><span class="opt-sub">{{ c.termLabel }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="上课班级">
        <el-select v-model="form.classId" filterable placeholder="选择班级" class="full">
          <el-option v-for="k in classes" :key="k.id" :label="`${k.name}（${k.studentCount}人）`" :value="k.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="分组模型">
        <el-select v-model="form.groupModelId" clearable placeholder="不分组" class="full">
          <el-option v-for="g in groupModels" :key="g.id" :label="g.name" :value="g.id" />
        </el-select>
        <div class="tip">选择后，学生进课时按所坐机位自动归入小组</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="starting" :disabled="!form.courseId || !form.classId" @click="start">开始上课</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../api/client'

const props = defineProps<{ modelValue: boolean; courseId?: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()
const router = useRouter()
const courses = ref<any[]>([])
const classes = ref<any[]>([])
const groupModels = ref<any[]>([])
const starting = ref(false)
const form = reactive<{ courseId?: number; classId?: number; groupModelId?: number }>({})

async function load() {
  form.courseId = props.courseId
  const [c, k, g] = await Promise.all([api.get('/teacher/courses'), api.get('/teacher/classes'), api.get('/teacher/lab/group-models')])
  courses.value = c
  classes.value = k
  groupModels.value = g
}

async function start() {
  starting.value = true
  try {
    const lesson = await api.post('/teacher/lessons', form)
    ElMessage.success('已开课')
    emit('update:modelValue', false)
    router.push(`/teacher/lessons/${lesson.id}`)
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    starting.value = false
  }
}
</script>

<style scoped>
.full { width: 100%; }
.opt-sub { float: right; color: #909399; font-size: 12px; margin-left: 12px; }
.tip { font-size: 12px; color: #909399; }
</style>
