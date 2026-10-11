<template>
  <div class="home">
    <el-card shadow="never" class="hero">
      <h2>你好，{{ auth.user?.displayName }}</h2>
      <p class="sub">祝你今天学习愉快！</p>
    </el-card>

    <el-card shadow="never">
      <template #header>当前课堂</template>
      <el-empty v-if="!lesson" description="你的班级现在没有正在进行的课堂">
        <el-button @click="load">刷新</el-button>
      </el-empty>
      <div v-else class="current">
        <div>
          <div class="course">《{{ lesson.courseName }}》</div>
          <div class="meta">{{ lesson.teacherName }} · {{ lesson.className }} · 开始于 {{ fmtTime(lesson.startedAt) }}</div>
          <div v-if="lesson.myAttendance" class="meta">
            已签到 · 机位 {{ lesson.myAttendance.pcName || '未登记' }}{{ lesson.myAttendance.groupNo ? ' · 第 ' + lesson.myAttendance.groupNo + ' 组' : '' }}
          </div>
        </div>
        <el-button type="primary" size="large" @click="enter">{{ lesson.myAttendance?.status === 'PRESENT' ? '回到课堂' : '进入课堂' }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { api } from '../../api/client'
import { fmtTime } from '../../utils/format'

const router = useRouter()
const auth = useAuthStore()
const lesson = ref<any>(null)

async function load() {
  lesson.value = await api.get('/student/lessons/current')
}

function enter() {
  router.push(`/student/lessons/${lesson.value.id}`)
}

onMounted(async () => {
  await load()
  // 公开课模式：有课时直接进入课堂
  try {
    const s = await api.get('/public/settings')
    if (s.loginMode === 'OPEN_CLASS' && lesson.value) enter()
  } catch {
    /* ignore */
  }
})
</script>

<style scoped>
.home { display: flex; flex-direction: column; gap: 16px; }
.hero h2 { margin: 0; }
.sub { color: #909399; margin: 6px 0 0; }
.current { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.course { font-size: 20px; font-weight: 600; }
.meta { color: #909399; margin-top: 6px; }
</style>
