<template>
  <TeacherGroupManager :lesson-id="lesson.id" />
</template>

<script setup lang="ts">
import TeacherGroupManager from './TeacherGroupManager.vue'

defineProps<{ lesson: any }>()
</script>
