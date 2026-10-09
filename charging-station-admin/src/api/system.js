import request from '@/utils/request'

// 获取AI客服配置
export function getAiConfig() {
  return request({
    url: '/admin/system/ai-config',
    method: 'get'
  })
}

// 更新AI客服配置
export function updateAiConfig(data) {
  return request({
    url: '/admin/system/ai-config',
    method: 'put',
    data
  })
}

// 获取登录日志列表
export function getLoginLogList(params) {
  return request({
    url: '/admin/login-log/list',
    method: 'get',
    params
  })
}
