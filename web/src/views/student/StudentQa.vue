<template>
  <el-card shadow="never">
    <el-tabs v-model="tab">
      <el-tab-pane label="我的问题" name="mine">
        <div class="bar">
          <div><Hint block>遇到不会的问题可以向老师提问，老师答复后会收到系统消息</Hint></div>
          <el-button type="primary" @click="open">提问</el-button>
        </div>
        <el-empty v-if="!mine.length" description="还没有提过问题" />
        <div v-for="q in mine" :key="q.id" class="q">
          <div class="q-head">
            <b>{{ q.title }}</b>
            <el-tag :type="q.answer ? 'success' : 'warning'" size="small">{{ q.answer ? '已答复' : '待答复' }}</el-tag>
            <el-tag v-if="q.isPublic" size="small" type="info">已公开</el-tag>
            <span class="muted meta">问 {{ q.teacherName }} · {{ fmtTime(q.createdAt) }}</span>
            <el-popconfirm title="删除这个问题？" @confirm="remove(q)">
              <template #reference><el-button link type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </div>
          <div class="pre">{{ q.content }}</div>
          <div v-if="q.answer" class="answer">
            <div class="muted">{{ q.teacherName }} 答复于 {{ fmtTime(q.answeredAt) }}</div>
            <div class="pre">{{ q.answer }}</div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="公开问答" name="public">
        <el-input v-model="keyword" placeholder="搜索问题" clearable class="w240 mb" @change="loadPublic" />
        <el-empty v-if="!publicList.length" description="暂无公开的问答" />
        <div v-for="q in publicList" :key="q.id" class="q">
          <div class="q-head">
            <b>{{ q.title }}</b>
            <span class="muted meta">{{ q.className }}同学提问 · {{ q.teacherName }} 答复</span>
          </div>
          <div class="pre">{{ q.content }}</div>
          <div class="answer"><div class="pre">{{ q.answer }}</div></div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="visible" title="向老师提问" width="560px" append-to-body>
      <el-form label-width="70px">
        <el-form-item label="老师" required>
          <el-select v-model="form.teacherId" filterable placeholder="选择老师" class="w240">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name + (t.mine ? '（任课）' : '')" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="问题" required>
          <el-input v-model="form.content" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="把问题描述清楚，可以附上你写的代码或出错信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="ask">提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'

const tab = ref('mine')
const mine = ref<any[]>([])
const publicList = ref<any[]>([])
const keyword = ref('')
const teachers = ref<any[]>([])
const visible = ref(false)
const saving = ref(false)
const form = reactive<any>({ teacherId: null, title: '', content: '' })

async function load() { mine.value = await api.get('/student/qa') }
async function loadPublic() { publicList.value = await api.get('/qa/public', { keyword: keyword.value || undefined }) }

async function open() {
  if (!teachers.value.length) teachers.value = await api.get('/student/qa/teachers')
  Object.assign(form, { teacherId: teachers.value.find(t => t.mine)?.id ?? null, title: '', content: '' })
  visible.value = true
}

async function ask() {
  saving.value = true
  try {
    await api.post('/student/qa', form)
    visible.value = false
    ElMessage.success('已提交，老师答复后会通知你')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

async function remove(q: any) {
  await api.del(`/student/qa/${q.id}`)
  await load()
}

watch(tab, t => { if (t === 'public') loadPublic() })
onMounted(load)
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.w240 { width: 240px; }
.mb { margin-bottom: 12px; }
.q { border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; }
.q-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
.meta { margin-left: auto; }
.pre { white-space: pre-wrap; font-size: 14px; line-height: 1.6; }
.answer { margin-top: 8px; padding: 8px 10px; background: var(--el-color-success-light-9); border-radius: 4px; }
</style>
