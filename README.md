# 布丁助手

一个基于大语言模型的个人助手应用，支持配置多种 AI 模型。

> 优先实现代码辅助功能，逐步扩展为完整的个人助手。

## 功能特性

### ✅ Phase 1 (实现中)
- 对话界面 - 类似 ChatGPT/Claude 的聊天 UI
- 模型配置页面
  - 支持 OpenAI / Anthropic / 自定义 API
  - 可配置 API Key、Base URL、模型名称
  - 支持温度、max_tokens 参数调节
- 对话功能
  - 对话功能
- 任务管理
  - 支持定时任务和一次性任务
  - 每天/每周频率设置
  - 任务开关、编辑、删除
  - 快速创建任务（自然语言输入）

### 🚧 Phase 2 (待实现)
- 对话历史管理(重置会话)

## ROADMAP

### 💬 对话交互（核心）
- [x] 模型 AI 对话
- [ ] 对话历史管理(重置会话)
- [ ] 个性化设置（人设/语气/风格）
- [x] Markdown 渲染
- [ ] 对话上下文记忆优化
- [ ] 对话记录搜索
- [ ] 会话分享
- [ ] 多模态输入（语音/图片）
- [ ] 语音播报

### 🧠 记忆与知识管理
- [ ] 长期记忆存储
- [ ] 用户偏好学习
- [ ] 搜索引擎集成
- [ ] 本地文件搜索

### 🔧 代码辅助
- [ ] AI 对话编程
  - [ ] 内嵌TypeScript编码环境
  - [ ] 内嵌Python编码环境
- [ ] 代码语法高亮
- [ ] 文件改动预览
- [ ] Git 操作集成

### ✅ 任务管理
- [x] 任务创建与管理
- [x] 定时任务执行
- [ ] 任务置顶与拖拽排序
- [ ] 提醒通知
- [ ] 任务结果展示
  - [ ] 通知推送（简单任务）
  - [ ] 应用内详情页（复杂任务）
- [ ] 日历视图
- [ ] 日程同步

### 📂 文档与数据
- [ ] 文档处理（PDF/Word/Excel）
- [ ] 数据分析与可视化

### 🛠️ 工具与插件
- [ ] 工具调用框架
- [ ] 插件市场
- [ ] 自定义工具接入

### 🎯 自动化
- [ ] 工作流编排
- [ ] 快捷指令
- [ ] 跨应用联动

### 📧 信息处理
- [ ] 微信/钉钉消息摘要
- [ ] 消息智能回复
- [ ] 文档翻译

## 技术栈
- **语言**: Kotlin
- **UI**: Jetpack Compose
- **架构**: MVVM
- **网络**: Retrofit + OkHttp
- **存储**: Room + DataStore

## 项目结构

```
app/src/main/java/com/pudding/ai/
├── AssistantApp.kt              # Application 类
├── MainActivity.kt              # 主 Activity
├── MainScreen.kt                # 主界面 Composable（导航）
├── ConversationViewModel.kt     # 对话 ViewModel
├── TaskViewModel.kt             # 任务 ViewModel
├── data/
│   ├── api/                     # API 接口定义
│   │   └── ChatApi.kt           # 聊天 API 模型
│   ├── database/                # Room 数据库
│   │   ├── Database.kt          # 数据库入口
│   │   ├── MessageDao.kt        # 消息 DAO
│   │   ├── ConversationDao.kt   # 对话 DAO
│   │   ├── TaskDao.kt           # 任务 DAO
│   │   └── TaskExecutionDao.kt  # 任务执行记录 DAO
│   ├── model/                   # 数据模型
│   │   ├── Message.kt           # 消息实体
│   │   ├── Conversation.kt      # 对话实体
│   │   ├── ModelConfig.kt       # 模型配置
│   │   └── Task.kt              # 任务相关模型
│   └── repository/              # 数据仓库
│       ├── ChatRepository.kt    # 聊天仓库
│       └── SettingsRepository.kt # 设置仓库
├── service/                     # 后台服务
│   ├── TaskScheduler.kt         # 任务调度器
│   ├── TaskTools.kt             # Function Call 工具定义
│   ├── TaskToolExecutor.kt      # 工具执行器
│   ├── TaskExecutionReceiver.kt # 任务执行广播接收器
│   └── BootReceiver.kt          # 开机启动接收器
└── ui/                          # 界面组件
    ├── chat/                    # 聊天界面
    │   ├── ChatScreen.kt        # 聊天主界面
    │   └── PrismBundleConfig.kt # 代码高亮配置
    ├── settings/                # 设置界面
    │   └── SettingsScreen.kt
    ├── tasks/                   # 任务界面
    │   ├── TasksScreen.kt
    │   ├── TaskEditScreen.kt
    │   └── ExecutionHistoryScreen.kt
    └── theme/                   # 主题配置
        └── Theme.kt
```

## 如何使用

### 1. 克隆项目
```bash
git clone https://github.com/your-username/pudding.git
```

### 2. 用 Android Studio 打开
- Android Studio Hedgehog 或更高版本
- JDK 17

### 3. 配置模型
在应用中点击设置按钮，配置：
- API Key（从 OpenAI/Anthropic 获取）
- Base URL（默认 OpenAI，可改为兼容 API）
- 模型名称（如 gpt-4, claude-3-opus）

### 4. 运行
连接 Android 设备或模拟器，点击运行

## 支持的 API

### OpenAI
- Base URL: `https://api.openai.com/v1`
- 模型: gpt-4, gpt-4-turbo, gpt-3.5-turbo

### Anthropic
- Base URL: `https://api.anthropic.com/v1`
- 模型: claude-3-opus, claude-3-sonnet, claude-3-haiku

### 自定义
支持任何 OpenAI 兼容的 API，如：
- 本地部署的 Ollama
- Azure OpenAI
- 其他兼容服务

## 许可证
MIT License