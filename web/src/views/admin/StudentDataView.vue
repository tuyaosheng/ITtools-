<template>
  <el-card shadow="never">
    <template #header>学生数据维护</template>
    <el-tabs v-model="tab">
      <el-tab-pane label="表格批量修改" name="edit">
        <div><Hint block>修改学生姓名、学籍号、学号、性别、班级（重新分班）、入学年份或在读/毕业状态：下载某个年级的学生信息表，在表格中修改后上传，先预览改动，确认无误再写入。“编号”列用于对应学生，请勿修改。</Hint></div>
        <div class="bar">
          <el-select v-model="yearId" placeholder="选择年级" class="w160">
            <el-option v-for="y in years" :key="y.id" :label="y.label" :value="y.id" />
          </el-select>
          <el-button :disabled="!yearId" @click="exportSheet">下载学生信息表</el-button>
          <el-upload :show-file-list="false" accept=".xlsx,.xls,.csv" :http-request="previewSheet">
            <el-button type="primary" :loading="busy">上传修改后的表格</el-button>
          </el-upload>
        </div>
        <template v-if="preview">
          <el-alert v-if="preview.errors.length" type="error" :closable="false" show-icon :title="`有 ${preview.errors.length} 处错误，修正后重新上传`" class="mb">
            <div v-for="(e, i) in preview.errors" :key="i">{{ e.row ? `第 ${e.row} 行：` : '' }}{{ e.message }}</div>
          </el-alert>
          <el-alert v-else-if="!preview.changes.length" type="info" :closable="false" show-icon title="表格中没有任何改动" class="mb" />
          <el-table v-if="preview.changes.length" :data="preview.changes" size="small" border max-height="420">
            <el-table-column prop="row" label="行" width="56" />
            <el-table-column prop="name" label="学生" width="100" />
            <el-table-column label="改动">
              <template #default="{ row }: { row: any }">
                <div v-for="(it, i) in row.items" :key="i" class="chg">
                  <b>{{ it.field }}</b>：<span class="old">{{ it.old || '（空）' }}</span> → <span class="new">{{ it.new || '（空）' }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="preview.changes.length && !preview.errors.length" class="bar mt">
            <el-popconfirm :title="`确认修改 ${preview.changes.length} 名学生的信息？`" width="240" @confirm="applySheet">
              <template #reference><el-button type="danger" :loading="busy">确认写入</el-button></template>
            </el-popconfirm>
            <span class="muted">共 {{ preview.rows }} 行，{{ preview.changes.length }} 名学生有改动</span>
          </div>
        </template>
      </el-tab-pane>

      <el-tab-pane label="批量导入头像" name="avatar">
        <div><Hint block>把学生照片按“学籍号”命名（教师照片按登录名命名），如 G330100200801010011.jpg，打包成 zip 上传。支持 JPG/PNG/GIF/BMP，系统会自动缩小保存。也可以在“用户管理”或教师的“我的班级”中逐个上传。</Hint></div>
        <el-upload :show-file-list="false" accept=".zip" :http-request="importAvatars">
          <el-button type="primary" :loading="busy">上传照片压缩包</el-button>
        </el-upload>
        <div v-if="avatarResult" class="mt">
          <el-alert type="success" :closable="false" :title="`成功导入 ${avatarResult.imported} 张；无效图片 ${avatarResult.invalid} 张；未匹配 ${avatarResult.unmatched.length} 张`" />
          <div v-if="avatarResult.unmatched.length" class="muted mt">未找到对应用户：{{ avatarResult.unmatched.join('、') }}</div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="毕业生管理" name="graduate">
        <div><Hint block>整班设为毕业后，学生不能再登录，也不再出现在登录名单、课堂和报表中。“释放资源空间”会删除所选班级毕业生的全部学习记录和文件（作品、网盘、测验、考勤等），只保留账号和精华作品，删除后无法恢复，建议先备份。</Hint></div>
        <div class="bar">
          <el-button :disabled="!selectedClasses.length" @click="setGraduated(true)">设为毕业</el-button>
          <el-button :disabled="!selectedClasses.length" @click="setGraduated(false)">恢复在读</el-button>
          <el-popconfirm title="删除所选班级毕业生的学习记录和文件（保留账号和精华作品）？不可恢复" width="300" @confirm="release">
            <template #reference><el-button type="danger" :disabled="!selectedClasses.length" :loading="busy">释放资源空间</el-button></template>
          </el-popconfirm>
          <span class="muted">已选 {{ selectedClasses.length }} 个班级</span>
        </div>
        <el-table :data="classes" size="small" border max-height="480" @selection-change="(v: any[]) => (selectedClasses = v)">
          <el-table-column type="selection" width="40" />
          <el-table-column prop="yearLabel" label="年级" width="110" />
          <el-table-column prop="name" label="班级" width="120" />
          <el-table-column prop="students" label="学生" width="70" />
          <el-table-column label="已毕业" width="90">
            <template #default="{ row }: { row: any }">
              <el-tag v-if="row.graduated && row.graduated === row.students" size="small" type="info">全部毕业</el-tag>
              <span v-else>{{ row.graduated }}</span>
            </template>
          </el-table-column>
          <el-table-column label="可释放空间">
            <template #default="{ row }: { row: any }">{{ row.releasableBytes ? size(row.releasableBytes) : '' }}</template>
          </el-table-column>
        </el-table>
        <el-alert v-if="releaseResult" class="mt" type="success" :closable="false"
          :title="`已处理 ${releaseResult.students} 名毕业生：删除记录 ${releaseResult.records} 条、文件 ${releaseResult.files} 个，释放 ${size(releaseResult.bytes)}`" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { api, errMsg } from '../../api/client'

const tab = ref('edit')
const years = ref<any[]>([])
const yearId = ref<number>()
const busy = ref(false)
const preview = ref<any>(null)
const lastFile = ref<File | null>(null)
const avatarResult = ref<any>(null)
const classes = ref<any[]>([])
const selectedClasses = ref<any[]>([])
const releaseResult = ref<any>(null)

const size = (b: number) => (b > 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`)

async function run<T>(fn: () => Promise<T>): Promise<T | undefined> {
  busy.value = true
  try {
    return await fn()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    busy.value = false
  }
}

const exportSheet = () => run(() => api.download('/admin/batch-edit/students/export', undefined, { yearId: yearId.value }))

async function previewSheet(o: UploadRequestOptions) {
  lastFile.value = o.file
  preview.value = (await run(() => api.upload('/admin/batch-edit/students/preview', o.file))) || null
}

async function applySheet() {
  if (!lastFile.value) return
  const r: any = await run(() => api.upload('/admin/batch-edit/students/apply', lastFile.value as File))
  if (r) {
    ElMessage.success(`已修改 ${r.applied} 名学生`)
    preview.value = null
  }
}

async function importAvatars(o: UploadRequestOptions) {
  avatarResult.value = (await run(() => api.upload('/admin/avatars/import', o.file))) || null
}

async function loadClasses() { classes.value = await api.get('/admin/graduation') }

async function setGraduated(g: boolean) {
  const n = await run(() => api.post<number>('/admin/graduation/classes', { classIds: selectedClasses.value.map(c => c.classId), graduated: g }))
  if (n !== undefined) {
    ElMessage.success(`已${g ? '设为毕业' : '恢复在读'} ${n} 名学生`)
    await loadClasses()
  }
}

async function release() {
  releaseResult.value = (await run(() => api.post('/admin/graduation/release', { classIds: selectedClasses.value.map(c => c.classId) }))) || null
  await loadClasses()
}

onMounted(async () => {
  years.value = await api.get('/admin/school-years')
  await loadClasses()
})
</script>

<style scoped>
.desc { color: var(--el-text-color-regular); font-size: 13px; line-height: 1.7; margin: 0 0 12px; }
.bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 10px; }
.w160 { width: 160px; }
.mb { margin-bottom: 10px; }
.mt { margin-top: 10px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.chg { font-size: 13px; }
.old { color: var(--el-text-color-secondary); text-decoration: line-through; }
.new { color: var(--el-color-success); font-weight: 600; }
</style>
