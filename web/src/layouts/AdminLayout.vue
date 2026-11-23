<template>
  <el-container class="admin-layout">
    <component :is="isMobile ? ElDrawer : ElAside" v-bind="isMobile ? drawerProps : { width: '220px' }"
      v-model="drawer" class="admin-layout__aside">
      <div class="admin-layout__brand"><span class="brand-logo">IT</span>管理后台</div>
      <el-menu
        :default-active="activePath"
        :default-openeds="menuGroups.map(g => g.title)"
        class="admin-layout__menu"
        router
        @select="drawer = false"
      >
        <el-sub-menu v-for="g in menuGroups" :key="g.title" :index="g.title">
          <template #title><span>{{ g.title }}</span></template>
          <el-menu-item v-for="item in g.items" :key="item.path" :index="item.path">
            <span class="menu-label">{{ item.label }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </component>
    <el-container class="admin-layout__body">
      <el-header class="admin-layout__header" :class="{ mobile: isMobile }">
        <button v-if="isMobile" class="burger" aria-label="菜单" @click="drawer = true"><span /><span /><span /></button>
        <span v-if="isMobile" class="m-title">{{ currentLabel }}</span>
        <div class="admin-layout__spacer" />
        <el-dropdown v-if="isMobile" trigger="click" @command="onUserCmd">
          <UserAvatar :user-id="auth.user?.userId" :name="auth.user?.displayName || auth.user?.name" :size="32" />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ auth.user?.displayName || auth.user?.name }}</el-dropdown-item>
              <el-dropdown-item v-if="isTeacher" command="teacher" divided>← 返回教师端</el-dropdown-item>
              <el-dropdown-item command="profile" :divided="!isTeacher">个人资料</el-dropdown-item>
              <el-dropdown-item command="pwd">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <div v-else class="admin-layout__user">
          <el-button v-if="isTeacher" type="primary" plain size="small" @click="router.push('/teacher')">← 返回教师端</el-button>
          <UserAvatar :user-id="auth.user?.userId" :name="auth.user?.displayName || auth.user?.name" :size="28" />
          <span class="admin-layout__username">{{ auth.user?.displayName || auth.user?.name }}</span>
          <el-button link @click="profileVisible = true">个人资料</el-button>
          <el-button link @click="pwdVisible = true">修改密码</el-button>
          <el-button link @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="admin-layout__main" :class="{ mobile: isMobile }">
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
import { ElContainer, ElAside, ElDrawer, ElHeader, ElMain, ElMenu, ElMenuItem, ElSubMenu, ElButton } from 'element-plus'
import { useIsMobile } from '../composables/useIsMobile'
import { useAuthStore } from '../stores/auth'
import ProfileDialog from '../components/ProfileDialog.vue'
import ChangePasswordDialog from '../components/ChangePasswordDialog.vue'
import UserAvatar from '../components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const profileVisible = ref(false)
const pwdVisible = ref(false)
const isMobile = useIsMobile()
const drawer = ref(false)
const drawerProps = { direction: 'ltr', size: '78%', withHeader: false, appendToBody: true, class: 'admin-drawer' }

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
      { path: '/admin/features', label: '功能开关' },
      { path: '/admin/settings', label: '系统设置' },
      { path: '/admin/tools', label: '应急工具' }
    ]
  }
]

const activePath = computed(() => route.path)
const currentLabel = computed(() => menuGroups.flatMap(g => g.items).find(i => route.path.startsWith(i.path))?.label || '管理后台')

function onUserCmd(c: string) {
  if (c === 'profile') profileVisible.value = true
  else if (c === 'pwd') pwdVisible.value = true
  else if (c === 'logout') handleLogout()
  else if (c === 'teacher') router.push('/teacher')
}
// 兼任管理员的教师
const isTeacher = computed(() => auth.user?.role === 'TEACHER')

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
.admin-layout__main.mobile { padding: 12px 10px 24px; overflow-x: hidden; }
.admin-layout__body { min-width: 0; }
.admin-layout__header.mobile { position: sticky; top: 0; z-index: 20; height: 52px; padding: 0 12px; gap: 10px; justify-content: flex-start; }
.burger { flex: none; width: 38px; height: 38px; border: 1.5px solid var(--hb-ink); border-radius: 10px; background: var(--hb-paper);
  box-shadow: 2px 2px 0 var(--hb-line); display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 4px; padding: 0; cursor: pointer; }
.burger span { width: 18px; height: 2px; border-radius: 2px; background: var(--hb-ink); }
.m-title { font-family: var(--hb-title-font); font-size: 19px; color: var(--hb-ink-deep); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>

<style>
.admin-drawer .el-drawer__body { padding: 0; background: var(--hb-paper); }
</style>
