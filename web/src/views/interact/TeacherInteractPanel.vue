<template>
  <div class="interact">
    <div class="col">
      <el-card shadow="never">
        <template #header>
          <div class="head">举手求助 <el-badge :value="openHelps.length" :hidden="!openHelps.length" /></div>
        </template>
        <el-empty v-if="!data.helps.length" description="暂无举手" :image-size="40" />
        <div v-for="h in data.helps" :key="h.id" class="help" :class="{ done: h.status !== 'OPEN' }">
          <div>
            <b>{{ h.studentName }}</b> <span class="muted">{{ h.pcName }} · {{ fmtTime(h.createdAt) }}</span>
            <div v-if="h.message" class="hmsg">{{ h.message }}</div>
            <div v-if="h.helperName" class="hmsg">🤝 {{ h.helperName }} 帮助了 TA</div>
          </div>
          <el-button v-if="h.status === 'OPEN'" size="small" type="success" @click="resolve(h)">已处理</el-button>
          <el-tag v-else size="small" type="info">已处理</el-tag>
        </div>
      </el-card>

      <el-card v-if="data.helpers?.length" shadow="never">
        <template #header>互助榜</template>
        <div v-for="(h, i) in data.helpers" :key="h.studentId" class="bmsg">{{ i + 1 }}. {{ h.name }} <b>帮助 {{ h.count }} 人次</b></div>
      </el-card>

      <el-card shadow="never">
        <template #header>广播消息</template>
        <div class="send">
          <el-input v-model="content" placeholder="发送给全班的消息" :disabled="!active" @keyup.enter="send" />
          <el-button type="primary" :disabled="!active || !content" @click="send">发送</el-button>
        </div>
        <div v-for="b in data.broadcasts" :key="b.id" class="bmsg">{{ b.content }}<span class="muted"> · {{ fmtTime(b.createdAt) }}</span></div>
      </el-card>
    </div>

    <div class="col">
      <el-card shadow="never">
        <template #header>
          <div class="head">在线监控 <span class="muted">在线 {{ online }}/{{ data.monitor.length }}</span></div>
        </template>
        <el-table :data="data.monitor" size="small" max-height="360">
          <el-table-column prop="name" label="姓名" width="80" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }: { row: any }">
              <el-tag size="small" :type="row.online ? 'success' : 'info'">{{ row.online ? '在线' : row.status === 'PRESENT' ? '离开' : '未进课' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="pcName" label="机位" width="90" />
          <el-table-column prop="groupNo" label="组" width="50" />
          <el-table-column prop="currentPage" label="正在查看" show-overflow-tooltip />
        </el-table>
      </el-card>
      <el-card shadow="never">
        <template #header>学生动态</template>
        <div class="activity">
          <div v-for="a in data.activity" :key="a.id" class="act">
            <span class="muted">{{ fmtTime(a.createdAt).slice(11) }}</span> {{ a.studentName }} {{ actionLabel[a.action] || a.action }}{{ a.detail ? '：' + a.detail : '' }}
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useLessonChannel } from '../../api/realtime'
import { fmtTime } from '../../utils/format'

const props = defineProps<{ lesson: any }>()
const data = ref<any>({ helps: [], broadcasts: [], monitor: [], activity: [] })
const content = ref('')
const active = computed(() => props.lesson.status === 'ACTIVE')
const openHelps = computed(() => data.value.helps.filter((h: any) => h.status === 'OPEN'))
const online = computed(() => data.value.monitor.filter((m: any) => m.online).length)
const actionLabel: Record<string, string> = { HELP: '举手', DISCUSS: '参与讨论', CONCLUSION: '提交小组结论', ANSWER_FIRST: '抢答' }
let timer: number | undefined

async function load() {
  data.value = await api.get(`/teacher/interact/lessons/${props.lesson.id}`)
}

async function resolve(h: any) {
  await api.post(`/teacher/interact/helps/${h.id}/resolve`)
  await load()
}

async function send() {
  try {
    await api.post(`/teacher/interact/lessons/${props.lesson.id}/broadcast`, { content: content.value })
    content.value = ''
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

if (props.lesson.status === 'ACTIVE') {
  useLessonChannel(props.lesson.id, true, e => {
    if (e.type === 'HELP') {
      ElNotification({ title: '有学生举手', message: `${e.data.studentName}（${e.data.pcName || '未知机位'}）${e.data.message || ''}`, type: 'warning' })
      load()
    } else if (['HELP_CHANGED', 'DISCUSS', 'ANSWER_FIRST_ENTRY'].includes(e.type)) {
      load()
    }
  })
}

onMounted(async () => {
  await load()
  timer = window.setInterval(load, 15000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.interact { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.col { display: flex; flex-direction: column; gap: 16px; }
.head { display: flex; align-items: center; gap: 8px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; font-weight: normal; }
.help { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px dashed var(--el-border-color-lighter); }
.help.done { opacity: 0.6; }
.hmsg { font-size: 13px; color: var(--el-text-color-regular); margin-top: 2px; }
.send { display: flex; gap: 8px; margin-bottom: 10px; }
.bmsg { font-size: 13px; padding: 4px 0; border-bottom: 1px dashed var(--el-border-color-lighter); }
.activity { max-height: 260px; overflow-y: auto; font-size: 13px; }
.act { padding: 3px 0; }
@media (max-width: 1000px) { .interact { grid-template-columns: 1fr; } }
</style>
