<template>
  <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
  <div v-else-if="!view" v-loading="true" class="loading" />
  <PythonWorkbench v-else :view="view" :base-path="base" @update="v => (view = v)" />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, errMsg } from '../../api/client'
import PythonWorkbench from './PythonWorkbench.vue'

const props = defineProps<{ lessonId: number; page: any }>()
const view = ref<any>(null)
const error = ref('')
const base = computed(() => `/student/python/lessons/${props.lessonId}/pages/${props.page.id}`)

onMounted(async () => {
  try {
    view.value = await api.get(base.value)
  } catch (err) {
    error.value = errMsg(err, 'Python 操作题暂不可用')
  }
})
</script>

<style scoped>
.loading { min-height: 200px; }
</style>
