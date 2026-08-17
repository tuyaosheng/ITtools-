import axios from 'axios'

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
