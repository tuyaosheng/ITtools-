<template>
  <div class="coming-soon-view">
    <el-empty description="敬请期待">
      <el-button @click="$router.push('/login')">返回登录</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.coming-soon-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
