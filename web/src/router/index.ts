import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'


const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'login', component: () => import('../views/login/LoginView.vue') },
    {
      path: '/admin',
      component: () => import('../layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, role: 'ADMIN' },
      children: [
        { path: '', redirect: '/admin/school-years' },
        { path: 'school-years', name: 'admin-school-years', component: () => import('../views/admin/SchoolYearView.vue') },
        { path: 'classes', name: 'admin-classes', component: () => import('../views/admin/ClassView.vue') },
        { path: 'lab-workstations', name: 'admin-lab-workstations', component: () => import('../views/admin/LabWorkstationView.vue') },
        { path: 'lab-rooms', name: 'admin-lab-rooms', component: () => import('../views/admin/LabRoomView.vue') },
        { path: 'lab-groups', name: 'admin-lab-groups', component: () => import('../views/admin/LabGroupView.vue') },
        { path: 'class-seats', name: 'admin-class-seats', component: () => import('../views/admin/ClassSeatView.vue') },
        { path: 'teaching', name: 'admin-teaching', component: () => import('../views/admin/TeachingAdminView.vue') },
        { path: 'electives', name: 'admin-electives', component: () => import('../views/admin/ElectiveView.vue') },
        { path: 'settings', name: 'admin-settings', component: () => import('../views/admin/SettingsView.vue') },
        { path: 'users', name: 'admin-users', component: () => import('../views/admin/UserView.vue') },
        { path: 'permissions', name: 'admin-permissions', component: () => import('../views/admin/PermissionView.vue') },
        { path: 'import', name: 'admin-import', component: () => import('../views/admin/ImportView.vue') }
      ]
    },
    {
      path: '/teacher',
      component: () => import('../layouts/TeacherLayout.vue'),
      meta: { requiresAuth: true, role: 'TEACHER' },
      children: [
        { path: '', redirect: '/teacher/home' },
        { path: 'home', component: () => import('../views/teacher/TeacherHome.vue') },
        { path: 'courses', component: () => import('../views/teacher/CourseList.vue') },
        { path: 'courses/:id', component: () => import('../views/teacher/CourseEdit.vue') },
        { path: 'lessons', component: () => import('../views/teacher/LessonHistory.vue') },
        { path: 'lessons/:id', component: () => import('../views/teacher/LessonConsole.vue') },
        { path: 'classes', component: () => import('../views/teacher/MyClasses.vue') },
        { path: 'timetable', component: () => import('../views/teacher/Timetable.vue') },
        { path: 'homework', component: () => import('../views/homework/TeacherHomeworkIndex.vue') },
        { path: 'quiz', component: () => import('../views/quiz/QuizBankView.vue') },
        { path: 'tests', component: () => import('../views/quiz/TestManageView.vue') },
        { path: 'rubrics', component: () => import('../views/teacher/RubricView.vue') },
        { path: 'python', component: () => import('../views/python/ProblemBankView.vue') },
        { path: 'resources', component: () => import('../views/teacher/ResourceView.vue') },
        { path: 'lesson-plans', component: () => import('../views/teacher/LessonPlanView.vue') },
        { path: 'reflections', component: () => import('../views/teacher/ReflectionView.vue') },
        { path: 'disk', component: () => import('../views/common/DiskView.vue') },
        { path: 'messages', component: () => import('../views/teacher/MessageView.vue') },
        { path: 'qa', component: () => import('../views/teacher/TeacherQa.vue') },
        { path: 'login-logs', component: () => import('../views/teacher/LoginLogs.vue') },
        { path: 'reports', component: () => import('../views/teacher/ClassReport.vue') }
      ]
    },
    {
      path: '/student',
      component: () => import('../layouts/StudentLayout.vue'),
      meta: { requiresAuth: true, role: 'STUDENT' },
      children: [
        { path: '', redirect: '/student/home' },
        { path: 'home', component: () => import('../views/student/StudentHome.vue') },
        { path: 'lessons/:id', component: () => import('../views/student/StudentLesson.vue') },
        { path: 'records', component: () => import('../views/student/StudentRecords.vue') },
        { path: 'works', component: () => import('../views/student/MyWorks.vue') },
        { path: 'showcase', component: () => import('../views/student/Showcase.vue') },
        { path: 'tests', component: () => import('../views/student/StudentTests.vue') },
        { path: 'practice', component: () => import('../views/student/PracticeView.vue') },
        { path: 'python', component: () => import('../views/python/StudentPracticeView.vue') },
        { path: 'resources', component: () => import('../views/student/StudentResources.vue') },
        { path: 'disk', component: () => import('../views/common/DiskView.vue') },
        { path: 'notes', component: () => import('../views/student/StudentNotes.vue') },
        { path: 'electives', component: () => import('../views/student/StudentElectives.vue') },
        { path: 'messages', component: () => import('../views/student/StudentMessages.vue') },
        { path: 'qa', component: () => import('../views/student/StudentQa.vue') },
        { path: 'report', component: () => import('../views/student/StudentReport.vue') }
      ]
    }
  ]
})

const home: Record<string, string> = { ADMIN: '/admin', TEACHER: '/teacher', STUDENT: '/student' }

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true

  const auth = useAuthStore()
  if (!auth.isAuthenticated) {
    await auth.fetchMe()
  }
  if (!auth.isAuthenticated) {
    return { name: 'login' }
  }
  // 角色守卫：进入其他角色的区域时回到自己的首页
  const need = to.matched.find(r => r.meta.role)?.meta.role as string | undefined
  if (need && auth.user && auth.user.role !== need) {
    return home[auth.user.role] || { name: 'login' }
  }
  return true
})

export default router
