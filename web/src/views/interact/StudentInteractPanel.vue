<template>
  <div class="panel">
    <div class="section">
      <div class="title">举手求助</div>
      <template v-if="myHelp">
        <el-alert type="warning" :closable="false" :title="`已举手，等待老师处理…${myHelp.message ? '（' + myHelp.message + '）' : ''}`" />
        <el-button size="small" class="mt" @click="cancel">放下手</el-button>
      </template>
      <template v-else>
        <el-input v-model="message" size="small" placeholder="遇到什么问题？" maxlength="100" />
        <el-button type="warning" size="small" class="mt full" :disabled="!active" @click="raise">✋ 举手</el-button>
      </template>
      <div v-if="active && classmates.length" class="thanks">
        <span class="muted">同学帮我解决了问题：</span>
        <el-select v-model="helperId" size="small" filterable placeholder="选择同学" class="helper">
          <el-option v-for="c in classmates" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button size="small" type="success" :disabled="!helperId" @click="thanks">谢谢 TA</el-button>
      </div>
    </div>

    <div v-if="group?.groupNo" class="section">
      <div class="title">我的小组 · 第 {{ group.groupNo }} 组</div>
      <div class="members">
        <el-tag v-for="m in group.members" :key="m.name" size="small" class="member">{{ m.name }}<span class="pc">{{ m.pcName }}</span></el-tag>
      </div>
    </div>

    <div class="section">
      <div class="title">老师消息</div>
      <el-empty v-if="!broadcasts.length" description="暂无消息" :image-size="40" />
      <div v-for="b in broadcasts" :key="b.id" class="msg">
        <div>{{ b.content }}</div>
        <div class="time">{{ fmtTime(b.createdAt) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useLessonChannel } from '../../api/realtime'
import { useAuthStore } from '../../stores/auth'
import { fmtTime } from '../../utils/format'

const props = defineProps<{ lesson: any }>()
const auth = useAuthStore()
const myHelp = ref<any>(null)
const broadcasts = ref<any[]>([])
const group = ref<any>(null)
const message = ref('')
const classmates = ref<any[]>([])
const helperId = ref<number>()
const active = computed(() => props.lesson?.status === 'ACTIVE')
let timer: number | undefined

async function load() {
  const d = await api.get(`/student/interact/lessons/${props.lesson.id}`)
  myHelp.value = d.myHelp
  broadcasts.value = d.broadcasts
  group.value = d.group
  classmates.value = d.classmates || []
}

async function thanks() {
  try {
    await api.post(`/student/interact/lessons/${props.lesson.id}/help/thanks`, { helperId: helperId.value })
    myHelp.value = null
    helperId.value = undefined
    ElMessage.success('已记录')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function raise() {
  try {
    myHelp.value = await api.post(`/student/interact/lessons/${props.lesson.id}/help`, { message: message.value })
    message.value = ''
    ElMessage.success('已举手')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function cancel() {
  await api.del(`/student/interact/lessons/${props.lesson.id}/help`)
  myHelp.value = null
}

if (props.lesson?.status === 'ACTIVE') {
  useLessonChannel(props.lesson.id, false, e => {
    if (e.type === 'BROADCAST') {
      broadcasts.value.unshift(e.data)
      ElNotification({ title: '老师消息', message: e.data.content, type: 'info', duration: 8000 })
    } else if (e.type === 'HELP_RESOLVED' && e.data === auth.user?.userId) {
      myHelp.value = null
      ElNotification({ title: '求助已处理', message: '老师已处理你的举手', type: 'success' })
    } else if (e.type === 'ANSWER_FIRST_START') {
      ElNotification({ title: '抢答开始！', message: e.data.question, type: 'warning', duration: 10000 })
    }
  })
}

onMounted(async () => {
  await load()
  timer = window.setInterval(load, 30000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.section { margin-bottom: 16px; }
.title { font-weight: 600; margin-bottom: 8px; }
.mt { margin-top: 8px; }
.full { width: 100%; }
.members { display: flex; flex-wrap: wrap; gap: 6px; }
.pc { margin-left: 4px; color: var(--el-text-color-placeholder); font-size: 11px; }
.msg { padding: 8px 10px; background: var(--el-color-primary-light-9); border-radius: 8px; margin-bottom: 6px; font-size: 13px; }
.time { font-size: 11px; color: var(--el-text-color-secondary); margin-top: 2px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.thanks { margin-top: 8px; display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.helper { width: 120px; }
</style>
