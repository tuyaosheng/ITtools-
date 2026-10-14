<template>
  <el-card shadow="never">
    <el-tabs v-model="tab">
      <el-tab-pane label="待批阅作品" name="pending">
        <el-empty v-if="!pending.length" description="暂无学生作品" />
        <el-table v-else :data="pending" stripe>
          <el-table-column label="课程" min-width="160"><template #default="{ row }: { row: any }">《{{ row.courseName }}》</template></el-table-column>
          <el-table-column prop="title" label="作品页" min-width="120" />
          <el-table-column prop="className" label="班级" width="120" />
          <el-table-column label="上课时间" width="150"><template #default="{ row }: { row: any }">{{ fmtTime(row.startedAt) }}</template></el-table-column>
          <el-table-column prop="submitted" label="已交" width="70" />
          <el-table-column label="待批阅" width="80">
            <template #default="{ row }: { row: any }"><el-tag :type="row.ungraded ? 'danger' : 'success'" size="small">{{ row.ungraded }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }: { row: any }"><el-button link type="primary" @click="open(row)">批阅</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="课后操作题" name="practice" lazy>
        <OpPracticeReport />
      </el-tab-pane>
      <el-tab-pane label="精华作品" name="showcase" lazy>
        <ShowcaseList />
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="visible" :title="current ? `《${current.courseName}》${current.title} · ${current.className}` : ''" width="90%" top="4vh" append-to-body @closed="load">
      <TeacherHomeworkPanel v-if="current" :lesson-id="current.lessonId" :page="{ id: current.pageId }" />
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import OpPracticeReport from './OpPracticeReport.vue'
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import { fmtTime } from '../../utils/format'
import TeacherHomeworkPanel from './TeacherHomeworkPanel.vue'
import ShowcaseList from './ShowcaseList.vue'

const tab = ref('pending')
const pending = ref<any[]>([])
const visible = ref(false)
const current = ref<any>(null)

async function load() { pending.value = await api.get('/teacher/homework/pending') }
function open(row: any) { current.value = row; visible.value = true }
onMounted(load)
</script>
