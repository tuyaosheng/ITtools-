<template>
  <div class="home">
    <div class="hello">
      <div>
        <h1>{{ greeting }}，{{ auth.user?.displayName }}</h1>
        <p class="sub">{{ lesson ? '有课正在进行，快进教室吧' : '现在没有课，可以复习一下' }}</p>
      </div>
      <div v-if="notices.unreadMessages" class="sticker" @click="router.push('/student/messages')">🔔 {{ notices.unreadMessages }} 条新消息</div>
    </div>

    <div class="grid">
      <el-card shadow="never" class="lesson">
        <template v-if="lesson">
          <span class="tag">正在上课</span>
          <h2>《{{ lesson.courseName }}》</h2>
          <div class="meta">{{ lesson.teacherName }} · {{ lesson.className }} · {{ fmtTime(lesson.startedAt) }} 开始</div>
          <div v-if="lesson.myAttendance" class="meta">
            ✅ 已签到 · 机位 {{ lesson.myAttendance.pcName || '未登记' }}{{ lesson.myAttendance.groupNo ? ' · 第 ' + lesson.myAttendance.groupNo + ' 组' : '' }}
          </div>
          <el-button type="primary" size="large" class="go" @click="enter">{{ lesson.myAttendance?.status === 'PRESENT' ? '回到课堂' : '进入课堂' }}</el-button>
        </template>
        <el-empty v-else description="现在没有正在上的课" :image-size="90">
          <el-button @click="load">刷新</el-button>
        </el-empty>
      </el-card>

      <div class="tiles">
        <div v-for="t in tiles" :key="t.path" class="tile" @click="router.push(t.path)">
          <span class="ic">{{ t.icon }}</span>{{ t.label }}<small>{{ t.sub }}</small>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useNoticeStore } from '../../stores/notices'
import { api } from '../../api/client'
import { fmtTime } from '../../utils/format'

const router = useRouter()
const notices = useNoticeStore()
const auth = useAuthStore()
const lesson = ref<any>(null)
const greeting = computed(() => {
  const h = new Date().getHours()
  return h < 5 ? '你好' : h < 11 ? '早上好' : h < 13 ? '中午好' : h < 18 ? '下午好' : '晚上好'
})
const tiles = [
  { path: '/student/tests', icon: '📝', label: '作业与测验', sub: '看看有没有待交的' },
  { path: '/student/works', icon: '🎨', label: '我的作品', sub: '交过的作品都在这' },
  { path: '/student/practice', icon: '✏️', label: '刷题练习', sub: '错题本也在里面' },
  { path: '/student/typing', icon: '⌨️', label: '指法练习', sub: '冲一冲英雄榜' }
]

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
.home { max-width: 1080px; }
.hello { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; margin: 4px 0 18px; }
.hello h1 { margin: 0; font-size: 30px; color: var(--hb-ink); }
.sub { color: var(--el-text-color-secondary); margin: 6px 0 0; }
.sticker {
  background: var(--hb-paper); border: 2px solid var(--hb-ink); border-radius: 4px; padding: 8px 14px;
  font-weight: 700; color: var(--hb-ink); transform: rotate(1.5deg); cursor: pointer;
}
.grid { display: grid; grid-template-columns: 1.6fr 1fr; gap: 20px; align-items: start; }
.lesson :deep(.el-card__body) { padding: 24px; }
.tag {
  display: inline-block; background: var(--hb-marker); color: var(--hb-ink); font-weight: 700; font-size: 13px;
  padding: 3px 10px; transform: rotate(-2deg); margin-bottom: 8px;
}
.lesson h2 { margin: 0 0 6px; font-size: 26px; color: var(--hb-ink); }
.meta { color: var(--el-text-color-secondary); margin-top: 4px; }
.go { margin-top: 20px; min-width: 160px; }
.tiles { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.tile {
  background: var(--hb-paper); border: 2px solid var(--hb-ink); border-radius: 6px; padding: 14px 16px;
  font-weight: 700; color: var(--hb-ink); cursor: pointer; transition: transform .1s;
}
.tile:nth-child(odd) { transform: rotate(-1deg); }
.tile:nth-child(even) { transform: rotate(1deg); }
.tile:hover { transform: rotate(0) translateY(-2px); box-shadow: 3px 3px 0 var(--hb-ink); }
.tile .ic { display: block; font-size: 26px; margin-bottom: 4px; }
.tile small { display: block; font-weight: 400; font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }
@media (max-width: 860px) { .grid { grid-template-columns: 1fr; } }
</style>
