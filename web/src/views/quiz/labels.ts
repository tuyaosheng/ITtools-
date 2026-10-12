/** 测验/课后作业的状态与方式文字（教师、学生端共用） */
export const modeLabel: Record<string, string> = { CONTROL: '控制模式', AUTO: '自动模式', INSTANT: '提交即批改' }
export const kindLabel: Record<string, string> = { TEST: '测验', HOMEWORK: '课后作业' }

export function testStatus(t: any): { text: string; type: string } {
  if (t.finalizedAt) return { text: '已结分', type: 'info' }
  if (t.status === 'STANDBY') return { text: '待机', type: 'warning' }
  if (t.status === 'OPEN') return { text: t.mode === 'CONTROL' ? '考试中' : '回收中', type: 'success' }
  return { text: t.kind === 'HOMEWORK' ? '停止回收（待结分）' : '已结束', type: '' }
}

export const attemptLabel: Record<string, string> = { SUBMITTED: '已交', IN_PROGRESS: '作答中', RETURNED: '发还重改' }
export const attemptTag: Record<string, string> = { SUBMITTED: 'success', IN_PROGRESS: 'warning', RETURNED: 'danger' }
