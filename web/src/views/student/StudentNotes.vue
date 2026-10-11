<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>学习笔记 <span class="muted">（关联课程并勾选「提交给老师」即作为课后学习心得）</span></span>
        <el-button type="primary" @click="open()">写笔记</el-button>
      </div>
    </template>

    <el-empty v-if="!notes.length" description="还没有笔记" />
    <div v-for="n in notes" :key="n.id" class="note">
      <div class="n-head">
        <div>
          <b>{{ n.title }}</b>
          <el-tag v-if="n.submitted" size="small" type="success" class="ml8">已提交给老师</el-tag>
          <el-tag v-if="n.courseName" size="small" class="ml8">{{ n.courseName }}</el-tag>
        </div>
        <div>
          <span class="muted">{{ fmtTime(n.updatedAt) }}</span>
          <el-button link type="primary" class="ml8" @click="open(n)">编辑</el-button>
          <el-popconfirm title="删除这条笔记？" @confirm="remove(n)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </div>
      </div>
      <div class="n-body" v-html="safeHtml(n.content)" />
      <div v-if="n.teacherComment" class="t-comment">老师点评：{{ n.teacherComment }}</div>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑笔记' : '写笔记'" width="760px" top="6vh" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="关联课程">
          <el-select v-model="form.courseId" clearable placeholder="不关联" class="w260">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-checkbox v-model="form.submitted" :disabled="!form.courseId" class="ml8">提交给老师（学习心得）</el-checkbox>
        </el-form-item>
        <el-form-item label="内容"><RichEditor v-model="form.content" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import RichEditor from '../../components/RichEditor.vue'
import { api, errMsg } from '../../api/client'
import { fmtTime, safeHtml } from '../../utils/format'

const notes = ref<any[]>([])
const courses = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({})

watch(() => form.courseId, v => { if (!v) form.submitted = false })

async function load() {
  const d = await api.get('/notes')
  notes.value = d.notes
  courses.value = d.courses || []
}

function open(n?: any) {
  Object.assign(form, n
    ? { id: n.id, title: n.title, courseId: n.courseId, content: n.content || '', submitted: n.submitted }
    : { id: null, title: '', courseId: null, content: '', submitted: false })
  visible.value = true
}

async function save() {
  const body = { title: form.title, content: form.content, courseId: form.courseId || null, submitted: !!form.submitted }
  try {
    if (form.id) await api.put(`/notes/${form.id}`, body)
    else await api.post('/notes', body)
    visible.value = false
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function remove(n: any) {
  await api.del(`/notes/${n.id}`)
  await load()
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.muted { color: #909399; font-size: 12px; }
.ml8 { margin-left: 8px; }
.w260 { width: 260px; }
.note { border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; margin-bottom: 12px; }
.n-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.n-body { margin-top: 8px; font-size: 14px; }
.t-comment { margin-top: 8px; padding: 8px; background: #f0f9eb; border-radius: 4px; color: #529b2e; font-size: 13px; }
</style>
