<template>
  <el-card shadow="never">
    <el-tabs v-model="tab">
      <el-tab-pane label="精华作品" name="list"><ShowcaseList /></el-tab-pane>
      <el-tab-pane label="排行榜" name="rank" lazy><EssenceRanking /></el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ShowcaseList from '../homework/ShowcaseList.vue'
import EssenceRanking from '../homework/EssenceRanking.vue'

const tab = ref('list')
</script>
