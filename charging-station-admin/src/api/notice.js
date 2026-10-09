import request from '@/utils/request'

// 获取公告列表
export function getNoticeList(params) {
  return request({
    url: '/admin/notice/list',
    method: 'get',
    params
  })
}

// 新增公告
export function addNotice(data) {
  return request({
    url: '/admin/notice',
    method: 'post',
    data
  })
}

// 修改公告
export function updateNotice(data) {
  return request({
    url: '/admin/notice',
    method: 'put',
    data
  })
}

// 删除公告
export function deleteNotice(id) {
  return request({
    url: `/admin/notice/${id}`,
    method: 'delete'
  })
}
