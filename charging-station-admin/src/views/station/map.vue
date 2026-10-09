<template>
  <div class="map-container">
    <!-- 高德Key输入栏 -->
    <div class="map-toolbar">
      <el-form :inline="true">
        <el-form-item label="高德地图Key">
          <el-input
            v-model="amapKey"
            placeholder="请输入高德地图 Web 端 JS API Key"
            style="width: 420px"
            clearable
          />
        </el-form-item>
        <el-form-item label="安全密钥">
          <el-input
            v-model="securityCode"
            placeholder="securityJsCode（可选）"
            style="width: 240px"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="MapLocation" @click="initMap">加载地图</el-button>
          <el-button :icon="Refresh" @click="loadStations">刷新站点</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="!amapKey"
        title="请先输入高德地图 Web 端 JS API Key，可在 https://console.amap.com 申请"
        type="info"
        :closable="false"
        show-icon
      />
    </div>

    <!-- 地图容器 -->
    <div v-loading="loading" class="map-wrapper">
      <div id="stationMap" class="map"></div>
      <div v-if="mapError" class="map-error">
        <el-result icon="warning" :title="mapError">
          <template #extra>
            <el-button type="primary" @click="initMap">重新加载</el-button>
          </template>
        </el-result>
      </div>
    </div>

    <!-- 站点列表 -->
    <div class="station-list">
      <div class="station-list-title">
        站点列表（共 {{ stations.length }} 个）
      </div>
      <el-table :data="stations" border size="small" max-height="240">
        <el-table-column prop="name" label="站点名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="chargerCount" label="充电桩" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '营业' : row.status === 2 ? '维护' : '停业' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="locateStation(row)">定位</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { MapLocation, Refresh } from '@element-plus/icons-vue'
import AMapLoader from '@amap/amap-jsapi-loader'
import { getStationList } from '@/api/station'

const amapKey = ref(localStorage.getItem('amap_key') || '')
const securityCode = ref(localStorage.getItem('amap_security') || '')
const loading = ref(false)
const mapError = ref('')
const stations = ref([])

let map = null
let markers = []
let infoWindow = null

const loadStations = async () => {
  loading.value = true
  try {
    const res = await getStationList({ page: 1, size: 1000 })
    stations.value = res.data.records || res.data.list || res.data || []
    if (map) {
      renderMarkers()
    }
    ElMessage.success(`加载到 ${stations.value.length} 个站点`)
  } catch (e) {
    stations.value = []
  } finally {
    loading.value = false
  }
}

const initMap = async () => {
  if (!amapKey.value) {
    ElMessage.warning('请输入高德地图 Key')
    return
  }
  localStorage.setItem('amap_key', amapKey.value)
  localStorage.setItem('amap_security', securityCode.value)
  mapError.value = ''
  loading.value = true

  // 设置安全密钥
  if (securityCode.value) {
    window._AMapSecurityConfig = {
      securityJsCode: securityCode.value
    }
  }

  try {
    const AMap = await AMapLoader.load({
      key: amapKey.value,
      version: '2.0',
      plugins: ['AMap.Scale', 'AMap.ToolBar', 'AMap.InfoWindow']
    })
    map = new AMap.Map('stationMap', {
      zoom: 11,
      center: [116.397428, 39.90923],
      viewMode: '2D'
    })
    map.addControl(new AMap.Scale())
    map.addControl(new AMap.ToolBar({ position: 'RB' }))
    await loadStations()
  } catch (e) {
    mapError.value = '地图加载失败：' + (e.message || '请检查 Key 是否正确')
    console.error(e)
  } finally {
    loading.value = false
  }
}

const renderMarkers = () => {
  // 清除旧标记
  markers.forEach((m) => map.remove(m))
  markers = []
  if (infoWindow) {
    infoWindow.close()
  }

  stations.value.forEach((station) => {
    if (!station.longitude || !station.latitude) return
    const marker = new window.AMap.Marker({
      position: [station.longitude, station.latitude],
      title: station.name,
      map
    })
    marker.on('click', () => {
      showInfoWindow(station)
    })
    markers.push(marker)
  })

  // 自动调整视野
  if (markers.length > 0) {
    map.setFitView(markers, false, [60, 60, 60, 60])
  }
}

const showInfoWindow = (station) => {
  const content = `
    <div style="padding:8px;min-width:260px;">
      <h4 style="margin:0 0 8px;color:#303133;">${station.name}</h4>
      <p style="margin:4px 0;color:#606266;font-size:13px;">地址：${station.address || '-'}</p>
      <p style="margin:4px 0;color:#606266;font-size:13px;">电话：${station.phone || '-'}</p>
      <p style="margin:4px 0;color:#606266;font-size:13px;">充电桩数：${station.chargerCount || 0}</p>
      <p style="margin:4px 0;color:#606266;font-size:13px;">营业时间：${station.businessHours || '-'}</p>
      <p style="margin:4px 0;color:#606266;font-size:13px;">状态：${
        station.status === 1 ? '营业中' : station.status === 2 ? '维护中' : '停业'
      }</p>
    </div>
  `
  infoWindow = new window.AMap.InfoWindow({
    content,
    offset: new window.AMap.Pixel(0, -30)
  })
  infoWindow.open(map, [station.longitude, station.latitude])
}

const locateStation = (station) => {
  if (!map) {
    ElMessage.warning('请先加载地图')
    return
  }
  if (!station.longitude || !station.latitude) {
    ElMessage.warning('该站点无经纬度信息')
    return
  }
  map.setZoomAndCenter(15, [station.longitude, station.latitude])
  showInfoWindow(station)
}

onMounted(() => {
  if (amapKey.value) {
    initMap()
  } else {
    loadStations()
  }
})

onBeforeUnmount(() => {
  if (map) {
    map.destroy()
    map = null
  }
})
</script>

<style scoped>
.map-container {
  padding: 16px;
}

.map-toolbar {
  background: #fff;
  padding: 16px 20px;
  border-radius: 4px;
  margin-bottom: 16px;
}

.map-wrapper {
  position: relative;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
}

.map {
  width: 100%;
  height: 520px;
}

.map-error {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}

.station-list {
  margin-top: 16px;
  background: #fff;
  padding: 16px 20px;
  border-radius: 4px;
}

.station-list-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}
</style>
