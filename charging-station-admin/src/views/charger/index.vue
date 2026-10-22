<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="充电桩编号">
          <el-input
            v-model="queryParams.code"
            placeholder="请输入编号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="所属站点">
          <el-select
            v-model="queryParams.stationId"
            placeholder="全部站点"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="item in stationOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" placeholder="全部类型" clearable style="width: 130px">
            <el-option label="直流快充" value="1" />
            <el-option label="交流慢充" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="空闲" value="0" />
            <el-option label="使用中" value="1" />
            <el-option label="故障" value="2" />
            <el-option label="离线" value="3" />
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
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增充电桩</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="code" label="编号" width="140" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="所属站点" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ stationNameOf(row.stationId) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <!-- 与后端/数据库保持一致: 1-交流慢充, 2-直流快充 -->
            <el-tag :type="row.type === 2 ? 'danger' : 'warning'">
              {{ row.type === 1 ? '交流慢充' : (row.type === 2 ? '直流快充' : '未知') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="power" label="功率(kW)" width="100" align="center" />
        <el-table-column prop="gunCount" label="枪数" width="80" align="center" />
        <el-table-column prop="price" label="单价(元/度)" width="110" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="所属站点" prop="stationId">
          <el-select
            v-model="form.stationId"
            placeholder="请选择站点"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="item in stationOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="编号" prop="code">
          <el-input v-model="form.code" placeholder="请输入充电桩编号" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入充电桩名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">交流慢充</el-radio>
            <el-radio :value="2">直流快充</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="功率(kW)" prop="power">
          <el-input-number v-model="form.power" :min="0" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="枪数" prop="gunCount">
          <el-input-number v-model="form.gunCount" :min="1" :max="20" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单价(元/度)" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <!-- 与后端/数据库保持一致: 0-故障, 1-空闲, 2-使用中 -->
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="故障" :value="0" />
            <el-option label="空闲" :value="1" />
            <el-option label="使用中" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getChargerList, addCharger, updateCharger, deleteCharger
} from '@/api/charger'
import { getAllStations } from '@/api/station'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const stationOptions = ref([])

const queryParams = reactive({
  page: 1,
  size: 10,
  code: '',
  stationId: '',
  type: '',
  status: ''
})

// 状态: 0-故障, 1-空闲, 2-使用中（与后端 t_charger.status 一致）
const statusText = (status) => {
  const map = { 0: '故障', 1: '空闲', 2: '使用中' }
  return map[status] ?? '未知'
}

const statusTagType = (status) => {
  const map = { 0: 'danger', 1: 'success', 2: 'primary' }
  return map[status] ?? 'info'
}

// 后端 Charger 只返回 stationId，站点名由下拉选项映射，避免列表出现空列
const stationNameOf = (stationId) => {
  const station = stationOptions.value.find((item) => item.id === stationId)
  return station ? station.name : '-'
}

const fetchStations = async () => {
  try {
    const res = await getAllStations()
    stationOptions.value = res.data || []
  } catch (e) {
    stationOptions.value = []
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getChargerList(queryParams)
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
  queryParams.code = ''
  queryParams.stationId = ''
  queryParams.type = ''
  queryParams.status = ''
  queryParams.page = 1
  fetchData()
}

// 新增/编辑
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)

const form = reactive({
  id: undefined,
  stationId: undefined,
  code: '',
  name: '',
  type: 1,
  power: 60,
  gunCount: 2,
  price: 1.5,
  status: 1
})

const formRules = {
  stationId: [{ required: true, message: '请选择站点', trigger: 'change' }],
  code: [{ required: true, message: '请输入编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  power: [{ required: true, message: '请输入功率', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const resetForm = () => {
  Object.assign(form, {
    id: undefined,
    stationId: undefined,
    code: '',
    name: '',
    type: 1,
    power: 60,
    gunCount: 2,
    price: 1.5,
    status: 1
  })
  formRef.value?.clearValidate()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增充电桩'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑充电桩'
  resetForm()
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await updateCharger(form)
        ElMessage.success('修改成功')
      } else {
        await addCharger(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (e) {
      // ignore
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除充电桩「${row.name || row.code}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await deleteCharger(row.id)
      ElMessage.success('删除成功')
      fetchData()
    })
    .catch(() => {})
}

onMounted(() => {
  fetchStations()
  fetchData()
})
</script>

<style scoped>
.table-toolbar {
  margin-bottom: 16px;
}
</style>
