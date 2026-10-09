<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="待处理" value="0" />
            <el-option label="处理中" value="1" />
            <el-option label="已处理" value="2" />
            <el-option label="已关闭" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" placeholder="全部类型" clearable style="width: 140px">
            <el-option label="设备故障" value="1" />
            <el-option label="充电异常" value="2" />
            <el-option label="计费问题" value="3" />
            <el-option label="其他" value="4" />
          </el-select>
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
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户" width="130" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag>{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link :icon="View" @click="handleDetail(row)">详情</el-button>
            <el-button
              v-if="row.status !== 2 && row.status !== 3"
              type="primary"
              link
              :icon="Edit"
              @click="handleProcess(row)"
            >处理</el-button>
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
    <el-dialog v-model="detailVisible" title="反馈详情" width="640px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeText(detail.type) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)">{{ statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="反馈内容" :span="2">
          <div class="content-text">{{ detail.content }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="图片" :span="2">
          <div v-if="detail.images && detail.images.length" class="image-list">
            <el-image
              v-for="(img, idx) in detail.images"
              :key="idx"
              :src="img"
              :preview-src-list="detail.images"
              fit="cover"
              class="feedback-image"
            />
          </div>
          <span v-else>无</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.reply" label="处理回复" :span="2">
          <div class="content-text">{{ detail.reply }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 处理对话框 -->
    <el-dialog v-model="processVisible" title="处理反馈" width="560px">
      <el-descriptions :column="1" border style="margin-bottom: 16px">
        <el-descriptions-item label="标题">{{ currentRow.title }}</el-descriptions-item>
        <el-descriptions-item label="内容">{{ currentRow.content }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="processFormRef" :model="processForm" :rules="processRules" label-width="90px">
        <el-form-item label="处理状态" prop="status">
          <el-select v-model="processForm.status" style="width: 100%">
            <el-option label="处理中" :value="1" />
            <el-option label="已处理" :value="2" />
            <el-option label="已关闭" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理回复" prop="reply">
          <el-input
            v-model="processForm.reply"
            type="textarea"
            :rows="4"
            placeholder="请输入处理回复"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, View, Edit } from '@element-plus/icons-vue'
import {
  getFeedbackList, getFeedbackDetail, handleFeedback
} from '@/api/feedback'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryParams = reactive({
  page: 1,
  size: 10,
  status: '',
  type: ''
})

const typeText = (type) => {
  const map = { 1: '设备故障', 2: '充电异常', 3: '计费问题', 4: '其他' }
  return map[type] ?? '其他'
}

const statusText = (status) => {
  const map = { 0: '待处理', 1: '处理中', 2: '已处理', 3: '已关闭' }
  return map[status] ?? '未知'
}

const statusTagType = (status) => {
  const map = { 0: 'danger', 1: 'warning', 2: 'success', 3: 'info' }
  return map[status] ?? 'info'
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getFeedbackList(queryParams)
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
  queryParams.status = ''
  queryParams.type = ''
  queryParams.page = 1
  fetchData()
}

// 详情
const detailVisible = ref(false)
const detail = ref({})

const handleDetail = async (row) => {
  try {
    const res = await getFeedbackDetail(row.id)
    const data = res.data || row
    // 图片字段兼容字符串/数组
    if (typeof data.images === 'string' && data.images) {
      data.images = data.images.split(',').filter(Boolean)
    } else if (!data.images) {
      data.images = []
    }
    detail.value = data
  } catch (e) {
    detail.value = { ...row, images: [] }
  }
  detailVisible.value = true
}

// 处理
const processVisible = ref(false)
const processFormRef = ref()
const currentRow = ref({})

const processForm = reactive({
  id: undefined,
  status: 2,
  reply: ''
})

const processRules = {
  status: [{ required: true, message: '请选择处理状态', trigger: 'change' }],
  reply: [{ required: true, message: '请输入处理回复', trigger: 'blur' }]
}

const handleProcess = (row) => {
  currentRow.value = row
  processForm.id = row.id
  processForm.status = 2
  processForm.reply = ''
  processVisible.value = true
}

const handleSubmit = () => {
  processFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await handleFeedback(processForm)
      ElMessage.success('处理成功')
      processVisible.value = false
      fetchData()
    } catch (e) {
      // ignore
    } finally {
      submitLoading.value = false
    }
  })
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.content-text {
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.feedback-image {
  width: 100px;
  height: 100px;
  border-radius: 4px;
}
</style>
