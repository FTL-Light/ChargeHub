<template>
  <div class="app-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card stat-card-blue">
          <div class="stat-icon">
            <el-icon><List /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">总订单数</div>
            <div class="stat-value">{{ stats.totalOrders }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card stat-card-green">
          <div class="stat-icon">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">总用户数</div>
            <div class="stat-value">{{ stats.totalUsers }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card stat-card-orange">
          <div class="stat-icon">
            <el-icon><Location /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">站点数量</div>
            <div class="stat-value">{{ stats.totalStations }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card stat-card-purple">
          <div class="stat-icon">
            <el-icon><Money /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">总收入(元)</div>
            <div class="stat-value">{{ stats.totalIncome }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="16">
        <div class="chart-card">
          <div class="chart-title">近7天订单趋势</div>
          <div ref="trendChartRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="chart-card">
          <div class="chart-title">充电类型分布</div>
          <div ref="pieChartRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getOrderStatistics } from '@/api/order'

const stats = reactive({
  totalOrders: 0,
  totalUsers: 0,
  totalStations: 0,
  totalIncome: '0.00'
})

const trendChartRef = ref()
const pieChartRef = ref()
let trendChart = null
let pieChart = null

const fetchAllData = async () => {
  try {
    const res = await getOrderStatistics()
    const data = res.data || {}

    // 统计卡片数据
    stats.totalOrders = data.totalOrders ?? 0
    stats.totalUsers = data.totalUsers ?? 0
    stats.totalStations = data.totalStations ?? 0
    stats.totalIncome = data.totalIncome != null
      ? Number(data.totalIncome).toFixed(2)
      : '0.00'

    // 趋势数据
    const trendList = data.trend || []
    const trendData = {
      dates: [],
      orders: [],
      incomes: []
    }
    trendList.forEach((item) => {
      trendData.dates.push(item.date)
      trendData.orders.push(item.orderCount || 0)
      trendData.incomes.push(Number(item.income) || 0)
    })

    await nextTick()
    initTrendChart(trendData)

    // 充电类型分布
    const typeDist = data.chargerTypeDistribution || []
    const pieData = [
      { value: 0, name: '直流快充' },
      { value: 0, name: '交流慢充' }
    ]
    typeDist.forEach((item) => {
      if (item.type === 1) {
        pieData[0].value = item.count || 0
      } else if (item.type === 2) {
        pieData[1].value = item.count || 0
      }
    })
    initPieChart(pieData)
  } catch (e) {
    // 失败时使用空数据
    await nextTick()
    initTrendChart({ dates: [], orders: [], incomes: [] })
    initPieChart([
      { value: 0, name: '直流快充' },
      { value: 0, name: '交流慢充' }
    ])
  }
}

const initTrendChart = (data) => {
  if (!trendChartRef.value) return
  if (trendChart) trendChart.dispose()
  trendChart = echarts.init(trendChartRef.value)
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['订单数', '收入(元)'],
      bottom: 0
    },
    grid: { left: 50, right: 50, top: 30, bottom: 50 },
    xAxis: {
      type: 'category',
      data: data.dates,
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#606266' }
    },
    yAxis: [
      {
        type: 'value',
        name: '订单数',
        position: 'left',
        axisLine: { show: true, lineStyle: { color: '#667eea' } },
        axisLabel: { color: '#606266' },
        splitLine: { lineStyle: { color: '#f0f0f0' } }
      },
      {
        type: 'value',
        name: '收入(元)',
        position: 'right',
        axisLine: { show: true, lineStyle: { color: '#764ba2' } },
        axisLabel: { color: '#606266', formatter: '{value}' },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '订单数',
        type: 'line',
        smooth: true,
        yAxisIndex: 0,
        data: data.orders,
        itemStyle: { color: '#667eea' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(102,126,234,0.4)' },
            { offset: 1, color: 'rgba(102,126,234,0.05)' }
          ])
        }
      },
      {
        name: '收入(元)',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: data.incomes,
        itemStyle: { color: '#764ba2' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(118,75,162,0.4)' },
            { offset: 1, color: 'rgba(118,75,162,0.05)' }
          ])
        }
      }
    ]
  })
}

const initPieChart = (pieData) => {
  if (!pieChartRef.value) return
  if (pieChart) pieChart.dispose()
  pieChart = echarts.init(pieChartRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [
      {
        name: '充电类型',
        type: 'pie',
        radius: ['40%', '65%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: false, position: 'center' },
        emphasis: {
          label: { show: true, fontSize: 16, fontWeight: 'bold' }
        },
        labelLine: { show: false },
        data: pieData,
        color: ['#409eff', '#67c23a']
      }
    ]
  })
}

const handleResize = () => {
  trendChart && trendChart.resize()
  pieChart && pieChart.resize()
}

onMounted(async () => {
  await fetchAllData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart && trendChart.dispose()
  pieChart && pieChart.dispose()
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: #fff;
  margin-right: 16px;
  flex-shrink: 0;
}

.stat-card-blue .stat-icon {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-card-green .stat-icon {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.stat-card-orange .stat-icon {
  background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
}

.stat-card-purple .stat-icon {
  background: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%);
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 26px;
  font-weight: 600;
  color: #303133;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-left: 10px;
  border-left: 3px solid #667eea;
}

.chart-box {
  height: 360px;
}
</style>
