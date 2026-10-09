import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/order/statistics',
    children: [
      {
        path: 'order/statistics',
        name: 'Dashboard',
        component: () => import('@/views/order/statistics.vue'),
        meta: { title: '首页', icon: 'HomeFilled' }
      },
      {
        path: 'station/list',
        name: 'StationList',
        component: () => import('@/views/station/list.vue'),
        meta: { title: '站点列表' }
      },
      {
        path: 'station/map',
        name: 'StationMap',
        component: () => import('@/views/station/map.vue'),
        meta: { title: '地图视图' }
      },
      {
        path: 'charger',
        name: 'Charger',
        component: () => import('@/views/charger/index.vue'),
        meta: { title: '充电桩管理' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'order/list',
        name: 'OrderList',
        component: () => import('@/views/order/list.vue'),
        meta: { title: '订单列表' }
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/feedback/index.vue'),
        meta: { title: '故障反馈' }
      },
      {
        path: 'notice',
        name: 'Notice',
        component: () => import('@/views/notice/index.vue'),
        meta: { title: '公告管理' }
      },
      {
        path: 'system/ai-config',
        name: 'AiConfig',
        component: () => import('@/views/system/ai-config.vue'),
        meta: { title: 'AI客服配置' }
      },
      {
        path: 'system/login-log',
        name: 'LoginLog',
        component: () => import('@/views/system/login-log.vue'),
        meta: { title: '登录日志' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
const whiteList = ['/login']

router.beforeEach((to, from, next) => {
  NProgress.start()
  document.title = to.meta.title
    ? `${to.meta.title} - 充电站管理平台`
    : '充电站管理平台'
  const hasToken = localStorage.getItem('admin_token')
  if (hasToken) {
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else {
      next()
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
      NProgress.done()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
