<template>
  <div>
    <el-divider content-position="left">掌控板编程</el-divider>
    <el-form label-width="96px">
      <el-form-item label="关卡">
        <el-radio-group v-model="lesson">
          <el-radio value="熟悉课">熟悉课</el-radio>
          <el-radio value="公开课">公开课</el-radio>
          <el-radio value="全部">全部关卡</el-radio>
        </el-radio-group>
        <Hint>熟悉课：认识掌控板 + 温室巡检 5 关（第一次接触掌控板）。公开课：小夜灯、声控灯、智能门、骰子、汉堡店。上课时也可以在教师看板顶上切换。</Hint>
      </el-form-item>
      <el-form-item label="分组方式">
        <el-radio-group v-model="mode">
          <el-radio value="GROUP">按组（一组一块板）</el-radio>
          <el-radio value="PERSON">按人（一人一块板）</el-radio>
        </el-radio-group>
        <Hint>按组时同组同学共用一份积木和通关记录，组号取课堂分组；没分组的学生自动按人。</Hint>
      </el-form-item>
      <el-form-item>
        <el-button size="small" type="primary" :loading="saving" @click="save">保存设置</el-button>
        <el-button size="small" @click="editStarter">编辑初始积木（锁定 / 解锁）</el-button>
        <Hint>在新窗口里调整每一关学生打开时看到的积木，锁住的积木学生拖不动也删不掉。点那里的“存为初始积木”保存。</Hint>
      </el-form-item>
      <el-form-item label="学生机">
        <span class="muted">学生机浏览器需先配置一次才能连接掌控板</span>
        <Hint>在每台学生机上以管理员身份运行 ITtools 的 scripts/mpython/配置学生机浏览器.bat，填教师机地址即可（Edge 和 Chrome 都会配好）。</Hint>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import Hint from '../../components/Hint.vue'

const props = defineProps<{ page: any }>()
const lesson = ref('公开课')
const mode = ref('GROUP')
const saving = ref(false)

onMounted(async () => {
  const c = await api.get(`/teacher/mpy/pages/${props.page.id}/config`)
  lesson.value = c.lesson
  mode.value = c.mode
})

async function save() {
  saving.value = true
  try {
    const r = await api.post(`/teacher/mpy/pages/${props.page.id}/settings`, { lesson: lesson.value, mode: mode.value })
    if (r && r.ok === false) throw new Error(r.error)
    ElMessage.success('已保存')
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

function editStarter() {
  window.open(`/mpython/index.html?${new URLSearchParams({ page: String(props.page.id), role: 'teacher', edit: '1' })}`, '_blank', 'noopener')
}
</script>

<style scoped>
.muted { color: var(--el-text-color-secondary); font-size: 13px; }
</style>
