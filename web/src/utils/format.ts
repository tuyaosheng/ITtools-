import DOMPurify from 'dompurify'

export function fmtTime(v?: string | null): string {
  if (!v) return ''
  const d = new Date(v)
  if (isNaN(d.getTime())) return String(v)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

export function fmtSize(bytes?: number | null): string {
  if (bytes == null) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

/** 教师编写的富文本在展示前统一消毒，防止 XSS（原系统 validateRequest=false 的问题） */
export function safeHtml(html?: string | null): string {
  if (!html) return ''
  // 纯文本（无标签）按段落换行展示
  if (!/<[a-z][\s\S]*>/i.test(html)) {
    return DOMPurify.sanitize(html.split(/\n/).map(l => `<p>${escapeHtml(l)}</p>`).join(''))
  }
  return DOMPurify.sanitize(html)
}

export function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string))
}

export const attendanceLabel: Record<string, string> = { PRESENT: '出席', ABSENT: '缺席', LEAVE: '请假' }
export const attendanceTag: Record<string, string> = { PRESENT: 'success', ABSENT: 'danger', LEAVE: 'warning' }
