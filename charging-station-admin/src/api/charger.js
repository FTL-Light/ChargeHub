import request from '@/utils/request'

// 获取充电桩列表
export function getChargerList(params) {
  return request({
    url: '/admin/charger/list',
    method: 'get',
    params
  })
}

// 新增充电桩
export function addCharger(data) {
  return request({
    url: '/admin/charger',
    method: 'post',
    data
  })
}

// 修改充电桩
export function updateCharger(data) {
  return request({
    url: '/admin/charger',
    method: 'put',
    data
  })
}

// 删除充电桩
export function deleteCharger(id) {
  return request({
    url: `/admin/charger/${id}`,
    method: 'delete'
  })
}
