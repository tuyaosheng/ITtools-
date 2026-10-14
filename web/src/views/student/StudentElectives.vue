<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>走班选课</span>
        <el-button @click="load">刷新</el-button>
      </div>
    </template>

    <el-empty v-if="!list.length" description="当前没有面向你的选课活动" />
    <div v-for="a in list" :key="a.id" class="activity">
      <div class="a-head">
        <b>{{ a.name }}</b>
        <el-tag :type="a.status === 'OPEN' ? 'success' : 'info'" size="small">{{ a.status === 'OPEN' ? '选课中' : '已结束' }}</el-tag>
        <span v-if="mine(a)" class="chosen">已选：{{ mine(a).name }}</span>
        <span v-else class="muted">尚未选择</span>
      </div>
      <p v-if="a.description" class="muted pre">{{ a.description }}</p>
      <el-table :data="a.subjects" size="small" border>
        <el-table-column prop="name" label="科目" min-width="140" />
        <el-table-column prop="teacherName" label="任课教师" width="100" />
        <el-table-column prop="schedule" label="上课时间" width="160" />
        <el-table-column label="名额" width="110">
          <template #default="{ row }">
            <span :class="{ full: row.chosen >= row.capacity }">{{ row.chosen }} / {{ row.capacity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="简介" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <template v-if="a.status === 'OPEN'">
              <el-button v-if="a.mySubjectId === row.id" link type="danger" @click="choose(a, null)">退选</el-button>
              <el-button v-else link type="primary" :disabled="row.chosen >= row.capacity" @click="choose(a, row.id)">
                {{ a.mySubjectId ? '改选' : '选择' }}
              </el-button>
            </template>
            <el-tag v-else-if="a.mySubjectId === row.id" size="small" type="success">已选</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const list = ref<any[]>([])

function mine(a: any) {
  return a.subjects.find((s: any) => s.id === a.mySubjectId)
}

async function load() { list.value = await api.get('/student/electives') }

async function choose(a: any, subjectId: number | null) {
  try {
    await api.put(`/student/electives/${a.id}`, { subjectId })
    ElMessage.success(subjectId ? '选课成功' : '已退选')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
  await load()
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.pre { white-space: pre-wrap; }
.activity { margin-bottom: 20px; }
.a-head { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.chosen { color: var(--el-color-success); font-size: 13px; }
.full { color: var(--el-color-danger); }
</style>
