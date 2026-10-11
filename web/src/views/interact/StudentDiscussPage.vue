<template>
  <div class="discuss">
    <el-divider content-position="left">小组讨论{{ data.groupNo ? ` · 第 ${data.groupNo} 组` : '（未分组，全班讨论区）' }}</el-divider>
    <div ref="box" class="posts">
      <el-empty v-if="!data.posts.length" description="还没有人发言，来说说你的想法吧" :image-size="40" />
      <div v-for="p in data.posts" :key="p.id" class="post" :class="{ mine: p.authorId === auth.user?.userId, teacher: p.role === 'TEACHER', conclusion: p.conclusion }">
        <div class="meta">{{ p.authorName }}{{ p.role === 'TEACHER' ? '（老师）' : '' }} · {{ fmtTime(p.createdAt).slice(11) }}
          <el-tag v-if="p.conclusion" size="small" type="success">小组结论</el-tag></div>
        <div class="bubble">{{ p.content }}</div>
      </div>
    </div>
    <div class="send">
      <el-input v-model="content" type="textarea" :rows="2" placeholder="发表你的观点" />
      <div class="send-ops">
        <el-button type="primary" :disabled="!content" @click="post(false)">发言</el-button>
        <el-button type="success" plain :disabled="!content" @click="post(true)">作为小组结论提交</el-button>
      </div>
    </div>
    <template v-if="data.conclusions.length">
      <el-divider content-position="left">各组结论</el-divider>
      <div v-for="c in data.conclusions" :key="c.groupNo" class="conc"><b>第 {{ c.groupNo }} 组：</b>{{ c.content }} <span class="muted">— {{ c.authorName }}</span></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { fmtTime } from '../../utils/format'

const props = defineProps<{ lessonId: number; page: any }>()
const auth = useAuthStore()
const data = ref<any>({ posts: [], conclusions: [], groupNo: 0 })
const content = ref('')
const box = ref<HTMLElement>()
let timer: number | undefined

async function load() {
  const prev = data.value.posts.length
  data.value = await api.get(`/student/interact/lessons/${props.lessonId}/discuss/${props.page.id}`)
  if (data.value.posts.length !== prev) { await nextTick(); box.value?.scrollTo({ top: box.value.scrollHeight }) }
}

async function post(conclusion: boolean) {
  try {
    await api.post(`/student/interact/lessons/${props.lessonId}/discuss/${props.page.id}`, { content: content.value, conclusion })
    content.value = ''
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

watch(() => props.page.id, load)
onMounted(async () => { await load(); timer = window.setInterval(load, 5000) })
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.posts { max-height: 380px; overflow-y: auto; padding: 4px; }
.post { margin-bottom: 10px; display: flex; flex-direction: column; align-items: flex-start; }
.post.mine { align-items: flex-end; }
.meta { font-size: 12px; color: #909399; margin-bottom: 2px; display: flex; gap: 6px; align-items: center; }
.bubble { background: #f4f4f5; padding: 8px 12px; border-radius: 10px; max-width: 80%; white-space: pre-wrap; }
.post.mine .bubble { background: var(--el-color-primary-light-8); }
.post.teacher .bubble { background: #fdf6ec; }
.post.conclusion .bubble { border: 1px solid #b3e19d; background: #f0f9eb; }
.send { margin-top: 10px; }
.send-ops { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.conc { padding: 6px 0; border-bottom: 1px dashed #ebeef5; }
.muted { color: #909399; font-size: 12px; }
</style>
