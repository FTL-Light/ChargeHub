<template>
  <div class="app-container">
    <div class="config-container">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon><Setting /></el-icon>
            <span>AI 客服配置</span>
          </div>
        </template>

        <el-form
          ref="formRef"
          v-loading="loading"
          :model="form"
          :rules="formRules"
          label-width="120px"
          style="max-width: 720px"
        >
          <el-form-item label="AI 提供商" prop="provider">
            <el-select v-model="form.provider" placeholder="请选择AI提供商" style="width: 100%">
              <el-option label="OpenAI" value="openai" />
              <el-option label="阿里通义千问" value="qwen" />
              <el-option label="百度文心一言" value="wenxin" />
              <el-option label="智谱 GLM" value="zhipu" />
              <el-option label="DeepSeek" value="deepseek" />
              <el-option label="自定义" value="custom" />
            </el-select>
          </el-form-item>
          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model="form.apiKey"
              placeholder="请输入 API Key"
              show-password
              clearable
            />
          </el-form-item>
          <el-form-item label="API URL" prop="apiUrl">
            <el-input v-model="form.apiUrl" placeholder="请输入 API URL（如 https://api.openai.com/v1/chat/completions）" />
          </el-form-item>
          <el-form-item label="模型名称" prop="model">
            <el-input v-model="form.model" placeholder="请输入模型名称（如 gpt-3.5-turbo）" />
          </el-form-item>
          <el-form-item label="系统提示词" prop="systemPrompt">
            <el-input
              v-model="form.systemPrompt"
              type="textarea"
              :rows="8"
              placeholder="请输入系统提示词，用于设定AI客服的角色和行为"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
              保存配置
            </el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button type="success" plain @click="handleTest">测试连接</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { getAiConfig, updateAiConfig } from '@/api/system'

const loading = ref(false)
const submitLoading = ref(false)
const formRef = ref()

const form = reactive({
  provider: 'openai',
  apiKey: '',
  apiUrl: '',
  model: 'gpt-3.5-turbo',
  systemPrompt: ''
})

const formRules = {
  provider: [{ required: true, message: '请选择AI提供商', trigger: 'change' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  model: [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
}

const fetchConfig = async () => {
  loading.value = true
  try {
    const res = await getAiConfig()
    const data = res.data || {}
    Object.assign(form, {
      provider: data.provider || 'openai',
      apiKey: data.apiKey || '',
      apiUrl: data.apiUrl || '',
      model: data.model || 'gpt-3.5-turbo',
      systemPrompt: data.systemPrompt || ''
    })
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await updateAiConfig(form)
      ElMessage.success('保存成功')
    } catch (e) {
      // ignore
    } finally {
      submitLoading.value = false
    }
  })
}

const handleReset = () => {
  fetchConfig()
}

const handleTest = () => {
  ElMessage.info('测试连接功能开发中')
}

onMounted(() => {
  fetchConfig()
})
</script>

<style scoped>
.config-container {
  max-width: 900px;
}

.card-header {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}

.card-header .el-icon {
  margin-right: 8px;
  color: #667eea;
}
</style>
