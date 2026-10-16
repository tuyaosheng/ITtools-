<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="admin-layout__aside">
      <div class="admin-layout__brand"><span class="brand-logo">IT</span>管理后台</div>
      <el-menu
        :default-active="activePath"
        :default-openeds="menuGroups.map(g => g.title)"
        class="admin-layout__menu"
        router
      >
        <el-sub-menu v-for="g in menuGroups" :key="g.title" :index="g.title">
          <template #title><span>{{ g.title }}</span></template>
          <el-menu-item v-for="item in g.items" :key="item.path" :index="item.path">
            <span class="menu-label">{{ item.label }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="admin-layout__header">
        <div class="admin-layout__spacer" />
        <div class="admin-layout__user">
          <UserAvatar :user-id="auth.user?.userId" :name="auth.user?.displayName || auth.user?.name" :size="28" />
          <span class="admin-layout__username">{{ auth.user?.displayName || auth.user?.name }}</span>
          <el-button link @click="profileVisible = true">个人资料</el-button>
          <el-button link @click="pwdVisible = true">修改密码</el-button>
          <el-button link @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="admin-layout__main">
        <router-view />
      </el-main>
      <ProfileDialog v-model="profileVisible" />
      <ChangePasswordDialog v-model="pwdVisible" />
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElContainer, ElAside, ElHeader, ElMain, ElMenu, ElMenuItem, ElSubMenu, ElButton } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import ProfileDialog from '../components/ProfileDialog.vue'
import ChangePasswordDialog from '../components/ChangePasswordDialog.vue'
import UserAvatar from '../components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const profileVisible = ref(false)
const pwdVisible = ref(false)

const menuGroups = [
  {
    title: '师生与班级',
    items: [
      { path: '/admin/school-years', label: '年级管理' },
      { path: '/admin/classes', label: '班级管理' },
      { path: '/admin/users', label: '用户管理' },
      { path: '/admin/import', label: '批量导入' },
      { path: '/admin/student-data', label: '学生数据维护' }
    ]
  },
  {
    title: '机房管理',
    items: [
      { path: '/admin/lab-workstations', label: '机位管理' },
      { path: '/admin/lab-rooms', label: '机房模型' },
      { path: '/admin/lab-groups', label: '分组模型' },
      { path: '/admin/class-seats', label: '固定机位' }
    ]
  },
  {
    title: '系统',
    items: [
      { path: '/admin/teaching', label: '教学管理' },
      { path: '/admin/electives', label: '走班选课' },
      { path: '/admin/permissions', label: '权限管理' },
      { path: '/admin/settings', label: '系统设置' },
      { path: '/admin/tools', label: '应急工具' }
    ]
  }
]

const activePath = computed(() => route.path)

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout { min-height: 100vh; }
.admin-layout__aside { background: var(--hb-paper); border-right: 2px dashed var(--hb-line); display: flex; flex-direction: column; }
.admin-layout__brand {
  height: 58px; display: flex; align-items: center; gap: 10px; padding: 0 16px;
  font-family: var(--hb-title-font); font-size: 20px; font-weight: normal; color: var(--hb-ink);
  border-bottom: 2px dashed var(--hb-line);
}
.brand-logo {
  width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; font-family: var(--el-font-family);
  background: var(--el-color-primary); color: #fff; font-size: 13px; font-weight: 800; transform: rotate(-8deg);
  border: 2px solid var(--hb-ink); box-shadow: 2px 2px 0 var(--hb-ink);
}
.admin-layout__menu {
  --el-menu-bg-color: transparent; --el-menu-hover-bg-color: #f6eddb; --el-menu-text-color: var(--hb-ink);
  --el-menu-active-color: var(--hb-ink-deep); --el-menu-item-height: 40px; --el-menu-sub-item-height: 40px;
  border-right: none; flex: 1; padding: 6px 8px; overflow-y: auto;
}
.admin-layout__menu :deep(.el-sub-menu__title) { color: var(--el-text-color-secondary); font-size: 13px; height: 38px; }
.admin-layout__menu :deep(.el-menu-item) { border-radius: 6px; }
.admin-layout__menu :deep(.el-menu-item.is-active) { font-weight: 700; background: transparent; }
.admin-layout__menu .el-menu-item.is-active .menu-label { background: linear-gradient(transparent 55%, var(--hb-marker) 55%); padding: 0 3px; }
.admin-layout__header {
  height: 58px; display: flex; align-items: center; justify-content: flex-end;
  background: var(--hb-paper); border-bottom: 2px dashed var(--hb-line);
}
.admin-layout__spacer { flex: 1; }
.admin-layout__user { display: flex; align-items: center; gap: 12px; }
.admin-layout__username { color: var(--hb-ink-deep); font-weight: 700; }
.admin-layout__main { background: transparent; padding: 22px; }
</style>
