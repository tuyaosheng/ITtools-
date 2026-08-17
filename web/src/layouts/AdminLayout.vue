<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="admin-layout__aside">
      <div class="admin-layout__brand">ITtools 管理后台</div>
      <el-menu :default-active="activePath" class="admin-layout__menu" router>
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="admin-layout__header">
        <div class="admin-layout__spacer" />
        <div class="admin-layout__user">
          <span class="admin-layout__username">{{ auth.user?.name }}</span>
          <el-button link @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="admin-layout__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElContainer, ElAside, ElHeader, ElMain, ElMenu, ElMenuItem, ElButton } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const menuItems = [
  { path: '/admin/school-years', label: '年级管理' },
  { path: '/admin/classes', label: '班级管理' },
  { path: '/admin/users', label: '用户管理' },
  { path: '/admin/permissions', label: '权限管理' }
]

const activePath = computed(() => route.path)

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-layout__aside {
  background: #1f2937;
  color: #fff;
  display: flex;
  flex-direction: column;
}

.admin-layout__brand {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.admin-layout__menu {
  border-right: none;
  background: transparent;
  flex: 1;
}

.admin-layout__header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.admin-layout__spacer {
  flex: 1;
}

.admin-layout__user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-layout__username {
  color: #303133;
  font-weight: 500;
}

.admin-layout__main {
  background: #f5f7fa;
  padding: 20px;
}
</style>
