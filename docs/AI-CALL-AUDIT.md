# 大模型调用链路检测与修复报告（v2）

| 项目 | 内容 |
| --- | --- |
| 项目 | character_app（假如是你）v1.3.0 |
| 检测对象 | `ai/` 全部代码、`AppViewModel` 的 AI 调用点、设置页、网络与配置持久化 |
| 检测日期 | 2026-11-02（第一轮检测；第二轮参数扩展 + 审计修复 + 复检） |
| 检测环境 | Windows、JDK 17.0.2（仓库内 `.toolchain`）、Gradle Wrapper、Android SDK 35 |
| 验证方式 | 静态审查 + 84 项 JVM 单测 + 本机 mock 真实 HTTP/SSE + 公开推理模型真实联调 + 服务商端点探测 |
| 总体结论 | 未发现 P1；第一轮 6 项 P2 中 5 项已修复，P2-5 传输侧已加固；剩余 4 项低优先级改进 |

---

## 1. 结论摘要

1. **调用方式正确**：统一 `POST {baseUrl}/chat/completions`，`Authorization: Bearer <key>`，请求体符合 OpenAI Chat Completions 规范；`/models` 用于获取模型列表。
2. **第二轮新增模型参数自定义**：设置页可填 Temperature、Top P、最大输出 Token、Frequency/Presence Penalty、Seed 和自定义 JSON，效果类似 Dify 的 LLM 节点参数面板；留空则沿用各场景默认值。
3. **推理模型可正常使用**：`reasoning_content` / `reasoning` 与正文分离；o1/o3/GPT-5 的 `max_completion_tokens` 与 `temperature=1` 限制会在 400 后自动降级；只返回推理、没有正文时会给出明确提示。
4. **第一轮风险已处理**：错误详情透出、保存前 URL 校验、流式取消、长输出截断、429 Retry-After、Tavily 失败提示、目标判定失败提示等均已修复。
5. **验证充分度**：84 项单测全绿；本机 mock 跑通连接测试、SSE、401 映射；公开 `gpt-oss-20b` 推理模型跑通真实连接、流式正文/推理分离、自定义参数与 JSON 模式。
6. **仍有限制**：没有商业 API Key，具体账号与模型可用性需在 App 内「测试连接」确认；API Key 仍为本机明文存储；Azure 与非标准路径服务商未适配。

---

## 2. 检测范围与证据

### 2.1 审查 / 修改文件

| 文件 | 作用 |
| --- | --- |
| `ai/AiClient.kt` | 流式/非流式、重试、参数降级、SSE、reasoning、截断、取消、错误详情 |
| `ai/AiConfig.kt` | 配置模型、8 个预设、参数字段、校验、SharedPreferences 读写 |
| `ai/UrlTools.kt` | Base URL 规范化、端点拼接、配置解析、公网明文判断 |
| `ai/AiErrors.kt` | HTTP/IO 错误文案、`AiHttpException.detail`、`AiTruncatedException` |
| `ai/HttpSupport.kt` | OkHttp ↔ 协程桥接、`AiResponse` 保留 Call 以支持取消 |
| `ai/SearchService.kt` | Tavily 三路检索、失败标记、模型知识兜底 |
| `ai/Prompts.kt` | 各业务提示词与消息组装 |
| `ui/AppViewModel.kt` | 设置、检索、圣经、故事栏、开场、行动、目标判定、结局、后日谈、润色 |
| `ui/UiState.kt` | 设置页参数输入状态 |
| `ui/screens/SettingsScreen.kt` | 模型参数面板、公网 HTTP 警告 |
| `data/Models.kt` / `data/ProjectCodec.kt` | `SearchData.searchFailed` 与存档兼容 |
| `AndroidManifest.xml` / `res/xml/network_security_config.xml` | INTERNET、明文流量策略 |

### 2.2 自动化测试

命令：

```powershell
$env:JAVA_HOME='C:\workspace\deepseek\agents\.toolchain\jdk\jdk-17.0.2'
.\gradlew.bat :app:testDebugUnitTest --offline
```

| 测试套件 | 用例数 | 结果 |
| --- | ---: | --- |
| `AiClientTest` | 20 | 全部通过 |
| `AiConfigTest` | 9 | 全部通过 |
| `PromptsTest` | 14 | 全部通过 |
| `UrlToolsTest` | 7 | 全部通过 |
| `ProjectCodecTest` | 18 | 全部通过 |
| `ProjectStoreTest` | 5 | 全部通过 |
| `RulesTest` | 11 | 全部通过 |
| **合计** | **84** | **0 失败 / 0 错误** |

AI 相关（`ai` 包）50 项，覆盖：

- SSE 增量、`[DONE]`、注释行、空 delta；
- 非流式降级、401 不重试、`/models` 解析；
- `max_tokens → max_completion_tokens`、删除 `temperature`、清空可选参数；
- `reasoning_content` / `reasoning` 与正文分离；
- `finish_reason=length` 截断异常；
- 429 尊重 `Retry-After`；
- 取消流式读取时 `Call.cancel()` 打断阻塞 `readLine()`，且不触发回退；
- 数组 content、SSE 包装的非流式响应；
- 用户参数合并、max_tokens 覆盖场景值、JSON 参数校验；
- Base URL 规范化、旧配置兼容、公网明文判断。

### 2.3 本机 mock 端到端

用 `character/backend/mock_openai_server.py` 在 `127.0.0.1:4901` 启动服务，App 侧使用真实 `AiClient`：

| 验证项 | 结果 |
| --- | --- |
| `testConnection()` | `model=mock-chat`，回复「连接成功」 |
| `streamChat()` | 4 个 chunk，拼接为「连接成功」 |
| 错误 API Key | `AiHttpException(401)`，文案以「AI 服务鉴权失败」开头并带详情 |
| 真实 HTTP 往返 | 成功（FastAPI + OkHttp + SSE） |

> mock 的提示词标记已同步到当前 `Prompts.kt`（小说考据师、小说考据助手、结局总结等）。

### 2.4 真实推理模型联调

公开 `gpt-oss-20b` 推理模型，经本机流式代理请求，App 侧仍走真实 `AiClient` + OkHttp：

| 验证项 | 结果 |
| --- | --- |
| 连接测试 | 返回「连接成功」 |
| 流式输出 | 正文「测试通过」，`reasoning` 单独收集，未混入正文；`finish_reason=stop` |
| 自定义参数 + JSON 模式 | `temperature/top_p/response_format/reasoning_effort` 链路成功，返回 `{"ok":true}` |
| 服务商限流 | 402 时展示原始错误，未误判为鉴权/模型错误 |

> 联调用例与代理脚本仅用于验证：测试类已删除，代理留在 `build/` 临时目录。

### 2.5 服务商端点探测

用无效 Key 探测与客户端相同的请求形状：

| 预设 | `/chat/completions` | `/models` | 判定 |
| --- | --- | --- | --- |
| OpenAI | 401 | 401 | 地址/路径/鉴权头正确 |
| DeepSeek | 401 | 401 | 正确 |
| 阿里百炼 | 401 | 401 | 正确 |
| 硅基流动 | 401 | 401 | 正确 |
| Moonshot | 401 | 401 | 正确 |
| 智谱 | 401 | 401 | 正确 |
| 火山方舟 | 401 | 401 | 正确 |
| Tavily `/search` | 401 | — | Bearer 与路径正确 |

> 401 只证明地址存在、路径正确、服务端解析了鉴权头，不证明 Key 有效或模型可输出。

---

## 3. 调用架构

```
界面（Settings / Home / Bible / Game / Epilogue）
  └─ AppViewModel（viewModelScope）
       ├─ SettingsStore.read() ── SharedPreferences（明文 JSON）
       ├─ AiClient
       │    ├─ streamChat()  → Flow<ChatChunk>（正文 + reasoning + finish_reason）
       │    ├─ chatOnce()    → 非流式
       │    ├─ listModels()  → GET {base}/models
       │    └─ testConnection() → 非流式，256 token，60 s
       │         └─ OkHttp
       │              ├─ POST {base}/chat/completions
       │              ├─ Authorization: Bearer <key>（空 Key → sk-local）
       │              ├─ 401/403/429 → 不降级
       │              ├─ 408/409/5xx → 最多重试 2 次
       │              ├─ 429 → 优先按 Retry-After 等待
       │              ├─ 400 → 按错误信息逐项降级参数
       │              └─ 流式失败且未输出正文 → 非流式回退一次
       └─ SearchService
            ├─ Tavily 三路并行，单路失败不影响其他
            ├─ 全部/部分失败标记 searchFailed 并提示
            └─ chatOnce() 汇总；无结果走模型知识兜底
```

### 3.1 各业务请求参数

| 场景 | 方法 | temperature | maxTokens | 超时 | 流式 |
| --- | --- | ---: | ---: | --- | --- |
| 连接测试 | chatOnce | 0.0 | 256 | call 60 s | 否 |
| 检索汇总 / 兜底 | chatOnce | 0.3 | 1400 | 默认 | 否 |
| 设定圣经 | streamChat | 0.5 | 6000 | 无 callTimeout（读 300 s） | 是 |
| 故事栏 | chatOnce | 0.95 | 1800 | call 90 s | 否 |
| 冒险开场 | streamChat | 0.9 | 2048 | 无 callTimeout | 是 |
| 行动推进 | streamChat | 0.9 | 1600 | 无 callTimeout | 是 |
| 目标判定 | chatOnce | 0.0 | 512 | call 20 s + 外层 20 s | 否 |
| 结局总结 | streamChat | 0.72 | 1400 | call 120 s + 外层 90 s | 是 |
| 后日谈 | streamChat | 0.85 | 1600 | call 120 s + 外层 120 s | 是 |
| 行动润色 | chatOnce | 0.6 | 300 | call 30 s + 外层 30 s | 否 |

> 用户设置采样参数后覆盖创作类场景；目标判定清空采样参数；全局 maxTokens 填写后覆盖场景值（连接测试、目标判定、润色保留固定预算）。留空完全沿用表中默认值。

---

## 4. 新功能：自定义模型参数

设置页新增「模型参数（可选）」：

| 参数 | 范围 | 留空时 |
| --- | --- | --- |
| Temperature | 0.0–2.0 | 使用场景默认值 |
| Top P | 0.0–1.0 | 不发送 |
| 最大输出 Token | 1–200000 | 使用场景值；填写后覆盖场景上限（推理模型可调高） |
| Frequency Penalty | -2.0–2.0 | 不发送 |
| Presence Penalty | -2.0–2.0 | 不发送 |
| Seed | 整数 | 不发送 |
| 自定义请求参数（JSON） | JSON 对象 | 不发送 |

规则：

- 自定义 JSON 先合并，再写核心字段；不能覆盖 `model/messages/stream`。
- 显式参数优先于自定义 JSON 中的同名键。
- 参数被服务商拒绝时自动降级，不会直接失败。
- 旧版配置没有这些字段时按未设置处理，不影响升级。

---

## 5. 问题修复对照

### 5.1 P2

| 编号 | 问题 | 状态 | 修复 |
| --- | --- | --- | --- |
| P2-1 | 推理模型不接受 `max_tokens` / 非默认 `temperature` | 已修复 | 400 后 `max_tokens → max_completion_tokens`、删除 `temperature/top_p/penalty/seed/response_format`；未知 400 清空可选参数；最多 4 次参数重试 |
| P2-2 | 400/403 错误详情被丢弃 | 已修复 | `extractApiErrorDetail()` 读取 `error.message/message/detail/msg`；`AiHttpException.detail` 保留 300 字；各状态码文案附带详情 |
| P2-3 | 保存配置不校验 URL | 已修复 | 保存/测试前调用 `normalizeBaseUrl`；保存时把规范化结果写回表单 |
| P2-4 | 流式取消不彻底 | 已修复 | `AiResponse` 保留 Call；取消观察者调用 `call.cancel()` 打断阻塞读；回退前 `ensureActive()` |
| P2-5 | 全局明文 HTTP + 密钥明文 | 部分修复 | 设置页对公网 `http://` 显示红色警告；NSC 仍允许局域网明文以兼容 Ollama；密钥加密未做 |
| P2-6 | 长输出与模型上限不匹配 | 已修复 | 全局 maxTokens 可覆盖场景上限（推理模型可调高）；解析 `finish_reason`；JSON 类任务遇 `length` 抛明确截断异常；Moonshot 预设改 32k |

### 5.2 P3

已处理：

- 429 尊重 `Retry-After`，注释与行为一致；
- 删除未使用的 `ResolvedAiConfig.timeoutSeconds`；
- `searchProvider` 大小写统一；
- SSE 错误块不再触发无意义回退；
- 非流式回退限制在「可能不支持流式」的状态与 IO 错误；
- 非流式响应被 SSE 包装时可解析；
- 数组 content、`reasoning_content/reasoning` 兼容；
- 目标判定失败/超时给出一次性提示；
- Tavily 部分/全部失败标记并提示；
- 403 文案可附详情；
- mock 提示词标记同步；
- 新增 22 项 AI 单测（`AiClientTest` 从 7 项扩到 20 项，新增 `AiConfigTest` 9 项）。

仍保留：

| 项 | 说明 |
| --- | --- |
| 密钥加密 | 仍为 SharedPreferences 明文；`allowBackup=false`、设置页掩码、公网 HTTP 警告已具备 |
| Azure OpenAI | Azure 使用 `api-key` 头与 `api-version` 查询参数，当前 URL 规范化不接受查询参数 |
| JSON 模式自动启用 | 已支持自定义 JSON 手动传 `response_format`，未对圣经/故事栏自动启用 |
| 逐 chunk 刷新 | 每个 chunk 更新一次 StateFlow，长文本有重组开销 |

---

## 6. 推理模型 / 非默认模型行为

| 场景 | 行为 |
| --- | --- |
| DeepSeek reasoner 风格 | 解析 `delta.reasoning_content`，只把 `content` 拼进正文 |
| gpt-oss 风格 | 解析 `delta.reasoning`，同样与正文分离 |
| OpenAI o 系列 / GPT-5 | 拒绝 `max_tokens` 时改用 `max_completion_tokens`；拒绝非默认 temperature 时删除该参数重试 |
| 只返回推理、无正文 | 抛明确错误：提示 Token 预算被推理消耗，建议调高最大输出或换非推理模型 |
| `finish_reason=length` | JSON 场景抛 `AiTruncatedException`，不再误报「无法解析」 |
| 数组 content | 兼容 `[{"type":"text","text":"..."}]` |
| 非流式 SSE 包装 | 按 `data:` 行拆分并拼接 |
| 取消生成 | `call.cancel()` 关闭连接，避免后台继续生成/计费 |

> 对用户设置的参数，如果服务商不支持，客户端会自动降级；但降级后该参数不会生效，这是兼容多服务商的必要取舍。

---

## 7. 复现与验证

```powershell
$env:JAVA_HOME='C:\workspace\deepseek\agents\.toolchain\jdk\jdk-17.0.2'

# 全量单测（84 项）
.\gradlew.bat :app:testDebugUnitTest --offline

# 仅 AI 包（50 项）
.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.ifitwereyou.character.ai.*"

# 构建
.\gradlew.bat :app:assembleDebug --offline

# 本地 mock
$env:MOCK_PORT='4901'
& C:\workspace\deepseek\agents\character\backend\.venv\Scripts\python.exe `
  C:\workspace\deepseek\agents\character\backend\mock_openai_server.py
```

App 内最终确认：

1. 设置页选择预设或填 Base URL / API Key / 模型；
2. 可选填模型参数；
3. 点「获取模型」确认地址与鉴权通路；
4. 点「测试连接」确认模型能回复；
5. 保存后回首页检索，观察设定圣经流式输出与 JSON 解析。

---

## 8. 剩余风险与建议

1. **没有商业 API Key**：真实 OpenAI/DeepSeek/百炼等账号的模型可用性、配额、内容审核仍需实测。
2. **密钥明文**：设备被 root/调试时可读；后续可用 Android Keystore 包装 AES 迁移。
3. **公网明文**：已提示，但未阻止；如面向普通用户，建议默认禁止公网 `http://` 并二次确认。
4. **非标准路径**：App 仍按「版本根路径 + /chat/completions」拼接；`/openai` 这类路径需要网关或后续增加「不自动补 /v1」开关。
5. **长上下文**：8k 模型 + 6000 输出仍可能超限；用户可用「最大输出 Token」压低，或换 32k+ 模型。

---

## 9. 参考

- [OpenAI Chat Completions API](https://platform.openai.com/docs/api-reference/chat)
- [openai-agents-python#5455：推理模型拒绝 max_tokens](https://github.com/openai/openai-agents-python/issues/5455)
- [openai-go：max_tokens 已 deprecated](https://pkg.go.dev/github.com/openai/openai-go/v3@v3.8.0)
- [Portkey：temperature 仅支持默认值 1](https://portkey.ai/error-library/unsupported-parameter-error-10521)
- [pollinations#5986：为推理模型强制 temperature=1](https://github.com/pollinations/pollinations/pull/5986)
- [Tavily Search API](https://docs.tavily.com/documentation/api-reference/endpoint/search)
