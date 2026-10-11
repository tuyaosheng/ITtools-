<template>
  <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
  <el-empty v-else-if="data && !data.groupNo" description="你还没有加入小组，请先在「分组」页面加入或等待老师分组" />
  <div v-else-if="data" class="work">
    <div class="head">
      <b>第 {{ data.groupNo }} 组</b>
      <span class="muted">组长：{{ group.leaderName || '未确定' }}</span>
      <el-tag v-if="data.isLeader" type="warning" size="small">我是组长</el-tag>
    </div>

    <section>
      <div class="sec-title">研究主题</div>
      <div v-if="data.isLeader" class="row">
        <el-input v-model="topic" maxlength="200" placeholder="组长填写本组研究主题" />
        <el-button type="primary" @click="saveTopic">保存</el-button>
      </div>
      <div v-else class="topic">{{ group.topic || '组长还没有设置主题' }}</div>
    </section>

    <section>
      <div class="sec-title">成员分工</div>
      <el-table :data="group.members" size="small" border>
        <el-table-column label="姓名" width="120">
          <template #default="{ row }"><span v-if="row.studentId === group.leaderId" class="crown">★</span>{{ row.name }}</template>
        </el-table-column>
        <el-table-column label="组内分工">
          <template #default="{ row }">
            <el-input v-if="data.isLeader" v-model="tasks[row.studentId]" size="small" maxlength="200" placeholder="如：查资料、做问卷、汇报" />
            <span v-else>{{ row.task || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-button v-if="data.isLeader" size="small" type="primary" class="mt8" @click="saveTasks">保存分工</el-button>
    </section>

    <section>
      <div class="sec-title">
        组内资源交流
        <el-button size="small" class="ml8" @click="uploadVisible = true">上传资源</el-button>
      </div>
      <el-empty v-if="!data.files.length" description="还没有资源" :image-size="40" />
      <div v-for="f in data.files" :key="f.id" class="file" :class="{ result: f.id === group.resultFileId }">
        <div>
          <b>{{ f.title }}</b>
          <el-tag v-if="f.id === group.resultFileId" size="small" type="success" class="ml8">小组成果</el-tag>
          <div class="muted">{{ f.uploaderName }} · {{ f.fileName }} · {{ fmtSize(f.fileSize) }} · {{ fmtTime(f.createdAt) }}</div>
          <div v-if="f.intro" class="intro">{{ f.intro }}</div>
        </div>
        <div class="ops">
          <el-button link type="primary" @click="download(f)">下载</el-button>
          <el-button v-if="data.isLeader && f.id !== group.resultFileId" link type="success" @click="pickResult(f.id)">设为成果</el-button>
          <el-popconfirm v-if="f.uploaderId === myId || data.isLeader" title="删除该资源？" @confirm="removeFile(f)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </div>
      </div>
    </section>

    <section>
      <div class="sec-title">小组成果</div>
      <template v-if="data.isLeader">
        <el-input v-model="resultText" type="textarea" :rows="3" placeholder="组长总结本组的研究结论或成果说明" />
        <div class="row mt8">
          <span class="muted">成果文件</span>
          <el-select v-model="resultFileId" clearable placeholder="从组内资源中选择" class="w240">
            <el-option v-for="f in data.files" :key="f.id" :label="f.title" :value="f.id" />
          </el-select>
          <el-button type="primary" @click="saveResult">保存成果</el-button>
        </div>
      </template>
      <div v-else>
        <div class="pre">{{ group.resultText || '组长还没有提交成果' }}</div>
        <div v-if="group.resultFileName" class="muted">成果文件：{{ group.resultFileName }}</div>
      </div>
    </section>

    <section v-if="data.myEvaluations.length">
      <div class="sec-title">组员互评 <span class="muted">（评价其他组员在本次合作中的表现）</span></div>
      <div v-for="e in data.myEvaluations" :key="e.studentId" class="peer">
        <span class="p-name">{{ e.name }}</span>
        <el-rate v-model="peerScore[e.studentId]" />
        <el-input v-model="peerComment[e.studentId]" size="small" maxlength="200" placeholder="评语（可选）" class="p-comment" />
        <el-button size="small" :type="e.score ? 'default' : 'primary'" @click="ratePeer(e)">{{ e.score ? '更新' : '提交' }}</el-button>
      </div>
    </section>

    <el-dialog v-model="uploadVisible" title="上传组内资源" width="480px" append-to-body>
      <el-form label-width="70px">
        <el-form-item label="文件" required><input ref="fileInput" type="file" @change="onFile" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="upTitle" maxlength="200" placeholder="留空则使用文件名" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="upIntro" type="textarea" :rows="2" maxlength="500" placeholder="说明或心得" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="upload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useLessonChannel } from '../../api/realtime'
import { useAuthStore } from '../../stores/auth'
import { fmtSize, fmtTime } from '../../utils/format'

const props = defineProps<{ lessonId: number; page?: any }>()
const base = `/student/groups/lessons/${props.lessonId}/work`
const auth = useAuthStore()
const myId = computed(() => auth.user?.userId)
const data = ref<any>(null)
const error = ref('')
const group = computed(() => data.value?.group || { members: [] })
const topic = ref('')
const tasks = reactive<Record<number, string>>({})
const resultText = ref('')
const resultFileId = ref<number | null>(null)
const peerScore = reactive<Record<number, number>>({})
const peerComment = reactive<Record<number, string>>({})
const uploadVisible = ref(false)
const uploading = ref(false)
const file = ref<File | null>(null)
const fileInput = ref<HTMLInputElement>()
const upTitle = ref('')
const upIntro = ref('')
let timer: number | undefined

/** dirty=false 时用服务端数据覆盖输入框；实时刷新时不覆盖正在编辑的内容 */
function apply(d: any, overwriteInputs = true) {
  data.value = d
  if (!d.groupNo || !overwriteInputs) return
  topic.value = d.group.topic || ''
  resultText.value = d.group.resultText || ''
  resultFileId.value = d.group.resultFileId || null
  for (const m of d.group.members) tasks[m.studentId] = m.task || ''
  for (const e of d.myEvaluations) {
    peerScore[e.studentId] = e.score || 0
    peerComment[e.studentId] = e.comment || ''
  }
}

async function load(overwrite = true) {
  try {
    apply(await api.get(base), overwrite)
    error.value = ''
  } catch (err) {
    error.value = errMsg(err, '加载失败')
  }
}

async function run(p: Promise<any>, ok: string) {
  try {
    apply(await p)
    ElMessage.success(ok)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

const saveTopic = () => run(api.put(`${base}/topic`, { topic: topic.value }), '已保存主题')
const saveTasks = () => run(api.put(`${base}/tasks`, { tasks: group.value.members.map((m: any) => ({ studentId: m.studentId, task: tasks[m.studentId] })) }), '已保存分工')
const saveResult = () => run(api.put(`${base}/result`, { resultText: resultText.value, resultFileId: resultFileId.value }), '已保存小组成果')

function pickResult(id: number) {
  resultFileId.value = id
  saveResult()
}

function ratePeer(e: any) {
  if (!peerScore[e.studentId]) { ElMessage.warning('请先选择星级'); return }
  run(api.put(`${base}/peers/${e.studentId}`, { score: peerScore[e.studentId], comment: peerComment[e.studentId] }), '已评价')
}

function onFile(e: Event) { file.value = (e.target as HTMLInputElement).files?.[0] || null }

async function upload() {
  if (!file.value) { ElMessage.warning('请选择文件'); return }
  uploading.value = true
  try {
    apply(await api.upload(`${base}/files`, file.value, { title: upTitle.value, intro: upIntro.value }), false)
    uploadVisible.value = false
    upTitle.value = ''
    upIntro.value = ''
    file.value = null
    if (fileInput.value) fileInput.value.value = ''
  } catch (err) {
    ElMessage.error(errMsg(err, '上传失败'))
  } finally {
    uploading.value = false
  }
}

async function removeFile(f: any) {
  try {
    apply(await api.del(`${base}/files/${f.id}`), false)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function download(f: any) {
  try {
    await api.download(`/groups/files/${f.id}`, f.fileName)
  } catch (err) {
    ElMessage.error(errMsg(err, '下载失败'))
  }
}

useLessonChannel(props.lessonId, false, e => {
  if (e.type === 'GROUPS_CHANGED') {
    window.clearTimeout(timer)
    // 组长看到的输入框不被其他人的改动覆盖；组员则同步最新的主题/分工/成果
    timer = window.setTimeout(() => load(!data.value?.isLeader), 300)
  }
})
onMounted(() => load())
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
section { margin-top: 16px; }
.sec-title { font-weight: 600; margin-bottom: 8px; display: flex; align-items: center; }
.muted { color: #909399; font-size: 12px; font-weight: normal; }
.row { display: flex; gap: 8px; align-items: center; }
.topic { padding: 8px 12px; background: #f5f7fa; border-radius: 4px; }
.crown { color: #e6a23c; margin-right: 2px; }
.mt8 { margin-top: 8px; }
.ml8 { margin-left: 8px; }
.w240 { width: 240px; }
.file { display: flex; justify-content: space-between; gap: 8px; border: 1px solid #ebeef5; border-radius: 4px; padding: 8px 10px; margin-bottom: 6px; }
.file.result { border-color: #67c23a; background: #f0f9eb; }
.intro { font-size: 13px; color: #606266; margin-top: 2px; }
.ops { white-space: nowrap; }
.pre { white-space: pre-wrap; padding: 8px 12px; background: #f5f7fa; border-radius: 4px; }
.peer { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.p-name { width: 80px; }
.p-comment { max-width: 280px; }
</style>
