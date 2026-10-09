const { getUserBalance, recharge, getRechargeList } = require('../../api/index.js')

Page({
  data: {
    balance: '0.00',
    amount: '',
    selectedAmount: null,
    quickAmounts: [50, 100, 200, 500],
    rechargeList: [],
    loading: false,
    submitting: false
  },

  onLoad() {
    this.loadBalance()
    this.loadRechargeList()
  },

  onShow() {
    this.loadBalance()
  },

  // 加载余额
  loadBalance() {
    getUserBalance()
      .then((res) => {
        const balance = (res && res.balance != null) ? res.balance : (typeof res === 'number' ? res : 0)
        this.setData({ balance: parseFloat(balance).toFixed(2) })
      })
      .catch(() => {})
  },

  // 加载充值记录
  loadRechargeList() {
    this.setData({ loading: true })
    getRechargeList({ page: 1, pageSize: 20 })
      .then((res) => {
        const list = Array.isArray(res) ? res : (res && res.list) || (res && res.records) || []
        this.setData({
          rechargeList: list,
          loading: false
        })
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  // 选择快捷金额
  onQuickAmount(e) {
    const amount = e.currentTarget.dataset.amount
    this.setData({
      selectedAmount: amount,
      amount: String(amount)
    })
  },

  // 输入金额
  onAmountInput(e) {
    let value = e.detail.value
    // 限制只能输入数字和小数点
    value = value.replace(/[^\d.]/g, '')
    // 只保留一个小数点
    const parts = value.split('.')
    if (parts.length > 2) {
      value = parts[0] + '.' + parts.slice(1).join('')
    }
    // 限制两位小数
    if (parts[1] && parts[1].length > 2) {
      value = parts[0] + '.' + parts[1].substring(0, 2)
    }
    this.setData({
      amount: value,
      selectedAmount: null
    })
  },

  // 充值
  onRecharge() {
    if (this.data.submitting) return
    const amount = parseFloat(this.data.amount)
    if (!amount || amount <= 0) {
      wx.showToast({ title: '请输入充值金额', icon: 'none' })
      return
    }
    if (amount < 1) {
      wx.showToast({ title: '最低充值1元', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    wx.showLoading({ title: '充值中' })
    recharge({ amount: amount })
      .then((res) => {
        wx.hideLoading()
        this.setData({ submitting: false })
        wx.showToast({ title: '充值成功', icon: 'success' })
        // 刷新余额和记录
        this.setData({
          amount: '',
          selectedAmount: null
        })
        this.loadBalance()
        this.loadRechargeList()
      })
      .catch(() => {
        wx.hideLoading()
        this.setData({ submitting: false })
      })
  },

  // 下拉刷新
  onPullDownRefresh() {
    this.loadBalance()
    this.loadRechargeList()
    setTimeout(() => wx.stopPullDownRefresh(), 1000)
  }
})
