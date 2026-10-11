<template>
  <div class="forum">
    <template v-if="!topic">
      <div class="ops">
        <el-button size="small" type="primary" @click="newVisible = true">发帖</el-button>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
      <el-empty v-if="!topics.length" description="还没有帖子" :image-size="40" />
      <div v-for="t in topics" :key="t.id" class="topic" @click="open(t.id)">
        <div class="t-title">
          <el-tag v-if="t.pinned" size="small" type="danger">置顶</el-tag>
          <el-tag v-if="t.recommended" size="small" type="warning">推荐</el-tag>
          {{ t.title }}
        </div>
        <div class="muted">{{ t.authorName }}{{ t.authorRole === 'TEACHER' ? '（老师）' : '' }} · 回复 {{ t.replyCount }} · 浏览 {{ t.clicks }}
          <template v-if="t.lastReplyAt"> · 最后回复 {{ t.lastReplyName }} {{ fmtTime(t.lastReplyAt) }}</template>
        </div>
      </div>
    </template>
    <template v-else>
      <el-button link @click="topic = null; load()">← 返回</el-button>
      <div class="post main">
        <div class="t-title big">{{ topic.title }}</div>
        <div class="muted">{{ topic.authorName }} · {{ fmtTime(topic.createdAt) }}</div>
        <div class="content">{{ topic.content }}</div>
        <div v-if="isTeacher" class="ops">
          <el-button size="small" @click="flag({ pinned: !topic.pinned })">{{ topic.pinned ? '取消置顶' : '置顶' }}</el-button>
          <el-button size="small" @click="flag({ recommended: !topic.recommended })">{{ topic.recommended ? '取消推荐' : '推荐' }}</el-button>
          <el-popconfirm title="删除该帖？" @confirm="delTopic">
            <template #reference><el-button size="small" type="danger" plain>删除</el-button></template>
          </el-popconfirm>
        </div>
      </div>
      <div v-for="(r, i) in topic.replies" :key="r.id" class="post">
        <div class="muted">#{{ i + 1 }} {{ r.authorName }}{{ r.authorRole === 'TEACHER' ? '（老师）' : '' }} · {{ fmtTime(r.createdAt) }}
          <el-button v-if="isTeacher || r.authorId === auth.user?.userId" link size="small" type="danger" @click="delReply(r)">删除</el-button>
        </div>
        <div class="content">{{ r.content }}</div>
      </div>
      <div class="reply">
        <el-input v-model="replyText" type="textarea" :rows="2" placeholder="写下你的回复" />
        <el-button type="primary" size="small" class="mt" :disabled="!replyText" @click="reply">回复</el-button>
      </div>
    </template>

    <el-dialog v-model="newVisible" title="发帖" width="480px" append-to-body>
      <el-input v-model="newTopic.title" placeholder="标题" class="mb" />
      <el-input v-model="newTopic.content" type="textarea" :rows="5" placeholder="内容" />
      <template #footer>
        <el-button @click="newVisible = false">取消</el-button>
        <el-button type="primary" @click="create">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { fmtTime } from '../../utils/format'

const props = defineProps<{ lesson: any }>()
const auth = useAuthStore()
const isTeacher = computed(() => auth.user?.role === 'TEACHER')
const topics = ref<any[]>([])
const topic = ref<any>(null)
const replyText = ref('')
const newVisible = ref(false)
const newTopic = reactive({ title: '', content: '' })

async function load() { topics.value = await api.get(`/forum/courses/${props.lesson.courseId}/topics`) }
async function open(id: number) { topic.value = await api.get(`/forum/topics/${id}`) }

async function create() {
  try {
    const id = await api.post(`/forum/courses/${props.lesson.courseId}/topics`, newTopic)
    newVisible.value = false
    Object.assign(newTopic, { title: '', content: '' })
    await open(id)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function reply() {
  await api.post(`/forum/topics/${topic.value.id}/replies`, { content: replyText.value })
  replyText.value = ''
  await open(topic.value.id)
}

async function flag(f: any) {
  await api.put(`/forum/topics/${topic.value.id}/flags`, f)
  await open(topic.value.id)
}

async function delTopic() {
  await api.del(`/forum/topics/${topic.value.id}`)
  topic.value = null
  await load()
}

async function delReply(r: any) {
  await api.del(`/forum/replies/${r.id}`)
  await open(topic.value.id)
}

onMounted(load)
</script>

<style scoped>
.ops { display: flex; gap: 8px; margin: 8px 0; }
.topic { padding: 10px 4px; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.topic:hover { background: #fafafa; }
.t-title { font-weight: 600; display: flex; gap: 6px; align-items: center; }
.t-title.big { font-size: 16px; }
.muted { color: #909399; font-size: 12px; }
.post { padding: 10px 0; border-bottom: 1px dashed #ebeef5; }
.content { margin-top: 6px; white-space: pre-wrap; line-height: 1.7; }
.reply { margin-top: 12px; }
.mt { margin-top: 8px; }
.mb { margin-bottom: 8px; }
</style>
