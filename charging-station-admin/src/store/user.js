import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi } from '@/api/admin'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('admin_token') || '')
  const adminInfo = ref(
    JSON.parse(localStorage.getItem('admin_info') || 'null')
  )

  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('admin_token', newToken)
  }

  function setAdminInfo(info) {
    adminInfo.value = info
    localStorage.setItem('admin_info', JSON.stringify(info))
  }

  async function login(loginForm) {
    const res = await loginApi(loginForm)
    setToken(res.data.token)
    setAdminInfo(res.data.adminInfo || res.data)
    return res
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (e) {
      // 忽略退出登录接口错误
    }
    resetState()
    router.push('/login')
  }

  function resetState() {
    token.value = ''
    adminInfo.value = null
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_info')
  }

  return {
    token,
    adminInfo,
    setToken,
    setAdminInfo,
    login,
    logout,
    resetState
  }
})
