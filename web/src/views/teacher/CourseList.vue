<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <el-radio-group v-model="tab" @change="load">
          <el-radio-button value="mine">我的课程</el-radio-button>
          <el-radio-button value="shared">共享课程</el-radio-button>
        </el-radio-group>
        <div class="ops">
          <el-select v-if="tab === 'mine'" v-model="termId" clearable placeholder="全部学期" size="default" @change="load">
            <el-option v-for="t in terms" :key="t.id" :label="t.label" :value="t.id" />
          </el-select>
          <el-button v-if="tab === 'mine'" type="primary" @click="openCreate">新建课程</el-button>
        </div>
      </div>
    </template>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="课程名称" min-width="200">
        <template #default="{ row }: { row: any }">
          <el-link type="primary" @click="router.push(`/teacher/courses/${row.id}`)">《{{ row.name }}》</el-link>
          <el-tag v-if="row.shared && tab === 'mine'" size="small" class="ml">共享</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="termLabel" label="学期" width="170" />
      <el-table-column v-if="tab === 'shared'" prop="teacherName" label="教师" width="100" />
      <el-table-column prop="pageCount" label="页面数" width="80" />
      <el-table-column v-if="tab === 'mine'" prop="lessonCount" label="开课次数" width="90" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }: { row: any }">
          <template v-if="tab === 'mine'">
            <el-button link type="primary" @click="startCourse = row.id; startVisible = true">开课</el-button>
            <el-button link type="primary" @click="openEdit(row)">属性</el-button>
            <el-button link type="primary" @click="copy(row)">复制</el-button>
            <el-popconfirm title="删除该课程？" @confirm="remove(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
          <template v-else>
            <el-button link type="primary" @click="router.push(`/teacher/courses/${row.id}`)">查看</el-button>
            <el-button link type="primary" @click="copy(row)">复制到我的课程</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editingId ? '课程属性' : '新建课程'" width="480px" append-to-body>
      <el-form :model="form" label-width="80px">
        <el-form-item label="课程名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" placeholder="如 网络基础、Python" /></el-form-item>
        <el-form-item label="学期">
          <el-select v-model="form.termId" clearable class="full">
            <el-option v-for="t in terms" :key="t.id" :label="t.label" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="共享"><el-switch v-model="form.shared" /><span class="tip">共享后其他教师可查看并复制</span></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
    <StartLessonDialog v-model="startVisible" :course-id="startCourse" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import StartLessonDialog from '../../components/StartLessonDialog.vue'

const router = useRouter()
const tab = ref('mine')
const list = ref<any[]>([])
const terms = ref<any[]>([])
const termId = ref<number | undefined>()
const loading = ref(false)
const dialog = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<any>({ name: '', category: '', termId: undefined, description: '', shared: false })
const startVisible = ref(false)
const startCourse = ref<number>()

async function load() {
  loading.value = true
  try {
    list.value = tab.value === 'mine'
      ? await api.get('/teacher/courses', termId.value ? { termId: termId.value } : undefined)
      : await api.get('/teacher/courses/shared')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', category: '', termId: terms.value.find(t => t.current)?.id, description: '', shared: false })
  dialog.value = true
}

function openEdit(row: any) {
  editingId.value = row.id
  Object.assign(form, { name: row.name, category: row.category, termId: row.termId, description: row.description, shared: row.shared })
  dialog.value = true
}

async function submit() {
  try {
    if (editingId.value) {
      await api.put(`/teacher/courses/${editingId.value}`, form)
    } else {
      const c = await api.post('/teacher/courses', form)
      dialog.value = false
      router.push(`/teacher/courses/${c.id}`)
      return
    }
    dialog.value = false
    ElMessage.success('已保存')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function copy(row: any) {
  try {
    const c = await api.post(`/teacher/courses/${row.id}/copy`)
    ElMessage.success('已复制为：' + c.name)
    tab.value = 'mine'
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function remove(row: any) {
  try {
    await api.del(`/teacher/courses/${row.id}`)
    ElMessage.success('已删除')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => {
  terms.value = await api.get('/teacher/terms')
  await load()
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.ops { display: flex; gap: 8px; }
.ml { margin-left: 6px; }
.full { width: 100%; }
.tip { margin-left: 10px; font-size: 12px; color: #909399; }
</style>
