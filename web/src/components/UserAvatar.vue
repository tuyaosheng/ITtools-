<template>
  <span class="ua" :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.45)}px` }" :title="name">
    <img v-if="userId && !failed" :src="src" alt="" @error="failed = true">
    <span v-else class="ua-initial">{{ (name || '?').slice(0, 1) }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { avatarVersion } from '../utils/avatar'

/** 用户头像：没有头像时显示姓名首字。头像更新后改 avatarVersion 刷新所有头像。 */
const props = withDefaults(defineProps<{ userId?: number; name?: string; size?: number }>(), { size: 32 })
const failed = ref(false)
const src = computed(() => `/api/users/${props.userId}/avatar?v=${avatarVersion.value}`)
watch(() => [props.userId, avatarVersion.value], () => { failed.value = false })
</script>

<style scoped>
.ua { display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; overflow: hidden; background: var(--el-color-primary-light-8); color: var(--el-color-primary); flex-shrink: 0; vertical-align: middle; }
.ua img { width: 100%; height: 100%; object-fit: cover; }
.ua-initial { font-weight: 600; }
</style>
