<template>
  <el-container class="role-layout">
    <el-aside width="210px" class="role-layout__aside">
      <div class="role-layout__brand">
        <div class="brand-title">{{ brand }}</div>
        <div class="brand-sub">{{ schoolName }}</div>
      </div>
      <el-menu :default-active="activePath" class="role-layout__menu" background-color="#1e293b"
        text-color="#cbd5e1" active-text-color="#ffffff" router>
        <el-menu-item v-for="item in menu" :key="item.path" :index="item.path">
          <span>{{ item.label }}</span>
          <el-badge v-if="item.badge" :value="item.badge" class="menu-badge" />
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="role-layout__header">
        <slot name="header-left" />
        <div class="spacer" />
        <div class="user">
          <UserAvatar :user-id="auth.user?.userId" :name="auth.user?.displayName || auth.user?.name" :size="28" class="avatar" @click="profileVisible = true" />
          <span class="name">{{ auth.user?.displayName || auth.user?.name }}</span>
          <span v-if="extraInfo" class="extra">{{ extraInfo }}</span>
          <el-button link @click="profileVisible = true">个人资料</el-button>
          <el-button link @click="pwdVisible = true">修改密码</el-button>
          <el-button link @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="role-layout__main">
        <router-view />
      </el-main>
    </el-container>
    <ChangePasswordDialog v-model="pwdVisible" />
    <ProfileDialog v-model="profileVisible" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { api } from '../api/client'
import ChangePasswordDialog from '../components/ChangePasswordDialog.vue'
import ProfileDialog from '../components/ProfileDialog.vue'
import UserAvatar from '../components/UserAvatar.vue'

export interface MenuItem { path: string; label: string; badge?: number | string }

const profileVisible = ref(false)
const props = defineProps<{ brand: string; menu: MenuItem[]; extraInfo?: string }>()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const pwdVisible = ref(false)
const schoolName = ref('')

const activePath = computed(() => {
  const m = props.menu.map(i => i.path).filter(p => route.path === p || route.path.startsWith(p + '/'))
  return m.sort((a, b) => b.length - a.length)[0] || route.path
})

async function logout() {
  await auth.logout()
  router.push('/login')
}

onMounted(async () => {
  try {
    schoolName.value = (await api.get('/public/settings')).schoolName
  } catch {
    /* ignore */
  }
})
</script>

<style scoped>
.role-layout { min-height: 100vh; }
.role-layout__aside { background: #1e293b; display: flex; flex-direction: column; }
.role-layout__brand { padding: 16px 20px; border-bottom: 1px solid rgba(255, 255, 255, 0.08); }
.brand-title { color: #fff; font-weight: 600; font-size: 16px; }
.brand-sub { color: #94a3b8; font-size: 12px; margin-top: 4px; }
.role-layout__menu { border-right: none; flex: 1; }
.menu-badge { margin-left: 8px; }
.role-layout__header { height: 56px; display: flex; align-items: center; background: #fff; border-bottom: 1px solid #ebeef5; gap: 12px; }
.spacer { flex: 1; }
.user { display: flex; align-items: center; gap: 10px; }
.name { font-weight: 600; color: #303133; }
.extra { font-size: 12px; color: #909399; }
.role-layout__main { background: #f5f7fa; padding: 20px; }
@media (max-width: 768px) {
  .role-layout__aside { width: 64px !important; }
  .brand-sub { display: none; }
}
.avatar { cursor: pointer; margin-right: 6px; }
</style>
