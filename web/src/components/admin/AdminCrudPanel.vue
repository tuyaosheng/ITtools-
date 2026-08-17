<template>
  <el-card class="admin-crud-panel" shadow="never">
    <template #header>
      <div class="admin-crud-panel__header">
        <h2 class="admin-crud-panel__title">{{ title }}</h2>
        <div class="admin-crud-panel__actions">
          <slot name="actions" />
        </div>
      </div>
    </template>

    <div v-if="$slots.filters" class="admin-crud-panel__filters">
      <slot name="filters" />
    </div>

    <div class="admin-crud-panel__body">
      <slot />
    </div>

    <el-dialog
      :model-value="dialogVisible"
      :title="dialogTitle"
      width="480px"
      append-to-body
      destroy-on-close
      @update:model-value="(v: boolean) => emit('update:dialogVisible', v)"
      @close="emit('dialog-cancel')"
    >
      <slot name="dialog" />
      <template #footer>
        <el-button @click="emit('dialog-cancel')">取消</el-button>
        <el-button type="primary" :loading="confirmLoading" @click="emit('dialog-confirm')">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ElCard, ElDialog, ElButton } from 'element-plus'

defineProps<{
  title: string
  dialogVisible: boolean
  dialogTitle: string
  confirmLoading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:dialogVisible', value: boolean): void
  (e: 'dialog-confirm'): void
  (e: 'dialog-cancel'): void
}>()
</script>

<style scoped>
.admin-crud-panel {
  --admin-gap: 16px;
}

.admin-crud-panel :deep(.el-card__header) {
  padding: 16px 20px;
}

.admin-crud-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--admin-gap);
}

.admin-crud-panel__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.admin-crud-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.admin-crud-panel__filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: var(--admin-gap);
  padding-bottom: var(--admin-gap);
  border-bottom: 1px solid #ebeef5;
}

.admin-crud-panel__body {
  min-height: 80px;
}
</style>
