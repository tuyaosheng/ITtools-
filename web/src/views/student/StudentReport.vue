<template>
  <el-card shadow="never">
    <template #header>我的学情</template>
    <el-skeleton v-if="!data" :rows="5" animated />
    <StudentReportCard v-else :data="data" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import StudentReportCard from '../../components/StudentReportCard.vue'

const data = ref<any>(null)
onMounted(async () => {
  try {
    data.value = await api.get('/student/report')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
})
</script>
