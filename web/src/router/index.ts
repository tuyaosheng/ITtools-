import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useFeatureStore } from '../stores/features'
import { ElMessage } from 'element-plus'


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
        { path: 'features', name: 'admin-features', component: () => import('../views/admin/FeatureView.vue') },
        { path: 'settings', name: 'admin-settings', component: () => import('../views/admin/SettingsView.vue') },
        { path: 'tools', name: 'admin-tools', component: () => import('../views/admin/EmergencyToolsView.vue') },
        { path: 'student-data', name: 'admin-student-data', component: () => import('../views/admin/StudentDataView.vue') },
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
        { path: 'timetable', meta: { feature: 'timetable' }, component: () => import('../views/teacher/Timetable.vue') },
        { path: 'homework', meta: { feature: 'homework' }, component: () => import('../views/homework/TeacherHomeworkIndex.vue') },
        { path: 'quiz', meta: { feature: 'quiz' }, component: () => import('../views/quiz/QuizBankView.vue') },
        { path: 'tests', meta: { feature: 'tests' }, component: () => import('../views/quiz/TestManageView.vue') },
        { path: 'rubrics', meta: { feature: 'rubrics' }, component: () => import('../views/teacher/RubricView.vue') },
        { path: 'python', meta: { feature: 'python' }, component: () => import('../views/python/ProblemBankView.vue') },
        { path: 'resources', meta: { feature: 'resources' }, component: () => import('../views/teacher/ResourceView.vue') },
        { path: 'lesson-plans', meta: { feature: 'lesson-plans' }, component: () => import('../views/teacher/LessonPlanView.vue') },
        { path: 'reflections', meta: { feature: 'reflections' }, component: () => import('../views/teacher/ReflectionView.vue') },
        { path: 'disk', meta: { feature: 'disk' }, component: () => import('../views/common/DiskView.vue') },
        { path: 'messages', component: () => import('../views/teacher/MessageView.vue') },
        { path: 'qa', meta: { feature: 'qa' }, component: () => import('../views/teacher/TeacherQa.vue') },
        { path: 'login-logs', meta: { feature: 'login-logs' }, component: () => import('../views/teacher/LoginLogs.vue') },
        { path: 'reports', meta: { feature: 'reports' }, component: () => import('../views/teacher/ClassReport.vue') },
        { path: 'student-space', meta: { feature: 'student-space' }, component: () => import('../views/teacher/StudentSpace.vue') }
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
        { path: 'works', meta: { feature: 'works' }, component: () => import('../views/student/MyWorks.vue') },
        { path: 'showcase', meta: { feature: 'showcase' }, component: () => import('../views/student/Showcase.vue') },
        { path: 'tests', meta: { feature: 'tests' }, component: () => import('../views/student/StudentTests.vue') },
        { path: 'practice', meta: { feature: 'practice' }, component: () => import('../views/student/PracticeView.vue') },
        { path: 'python', meta: { feature: 'python' }, component: () => import('../views/python/StudentPracticeView.vue') },
        { path: 'blocks', meta: { feature: 'blocks' }, component: () => import('../views/student/StudentBlocks.vue') },
        { path: 'resources', meta: { feature: 'resources' }, component: () => import('../views/student/StudentResources.vue') },
        { path: 'disk', meta: { feature: 'disk' }, component: () => import('../views/common/DiskView.vue') },
        { path: 'notes', meta: { feature: 'notes' }, component: () => import('../views/student/StudentNotes.vue') },
        { path: 'electives', meta: { feature: 'electives' }, component: () => import('../views/student/StudentElectives.vue') },
        { path: 'messages', component: () => import('../views/student/StudentMessages.vue') },
        { path: 'qa', meta: { feature: 'qa' }, component: () => import('../views/student/StudentQa.vue') },
        { path: 'report', meta: { feature: 'report' }, component: () => import('../views/student/StudentReport.vue') },
        { path: 'op-practice', meta: { feature: 'op-practice' }, component: () => import('../views/student/StudentOpPractice.vue') },
        { path: 'typing', meta: { feature: 'typing' }, component: () => import('../views/student/StudentTyping.vue') },
        { path: 'site', meta: { feature: 'site' }, component: () => import('../views/student/StudentSite.vue') }
      ]
    },
    // 投屏大屏：全屏、不带菜单，老师在课堂控制台“物联网消息”点“投屏大屏”打开
    { path: '/teacher/screen/mqtt/:id', meta: { requiresAuth: true, role: 'TEACHER' }, component: () => import('../views/activity/MqttScreen.vue') }
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
  // 兼任管理员的教师可以进管理后台
  const allowed = auth.user?.role === need || (need === 'ADMIN' && !!auth.user?.canAdmin)
  if (need && auth.user && !allowed) {
    return home[auth.user.role] || { name: 'login' }
  }
  // 管理员关闭的功能：直接输入网址也进不去
  const feature = to.meta.feature as string | undefined
  if (feature) {
    const features = useFeatureStore()
    await features.load()
    if (!features.isOpen(feature)) {
      ElMessage.warning('该功能已被管理员关闭')
      return home[auth.user?.role || ''] || { name: 'login' }
    }
  }
  return true
})

export default router
