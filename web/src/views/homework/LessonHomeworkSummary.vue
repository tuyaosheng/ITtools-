<template>
  <div>
    <el-empty v-if="!list.length" description="本课程没有作品提交页" />
    <el-table v-else :data="list" stripe>
      <el-table-column prop="title" label="作品页" />
      <el-table-column label="提交" width="110">
        <template #default="{ row }: { row: any }">
          <el-progress :percentage="row.studentCount ? Math.round(row.submitted * 100 / row.studentCount) : 0" :format="() => `${row.submitted}/${row.studentCount}`" />
        </template>
      </el-table-column>
      <el-table-column label="已批阅" width="90"><template #default="{ row }: { row: any }">{{ row.graded }}/{{ row.submitted }}</template></el-table-column>
      <el-table-column prop="avgScore" label="平均分" width="90" />
      <el-table-column prop="essence" label="精华" width="70" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }: { row: any }"><el-button link type="primary" @click="open(row)">批阅</el-button></template>
      </el-table-column>
    </el-table>
    <template v-if="groups.length">
      <el-divider content-position="left">按小组统计（只统计已进课的学生）</el-divider>
      <el-table :data="groups" size="small" border>
        <el-table-column prop="pageTitle" label="作品页" min-width="140" />
        <el-table-column label="小组" width="90"><template #default="{ row }: { row: any }">{{ row.groupNo ? `第 ${row.groupNo} 组` : '未分组' }}</template></el-table-column>
        <el-table-column label="已交 / 人数" width="110"><template #default="{ row }: { row: any }">{{ row.submitted }} / {{ row.members }}</template></el-table-column>
        <el-table-column prop="avgScore" label="平均分" width="90" />
        <el-table-column prop="essence" label="精华" width="70" />
      </el-table>
    </template>
    <el-dialog v-model="visible" :title="current?.title" width="90%" top="4vh" append-to-body>
      <TeacherHomeworkPanel v-if="current" :lesson-id="lesson.id" :page="{ id: current.pageId }" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import TeacherHomeworkPanel from './TeacherHomeworkPanel.vue'

const props = defineProps<{ lesson: any }>()
const list = ref<any[]>([])
const visible = ref(false)
const current = ref<any>(null)

function open(row: any) {
  current.value = row
  visible.value = true
}

const groups = ref<any[]>([])
onMounted(async () => {
  ;[list.value, groups.value] = await Promise.all([
    api.get(`/teacher/homework/lessons/${props.lesson.id}/summary`),
    api.get(`/teacher/homework/lessons/${props.lesson.id}/group-stats`)])
  // 全班都没分组时不显示按组统计
  if (groups.value.every((g: any) => !g.groupNo)) groups.value = []
})
</script>
