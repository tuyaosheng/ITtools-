<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>测验管理</span>
        <el-button type="primary" @click="publishVisible = true">发布测验</el-button>
      </div>
    </template>
    <el-table :data="tests" stripe>
      <el-table-column prop="title" label="测验" min-width="180" />
      <el-table-column prop="className" label="班级" width="120" />
      <el-table-column label="来源" width="90"><template #default="{ row }: { row: any }">{{ row.lessonId ? '课堂测验' : '独立测验' }}</template></el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }: { row: any }"><el-tag :type="row.status === 'OPEN' ? 'success' : 'info'" size="small">{{ row.status === 'OPEN' ? '回收中' : '已停止' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="已交" width="90"><template #default="{ row }: { row: any }">{{ row.submittedCount }}/{{ row.studentCount }}</template></el-table-column>
      <el-table-column prop="avgScore" label="平均分" width="80" />
      <el-table-column prop="totalScore" label="满分" width="70" />
      <el-table-column label="发布时间" width="150"><template #default="{ row }: { row: any }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }: { row: any }">
          <el-button link type="primary" @click="open(row.id)">查看</el-button>
          <el-popconfirm v-if="!row.lessonId" title="删除该测验及所有答卷？" @confirm="remove(row)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <el-drawer v-model="detailVisible" size="70%" title="测验详情" @closed="load">
      <TestDetail v-if="openId" :key="openId" :load-url="`/teacher/quiz/tests/${openId}`" />
    </el-drawer>
    <PublishTestDialog v-model="publishVisible" @published="(t: any) => { load(); open(t.id) }" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'
import TestDetail from './TestDetail.vue'
import PublishTestDialog from './PublishTestDialog.vue'

const route = useRoute()
const tests = ref<any[]>([])
const detailVisible = ref(false)
const openId = ref<number | null>(null)
const publishVisible = ref(false)

async function load() { tests.value = await api.get('/teacher/quiz/tests') }
function open(id: number) { openId.value = id; detailVisible.value = true }
async function remove(row: any) {
  try { await api.del(`/teacher/quiz/tests/${row.id}`); await load() } catch (err) { ElMessage.error(errMsg(err)) }
}

onMounted(async () => {
  await load()
  if (route.query.open) open(Number(route.query.open))
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
</style>
