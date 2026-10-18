import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

// 根据角色返回首页路径
function defaultHome(role) {
  return Number(role) === 1 ? '/home/room-manage' : '/home/rooms'
}

const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { public: true, title: '注册' }
  },
  {
    path: '/home',
    component: () => import('@/views/Home.vue'),
    redirect: () => defaultHome(localStorage.getItem('role')),
    children: [
      // ===== 公共菜单（员工 / 管理员均可见，本次升级新增） =====
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'calendar',
        name: 'RoomCalendar',
        component: () => import('@/views/RoomCalendar.vue'),
        meta: { title: '预约日历' }
      },
      // ===== 员工菜单 =====
      {
        path: 'rooms',
        name: 'RoomList',
        component: () => import('@/views/employee/RoomList.vue'),
        meta: { title: '会议室列表', role: 0 }
      },
      {
        path: 'my-bookings',
        name: 'MyBookings',
        component: () => import('@/views/employee/MyBookings.vue'),
        meta: { title: '我的预约', role: 0 }
      },
      // ===== 管理员菜单 =====
      {
        path: 'room-manage',
        name: 'RoomManage',
        component: () => import('@/views/admin/RoomManage.vue'),
        meta: { title: '会议室管理', role: 1 }
      },
      {
        path: 'booking-approve',
        name: 'BookingApprove',
        component: () => import('@/views/admin/BookingAdmin.vue'),
        meta: { title: '预约审批', role: 1 }
      },
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理', role: 1 }
      },
      {
        path: 'logs',
        name: 'LogList',
        component: () => import('@/views/admin/LogList.vue'),
        meta: { title: '操作日志', role: 1 }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：登录校验 + 角色校验
router.beforeEach((to, from, next) => {
  document.title = to.meta.title
    ? `${to.meta.title} - 会议室预约管理系统`
    : '会议室预约管理系统'

  const token = localStorage.getItem('token')

  // 公开页面（登录/注册）
  if (to.meta.public) {
    if (token && (to.path === '/login' || to.path === '/register')) {
      return next(defaultHome(localStorage.getItem('role')))
    }
    return next()
  }

  // 未登录禁止访问
  if (!token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  // 管理员页面普通员工无法进入
  const role = Number(localStorage.getItem('role'))
  if (to.meta.role !== undefined && to.meta.role !== role) {
    ElMessage.error('无权限访问该页面')
    return next(defaultHome(role))
  }

  next()
})

export default router