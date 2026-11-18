# DeepSeek Personal API · UI5

基于用户提供的 DeepSeek Android 2.8.0 (297) 原包制作的个人 API 适配补丁，保留原生聊天界面。新增额度管理、自定义聊天接口、人设、上下文空间及可编辑的向量记忆。

**基于 DeepSeek 原版客户端，使用 DSH 与 CODEX 共同修改完成。个人修改版，非官方发布。**

本项目使用个人签名，没有 DeepSeek 官方数字签名。原应用名称、图标和其他原内容归其各自权利人所有。原设置页两项备案标识已移除，保留 AI 内容提示。

## 安装与配置

从 [Releases](https://github.com/ink-ljy/deepseek-personal-api/releases) 下载共存版 `deepseek-api-ui5-coexist.apk`，约 67 MB。它与官方应用分别运行，沿用原包登录和聊天流程，需单独登录。API Key 不随安装包分发。

- **官方额度**：设置 → 额度管理，输入自己的 DeepSeek Key，验证保存并启用。充值打开 [官方平台](https://platform.deepseek.com/top_up)，请登录与 Key 所属账号相同的账号。API 与免费聊天服务额度分别管理。
- **其他聊天服务**：设置 → 接口、人设与记忆 → 自定义聊天接口。填写 HTTPS Base URL、Key、模型和 token 上限。例如 `https://example.com/v1`，应用追加 `/chat/completions`。保存从下一次请求生效。
- **人设**：同一设置入口 → 人设，填写称呼、语气和回答偏好，作为固定系统指令发送。留空恢复默认。
- **普通用户的记忆**：默认内置离线模型，无需下载、无需 Embeddings Key。自动记录完成的 API 问答，支持添加、编辑、删除和搜索。
- **高级向量服务**：记忆模型设置中可选填独立 Embeddings Base URL、Key 和模型，保存后才使用远程服务；可以随时切回内置离线模型。

同包名 APK 与官方应用签名不同，不能直接覆盖官方应用；优先使用共存版。更新本项目此前的共存版可以保留本地数据。

## 默认离线记忆

采用 [BAAI/bge-small-zh-v1.5](https://huggingface.co/BAAI/bge-small-zh-v1.5)，23,953,920 参数，约 **0.024B**，512 维。由官方 safetensors 固定版本 `7999e1d3359715c523056ef9478215996d62a620` 在本机导出 ONNX，MatMul/Gather 按通道动态 INT8 量化。模型文件 **24,012,525 字节**，通过 ONNX Runtime Android 在 CPU 运行，模型与运行库随 APK 内置。

这是真实的小型语义向量模型，用于找出措辞不同但意思相近的旧记忆。它并非关键词哈希，也不需要用户配置远程接口。

- API 回答正常完成后保存问答文字，不保存思考过程或图片原始内容。长问答分段保存，后台生成向量。
- 添加或编辑后更新索引；删除后，待完成的旧向量任务不能把已删除行写回来。启动时恢复尚未完成的索引。
- 默认在不同会话间检索，避免重复注入当前会话已有原文。最多引用 3 条，并限制长度；相似度不足时不引用。
- 命中片段固定到当前用户消息的 API 内容，原聊天气泡仍是原问题。以后编辑记忆不会改写先前历史前缀。
- 免费聊天的旧轮次不自动导入记忆库，重要内容可手动添加；当前已加载历史仍正常作为聊天上下文携带。
- 自动记录与聊天检索可分别关闭。删除聊天不会同时删除记忆，记忆页可单条删除或清空。
- 每条手动记忆最多 12,000 字符，全库最多 5,000 条。长文本分窗编码；每批最多补建 100 条索引，可手动继续补建。
- 离线向量不发送到 Embeddings 服务。**命中的记忆文字会随当前消息发送给所选聊天服务，并计入聊天输入 token。**
- 模型可能误检或遗漏，可直接修正；小量对照测试不等于完整的语义质量评估。

高级模式使用真正的 `/embeddings` 浮点向量。向量接口和聊天接口的 Key 独立加密保存，改变地址不能默默沿用旧 Key。切换向量模型后保留文字、重建索引，不混用旧向量。远程检索失败时继续普通聊天。保存高级接口前，页面说明会将记忆文字发送到该地址并可能产生费用。

模型和运行库的 MIT 许可证随 APK 附带；版本、哈希、转换环境与量化对照结果见 `local-model-manifest.json`。

## 聊天、图片与语音

官方模式使用 DeepSeek 公共 Messages / Files / TTS API，保留原“深度思考”“智能搜索”的对应参数。朗读使用原播放器需要的 24 kHz / 16-bit / 单声道 PCM，默认 Stella 声音；停止会断开请求。原音色设置、语音输入和通话未完整适配。

UI5 直接适配原生图片附件模型入口，覆盖“原免费会话切 API，不新建会话即选系统相册图片”的路径。上下文详情可查看最近上传阶段。官方模式通过 Files API，服务器确认后才显示完成；按真实字节识别格式，保留本地预览，可解码的 BMP/HEIC 等转换 PNG。原免费服务文件 ID 不能直接作为公共 API 文件，旧附件需重新选择。

自定义模式使用 **OpenAI Chat Completions 兼容协议**，支持流式多轮文本、接口实际 usage/缓存统计。图片在本机准备，随消息以 `image_url`/Base64 发送，不依赖专用 Files 服务；需所选模型支持视觉。单图限制 16 MiB，整体请求限制 48 MiB。“图片已准备”不等于服务已接收，消息发送时才传输。

自定义文档上传、DeepSeek 专用联网搜索、工具调用与 TTS 不通用，当前未代发这些功能。不会把自定义 Key 发给 DeepSeek 文件或语音接口。服务不支持 `stream_options` 时可关闭流式用量统计；特殊请求格式的服务可能还需适配。

参考：[Files](https://api-docs.deepseek.com/guides/files_api/)、[视觉输入](https://api-docs.deepseek.com/guides/vision/)、[Messages](https://api-docs.deepseek.com/guides/anthropic_api/)、[TTS](https://api-docs.deepseek.com/guides/tts/)、[Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[Embeddings](https://developers.openai.com/api/reference/resources/embeddings/methods/create)。

## 上下文、缓存和恢复

聊天顶部圆环百分比入口显示当前分支的**估算空间**，点击查看实际 usage、缓存和压缩/恢复。官方模式按 1M 上限，预留 16,384 输出 token；自定义模式使用填写的上限。字符估算不等于精确分词，附件和工具以接口统计为准。

每轮发送分支历史是无状态 API 的正常工作方式，并非每轮新建会话。免费聊天历史可携带，但公开 API 不提供导入免费聊天缓存的操作，不能承诺继承 C 端缓存。缓存依赖完整前缀与服务端策略；改模型、人设、摘要或思考/搜索选项都会影响前缀。

压缩调用当前聊天接口生成一次摘要，保留最近 2 轮原文及全部本地记录，只作用于对应分支。失败、截断、没有缩小空间或分支变化时不替换原上下文。恢复只移除摘要。压缩可能遗漏细节，也可能降低下一次缓存命中。累计 usage 覆盖保存，缓存 token 不重复计数。

页面读取与上游生成分离，返回页面不会直接取消请求。`resume_stream` 重新订阅运行中的请求或回放已完成内容，不新增生成请求。真实断流保留部分内容，符合条件时最多对每条失败回答重新生成一次，可能额外计费。Key/余额错误及主动停止不自动重复生成。Android 杀进程后不能接回原上游连接。

Key 验证、保存、读取与请求头构造统一清理 CR/LF、空白及不可见格式字符，兼容 `Bearer ` 前缀。非法字符在本地拒绝，不回显含 Key 的底层网络错误。

参考：[缓存规则](https://api-docs.deepseek.com/guides/kv_cache/)、[模型信息](https://api-docs.deepseek.com/quick_start/pricing/)。

## 界面与数据

新增页复用原 Compose 顶部栏、返回箭头、设置行和绿色开关。页面横移为宽度五分之一、300 ms；淡出 105 ms，淡入延迟 105 ms 再持续 195 ms，曲线取自原包。嵌套设置页返回恢复前一页，禁用系统动画时直接切换。实体手机最终观感仍需用户确认。

Key 使用 AndroidKeyStore AES/GCM 加密。本地会话、附件和记忆不与官方聊天云端同步。记忆文字是应用私有 SQLite 数据，并非端到端加密备份。卸载或清数据会丢失本地资料，本版尚无专用导出迁移功能。

## 验证范围

隔离 Android 36 模拟器中，UI5 完成 **136 项检查**：

| 范围 | 项数 | 报告 |
| --- | ---: | --- |
| 原上传与朗读协议 | 29 | media-verification.json |
| 流式恢复与受控重试 | 22 | stream-verification.json |
| 上下文、usage、压缩恢复 | 16 | context-verification.json |
| Key 输入与安全错误 | 12 | key-verification.json |
| 原会话选图入口与 API 历史接续 | 12 | handover-verification.json |
| 自定义接口、人设、实时记忆与密钥分离 | 32 | features-verification.json |
| **真实本地 ONNX 推理及分词对照** | **13** | local-verification.json |

网络检查使用可控 HTTPS 响应，不使用真实 Key、不消耗真实 API 额度。本地检查实际运行随 APK 打包的 INT8 模型，比较 Android 分词及向量与桌面参考结果，验证中文检索和删除。测试代码不打入发布 APK。

两种 APK 的签名、16 KiB 对齐、原资源保留、模型哈希和无内置凭据检查通过。新增 ONNX 运行库 ELF 的 LOAD 对齐为 16 KiB。新增设置页、人设页、模型页和记忆添加在测试用原生页面中检查。

**本轮没有完成用户实体手机的真实图片问答、语音试听、网络断流复现或真实缓存命中改善验证。** 完整原账号登录后的聊天路由、各第三方服务兼容性、摘要质量与实体手机观感还需实际使用确认；模拟器和协议测试不能替代这些验收。

## 从原包构建

仓库只提交新增适配源码与构建脚本，不包含完整反编译源码、原始 APK、签名私钥或 API Key。此前检索未发现完整官方原生 Android 客户端源码；官方 [chat-demo](https://github.com/deepseek-ai/chat-demo) 是 Web 示例，本项目没有用它替换原生界面。

需要 JDK 21、Android SDK Platform 36 / Build Tools 36.0.0、Apktool 2.12.1、Python 3，及 PyTorch 2.5.1、ONNX、ONNX Runtime。构建使用 Windows 工具链，转换依赖放在独立目录，避免覆盖现有包。

```powershell
# 原包 2.8.0 / 297，SHA-256：
# 489ff75c6debfce94fcb247595969b71314522c736b11548eb27c845a5f4dce4
java -jar apktool.jar d -f '原始安装包路径' -o original-decoded
$env:DEEPSEEK_ORIGINAL_APK='原始安装包路径'
$env:ANDROID_SDK_ROOT='C:\Android\sdk'
python -m pip install --target local-model/export-deps --no-deps transformers==4.48.3 tokenizers==0.21.4 huggingface-hub==0.36.0
python -X utf8 prepare_local_model.py
python -X utf8 build_native.py
python -X utf8 make_coexist.py
```

模型导出脚本获取固定版本的官方 safetensors，以及 Maven Central 的 ONNX Runtime Android 1.24.3。转换文件和下载位于被忽略的 `local-model/`。默认代理优先使用现有环境；未配置时脚本使用本机 `127.0.0.1:7897`，可按网络情况修改。

首次构建会在 `.signing` 生成个人签名，请妥善保留以供更新。补丁依赖此版本的混淆类，不能直接用于其他版本；不同签名构建不能互相覆盖。

测试先运行 `python -X utf8 build_test.py`，只在隔离模拟器安装主包和测试包。模式为 `media`、`stream`、`context`、`key`、`handover`、`features`、`local`。本地模式先把 `local-model/tokenizer-fixtures.json` 推送到 `/data/local/tmp/local-tokenizer-fixtures.json`。

```powershell
adb -s '隔离模拟器' shell am instrument -w -e mode local dev.local.deepseekapitest/.ApiSelfTest
```

测试会改写设置、虚拟聊天和记忆库，不能在保存真实数据的手机上运行。
