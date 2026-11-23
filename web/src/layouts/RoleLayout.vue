<template>
  <el-container class="role-layout">
    <el-aside v-if="!isMobile" width="210px" class="role-layout__aside">
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

    <!-- 手机：菜单收进左侧抽屉 -->
    <el-drawer v-else v-model="drawer" direction="ltr" size="78%" :with-header="false" class="role-drawer" append-to-body>
      <div class="role-layout__brand">
        <span class="brand-logo">IT</span>
        <div>
          <div class="brand-title">{{ brand }}</div>
          <div class="brand-sub">{{ schoolName }}</div>
        </div>
      </div>
      <el-menu :default-active="activePath" class="role-layout__menu" router @select="drawer = false">
        <el-menu-item v-for="item in menu" :key="item.path" :index="item.path">
          <span v-if="item.icon" class="menu-ic">{{ item.icon }}</span>
          <span class="menu-label">{{ item.label }}</span>
          <el-badge v-if="item.badge" :value="item.badge" class="menu-badge" />
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <el-container class="role-layout__body">
      <el-header class="role-layout__header" :class="{ mobile: isMobile }">
        <button v-if="isMobile" class="burger" aria-label="菜单" @click="drawer = true">
          <span /><span /><span />
          <i v-if="badgeSum" class="dot" />
        </button>
        <span v-if="isMobile" class="m-title">{{ currentLabel || brand }}</span>
        <slot name="header-left" />
        <div class="spacer" />
        <div v-if="!isMobile" class="user">
          <UserAvatar :user-id="auth.user?.userId" :name="auth.user?.displayName || auth.user?.name" :size="28" class="avatar" @click="profileVisible = true" />
          <span class="name">{{ auth.user?.displayName || auth.user?.name }}</span>
          <span v-if="extraInfo" class="extra">{{ extraInfo }}</span>
          <el-button link @click="profileVisible = true">个人资料</el-button>
          <el-button link @click="pwdVisible = true">修改密码</el-button>
          <el-button link @click="logout">退出</el-button>
        </div>
        <el-dropdown v-else trigger="click" @command="onUserCmd">
          <UserAvatar :user-id="auth.user?.userId" :name="auth.user?.displayName || auth.user?.name" :size="32" class="avatar" />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ auth.user?.displayName || auth.user?.name }}{{ extraInfo ? ' · ' + extraInfo : '' }}</el-dropdown-item>
              <el-dropdown-item command="profile" divided>个人资料</el-dropdown-item>
              <el-dropdown-item command="pwd">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="role-layout__main" :class="{ mobile: isMobile }">
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
import { useIsMobile } from '../composables/useIsMobile'
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
const isMobile = useIsMobile()
const drawer = ref(false)

const activePath = computed(() => {
  const m = props.menu.map(i => i.path).filter(p => route.path === p || route.path.startsWith(p + '/'))
  return m.sort((a, b) => b.length - a.length)[0] || route.path
})
const currentLabel = computed(() => props.menu.find(i => i.path === activePath.value)?.label)
const badgeSum = computed(() => props.menu.some(i => i.badge))

async function logout() {
  await auth.logout()
  router.push('/login')
}

function onUserCmd(c: string) {
  if (c === 'profile') profileVisible.value = true
  else if (c === 'pwd') pwdVisible.value = true
  else if (c === 'logout') logout()
}

onMounted(async () => {
  try {
    schoolName.value = (await api.get('/public/settings')).schoolName
    if (schoolName.value) document.title = schoolName.value
  } catch {
    /* ignore */
  }
})
</script>

<style scoped>
.role-layout { min-height: 100vh; }
.role-layout__body { min-width: 0; }
.role-layout__aside { background: var(--hb-paper); border-right: 2px dashed var(--hb-line); display: flex; flex-direction: column; }
.role-layout__brand { padding: 16px 16px 14px; display: flex; align-items: center; gap: 10px; border-bottom: 2px dashed var(--hb-line); }
.brand-logo {
  flex: none; width: 36px; height: 36px; border-radius: 50%; display: grid; place-items: center;
  background: var(--el-color-primary); color: #fff; font-weight: 800; transform: rotate(-8deg);
  border: 2px solid var(--hb-ink); box-shadow: 2px 2px 0 var(--hb-ink);
}
.brand-title { font-family: var(--hb-title-font); color: var(--hb-ink); font-weight: normal; font-size: 19px; line-height: 1.2; }
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
.role-layout__header.mobile { position: sticky; top: 0; z-index: 20; height: 52px; padding: 0 12px; gap: 10px; }
.burger { position: relative; flex: none; width: 38px; height: 38px; border: 1.5px solid var(--hb-ink); border-radius: 10px; background: var(--hb-paper);
  box-shadow: 2px 2px 0 var(--hb-line); display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 4px; padding: 0; cursor: pointer; }
.burger span { width: 18px; height: 2px; border-radius: 2px; background: var(--hb-ink); }
.burger .dot { position: absolute; top: -3px; right: -3px; width: 9px; height: 9px; border-radius: 50%; background: var(--el-color-danger); }
.m-title { font-family: var(--hb-title-font); font-size: 19px; color: var(--hb-ink-deep); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.spacer { flex: 1; }
.user { display: flex; align-items: center; gap: 10px; }
.name { font-weight: 700; color: var(--hb-ink-deep); }
.extra { font-size: 12px; color: var(--el-text-color-secondary); background: var(--el-fill-color); padding: 2px 8px; border-radius: 10px; }
.role-layout__main { background: transparent; padding: 22px; }
.role-layout__main.mobile { padding: 12px 10px 24px; overflow-x: hidden; }
.avatar { cursor: pointer; margin-right: 6px; }
</style>

<style>
.role-drawer .el-drawer__body { padding: 0; background: var(--hb-paper); }
</style>
