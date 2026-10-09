import request from '@/utils/request'

// 获取反馈列表
export function getFeedbackList(params) {
  return request({
    url: '/admin/feedback/list',
    method: 'get',
    params
  })
}

// 获取反馈详情
export function getFeedbackDetail(id) {
  return request({
    url: `/admin/feedback/${id}`,
    method: 'get'
  })
}

// 处理反馈
export function handleFeedback(data) {
  return request({
    url: '/admin/feedback/handle',
    method: 'put',
    data
  })
}
