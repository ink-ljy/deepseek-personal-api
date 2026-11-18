# DeepSeek Personal API

基于用户提供的 DeepSeek Android 2.8.0 (297) 安装包制作的个人 API 适配补丁。保留原应用的原生聊天界面，在设置中加入“额度管理”和“关于 DeepSeek API”。

**基于 DeepSeek 原版应用重构，支持使用个人 API 额度（个人修改版，非官方发布）。**

基于 DeepSeek 原版客户端，使用 DSH 与 CODEX 共同修改完成。

本项目没有 DeepSeek 官方数字签名，也不代表 DeepSeek 官方。原应用名称、图标和其他原有内容的权利归其各自权利人所有；本仓库中的新增适配代码不意味着对原应用内容获得了所有权。

## 使用

安装发布页中的共存测试版，与官方应用分别运行。打开“设置 → 额度管理”，输入自己的 DeepSeek API Key 并验证保存，启用“使用 API 额度”。Key 不随应用分发。

充值按钮打开 [DeepSeek 官方开放平台](https://platform.deepseek.com/top_up)。请登录与 API Key 所属账号相同的账号，付款由官方平台完成，回到应用刷新余额。API 余额与原聊天服务额度分别管理。

当前聊天使用固定的官方 `api.deepseek.com` 公共 Messages API 和 `deepseek-flash`。原“深度思考”和“智能搜索”开关映射到 API 对应能力。没有增加私人 API 中转服务器。

## UI4 更新（2026-11-18）

- 对照用户提供的原版操作录屏，并追踪原包设置路由的 `cu7`、`eh`、`io7`、`z64` 动效参数；额度管理与关于页面改为左向滑入、右向滑出，同时淡入淡出，后面的设置页同步反向移动。
- 横移距离为页面宽度的五分之一，移动时长 300 ms，曲线为 `(0.4,0,0.2,1)`。淡出为前 105 ms，淡入延迟 105 ms 后持续 195 ms，分别使用原版对应的贝塞尔曲线。系统关闭动画时直接切换。
- 移除原设置页底部的原应用备案号展示；构建检查确认两项原备案标识均不再存在于交付 APK 中。
- 设置页底部和“关于 DeepSeek API”增加“基于 DeepSeek 原版客户端，使用 DSH 与 CODEX 共同修改完成”。关于页继续明确标识个人修改版、非官方发布。

## 保留的 UI3 功能

- 回答下方的“朗读”改接官方公共 TTS API，正文转为 24 kHz / 16-bit / 单声道 PCM，封装为原播放器需要的带序号音频包，保留原朗读控件。停止播放会断开正在进行的语音请求；思考内容不朗读。
- 朗读默认使用官方 Stella 女声，当前未与原应用“朗读音色”选择同步；单次最多 10000 计费字符。原语音输入与通话尚未适配，不能由本次朗读修复推断为可用。
- 图片上传增加原进度回调；上传成功前最多显示 95%，服务器确认后才显示完成。失败会回到原生错误状态。
- 按图片实际内容识别格式，支持无扩展名的图库图片；对手机能够解码的 BMP、HEIC 等非 API 支持格式转换为 PNG，超大尺寸图片缩小后上传。无法解码的格式明确提示重新选择。
- 图片通过官方 Files API 上传，再以 `image.source.file_id` 发送给 Messages API；保留本机预览。API 上传与原聊天服务的文件 ID 分别管理，旧附件需要重新选择。

语音合成与图片理解使用个人 Key 对应的官方 API 额度。没有新增第三方服务。接口依据：[TTS](https://api-docs.deepseek.com/guides/tts/)、[视觉输入](https://api-docs.deepseek.com/guides/vision/)、[Anthropic 兼容 API](https://api-docs.deepseek.com/guides/anthropic_api/)。

### 保留的 UI2 调整

- 额度页复用原 APK 的 Compose 顶部栏和返回组件，包含原 32 dp 圆形返回按钮与原箭头图形。
- 复用原设置行、右侧箭头和数据管理页的开关组件。
- 充值、Key 管理和余额刷新采用设置列表形式，取消第一版的大蓝色按钮。
- 在原设置的“关于”分组增加“关于 DeepSeek API”，展示原应用来源与个人改版说明。

## 数据

API Key 使用 AndroidKeyStore AES/GCM 加密后保存于应用私有目录。API 会话在本机保存，不同步到官方聊天云端。侧边栏适配了本地 API 会话与原有会话列表；共存版需要单独登录原聊天账号。

卸载或清除修改版应用数据会删除它保存的本地会话。此版本尚无专用导出和迁移功能。

## 验证范围

在隔离 Android 36 模拟器中测试过安装、启动、额度页面，以及真实 API 的流式回复、多轮上下文、深度思考、联网搜索和 `.txt` 文件上传读取。接口返回可由原 APK 的消息与会话序列化器解析。

UI3 在隔离 Android 36 模拟器中完成 **29 项确定性媒体协议测试**：调用原 `ny1` 上传入口及 `it1` 语音入口，检查上传进度、无扩展名 PNG、BMP 转换、原附件序列化、图片文件引用、PCM 序号封装、正文过滤、HTTP 错误、取消与主动停止请求。测试使用隔离的 HTTPS 响应替身及合成 PCM 字节，不调用真实付费接口；测试代码没有打入交付 APK。

同包名版与共存版安装更新通过，共存版冷启动通过；测试用原生设置入口下额度页、关于页的点击进入、左上角返回及系统返回通过。UI4 录屏抽帧检查新增页的横移与透明度变化；原设置备案说明组件在中国地区分支下显示了修改版来源说明，没有显示原备案号。原包 877 个资源条目保持不变，个人签名与 16 KiB 对齐校验通过。

上述结果不等于实体手机全部操作验收。**本轮没有使用真实 Key 完成语音合成试听、真实图片上传后问答；这两项仍需实体手机复测。** 早先真实附件验证仅覆盖 `.txt`。原账号完整登录与聊天路由、编辑及重新生成等逐项 UI 操作、第三方登录、语音输入和通话均未完成全面验收。动效使用从原包取得的参数重现，实体手机上是否与原版观感完全相同仍由用户复测确认。详细证据与限制见 `verification.json`、`media-verification.json`、`ui-verification.json`。

## 官方源码检索

2026-11-18 检索了 [deepseek-ai 官方组织](https://github.com/deepseek-ai) 当时公开的 47 个仓库，没有找到 `com.deepseek.chat` Android 原生客户端的完整源码。官方 [chat-demo](https://github.com/deepseek-ai/chat-demo) 是浏览器直连 API 的网页示例，包含语音、附件等实现；本轮参考其公开接口用法，没有将应用替换为网页客户端。本补丁仍依赖用户提供的原 APK。

## 从原包构建

仓库只保存新增的适配源码与构建脚本，不提交完整反编译代码、原始 APK、签名私钥或 API Key。

需要 JDK 21、Android SDK Platform 36 / Build Tools 36.0.0、Python 3 和 Apktool 2.12.1。构建脚本默认使用 Windows 工具链。

1. 自行准备匹配的原始安装包（版本 2.8.0 / 297，SHA-256 `489ff75c6debfce94fcb247595969b71314522c736b11548eb27c845a5f4dce4`）。
2. 将 Apktool JAR 命名为 `apktool.jar` 放到仓库根目录，解码原包到 `original-decoded`：

   ```powershell
   java -jar apktool.jar d -f '原始安装包路径' -o original-decoded
   $env:DEEPSEEK_ORIGINAL_APK='原始安装包路径'
   $env:ANDROID_SDK_ROOT='C:\Android\sdk'
   python -X utf8 build_native.py
   python -X utf8 make_coexist.py
   ```

3. 脚本修改少量原 smali 调用点、编译适配 DEX，保留原资源，生成同包名和共存包。首次构建会生成本地个人签名于 `.signing`，需要自行妥善保留以供后续覆盖更新。

补丁依赖此版本被混淆后的具体类与方法，不能直接用于其他版本。自行构建的签名不同于本项目现有测试包，无法直接覆盖不同签名的安装。

### 运行媒体协议测试

构建 APK 后运行 `python -X utf8 build_test.py`，在隔离测试设备安装主 APK 与 `native-api-test.apk`，执行：

```powershell
adb -s '测试设备序列号' shell am instrument -w -e mode media dev.local.deepseekapitest/.ApiSelfTest
```

测试会改写并清空测试应用的 API 设置、建立虚拟本地会话和附件，**仅用于隔离模拟器，勿在保存真实数据的手机上运行**。媒体模式不需要真实 API Key；其他历史测试模式需要单独准备接口凭据。

官方 API 参考：[构建聊天应用](https://api-docs.deepseek.com/zh-cn/guides/build_a_chatbot/)。
