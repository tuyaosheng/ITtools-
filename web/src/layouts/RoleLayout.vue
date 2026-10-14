<template>
  <el-container class="role-layout">
    <el-aside width="210px" class="role-layout__aside">
      <div class="role-layout__brand">
        <span class="brand-logo">IT</span>
        <div>
          <div class="brand-title">{{ brand }}</div>
          <div class="brand-sub">{{ schoolName }}</div>
        </div>
      </div>
      <el-menu :default-active="activePath" class="role-layout__menu" router>
        <el-menu-item v-for="item in menu" :key="item.path" :index="item.path">
          <span v-if="item.icon" class="menu-ic">{{ item.icon }}</span>
          <span class="menu-label">{{ item.label }}</span>
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

export interface MenuItem { path: string; label: string; icon?: string; badge?: number | string }

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
.role-layout__aside { background: var(--hb-paper); border-right: 2px dashed var(--hb-line); display: flex; flex-direction: column; }
.role-layout__brand { padding: 16px 16px 14px; display: flex; align-items: center; gap: 10px; border-bottom: 2px dashed var(--hb-line); }
.brand-logo {
  flex: none; width: 36px; height: 36px; border-radius: 50%; display: grid; place-items: center;
  background: var(--el-color-primary); color: #fff; font-weight: 800; transform: rotate(-8deg);
  border: 2px solid var(--hb-ink); box-shadow: 2px 2px 0 var(--hb-ink);
}
.brand-title { font-family: var(--hb-title-font); color: var(--hb-ink); font-weight: 700; font-size: 18px; line-height: 1.2; }
.brand-sub { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 2px; }
.role-layout__menu {
  --el-menu-bg-color: transparent; --el-menu-hover-bg-color: #f6eddb; --el-menu-text-color: var(--hb-ink);
  --el-menu-active-color: var(--hb-ink-deep); --el-menu-item-height: 44px;
  border-right: none; flex: 1; padding: 8px 10px; overflow-y: auto;
}
.role-layout__menu .el-menu-item { border-radius: 6px; margin-bottom: 2px; font-size: 15px; }
.role-layout__menu .el-menu-item.is-active { font-weight: 700; background: transparent; }
.el-menu-item.is-active .menu-label { background: linear-gradient(transparent 55%, var(--hb-marker) 55%); padding: 0 3px; }
.menu-ic { width: 22px; margin-right: 8px; text-align: center; font-size: 16px; }
.menu-badge { margin-left: 8px; }
.role-layout__header {
  height: 58px; display: flex; align-items: center; gap: 12px;
  background: var(--hb-paper); border-bottom: 2px dashed var(--hb-line);
}
.spacer { flex: 1; }
.user { display: flex; align-items: center; gap: 10px; }
.name { font-weight: 700; color: var(--hb-ink-deep); }
.extra { font-size: 12px; color: var(--el-text-color-secondary); background: var(--el-fill-color); padding: 2px 8px; border-radius: 10px; }
.role-layout__main { background: transparent; padding: 22px; }
@media (max-width: 768px) {
  .role-layout__aside { width: 64px !important; }
  .brand-sub, .brand-title { display: none; }
}
.avatar { cursor: pointer; margin-right: 6px; }
</style>
