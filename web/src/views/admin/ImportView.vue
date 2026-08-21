<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, type ImportResult } from '../../api/admin'

type Kind = 'students' | 'teachers'

const studentFile = ref<File | null>(null)
const teacherFile = ref<File | null>(null)
const studentResult = ref<ImportResult | null>(null)
const teacherResult = ref<ImportResult | null>(null)
const studentBusy = ref(false)
const teacherBusy = ref(false)

function onStudentChange(uploadFile: any) {
  studentFile.value = uploadFile?.raw ?? null
}
function onTeacherChange(uploadFile: any) {
  teacherFile.value = uploadFile?.raw ?? null
}

async function downloadTemplate(kind: Kind) {
  try {
    if (kind === 'students') await adminApi.imports.downloadStudentTemplate()
    else await adminApi.imports.downloadTeacherTemplate()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '下载模板失败')
  }
}

async function doImport(kind: Kind) {
  const file = kind === 'students' ? studentFile.value : teacherFile.value
  if (!file) {
    ElMessage.warning('请先选择文件')
    return
  }
  const setBusy = (v: boolean) => (kind === 'students' ? (studentBusy.value = v) : (teacherBusy.value = v))
  setBusy(true)
  try {
    const result = kind === 'students'
      ? await adminApi.imports.students(file)
      : await adminApi.imports.teachers(file)
    if (kind === 'students') studentResult.value = result
    else teacherResult.value = result
    ElMessage.success(`导入完成：成功 ${result.imported} 条，失败 ${result.failed} 条`)
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '导入失败')
  } finally {
    setBusy(false)
  }
}
</script>

<template>
  <div class="import-view">
    <!-- 学生导入 -->
    <el-card class="import-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">学生导入</span>
          <el-button data-test="dl-student-template" size="small" @click="downloadTemplate('students')">
            下载模板
          </el-button>
        </div>
      </template>
      <p class="hint">
        支持 .xlsx / .xls / .csv。列：<b>姓名*</b>、学号、学籍号、<b>年级码*</b>(如 2024)、<b>班级*</b>(如 1班)、初始密码(默认 123456)。
        年级/班级不存在时自动创建；带 * 为必填。
      </p>
      <el-upload
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls,.csv"
        :on-change="onStudentChange"
        :on-exceed="() => ElMessage.warning('一次只能选择一个文件')"
      >
        <el-button type="primary" plain>选择文件</el-button>
      </el-upload>
      <div class="actions">
        <el-button type="primary" :loading="studentBusy" @click="doImport('students')">开始导入</el-button>
      </div>
      <div v-if="studentResult" class="result">
        <el-alert
          :title="`共 ${studentResult.total} 行，成功 ${studentResult.imported}，失败 ${studentResult.failed}`"
          :type="studentResult.failed > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
        />
        <el-table v-if="studentResult.errors.length" :data="studentResult.errors" size="small" border class="err-table">
          <el-table-column prop="row" label="行号" width="90" />
          <el-table-column prop="message" label="错误原因" />
        </el-table>
      </div>
    </el-card>

    <!-- 教师导入 -->
    <el-card class="import-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">教师导入</span>
          <el-button data-test="dl-teacher-template" size="small" @click="downloadTemplate('teachers')">
            下载模板
          </el-button>
        </div>
      </template>
      <p class="hint">
        支持 .xlsx / .xls / .csv。列：<b>姓名*</b>、<b>登录名*</b>、初始密码(默认 123456)。带 * 为必填，登录名不可重复。
      </p>
      <el-upload
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls,.csv"
        :on-change="onTeacherChange"
        :on-exceed="() => ElMessage.warning('一次只能选择一个文件')"
      >
        <el-button type="primary" plain>选择文件</el-button>
      </el-upload>
      <div class="actions">
        <el-button type="primary" :loading="teacherBusy" @click="doImport('teachers')">开始导入</el-button>
      </div>
      <div v-if="teacherResult" class="result">
        <el-alert
          :title="`共 ${teacherResult.total} 行，成功 ${teacherResult.imported}，失败 ${teacherResult.failed}`"
          :type="teacherResult.failed > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
        />
        <el-table v-if="teacherResult.errors.length" :data="teacherResult.errors" size="small" border class="err-table">
          <el-table-column prop="row" label="行号" width="90" />
          <el-table-column prop="message" label="错误原因" />
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.import-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 820px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-header .title {
  font-weight: 600;
}
.hint {
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
  line-height: 1.6;
  margin: 0 0 12px;
}
.actions {
  margin-top: 12px;
}
.result {
  margin-top: 16px;
}
.err-table {
  margin-top: 12px;
}
</style>
