import { defineStore } from 'pinia'
import { api } from '../api/client'

/** 菜单角标：未读系统消息、待答复问题（教师）。布局组件定时刷新，阅读/答复后各页面主动调用 refresh。 */
export const useNoticeStore = defineStore('notices', {
  state: () => ({ unreadMessages: 0, pendingQuestions: 0 }),
  actions: {
    async refresh() {
      try {
        const s = await api.get('/notices/summary')
        this.unreadMessages = s.unreadMessages
        this.pendingQuestions = s.pendingQuestions
      } catch {
        /* 未登录或网络问题时忽略 */
      }
    }
  }
})
