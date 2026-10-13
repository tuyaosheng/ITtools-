<template>
  <div>
    <el-divider content-position="left">抽题题目</el-divider>
    <el-form-item label="说明">
      <span class="muted">学生点“随机抽取我的问题”后，按 学号 ÷ 题目数 的余数分到一道题（余 0 为最后一题），相邻座位的同学拿到不同题目。</span>
    </el-form-item>
    <el-form-item v-for="(q, i) in questions" :key="i" :label="`第 ${i + 1} 题`">
      <div class="row">
        <el-input v-model="questions[i]" type="textarea" :rows="2" maxlength="2000" />
        <el-button size="small" link type="danger" :disabled="questions.length <= 1" @click="questions.splice(i, 1)">删除</el-button>
      </div>
    </el-form-item>
    <el-form-item>
      <el-button size="small" :disabled="questions.length >= 60" @click="questions.push('')">+ 添加题目</el-button>
      <el-button size="small" type="primary" @click="save">保存抽题题目</el-button>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ page: any }>()
const questions = ref<string[]>([''])

async function load() {
  const c = await api.get(`/teacher/activity/pages/${props.page.id}/config`)
  questions.value = c.questions?.length ? [...c.questions] : ['']
}

async function save() {
  try {
    await api.put(`/teacher/activity/pages/${props.page.id}/questions`, { questions: questions.value })
    ElMessage.success('抽题题目已保存')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(load)
watch(() => props.page.id, load)
</script>

<style scoped>
.row { display: flex; gap: 6px; align-items: flex-start; width: 100%; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
</style>
