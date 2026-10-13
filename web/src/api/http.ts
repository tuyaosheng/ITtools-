import axios from 'axios'
import { ElMessageBox } from 'element-plus'

function cookie(name: string): string | undefined {
  return document.cookie.split('; ').find(c => c.startsWith(name + '='))?.split('=')[1]
}

export const http = axios.create({ baseURL: '/api', withCredentials: true })

http.interceptors.request.use(cfg => {
  const method = (cfg.method || 'get').toLowerCase()
  if (['post', 'put', 'delete', 'patch'].includes(method)) {
    const token = cookie('XSRF-TOKEN')
    if (token) {
      cfg.headers = cfg.headers ?? {}
      cfg.headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
    }
  }
  return cfg
})

// 被“重复登录”踢下线：提示一次后回到登录页
let kickedShown = false
http.interceptors.response.use(r => r, err => {
  const d = err?.response?.data
  if (err?.response?.status === 401 && d?.data?.kicked && !kickedShown) {
    kickedShown = true
    ElMessageBox.alert(d.message || '你的账号已在其他电脑登录', '已下线', { type: 'warning', confirmButtonText: '重新登录' })
      .finally(() => { window.location.href = '/login' })
  }
  return Promise.reject(err)
})
