import { defineStore } from 'pinia'
import { ref } from 'vue'

// 创建store
export const useChatStore = defineStore('chat', () => {

  // 模型列表：name 必须与 AI_BASE_URL 对应服务商的 /models 返回的 ID 完全一致，否则接口返回 404 Model not exist
  // 增删改这里即可，不需要动后端；selected 只应有一个为 true
  const models = ref([
    { id: 1, name: 'qwen3.8-flash', icon: 'deepseek-logo', description: '默认 · 响应快', selected: true },
    { id: 2, name: 'qwen3.8-max', icon: 'deepseek-logo', description: '能力最强', selected: false },
    { id: 3, name: 'deepseek-v4.1-flash', icon: 'ai-robot-logo', description: 'DeepSeek 轻量', selected: false },
    { id: 4, name: 'glm-5.3', icon: 'ai-robot-logo', description: '智谱 GLM', selected: false },
    { id: 5, name: 'kimi-k3', icon: 'ai-robot-logo', description: '长上下文', selected: false },
  ])

  // 选中的模型，默认为第一个
  const selectedModel = ref(models.value[0])

  // 联网搜索状态，默认为false
  const isNetworkSearchSelected = ref(false)

  // 更新选中的模型
  function updateSelectedModel(model) {
    // 将所有模型的 selected 置为 false
    models.value.forEach(m => {
      m.selected = false;
    });
    
    // 将选中模型的 selected 置为 true
    model.selected = true;
    
    // 更新当前选中的模型
    selectedModel.value = model;
  }

  // 更新联网搜索状态
  function updateNetworkSearchStatus(status) {
    isNetworkSearchSelected.value = status
  }

  // 对外暴露相关变量与方法
  return { models, selectedModel, isNetworkSearchSelected, updateSelectedModel, updateNetworkSearchStatus }
},
{
  // 开启持久化
  persist: true,
})