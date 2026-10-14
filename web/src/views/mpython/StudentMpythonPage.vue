<template>
  <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
  <div v-else-if="!src" v-loading="true" class="loading" />
  <div v-else class="mpy">
    <div class="bar">
      <span class="who">{{ who }}</span>
      <Hint>插上掌控板会自动连接；连不上时点页面里的“连接掌控板”。拼好积木点“刷入”，程序就写进板子了。</Hint>
      <span class="grow" />
      <el-button size="small" @click="fullscreen">全屏</el-button>
      <el-link :href="src" target="_blank" type="primary">在新窗口打开</el-link>
    </div>
    <div ref="wrap" class="frame-wrap">
      <!-- 同源 iframe：串口权限要显式放行（allow="serial"），否则页面里连不了掌控板 -->
      <iframe :src="src" class="frame" allow="serial; fullscreen; microphone; clipboard-write" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, errMsg } from '../../api/client'
import Hint from '../../components/Hint.vue'

/** 掌控板编程（学生）：嵌入 web/public/mpython 的学生页（原 mpython-lite），组号由服务器决定。 */
const props = defineProps<{ lessonId: number; page: any }>()
const src = ref('')
const error = ref('')
const me = ref<any>(null)
const wrap = ref<HTMLElement>()

const who = computed(() => (me.value?.label ? `我：${me.value.label}` : me.value ? `第 ${me.value.key} 组` : ''))

function fullscreen() { wrap.value?.requestFullscreen?.() }

onMounted(async () => {
  try {
    const c = await api.get(`/student/mpy/lessons/${props.lessonId}/pages/${props.page.id}/config`)
    me.value = c.me
    const q = new URLSearchParams({ page: String(props.page.id), lesson: String(props.lessonId), group: c.me.key })
    src.value = `/mpython/index.html?${q}`
  } catch (err) {
    error.value = errMsg(err, '掌控板编程暂不可用')
  }
})
</script>

<style scoped>
.loading { min-height: 200px; }
.bar { display: flex; gap: 10px; align-items: center; margin-bottom: 8px; }
.who { font-family: var(--hb-title-font); font-size: 16px; }
.grow { flex: 1; }
.frame-wrap { width: 100%; height: 78vh; min-height: 560px; border: var(--hb-border-w) solid var(--hb-shadow-color); border-radius: 8px; overflow: hidden; background: #fff; }
.frame-wrap:fullscreen { height: 100%; border: none; border-radius: 0; }
.frame { width: 100%; height: 100%; border: 0; }
</style>
