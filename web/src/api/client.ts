import { http } from './http'

/**
 * 子项目 1-7 通用的轻量 API 调用封装：自动解包 ApiResponse.data。
 * 错误提示统一取后端 message（见 errMsg）。
 */
export const api = {
  get: <T = any>(url: string, params?: Record<string, unknown>): Promise<T> =>
    http.get(url, { params }).then(r => r.data.data),
  post: <T = any>(url: string, body?: unknown, params?: Record<string, unknown>): Promise<T> =>
    http.post(url, body ?? {}, { params }).then(r => r.data.data),
  put: <T = any>(url: string, body?: unknown): Promise<T> => http.put(url, body ?? {}).then(r => r.data.data),
  del: <T = any>(url: string): Promise<T> => http.delete(url).then(r => r.data.data),
  upload: <T = any>(url: string, file: File, extra?: Record<string, string | number | boolean>): Promise<T> => {
    const fd = new FormData()
    fd.append('file', file)
    if (extra) Object.entries(extra).forEach(([k, v]) => fd.append(k, String(v)))
    return http.post(url, fd).then(r => r.data.data)
  },
  /** 以 blob 下载后端文件并触发浏览器保存 */
  download: async (url: string, filename?: string, params?: Record<string, unknown>): Promise<void> => {
    const resp = await http.get(url, { params, responseType: 'blob' })
    let name = filename
    const cd = resp.headers['content-disposition'] as string | undefined
    if (!name && cd) {
      const m = /filename\*=UTF-8''([^;]+)/i.exec(cd) || /filename="?([^";]+)"?/i.exec(cd)
      if (m) name = decodeURIComponent(m[1])
    }
    const blobUrl = window.URL.createObjectURL(resp.data as Blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = name || 'download'
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(blobUrl)
  }
}

export function errMsg(err: any, fallback = '操作失败'): string {
  return err?.response?.data?.message || fallback
}

/** 后端文件下载/预览地址（同源，带会话 cookie） */
export function fileUrl(path: string): string {
  return '/api' + path
}
