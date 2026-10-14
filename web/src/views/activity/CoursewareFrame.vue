<template>
  <div class="cw">
    <el-empty v-if="loaded && !url" description="教师还没有上传课件包" />
    <template v-else-if="url">
      <div class="bar">
        <el-button size="small" @click="fullscreen">全屏</el-button>
        <el-link :href="url" target="_blank" type="primary">在新窗口打开</el-link>
      </div>
      <div ref="wrap" class="frame-wrap">
        <!-- 隔离由服务端响应头 Content-Security-Policy: sandbox 保证（课件作为不同源的沙箱文档运行） -->
        <iframe :src="url" class="frame" allow="autoplay; fullscreen" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import { useAuthStore } from '../../stores/auth'

/** 课件包（Storyline 等）在沙箱 iframe 中播放；教师走教师接口，学生走课堂接口（须页面已开放）。 */
const props = defineProps<{ page: any; lessonId?: number; lesson?: any; teacher?: boolean }>()
const auth = useAuthStore()
const url = ref('')
const loaded = ref(false)
const wrap = ref<HTMLElement>()

function fullscreen() { wrap.value?.requestFullscreen?.() }

onMounted(async () => {
  const c = props.teacher || auth.user?.role === 'TEACHER' || !props.lessonId
    ? await api.get(`/teacher/courseware/pages/${props.page.id}`)
    : await api.get(`/student/courseware/lessons/${props.lessonId}/pages/${props.page.id}`)
  url.value = c.url ? encodeURI(c.url) : ''
  loaded.value = true
})
</script>

<style scoped>
.bar { display: flex; gap: 10px; align-items: center; margin-bottom: 8px; }
.frame-wrap { width: 100%; aspect-ratio: 16 / 10; border: 1px solid var(--el-border-color); border-radius: 6px; overflow: hidden; background: var(--el-bg-color); }
.frame-wrap:fullscreen { aspect-ratio: auto; border: none; }
.frame { width: 100%; height: 100%; border: 0; }
</style>
