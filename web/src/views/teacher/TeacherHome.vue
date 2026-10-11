<template>
  <div class="home">
    <el-card shadow="never">
      <div class="hero">
        <div>
          <h2>你好，{{ auth.user?.displayName }}</h2>
          <p class="sub">{{ currentTerm ? '当前学期：' + currentTerm.label : '尚未设置当前学期' }}</p>
        </div>
        <el-button type="primary" size="large" @click="startVisible = true">开 课</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>正在进行的课堂</template>
      <el-empty v-if="!active.length" description="当前没有进行中的课堂" :image-size="60" />
      <div class="lesson-list">
        <div v-for="l in active" :key="l.id" class="lesson" @click="router.push(`/teacher/lessons/${l.id}`)">
          <div class="lesson-title">《{{ l.courseName }}》</div>
          <div class="lesson-meta">{{ l.className }} · 开始于 {{ fmtTime(l.startedAt) }}</div>
          <div class="lesson-stat">
            <el-tag type="success">已到 {{ l.presentCount }} / {{ l.studentCount }}</el-tag>
            <el-button type="primary" link>进入课堂控制台 →</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <div class="two">
      <el-card shadow="never">
        <template #header>最近上课</template>
        <el-table :data="recent" size="small">
          <el-table-column label="课程" prop="courseName" />
          <el-table-column label="班级" prop="className" width="120" />
          <el-table-column label="时间" width="150">
            <template #default="{ row }: { row: any }">{{ fmtTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column label="出勤" width="90">
            <template #default="{ row }: { row: any }">{{ row.presentCount }}/{{ row.studentCount }}</template>
          </el-table-column>
        </el-table>
      </el-card>
      <el-card shadow="never">
        <template #header>
          <div class="card-head"><span>今日课表</span><el-button link type="primary" @click="router.push('/teacher/timetable')">编辑课表</el-button></div>
        </template>
        <el-empty v-if="!today.length" description="今天没有课" :image-size="50" />
        <div v-for="c in today" :key="c.period" class="tt-row"><b>第 {{ c.period }} 节</b> {{ c.content }}</div>
      </el-card>
    </div>

    <StartLessonDialog v-model="startVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { api } from '../../api/client'
import { fmtTime } from '../../utils/format'
import StartLessonDialog from '../../components/StartLessonDialog.vue'

const router = useRouter()
const auth = useAuthStore()
const active = ref<any[]>([])
const recent = ref<any[]>([])
const terms = ref<any[]>([])
const timetable = ref<any[]>([])
const startVisible = ref(false)

const currentTerm = computed(() => terms.value.find(t => t.current))
const today = computed(() => {
  const wd = new Date().getDay() || 7
  return timetable.value.filter(c => c.weekday === wd).sort((a, b) => a.period - b.period)
})

onMounted(async () => {
  const [a, all, t, tt] = await Promise.all([
    api.get('/teacher/lessons', { status: 'ACTIVE' }),
    api.get('/teacher/lessons', { status: 'ENDED' }),
    api.get('/teacher/terms'),
    api.get('/teacher/timetable')
  ])
  active.value = a
  recent.value = all.slice(0, 8)
  terms.value = t
  timetable.value = tt
})
</script>

<style scoped>
.home { display: flex; flex-direction: column; gap: 16px; }
.hero { display: flex; justify-content: space-between; align-items: center; }
.hero h2 { margin: 0; }
.sub { color: #909399; margin: 6px 0 0; }
.lesson-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.lesson { border: 1px solid #e4e7ed; border-radius: 10px; padding: 14px; cursor: pointer; transition: box-shadow .2s; }
.lesson:hover { box-shadow: 0 4px 14px rgba(0, 0, 0, .08); }
.lesson-title { font-weight: 600; font-size: 16px; }
.lesson-meta { color: #909399; font-size: 13px; margin: 6px 0 10px; }
.lesson-stat { display: flex; justify-content: space-between; align-items: center; }
.two { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.tt-row { padding: 6px 0; border-bottom: 1px dashed #ebeef5; }
@media (max-width: 900px) { .two { grid-template-columns: 1fr; } }
</style>
