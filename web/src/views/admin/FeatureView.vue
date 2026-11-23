<template>
  <el-card shadow="never" v-loading="loading">
    <template #header>
      <div class="head">
        <span>功能开关</span>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </template>
    <Hint block>
      关闭后，对应角色的菜单入口会隐藏，直接打开网址也会被拦下；已有的数据不会删除，重新开启即可恢复。
      带「仅隐藏入口」的功能课堂里也会用到，所以只隐藏菜单，课堂内照常可用。保存后，用户刷新页面生效。
    </Hint>
    <el-tabs v-model="role">
      <el-tab-pane v-for="r in roles" :key="r.key" :label="r.label" :name="r.key">
        <div class="bar">
          <el-button size="small" @click="setAll(r.key, true)">全部开放</el-button>
          <el-button size="small" @click="setAll(r.key, false)">全部关闭</el-button>
          <span class="count">已开放 {{ openCount(r.key) }} / {{ data[r.key]?.features.length || 0 }}</span>
        </div>
        <div v-for="g in groups(r.key)" :key="g.name" class="group">
          <div class="gname">{{ g.name }}</div>
          <div class="items">
            <div v-for="f in g.items" :key="f.key" class="item" :class="{ off: isOff(r.key, f.key) }">
              <span class="label">{{ f.label }}</span>
              <el-tag v-if="!f.enforced" size="small" type="info" effect="plain">仅隐藏入口</el-tag>
              <span class="spacer" />
              <el-switch :model-value="!isOff(r.key, f.key)" @change="(v: any) => toggle(r.key, f.key, Boolean(v))" />
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

interface Feat { key: string; label: string; group: string; enforced: boolean }
interface RoleData { features: Feat[]; disabled: string[] }

const roles = [{ key: 'student', label: '学生' }, { key: 'teacher', label: '教师' }]
const role = ref('student')
const loading = ref(false)
const saving = ref(false)
const data = reactive<Record<string, RoleData>>({ student: { features: [], disabled: [] }, teacher: { features: [], disabled: [] } })

function apply(d: any) {
  for (const r of roles) data[r.key] = { features: d[r.key].features, disabled: [...d[r.key].disabled] }
}
async function load() {
  loading.value = true
  try {
    apply(await api.get('/admin/features'))
  } catch (e) {
    ElMessage.error(errMsg(e, '加载失败'))
  } finally {
    loading.value = false
  }
}
const isOff = (r: string, k: string) => data[r].disabled.includes(k)
const openCount = (r: string) => data[r].features.filter(f => !isOff(r, f.key)).length
function toggle(r: string, k: string, open: boolean) {
  const s = new Set(data[r].disabled)
  if (open) s.delete(k)
  else s.add(k)
  data[r].disabled = [...s]
}
function setAll(r: string, open: boolean) {
  data[r].disabled = open ? [] : data[r].features.map(f => f.key)
}
function groups(r: string) {
  const out: { name: string; items: Feat[] }[] = []
  for (const f of data[r].features) {
    let g = out.find(x => x.name === f.group)
    if (!g) out.push((g = { name: f.group, items: [] }))
    g.items.push(f)
  }
  return out
}
async function save() {
  saving.value = true
  try {
    apply(await api.put('/admin/features', { student: data.student.disabled, teacher: data.teacher.disabled }))
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error(errMsg(e, '保存失败'))
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; }
.bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.count { color: var(--el-text-color-secondary); font-size: 13px; }
.group { margin-bottom: 16px; }
.gname { font-weight: 700; margin-bottom: 8px; color: var(--hb-ink-deep); }
.items { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 10px; }
.item { display: flex; align-items: center; gap: 8px; padding: 10px 14px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--hb-paper); }
.item.off { opacity: 0.6; background: var(--el-fill-color-light); }
.label { font-weight: 600; }
.spacer { flex: 1; }
</style>
