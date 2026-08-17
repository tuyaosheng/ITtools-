<template>
  <AdminCrudPanel
    title="权限管理"
    dialog-title=""
    :dialog-visible="false"
    @update:dialog-visible="() => {}"
    @dialog-confirm="() => {}"
    @dialog-cancel="() => {}"
  >
    <template #actions>
      <span class="hint">全局启用/停用各权限（作用域：GLOBAL）</span>
    </template>

    <div v-loading="loading">
      <div v-for="group in groupedPermissions" :key="group.module" class="module-group">
        <h3 class="module-group__title">{{ group.module }}</h3>
        <el-table :data="group.items" stripe class="full-width">
          <el-table-column prop="code" label="权限代码" width="220" />
          <el-table-column prop="name" label="权限名称" min-width="200" />
          <el-table-column label="启用" width="100">
            <template #default="{ row }: { row: any }">
              <el-switch
                :model-value="enabledMap[row.id] ?? true"
                :loading="togglingId === row.id"
                @change="(v: string | number | boolean) => toggle(row, Boolean(v))"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </AdminCrudPanel>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElSwitch, ElMessage } from 'element-plus'
import AdminCrudPanel from '../../components/admin/AdminCrudPanel.vue'
import { adminApi, type PermissionItem } from '../../api/admin'

const permissions = ref<PermissionItem[]>([])
const loading = ref(false)
const togglingId = ref<number | null>(null)
// The list endpoint only returns the permission catalogue (id/code/name/
// module), not each permission's current GLOBAL module-permission state -
// there is no GET for /admin/module-permissions. Toggles therefore default
// to "enabled" until the admin flips one, at which point the PUT call is
// the source of truth going forward for this session.
const enabledMap = reactive<Record<number, boolean>>({})

const groupedPermissions = computed(() => {
  const byModule = new Map<string, PermissionItem[]>()
  for (const p of permissions.value) {
    const list = byModule.get(p.module) ?? []
    list.push(p)
    byModule.set(p.module, list)
  }
  return Array.from(byModule.entries()).map(([module, items]) => ({ module, items }))
})

async function load() {
  loading.value = true
  try {
    permissions.value = await adminApi.permissions.list()
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function toggle(row: PermissionItem, value: boolean) {
  togglingId.value = row.id
  try {
    await adminApi.permissions.upsertModulePermission({
      scope: 'GLOBAL',
      scopeRefId: null,
      permissionId: row.id,
      enabled: value
    })
    enabledMap[row.id] = value
    ElMessage.success('已更新')
  } finally {
    togglingId.value = null
  }
}
</script>

<style scoped>
.full-width {
  width: 100%;
}

.module-group {
  margin-bottom: 24px;
}

.module-group__title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.hint {
  color: #909399;
  font-size: 13px;
}
</style>
