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

onMounted(async () => { list.value = await api.get(`/teacher/homework/lessons/${props.lesson.id}/summary`) })
</script>
