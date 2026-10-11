<template>
  <el-card shadow="never">
    <template #header>系统消息</template>
    <InboxView />
  </el-card>
</template>

<script setup lang="ts">
import InboxView from '../common/InboxView.vue'
</script>
