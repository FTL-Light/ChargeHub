const {
  getChargerDetail,
  startCharging,
  stopCharging,
  getChargingStatus
} = require('../../api/index.js')

Page({
  data: {
    chargerId: null,
    chargerInfo: {},
    isCharging: false,
    chargingTime: 0,
    chargingTimeText: '00:00',
    electricity: 0,
    amount: 0,
    orderId: null,
    timer: null,
    loading: true,
    starting: false,
    stopping: false
  },

  onLoad(options) {
    if (options.chargerId) {
      this.setData({ chargerId: options.chargerId })
      this.loadChargerInfo(options.chargerId)
    } else {
      wx.showToast({ title: '参数错误', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1500)
    }
  },

  onUnload() {
    this.clearTimer()
  },

  onHide() {
    this.clearTimer()
  },

  // 加载充电桩信息
  loadChargerInfo(id) {
    this.setData({ loading: true })
    getChargerDetail(id)
      .then((res) => {
        const info = res || {}
        this.setData({
          chargerInfo: info,
          loading: false
        })
        // 检查是否已在充电
        if (info.orderId && (info.status === 1 || info.status === '使用中' || info.status === '充电中')) {
          this.setData({
            isCharging: true,
            orderId: info.orderId
          })
          this.startTimer()
        }
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  // 开始充电
  startCharging() {
    if (this.data.starting) return
    const charger = this.data.chargerInfo
    if (charger.status !== 0 && charger.status !== '空闲') {
      wx.showToast({ title: '该充电桩当前不可用', icon: 'none' })
      return
    }
    this.setData({ starting: true })
    wx.showLoading({ title: '正在启动充电' })
    startCharging({ chargerId: this.data.chargerId })
      .then((res) => {
        wx.hideLoading()
        const orderId = (res && res.orderId) || (res && res.id) || res
        this.setData({
          isCharging: true,
          orderId: orderId,
          chargingTime: 0,
          electricity: 0,
          amount: 0,
          starting: false
        })
        wx.showToast({ title: '充电已开始', icon: 'success' })
        this.startTimer()
      })
      .catch(() => {
        wx.hideLoading()
        this.setData({ starting: false })
      })
  },

  // 停止充电
  stopCharging() {
    if (this.data.stopping) return
    wx.showModal({
      title: '确认停止充电',
      content: '确定要停止充电吗？停止后将生成订单。',
      success: (res) => {
        if (res.confirm) {
          this.setData({ stopping: true })
          wx.showLoading({ title: '正在停止充电' })
          stopCharging({ orderId: this.data.orderId, chargerId: this.data.chargerId })
            .then((res) => {
              wx.hideLoading()
              this.clearTimer()
              const orderId = (res && res.orderId) || (res && res.id) || this.data.orderId
              wx.showToast({ title: '充电已结束', icon: 'success' })
              this.setData({
                isCharging: false,
                stopping: false,
                chargingTime: 0,
                electricity: 0,
                amount: 0
              })
              setTimeout(() => {
                wx.redirectTo({
                  url: '/pages/order/detail?id=' + orderId
                })
              }, 1500)
            })
            .catch(() => {
              wx.hideLoading()
              this.setData({ stopping: false })
            })
        }
      }
    })
  },

  // 启动定时器
  startTimer() {
    this.clearTimer()
    const timer = setInterval(() => {
      const time = this.data.chargingTime + 1
      this.setData({
        chargingTime: time,
        chargingTimeText: this.formatTime(time)
      })
      // 每5秒拉取一次实时数据
      if (time % 5 === 0) {
        this.updateChargingInfo()
      } else {
        // 本地预估
        const power = parseFloat(this.data.chargerInfo.power || this.data.chargerInfo.powerRate || 7) // kW
        const price = parseFloat(this.data.chargerInfo.price || this.data.chargerInfo.unitPrice || 1.5) // 元/度
        const electricity = (power * time / 3600).toFixed(2)
        const amount = (electricity * price).toFixed(2)
        this.setData({
          electricity: electricity,
          amount: amount
        })
      }
    }, 1000)
    this.setData({ timer: timer })
  },

  // 清除定时器
  clearTimer() {
    if (this.data.timer) {
      clearInterval(this.data.timer)
      this.setData({ timer: null })
    }
  },

  // 更新充电信息（实时拉取）
  updateChargingInfo() {
    getChargingStatus({ orderId: this.data.orderId })
      .then((res) => {
        if (res) {
          this.setData({
            electricity: res.electricity != null ? res.electricity : this.data.electricity,
            amount: res.amount != null ? res.amount : this.data.amount,
            chargingTime: res.duration != null ? res.duration : this.data.chargingTime,
            chargingTimeText: this.formatTime(res.duration != null ? res.duration : this.data.chargingTime)
          })
        }
      })
      .catch(() => {})
  },

  // 格式化时间 mm:ss / hh:mm:ss
  formatTime(seconds) {
    seconds = parseInt(seconds) || 0
    const h = Math.floor(seconds / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    const s = seconds % 60
    const pad = (n) => (n < 10 ? '0' + n : '' + n)
    if (h > 0) {
      return pad(h) + ':' + pad(m) + ':' + pad(s)
    }
    return pad(m) + ':' + pad(s)
  }
})
