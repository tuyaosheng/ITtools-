<template>
  <el-card shadow="never">
    <el-tabs v-model="tab">
      <el-tab-pane label="发送消息" name="send">
        <el-form label-width="80px" class="form">
          <el-form-item label="发送给">
            <el-radio-group v-model="mode">
              <el-radio-button label="class">班级群发</el-radio-button>
              <el-radio-button label="person">指定学生</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="mode === 'class'" label="班级">
            <el-checkbox-group v-model="classIds">
              <el-checkbox v-for="k in classes" :key="k.id" :label="k.id">{{ k.name }}（{{ k.studentCount }}人）</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <template v-else>
            <el-form-item label="班级">
              <el-select v-model="pickClass" placeholder="先选择班级" class="w240" @change="loadStudents">
                <el-option v-for="k in classes" :key="k.id" :label="k.name" :value="k.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="学生">
              <el-select v-model="studentIds" multiple filterable placeholder="选择一名或多名学生（可搜索）" class="full">
                <el-option v-for="s in students" :key="s.id" :label="`${s.studentNo || ''} ${s.name}`" :value="s.id" />
              </el-select>
            </el-form-item>
          </template>
          <el-form-item label="标题" required><el-input v-model="title" maxlength="200" show-word-limit /></el-form-item>
          <el-form-item label="内容"><el-input v-model="content" type="textarea" :rows="5" maxlength="2000" show-word-limit /></el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="sending" @click="send">发送</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="已发送" name="sent">
        <el-table :data="sent" stripe empty-text="还没有发送过消息">
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="targetDesc" label="收件人" min-width="160" show-overflow-tooltip />
          <el-table-column label="已读" width="90">
            <template #default="{ row }">{{ row.readCount }} / {{ row.recipientCount }}</template>
          </el-table-column>
          <el-table-column label="发送时间" width="150"><template #default="{ row }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="showDetail(row)">阅读情况</el-button>
              <el-popconfirm title="撤回后学生将看不到这条消息" @confirm="retract(row)">
                <template #reference><el-button link type="danger">撤回</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="收件箱" name="inbox">
        <InboxView v-if="tab === 'inbox'" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" :title="detail?.title" width="560px" append-to-body>
      <div class="pre">{{ detail?.content }}</div>
      <el-divider />
      <div class="muted">已读 {{ detail?.readCount }} / {{ detail?.recipientCount }}</div>
      <el-table :data="detail?.recipients || []" size="small" max-height="360">
        <el-table-column prop="className" label="班级" width="130" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column label="状态">
          <template #default="{ row }">
            <el-tag v-if="row.readAt" type="success" size="small">已读 {{ fmtTime(row.readAt) }}</el-tag>
            <el-tag v-else type="info" size="small">未读</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'
import InboxView from '../common/InboxView.vue'

const tab = ref('send')
const mode = ref<'class' | 'person'>('class')
const classes = ref<any[]>([])
const classIds = ref<number[]>([])
const pickClass = ref<number>()
const students = ref<any[]>([])
const studentIds = ref<number[]>([])
const title = ref('')
const content = ref('')
const sending = ref(false)
const sent = ref<any[]>([])
const detailVisible = ref(false)
const detail = ref<any>(null)

async function loadStudents() {
  students.value = pickClass.value ? await api.get(`/teacher/classes/${pickClass.value}/students`) : []
}

async function send() {
  if (!title.value.trim()) { ElMessage.warning('请填写标题'); return }
  sending.value = true
  try {
    const body = mode.value === 'class'
      ? { title: title.value, content: content.value, classIds: classIds.value }
      : { title: title.value, content: content.value, studentIds: studentIds.value }
    const m = await api.post('/teacher/messages', body)
    ElMessage.success(`已发送给 ${m.recipientCount} 名学生`)
    title.value = ''
    content.value = ''
    studentIds.value = []
  } catch (err) {
    ElMessage.error(errMsg(err, '发送失败'))
  } finally {
    sending.value = false
  }
}

async function loadSent() { sent.value = await api.get('/teacher/messages') }

async function showDetail(row: any) {
  detail.value = await api.get(`/teacher/messages/${row.id}`)
  detailVisible.value = true
}

async function retract(row: any) {
  try {
    await api.del(`/teacher/messages/${row.id}`)
    await loadSent()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

watch(tab, t => { if (t === 'sent') loadSent() })
onMounted(async () => { classes.value = await api.get('/teacher/classes') })
</script>

<style scoped>
.form { max-width: 760px; }
.w240 { width: 240px; }
.full { width: 100%; }
.muted { color: #909399; font-size: 12px; }
.pre { white-space: pre-wrap; }
</style>
