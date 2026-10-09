import request from '@/utils/request'

// 获取用户列表
export function getUserList(params) {
  return request({
    url: '/admin/user/list',
    method: 'get',
    params
  })
}

// 获取用户详情
export function getUserDetail(id) {
  return request({
    url: `/admin/user/${id}`,
    method: 'get'
  })
}

// 修改用户状态
export function updateUserStatus(id, status) {
  return request({
    url: '/admin/user',
    method: 'put',
    data: { id, status }
  })
}
