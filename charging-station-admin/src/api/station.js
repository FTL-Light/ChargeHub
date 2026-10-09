import request from '@/utils/request'

// 获取站点列表
export function getStationList(params) {
  return request({
    url: '/admin/station/list',
    method: 'get',
    params
  })
}

// 新增站点
export function addStation(data) {
  return request({
    url: '/admin/station',
    method: 'post',
    data
  })
}

// 修改站点
export function updateStation(data) {
  return request({
    url: '/admin/station',
    method: 'put',
    data
  })
}

// 删除站点
export function deleteStation(id) {
  return request({
    url: `/admin/station/${id}`,
    method: 'delete'
  })
}

// 获取站点详情
export function getStationDetail(id) {
  return request({
    url: `/admin/station/${id}`,
    method: 'get'
  })
}

// 获取全部站点(下拉用，使用list不分页)
export function getAllStations() {
  return request({
    url: '/admin/station/list',
    method: 'get',
    params: { current: 1, size: 999 }
  })
}
