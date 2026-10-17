import { http } from './http'

const unwrap = <T>(p: Promise<any>): Promise<T> => p.then(r => r.data.data)

export interface SchoolYearItem {
  id: number
  yearCode: string
  label: string
  active: boolean
}

export interface SchoolYearBody {
  yearCode: string
  label: string
  active: boolean
}

export interface ClassItem {
  id: number
  schoolYearId: number
  yearLabel: string
  name: string
  displayOrder: number
}

export interface ClassBody {
  schoolYearId: number
  name: string
  displayOrder: number
}

export type UserRole = 'ADMIN' | 'TEACHER' | 'STUDENT'

export interface UserItem {
  id: number
  role: UserRole
  name: string
  loginName: string | null
  studentNo: string | null
  xjh: string | null
  classId: number | null
  className: string | null
  graduated: boolean
  enabled: boolean
  enrollYearId: number | null
}

export interface CreateUserBody {
  role: UserRole
  name: string
  loginName?: string | null
  studentNo?: string | null
  xjh?: string | null
  classId?: number | null
  enrollYearId?: number | null
  password: string
}

/**
 * FULL-REPLACE semantics on PUT /admin/users/{id}: the backend's
 * UpdateUserCommand overwrites classId/enrollYearId unconditionally, so
 * omitting either field CLEARS it. Callers must always send the complete
 * object.
 */
export interface UpdateUserBody {
  name: string
  loginName?: string | null
  studentNo?: string | null
  xjh?: string | null
  classId?: number | null
  enrollYearId?: number | null
}

export interface UserListParams {
  role?: string
  classId?: number
}

export interface UserPageParams {
  role?: string
  classId?: number
  yearId?: number
  keyword?: string
  page: number
  size: number
}

export interface UserPage {
  items: UserItem[]
  total: number
}

export interface PermissionItem {
  id: number
  code: string
  name: string
  module: string
}

export interface ModulePermissionBody {
  scope: string
  scopeRefId: number | null
  permissionId: number
  enabled: boolean
}

export interface LabWorkstationItem {
  id: number
  pcName: string
  ipAddress: string
}

export interface LabWorkstationBody {
  pcName: string
  ipAddress: string
}

export interface ImportRowError {
  row: number
  message: string
}

export interface ImportResult {
  total: number
  imported: number
  failed: number
  errors: ImportRowError[]
}

export type ImportJobStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED'

export interface ImportJob {
  id: string
  kind: 'STUDENTS' | 'TEACHERS'
  filename: string
  status: ImportJobStatus
  total: number
  processed: number
  imported: number
  failed: number
  message: string | null
  /** Only present once status is DONE. */
  result: ImportResult | null
}

function startJob(url: string, file: File, onUpload?: (percent: number) => void): Promise<ImportJob> {
  const fd = new FormData()
  fd.append('file', file)
  return unwrap(http.post(url, fd, {
    onUploadProgress: e => { if (onUpload && e.total) onUpload(Math.round((e.loaded / e.total) * 100)) }
  }))
}

function uploadFile(url: string, file: File): Promise<ImportResult> {
  const fd = new FormData()
  fd.append('file', file)
  // axios sets the multipart Content-Type (with boundary) from the FormData;
  // the http interceptor still attaches the X-XSRF-TOKEN header for the POST.
  return unwrap(http.post(url, fd))
}

async function downloadTemplate(url: string, filename: string): Promise<void> {
  const resp = await http.get(url, { responseType: 'blob' })
  const blobUrl = window.URL.createObjectURL(resp.data as Blob)
  const a = document.createElement('a')
  a.href = blobUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.URL.revokeObjectURL(blobUrl)
}

export const adminApi = {
  years: {
    list: (): Promise<SchoolYearItem[]> => unwrap(http.get('/admin/school-years')),
    create: (body: SchoolYearBody): Promise<SchoolYearItem> => unwrap(http.post('/admin/school-years', body)),
    update: (id: number, body: SchoolYearBody): Promise<SchoolYearItem> => unwrap(http.put(`/admin/school-years/${id}`, body)),
    remove: (id: number): Promise<void> => unwrap(http.delete(`/admin/school-years/${id}`))
  },
  classes: {
    list: (yearId: number): Promise<ClassItem[]> => unwrap(http.get('/admin/classes', { params: { yearId } })),
    create: (body: ClassBody): Promise<ClassItem> => unwrap(http.post('/admin/classes', body)),
    update: (id: number, body: ClassBody): Promise<ClassItem> => unwrap(http.put(`/admin/classes/${id}`, body)),
    remove: (id: number): Promise<void> => unwrap(http.delete(`/admin/classes/${id}`))
  },
  users: {
    list: (params: UserListParams = {}): Promise<UserItem[]> => unwrap(http.get('/admin/users', { params })),
    page: (params: UserPageParams): Promise<UserPage> => unwrap(http.get('/admin/users/page', { params })),
    create: (body: CreateUserBody): Promise<UserItem> => unwrap(http.post('/admin/users', body)),
    update: (id: number, body: UpdateUserBody): Promise<UserItem> => unwrap(http.put(`/admin/users/${id}`, body)),
    remove: (id: number): Promise<void> => unwrap(http.delete(`/admin/users/${id}`)),
    resetPassword: (id: number, password: string): Promise<void> =>
      unwrap(http.post(`/admin/users/${id}/reset-password`, { password })),
    graduate: (id: number, value: boolean): Promise<void> =>
      unwrap(http.post(`/admin/users/${id}/graduate`, null, { params: { value } })),
    enabled: (id: number, value: boolean): Promise<void> =>
      unwrap(http.post(`/admin/users/${id}/enabled`, null, { params: { value } }))
  },
  labWorkstations: {
    list: (): Promise<LabWorkstationItem[]> => unwrap(http.get('/admin/lab-workstations')),
    create: (body: LabWorkstationBody): Promise<LabWorkstationItem> => unwrap(http.post('/admin/lab-workstations', body)),
    update: (id: number, body: LabWorkstationBody): Promise<LabWorkstationItem> => unwrap(http.put(`/admin/lab-workstations/${id}`, body)),
    remove: (id: number): Promise<void> => unwrap(http.delete(`/admin/lab-workstations/${id}`))
  },
  permissions: {
    list: (): Promise<PermissionItem[]> => unwrap(http.get('/admin/permissions')),
    upsertModulePermission: (body: ModulePermissionBody): Promise<void> =>
      unwrap(http.put('/admin/module-permissions', body))
  },
  imports: {
    students: (file: File): Promise<ImportResult> => uploadFile('/admin/import/students', file),
    teachers: (file: File): Promise<ImportResult> => uploadFile('/admin/import/teachers', file),
    startStudentJob: (file: File, onUpload?: (percent: number) => void): Promise<ImportJob> =>
      startJob('/admin/import/students/jobs', file, onUpload),
    startTeacherJob: (file: File, onUpload?: (percent: number) => void): Promise<ImportJob> =>
      startJob('/admin/import/teachers/jobs', file, onUpload),
    job: (id: string): Promise<ImportJob> => unwrap(http.get(`/admin/import/jobs/${id}`)),
    downloadStudentTemplate: (): Promise<void> =>
      downloadTemplate('/admin/import/students/template', 'students_template.xlsx'),
    downloadTeacherTemplate: (): Promise<void> =>
      downloadTemplate('/admin/import/teachers/template', 'teachers_template.xlsx')
  }
}
