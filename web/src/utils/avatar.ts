import { ref } from 'vue'

/** 头像缓存版本：上传/删除头像后自增，所有 UserAvatar 重新加载图片 */
export const avatarVersion = ref(Date.now())

export function refreshAvatars() {
  avatarVersion.value = Date.now()
}
