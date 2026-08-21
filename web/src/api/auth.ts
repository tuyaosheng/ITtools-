import { http } from './http'

export interface SchoolYear {
  id: number
  yearCode: string
  label: string
}

export interface SchoolClass {
  id: number
  name: string
}

export interface StudentLite {
  id: number
  name: string
}

export const publicApi = {
  years: (): Promise<SchoolYear[]> => http.get('/public/school-years').then(r => r.data.data),
  classes: (yearId: number): Promise<SchoolClass[]> => http.get('/public/classes', { params: { yearId } }).then(r => r.data.data),
  students: (classId: number): Promise<StudentLite[]> => http.get('/public/students', { params: { classId } }).then(r => r.data.data)
}
