<template>
  <div>
    <div v-if="classes" class="bar">
      <el-select v-model="classId" placeholder="选择班级" class="w160" @change="load">
        <el-option v-for="k in classes" :key="k.id" :label="k.name" :value="k.id" />
      </el-select>
    </div>
    <el-empty v-if="loaded && !sites.length" description="还没有同学上传网站" :image-size="70" />
    <div class="grid">
      <el-card v-for="s in sites" :key="s.studentId" shadow="hover" class="card">
        <div class="t">{{ s.title }}</div>
        <div class="muted">{{ s.name }}<template v-if="s.studentNo"> · {{ s.studentNo }} 号</template> · {{ fmtTime(s.updatedAt) }}</div>
        <div class="ops">
          <el-button size="small" type="primary" tag="a" :href="s.url" target="_blank">打开</el-button>
          <el-popconfirm v-if="classes" title="删除该学生的网站（内容不当时使用）？" width="240" @confirm="remove(s)">
            <template #reference><el-button size="small" type="danger" plain>删除</el-button></template>
          </el-popconfirm>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'

/** 班级网站展示。传入 classes（教师端）时可选班级并删除不当站点；学生端只看本班。 */
const props = defineProps<{ classes?: any[] }>()
const classId = ref<number>()
const sites = ref<any[]>([])
const loaded = ref(false)

async function load() {
  if (props.classes && !classId.value) return
  sites.value = await api.get('/sites/class', classId.value ? { classId: classId.value } : undefined)
  loaded.value = true
}

async function remove(s: any) {
  try {
    await api.del(`/teacher/sites/${s.studentId}`)
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(load)
</script>

<style scoped>
.bar { margin-bottom: 10px; }
.w160 { width: 160px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.t { font-weight: 600; margin-bottom: 4px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.ops { margin-top: 8px; display: flex; gap: 6px; }
</style>
