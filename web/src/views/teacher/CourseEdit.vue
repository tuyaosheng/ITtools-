<template>
  <div v-if="course" class="course-edit">
    <el-page-header @back="router.push('/teacher/courses')">
      <template #content>
        <span class="title">《{{ course.name }}》</span>
        <el-tag size="small" class="ml">{{ course.termLabel || '未分学期' }}</el-tag>
        <el-tag v-if="!isOwner" size="small" type="info" class="ml">{{ course.teacherName }} 的共享课程（只读）</el-tag>
      </template>
      <template #extra>
        <el-button v-if="isOwner" type="primary" @click="startVisible = true">用此课程开课</el-button>
      </template>
    </el-page-header>

    <div class="layout">
      <el-card shadow="never" class="pages">
        <template #header>
          <div class="card-head">
            <span>课程页面（{{ pages.length }}）</span>
            <el-button v-if="isOwner" size="small" type="primary" @click="newPage">添加页面</el-button>
          </div>
        </template>
        <el-empty v-if="!pages.length" description="还没有页面" :image-size="50" />
        <div v-for="(p, i) in pages" :key="p.id" class="page-item" :class="{ active: p.id === current?.id }" @click="select(p)">
          <span class="seq">{{ i + 1 }}</span>
          <div class="info">
            <div class="ptitle">{{ p.title }}</div>
            <div class="ptype">{{ pageTypes[p.pageType]?.label }}{{ p.studentVisible ? '' : ' · 默认隐藏' }}</div>
          </div>
          <div v-if="isOwner" class="move" @click.stop>
            <el-button link size="small" :disabled="i === 0" @click="move(i, -1)">↑</el-button>
            <el-button link size="small" :disabled="i === pages.length - 1" @click="move(i, 1)">↓</el-button>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="editor">
        <el-empty v-if="!current" description="选择左侧页面进行编辑，或添加新页面" />
        <template v-else-if="!isOwner">
          <h3>{{ current.title }}</h3>
          <PageContent :page="current" />
        </template>
        <el-form v-else :model="current" label-width="90px">
          <el-form-item label="页面类型">
            <el-select v-model="current.pageType" class="w240">
              <el-option v-for="o in pageTypeOptions" :key="o.value" :label="o.label" :value="o.value">
                <span>{{ o.label }}</span><span class="opt-sub">{{ o.desc }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="标题"><el-input v-model="current.title" /></el-form-item>
          <el-form-item label="说明/内容">
            <RichEditor v-model="current.content" />
          </el-form-item>
          <el-form-item v-if="['LINK', 'VIDEO'].includes(current.pageType)" :label="current.pageType === 'LINK' ? '网址' : '视频地址'">
            <el-input v-model="current.linkUrl" placeholder="https://..." />
          </el-form-item>
          <el-form-item v-if="['FILE', 'VIDEO', 'TEXT', 'HOMEWORK', 'DISCUSS'].includes(current.pageType)" label="附件">
            <div class="attach">
              <span v-if="current.fileName">{{ current.fileName }}</span>
              <span v-else class="muted">未上传</span>
              <el-upload :show-file-list="false" :http-request="uploadFile" :disabled="!current.id">
                <el-button size="small">{{ current.fileName ? '替换' : '上传' }}文件</el-button>
              </el-upload>
              <span v-if="!current.id" class="muted">（先保存页面再上传）</span>
            </div>
          </el-form-item>
          <el-form-item v-if="current.pageType === 'QUIZ'" label="关联试卷">
            <el-select v-model="current.refId" filterable clearable placeholder="从题库试卷中选择" class="w240">
              <el-option v-for="p in papers" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <span class="muted ml">在“题库与试卷”中编辑试卷</span>
          </el-form-item>
          <el-form-item v-if="current.pageType === 'PYTHON'" label="Python 题目">
            <el-select v-model="current.refId" filterable clearable placeholder="从 Python 题库中选择" class="w240">
              <el-option v-for="p in pyProblems" :key="p.id" :label="p.title + (p.mine ? '' : '（共享）')" :value="p.id" />
            </el-select>
            <span class="muted ml">在“Python 题库”中编辑题目和测试点</span>
          </el-form-item>
          <el-form-item v-if="['HOMEWORK', 'QUIZ'].includes(current.pageType)" label="分值">
            <el-input-number v-model="current.score" :min="0" :max="1000" />
          </el-form-item>
          <el-form-item label="学生可见">
            <el-switch v-model="current.studentVisible" />
            <span class="muted ml">开课后教师仍可在课堂控制台随时开放/隐藏</span>
          </el-form-item>
          <component :is="settingsComponent" v-if="settingsComponent && current.id" :page="current" />
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="save">保存页面</el-button>
            <el-popconfirm v-if="current.id" title="删除该页面？" @confirm="removePage">
              <template #reference><el-button type="danger" plain>删除</el-button></template>
            </el-popconfirm>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
    <StartLessonDialog v-model="startVisible" :course-id="course.id" />
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { pageTypes, pageTypeOptions } from '../../modules/pageTypes'
import RichEditor from '../../components/RichEditor.vue'
import PageContent from '../../components/PageContent.vue'
import StartLessonDialog from '../../components/StartLessonDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const course = ref<any>(null)
const pages = ref<any[]>([])
const current = ref<any>(null)
const saving = ref(false)
const papers = ref<any[]>([])
const pyProblems = ref<any[]>([])
const startVisible = ref(false)
const isOwner = computed(() => course.value && course.value.teacherId === auth.user?.userId)

// 作品提交页的专属设置（允许的文件类型、自动评分规则等）由作业模块提供
const homeworkSettings = defineAsyncComponent(() => import('../homework/HomeworkPageSettings.vue'))
const quizSettings = defineAsyncComponent(() => import('../quiz/QuizPageSettings.vue'))
const settingsComponent = computed(() => (current.value?.pageType === 'HOMEWORK' ? homeworkSettings
  : current.value?.pageType === 'QUIZ' ? quizSettings : null))

async function load() {
  const id = Number(route.params.id)
  course.value = await api.get(`/teacher/courses/${id}`)
  pages.value = course.value.pages
  if (current.value?.id) {
    current.value = { ...(pages.value.find(p => p.id === current.value.id) || current.value) }
  }
}

function select(p: any) {
  current.value = { ...p }
}

function newPage() {
  current.value = { title: '新页面', pageType: 'TEXT', content: '', studentVisible: true, score: 0 }
}

async function save() {
  saving.value = true
  try {
    const saved = current.value.id
      ? await api.put(`/teacher/pages/${current.value.id}`, current.value)
      : await api.post(`/teacher/courses/${course.value.id}/pages`, current.value)
    current.value = { ...saved }
    ElMessage.success('已保存')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

async function removePage() {
  try {
    await api.del(`/teacher/pages/${current.value.id}`)
    current.value = null
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function move(i: number, d: number) {
  const ids = pages.value.map(p => p.id)
  ;[ids[i], ids[i + d]] = [ids[i + d], ids[i]]
  pages.value = await api.put(`/teacher/courses/${course.value.id}/pages/order`, { ids })
}

async function uploadFile(opt: UploadRequestOptions) {
  try {
    const saved = await api.upload(`/teacher/pages/${current.value.id}/file`, opt.file)
    current.value.fileName = saved.fileName
    current.value.filePath = saved.filePath
    ElMessage.success('已上传')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => {
  await load()
  try {
    papers.value = await api.get('/teacher/quiz/papers')
  } catch {
    papers.value = []
  }
  try {
    const [mine, shared] = await Promise.all([api.get('/teacher/python/problems'), api.get('/teacher/python/problems', { shared: true })])
    pyProblems.value = [...mine.map((p: any) => ({ ...p, mine: true })), ...shared]
  } catch {
    pyProblems.value = []
  }
})
</script>

<style scoped>
.course-edit { display: flex; flex-direction: column; gap: 16px; }
.title { font-weight: 600; font-size: 16px; }
.ml { margin-left: 8px; }
.layout { display: grid; grid-template-columns: 300px 1fr; gap: 16px; align-items: start; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.page-item { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px; cursor: pointer; border: 1px solid transparent; }
.page-item:hover { background: #f5f7fa; }
.page-item.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.seq { width: 24px; height: 24px; border-radius: 50%; background: #e5eaf3; display: flex; align-items: center; justify-content: center; font-size: 12px; flex-shrink: 0; }
.info { flex: 1; min-width: 0; }
.ptitle { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ptype { font-size: 12px; color: #909399; }
.w240 { width: 240px; }
.opt-sub { float: right; color: #a8abb2; font-size: 12px; margin-left: 16px; }
.attach { display: flex; align-items: center; gap: 10px; }
.muted { color: #a8abb2; font-size: 12px; }
@media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } }
</style>
