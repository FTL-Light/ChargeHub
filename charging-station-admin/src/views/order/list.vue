<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="订单编号">
          <el-input
            v-model="queryParams.orderNo"
            placeholder="请输入订单编号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input
            v-model="queryParams.phone"
            placeholder="请输入手机号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="充电中" :value="1" />
            <el-option label="待支付" :value="2" />
            <el-option label="已完成" :value="3" />
            <el-option label="已取消" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="站点">
          <el-select
            v-model="queryParams.stationId"
            placeholder="全部站点"
            clearable
            filterable
            style="width: 180px"
          >
            <el-option
              v-for="item in stationOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="orderNo" label="订单编号" width="180" show-overflow-tooltip />
        <el-table-column label="用户ID" width="90" align="center">
          <template #default="{ row }">{{ row.userId }}</template>
        </el-table-column>
        <el-table-column label="站点" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ stationNameOf(row.stationId) }}</template>
        </el-table-column>
        <el-table-column label="充电桩" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ chargerNameOf(row.chargerId) }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="时长" width="100" align="center">
          <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
        </el-table-column>
        <el-table-column label="充电量(度)" width="110" align="right">
          <template #default="{ row }">{{ row.electricity != null ? row.electricity : '-' }}</template>
        </el-table-column>
        <el-table-column label="金额(元)" width="110" align="right">
          <template #default="{ row }">
            {{ row.payAmount != null ? Number(row.payAmount).toFixed(2) : '0.00' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </div>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="640px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单编号" :span="2">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
        <el-descriptions-item label="支付方式">{{ detail.payType === 2 ? '微信支付' : '余额支付' }}</el-descriptions-item>
        <el-descriptions-item label="站点">{{ stationNameOf(detail.stationId) }}</el-descriptions-item>
        <el-descriptions-item label="充电桩">{{ chargerNameOf(detail.chargerId) }}</el-descriptions-item>
        <el-descriptions-item label="充电枪ID">{{ detail.gunId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单价(元/度)">{{ detail.price != null ? detail.price : '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatTime(detail.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatTime(detail.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="时长">{{ formatDuration(detail.duration) }}</el-descriptions-item>
        <el-descriptions-item label="充电量(度)">{{ detail.electricity != null ? detail.electricity : '-' }}</el-descriptions-item>
        <el-descriptions-item label="金额(元)">{{ detail.payAmount != null ? Number(detail.payAmount).toFixed(2) : '0.00' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)">{{ statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, View } from '@element-plus/icons-vue'
import { getOrderList } from '@/api/order'
import { getAllStations } from '@/api/station'
import { getChargerList } from '@/api/charger'
import { formatTime, formatDuration } from '@/utils/format'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const stationOptions = ref([])
const chargerOptions = ref([])
const dateRange = ref([])

const queryParams = reactive({
  page: 1,
  size: 10,
  orderNo: '',
  phone: '',
  status: '',
  stationId: '',
  startTime: '',
  endTime: ''
})

// 订单状态与后端 Order.status 一致: 0-已取消, 1-充电中, 2-待支付, 3-已完成
const statusText = (status) => {
  const map = { 0: '已取消', 1: '充电中', 2: '待支付', 3: '已完成' }
  return map[status] ?? '未知'
}

const statusTagType = (status) => {
  const map = { 0: 'info', 1: 'primary', 2: 'warning', 3: 'success' }
  return map[status] ?? 'info'
}

// 后端返回的是 ID，列表展示需要名称，用已加载的选项做映射
const stationNameOf = (stationId) => {
  const station = stationOptions.value.find((item) => item.id === stationId)
  return station ? station.name : '-'
}

const chargerNameOf = (chargerId) => {
  const charger = chargerOptions.value.find((item) => item.id === chargerId)
  return charger ? (charger.name || charger.code) : '-'
}

const handleDateChange = (val) => {
  if (val && val.length === 2) {
    queryParams.startTime = val[0]
    queryParams.endTime = val[1]
  } else {
    queryParams.startTime = ''
    queryParams.endTime = ''
  }
}

const fetchStations = async () => {
  try {
    const res = await getAllStations()
    stationOptions.value = res.data || []
  } catch (e) {
    stationOptions.value = []
  }
}

const fetchChargers = async () => {
  try {
    const res = await getChargerList({ current: 1, size: 500 })
    chargerOptions.value = res.data.records || []
  } catch (e) {
    chargerOptions.value = []
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getOrderList(queryParams)
    tableData.value = res.data.records || res.data.list || res.data || []
    total.value = res.data.total || 0
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.page = 1
  fetchData()
}

const handleReset = () => {
  queryParams.orderNo = ''
  queryParams.phone = ''
  queryParams.status = ''
  queryParams.stationId = ''
  queryParams.startTime = ''
  queryParams.endTime = ''
  dateRange.value = []
  queryParams.page = 1
  fetchData()
}

// 详情
const detailVisible = ref(false)
const detail = ref({})

const handleDetail = (row) => {
  detail.value = row
  detailVisible.value = true
}

onMounted(() => {
  fetchStations()
  fetchChargers()
  fetchData()
})
</script>

<style scoped>
</style>
