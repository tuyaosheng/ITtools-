<template>
  <div>
    <el-divider content-position="left">考试设置</el-divider>
    <el-form-item label="考试方式">
      <el-radio-group v-model="cfg.mode">
        <el-radio value="CONTROL">控制模式</el-radio>
        <el-radio value="AUTO">自动模式</el-radio>
        <el-radio value="INSTANT">提交即批改</el-radio>
      </el-radio-group>
      <div class="muted full">{{ modeHelp[cfg.mode] }}</div>
    </el-form-item>
    <el-form-item label="限时">
      <el-input-number v-model="cfg.timeLimit" :min="0" :max="300" />
      <span class="muted ml">分钟，0 为不限时</span>
    </el-form-item>
    <el-form-item label="公布答案">
      <el-switch v-model="cfg.showAnswer" />
      <span class="muted ml">结束后（提交即批改为交卷后）向学生显示标准答案与解析</span>
    </el-form-item>
    <el-form-item label="每人乱序">
        <el-checkbox v-model="cfg.shuffleQuestions">题目顺序</el-checkbox>
        <el-checkbox v-model="cfg.shuffleOptions">单选/多选的选项顺序</el-checkbox>
        <span class="muted ml">每个学生看到的顺序不同（同一学生每次打开不变），批改与统计自动按原题对应</span>
    </el-form-item>
    <el-form-item>
      <el-button size="small" type="primary" @click="save">保存考试设置</el-button>
      <span class="muted ml">对之后开课时新建的测验生效</span>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ page: any }>()
const cfg = reactive<any>({ mode: 'CONTROL', timeLimit: 0, showAnswer: true, shuffleQuestions: false, shuffleOptions: false })
const modeHelp: Record<string, string> = {
  CONTROL: '学生先看到“等待开考”，由老师在课堂中发送开考、结束指令（推荐）',
  AUTO: '页面开放后学生即可作答，老师停止回收后公布答案',
  INSTANT: '学生交卷后立即看到得分和答案，适合课堂练习'
}

async function load() {
  const c = await api.get(`/teacher/quiz/pages/${props.page.id}/config`)
  Object.assign(cfg, { mode: c.mode, timeLimit: c.timeLimit || 0, showAnswer: c.showAnswer, shuffleQuestions: !!c.shuffleQuestions, shuffleOptions: !!c.shuffleOptions })
}

async function save() {
  try {
    await api.put(`/teacher/quiz/pages/${props.page.id}/config`, { ...cfg, timeLimit: cfg.timeLimit || null })
    ElMessage.success('考试设置已保存')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

watch(() => props.page.id, load)
onMounted(load)
</script>

<style scoped>
.muted { color: #909399; font-size: 12px; }
.ml { margin-left: 8px; }
.full { width: 100%; }
</style>
