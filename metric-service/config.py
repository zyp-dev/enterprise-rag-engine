import os

# os.getenv("变量名", "默认值")：先从环境变量读，读不到就用默认值
# ⚠️ 密钥不在代码里写死：发布仓库前已清空，本地运行时通过环境变量注入：
#    Windows:  set LLM_API_KEY=sk-xxx
#    PowerShell: $env:LLM_API_KEY="sk-xxx"
#    或在启动脚本里 set 后运行 python api_server.py

# 裁判 LLM 配置（千问/DeepSeek 均可，OpenAI 兼容接口）
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://maas.qianwenaiapi.com/compatible-mode/v1")
LLM_MODEL = os.getenv("LLM_MODEL", "qwen-plus")

# 向量模型配置（千问，同平台同 key）
EMBEDDING_API_KEY = os.getenv("EMBEDDING_API_KEY", "")
EMBEDDING_BASE_URL = os.getenv("EMBEDDING_BASE_URL", "https://maas.qianwenaiapi.com/compatible-mode/v1")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-v4")

# 【备用】想换回 DeepSeek 当裁判 LLM 时，把 LLM_BASE_URL/LLM_MODEL 换成：
#   https://api.deepseek.com / deepseek-chat
