const {
  getChargerDetail,
  startCharging,
  stopCharging,
  getChargingStatus
} = require('../../api/index.js')

Page({
  data: {
    chargerId: null,
    stationId: null,
    gunId: null,
    gunCode: '',
    chargerInfo: {},
    guns: [],
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
  // 后端 /miniapp/charger/{id} 返回 { charger, guns }，此处解包并自动选中一把空闲枪
  loadChargerInfo(id) {
    this.setData({ loading: true })
    getChargerDetail(id)
      .then((res) => {
        const charger = (res && res.charger) || {}
        const guns = (res && res.guns) || []
        // 优先选空闲枪(status=1)，没有空闲枪则取第一把用于展示
        const idleGun = guns.find((g) => g.status === 1) || guns[0] || null
        this.setData({
          chargerInfo: charger,
          stationId: charger.stationId || null,
          guns,
          gunId: idleGun ? idleGun.id : null,
          gunCode: idleGun ? idleGun.code : '',
          loading: false
        })
        // 桩状态 2 = 使用中，说明当前正在充电，拉取进行中的订单
        if (charger.status === 2) {
          this.syncChargingOrder()
        }
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  // 拉取当前进行中的订单(用户可能在其他入口发起了充电)
  syncChargingOrder() {
    getChargingStatus({})
      .then((res) => {
        if (res && res.id) {
          this.setData({
            isCharging: true,
            orderId: res.id,
            stationId: res.stationId || this.data.stationId,
            gunId: res.gunId || this.data.gunId,
            chargingTime: res.duration != null ? res.duration * 60 : 0
          })
          this.startTimer()
        }
      })
      .catch(() => {})
  },

  // 开始充电
  startCharging() {
    if (this.data.starting) return
    const charger = this.data.chargerInfo
    // 桩状态: 0-故障, 1-空闲, 2-使用中
    if (charger.status !== 1) {
      wx.showToast({ title: '该充电桩当前不可用', icon: 'none' })
      return
    }
    if (!this.data.gunId) {
      wx.showToast({ title: '该充电桩暂无空闲充电枪', icon: 'none' })
      return
    }
    this.setData({ starting: true })
    wx.showLoading({ title: '正在启动充电' })
    startCharging({
      chargerId: this.data.chargerId,
      stationId: this.data.stationId,
      gunId: this.data.gunId
    })
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
        // 后端返回的订单里带最新桩状态，同步一次避免本地状态过期
        this.setData({ 'chargerInfo.status': 2 })
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
          stopCharging({ orderId: this.data.orderId })
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
                amount: 0,
                'chargerInfo.status': 1
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

  // 轮询订单状态
  // 后端当前未提供实时计量，电量/金额由本地按"功率 × 时长"估算；
  // 这里只负责探测订单是否已被结束(服务端超时结算 / 其他端停止)。
  updateChargingInfo() {
    getChargingStatus({})
      .then((res) => {
        if (!res || res.status !== 1) {
          this.clearTimer()
          this.setData({
            isCharging: false,
            'chargerInfo.status': 1
          })
          wx.showToast({ title: '充电已结束', icon: 'none' })
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
