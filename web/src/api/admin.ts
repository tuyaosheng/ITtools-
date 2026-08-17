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
  permissions: {
    list: (): Promise<PermissionItem[]> => unwrap(http.get('/admin/permissions')),
    upsertModulePermission: (body: ModulePermissionBody): Promise<void> =>
      unwrap(http.put('/admin/module-permissions', body))
  }
}
