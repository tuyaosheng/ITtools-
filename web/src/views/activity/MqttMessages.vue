<template>
  <div class="mq">
    <el-alert v-if="teacher && data && !data.running" type="error" :closable="false" show-icon class="mb"
      :title="'内置 MQTT 服务器没有运行' + (data.error ? '：' + data.error : '（管理员在“系统设置 → 内置 MQTT 服务器”里打开）')" />
    <div v-if="data" class="head">
      <div class="row"><span class="k">MQTT 项目</span><b class="code">{{ data.project || '（本节课没有项目，请重新开课）' }}</b></div>
      <div v-if="!teacher" class="row"><span class="k">本组设备</span>
        <b v-if="data.myDevice" class="code">{{ data.myDevice }}</b><span v-else class="muted">还没进组</span>
        <span v-if="data.myDevice" class="muted">（主题 {{ data.project }}/{{ data.myDevice }}）</span>
      </div>
      <div class="row muted small">服务器 {{ data.server }}　端口 {{ data.port }}<template v-if="teacher">　账号 {{ data.username }} / {{ data.password }}　在线设备 {{ data.clients ?? 0 }}</template></div>
      <div v-if="teacher" class="ops">
        <el-button size="small" type="success" @click="openScreen">投屏大屏</el-button>
        <el-button size="small" @click="load">刷新</el-button>
        <el-button size="small" type="danger" plain :disabled="!data.devices.length" @click="clear">清空本课消息</el-button>
        <el-input v-model="sendDevice" size="small" placeholder="设备名，如 G01" class="w110" />
        <el-input v-model="sendText" size="small" placeholder="消息内容" class="w200" @keyup.enter="send" />
        <el-button size="small" type="primary" :disabled="!sendDevice || !data.project" @click="send">从网页发送</el-button>
      </div>
    </div>
    <el-empty v-if="data && !data.devices.length" :image-size="60"
      :description="teacher ? '还没有设备发来消息' : '还没有设备发来消息。程序上传到掌控板后，过几秒这里会自动出现'" />
    <div class="grid">
      <div v-for="d in sorted" :key="d.device" class="dev" :class="{ mine: d.device === data?.myDevice }">
        <div class="dev-h">
          <b class="code">{{ d.device }}</b>
          <el-tag v-if="d.device === data?.myDevice" size="small" type="success">本组</el-tag>
          <span class="muted small">共 {{ d.count }} 条</span>
        </div>
        <div v-for="(m, i) in d.messages" :key="i" class="msg">
          <span class="t">{{ fmt(m.createdAt) }}</span><span class="p">{{ m.payload }}</span>
        </div>
        <div v-if="d.count > d.messages.length" class="muted small">只显示最近 {{ d.messages.length }} 条</div>
      </div>
    </div>
    <p class="muted small">每 3 秒自动刷新。主题大小写不同就是不同的设备（如 G01 和 g01）。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ lessonId?: number | string, lesson?: any, page?: any }>()
const route = useRoute()
const teacher = computed(() => route.path.startsWith('/teacher'))
const lid = computed(() => props.lessonId ?? props.lesson?.id)
const data = ref<any>(null)
const sendDevice = ref('')
const sendText = ref('')
let timer: any = null

const sorted = computed(() => {
  const list = [...(data.value?.devices || [])]
  const mine = data.value?.myDevice
  return list.sort((a, b) => (a.device === mine ? -1 : b.device === mine ? 1 : String(a.device).localeCompare(String(b.device))))
})

function fmt(v: string) {
  const d = new Date(v)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function load() {
  if (!lid.value) return
  try {
    data.value = await api.get(`/${teacher.value ? 'teacher' : 'student'}/lessons/${lid.value}/mqtt`)
  } catch (err) {
    if (!data.value) ElMessage.error(errMsg(err))
  }
}

// 新窗口打开全屏大屏（拖到投影仪那块屏幕上，点“全屏”）
function openScreen() { window.open(`/teacher/screen/mqtt/${lid.value}`, `mqtt-screen-${lid.value}`) }

async function clear() {
  try {
    await ElMessageBox.confirm('清空本节课收到的所有 MQTT 消息？不能恢复。', '清空消息', { type: 'warning', confirmButtonText: '清空' })
    const n = await api.del(`/teacher/lessons/${lid.value}/mqtt`)
    ElMessage.success(`已清空 ${n} 条`)
    await load()
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(errMsg(err))
  }
}

async function send() {
  try {
    await api.post(`/teacher/lessons/${lid.value}/mqtt/publish`, { device: sendDevice.value, payload: sendText.value })
    sendText.value = ''
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(() => { load(); timer = setInterval(load, 3000) })
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.mb { margin-bottom: 10px; }
.head { padding: 10px 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; margin-bottom: 12px; background: var(--el-fill-color-lighter); }
.row { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; margin: 2px 0; }
.k { color: var(--el-text-color-secondary); width: 80px; flex: none; white-space: nowrap; }
.code { font-family: Consolas, 'Courier New', monospace; font-size: 16px; }
.ops { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; align-items: center; }
.w110 { width: 110px; }
.w200 { width: 200px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 10px; }
.dev { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 8px 10px; background: var(--el-bg-color); }
.dev.mine { border-color: var(--el-color-success); box-shadow: 0 0 0 2px var(--el-color-success-light-7); }
.dev-h { display: flex; gap: 8px; align-items: center; margin-bottom: 4px; }
.msg { display: flex; gap: 8px; padding: 3px 0; border-top: 1px dashed var(--el-border-color-lighter); font-size: 14px; }
.t { color: var(--el-text-color-secondary); font-family: Consolas, monospace; flex: none; }
.p { word-break: break-all; }
.muted { color: var(--el-text-color-secondary); }
.small { font-size: 12px; }
</style>
