const { userLogin, userRegister } = require('../../api/index.js')

const app = getApp()

Page({
  data: {
    loginType: 'wechat', // wechat / phone
    phoneForm: {
      phone: '',
      password: ''
    },
    isRegister: false,
    registerForm: {
      phone: '',
      password: '',
      confirmPassword: '',
      nickname: ''
    },
    submitting: false
  },

  // 切换登录方式
  switchTab(e) {
    const type = e.currentTarget.dataset.type
    this.setData({ loginType: type })
  },

  // 手机号输入
  onPhoneInput(e) {
    this.setData({ 'phoneForm.phone': e.detail.value })
  },

  // 密码输入
  onPasswordInput(e) {
    this.setData({ 'phoneForm.password': e.detail.value })
  },

  // 注册表单输入
  onRegisterInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ ['registerForm.' + field]: e.detail.value })
  },

  // 微信一键登录
  onWechatLogin() {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    wx.showLoading({ title: '登录中' })
    wx.login({
      success: (res) => {
        if (res.code) {
          userLogin({ code: res.code, loginType: 'wechat' })
            .then((data) => {
              wx.hideLoading()
              this.setData({ submitting: false })
              this.handleLoginSuccess(data)
            })
            .catch(() => {
              wx.hideLoading()
              this.setData({ submitting: false })
            })
        } else {
          wx.hideLoading()
          this.setData({ submitting: false })
          wx.showToast({ title: '微信登录失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.hideLoading()
        this.setData({ submitting: false })
        wx.showToast({ title: '微信登录失败', icon: 'none' })
      }
    })
  },

  // 手机号登录
  onPhoneLogin() {
    if (this.data.submitting) return
    const { phone, password } = this.data.phoneForm
    if (!phone) {
      wx.showToast({ title: '请输入手机号', icon: 'none' })
      return
    }
    if (!/^1\d{10}$/.test(phone)) {
      wx.showToast({ title: '手机号格式不正确', icon: 'none' })
      return
    }
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    wx.showLoading({ title: '登录中' })
    userLogin({ phone: phone, password: password, loginType: 'phone' })
      .then((data) => {
        wx.hideLoading()
        this.setData({ submitting: false })
        this.handleLoginSuccess(data)
      })
      .catch(() => {
        wx.hideLoading()
        this.setData({ submitting: false })
      })
  },

  // 切换注册/登录
  toggleRegister() {
    this.setData({
      isRegister: !this.data.isRegister
    })
  },

  // 注册
  onRegister() {
    if (this.data.submitting) return
    const form = this.data.registerForm
    if (!form.phone) {
      wx.showToast({ title: '请输入手机号', icon: 'none' })
      return
    }
    if (!/^1\d{10}$/.test(form.phone)) {
      wx.showToast({ title: '手机号格式不正确', icon: 'none' })
      return
    }
    if (!form.password || form.password.length < 6) {
      wx.showToast({ title: '密码至少6位', icon: 'none' })
      return
    }
    if (form.password !== form.confirmPassword) {
      wx.showToast({ title: '两次密码不一致', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    wx.showLoading({ title: '注册中' })
    userRegister({
      phone: form.phone,
      password: form.password,
      nickname: form.nickname || ('用户' + form.phone.slice(-4))
    })
      .then(() => {
        wx.hideLoading()
        this.setData({ submitting: false })
        wx.showToast({ title: '注册成功', icon: 'success' })
        // 注册成功后自动登录
        setTimeout(() => {
          this.setData({
            isRegister: false,
            phoneForm: {
              phone: form.phone,
              password: form.password
            }
          })
          this.onPhoneLogin()
        }, 1500)
      })
      .catch(() => {
        wx.hideLoading()
        this.setData({ submitting: false })
      })
  },

  // 处理登录成功
  handleLoginSuccess(data) {
    const token = (data && data.token) || (data && data.accessToken) || data
    const userInfo = (data && data.userInfo) || (data && data.user) || {}
    app.globalData.token = token
    app.globalData.userInfo = userInfo
    wx.setStorageSync('token', token)
    wx.setStorageSync('userInfo', userInfo)
    wx.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      wx.switchTab({
        url: '/pages/index/index'
      })
    }, 1500)
  }
})
