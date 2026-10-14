<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>学生学习心得 <Hint>学生提交到你课程的课后反思</Hint></span>
        <div class="filters">
          <el-select v-model="course" placeholder="全部课程" clearable class="w180">
            <el-option v-for="c in courses" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="klass" placeholder="全部班级" clearable class="w140">
            <el-option v-for="k in klasses" :key="k" :label="k" :value="k" />
          </el-select>
          <el-checkbox v-model="onlyPending">只看未点评</el-checkbox>
        </div>
      </div>
    </template>

    <el-empty v-if="!shown.length" description="暂无学习心得" />
    <div v-for="n in shown" :key="n.id" class="note">
      <div class="n-head">
        <b>{{ n.title }}</b>
        <span class="muted">{{ n.className }} {{ n.studentName }} · {{ n.courseName }} · {{ fmtTime(n.updatedAt) }}</span>
      </div>
      <div class="n-body" v-html="safeHtml(n.content)" />
      <div class="n-comment">
        <el-input v-model="drafts[n.id]" placeholder="写点评…" maxlength="512" size="small" />
        <el-button size="small" type="primary" @click="comment(n)">{{ n.teacherComment ? '更新点评' : '点评' }}</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime, safeHtml } from '../../utils/format'

const list = ref<any[]>([])
const drafts = reactive<Record<number, string>>({})
const course = ref<string>()
const klass = ref<string>()
const onlyPending = ref(false)

const courses = computed(() => [...new Set(list.value.map(n => n.courseName))])
const klasses = computed(() => [...new Set(list.value.map(n => n.className).filter(Boolean))])
const shown = computed(() => list.value.filter(n =>
  (!course.value || n.courseName === course.value) &&
  (!klass.value || n.className === klass.value) &&
  (!onlyPending.value || !n.teacherComment)))

async function load() {
  list.value = await api.get('/teacher/reflections')
  list.value.forEach(n => { drafts[n.id] = n.teacherComment || '' })
}

async function comment(n: any) {
  try {
    await api.put(`/teacher/reflections/${n.id}`, { comment: drafts[n.id] })
    n.teacherComment = drafts[n.id]
    ElMessage.success('已点评')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; align-items: center; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.w140 { width: 140px; }
.w180 { width: 180px; }
.note { border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 12px; margin-bottom: 12px; }
.n-head { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.n-body { margin: 8px 0; font-size: 14px; }
.n-comment { display: flex; gap: 8px; }
</style>
