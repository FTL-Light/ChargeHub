// API 接口封装
const { get, post, put, del } = require('../utils/request.js')

// ============ 用户相关 ============
// 用户登录
function userLogin(data) {
  return post('/miniapp/user/login', data)
}

// 用户注册
function userRegister(data) {
  return post('/miniapp/user/register', data)
}

// 获取用户信息
function getUserInfo() {
  return get('/miniapp/user/info')
}

// 获取用户余额
function getUserBalance() {
  return get('/miniapp/user/balance')
}

// ============ 充电站相关 ============
// 充电站列表
function getStationList(params) {
  return get('/miniapp/station/list', params)
}

// 充电站详情
function getStationDetail(id) {
  return get('/miniapp/station/' + id)
}

// 附近充电站
function getNearbyStations(params) {
  return get('/miniapp/station/nearby', params)
}

// ============ 充电桩相关 ============
// 充电桩详情
function getChargerDetail(id) {
  return get('/miniapp/charger/' + id)
}

// ============ 充电/订单相关 ============
// 开始充电
function startCharging(data) {
  return post('/miniapp/order/start', data)
}

// 停止充电
function stopCharging(data) {
  return post('/miniapp/order/stop', data)
}

// 获取充电状态
function getChargingStatus(params) {
  return get('/miniapp/order/charging-status', params)
}

// 订单列表
function getOrderList(params) {
  return get('/miniapp/order/list', params)
}

// 订单详情
function getOrderDetail(id) {
  return get('/miniapp/order/' + id)
}

// ============ 充值相关 ============
// 账户充值
function recharge(data) {
  return post('/miniapp/recharge', data)
}

// 充值记录列表
function getRechargeList(params) {
  return get('/miniapp/recharge/list', params)
}

// ============ 反馈相关 ============
// 提交反馈
function submitFeedback(data) {
  return post('/miniapp/feedback', data)
}

// 反馈列表
function getFeedbackList(params) {
  return get('/miniapp/feedback/list', params)
}

// ============ 公告相关 ============
// 公告列表
function getNoticeList(params) {
  return get('/miniapp/notice/list', params)
}

// 公告详情
function getNoticeDetail(id) {
  return get('/miniapp/notice/' + id)
}

// ============ AI 客服 ============
// AI 对话
function aiChat(data) {
  return post('/miniapp/ai/chat', data)
}

module.exports = {
  // 用户
  userLogin,
  userRegister,
  getUserInfo,
  getUserBalance,
  // 站点
  getStationList,
  getStationDetail,
  getNearbyStations,
  // 充电桩
  getChargerDetail,
  // 订单
  startCharging,
  stopCharging,
  getChargingStatus,
  getOrderList,
  getOrderDetail,
  // 充值
  recharge,
  getRechargeList,
  // 反馈
  submitFeedback,
  getFeedbackList,
  // 公告
  getNoticeList,
  getNoticeDetail,
  // AI
  aiChat
}
