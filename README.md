# DeepSeek Personal API

基于用户提供的 DeepSeek Android 2.8.0 (297) 安装包制作的个人 API 适配补丁。保留原应用的原生聊天界面，在设置中加入“额度管理”和“关于 DeepSeek API”。

**基于 DeepSeek 原版应用重构，支持使用个人 API 额度（个人修改版，非官方发布）。**

本项目没有 DeepSeek 官方数字签名，也不代表 DeepSeek 官方。原应用名称、图标和其他原有内容的权利归其各自权利人所有；本仓库中的新增适配代码不意味着对原应用内容获得了所有权。

## 使用

安装发布页中的共存测试版，与官方应用分别运行。打开“设置 → 额度管理”，输入自己的 DeepSeek API Key 并验证保存，启用“使用 API 额度”。Key 不随应用分发。

充值按钮打开 [DeepSeek 官方开放平台](https://platform.deepseek.com/top_up)。请登录与 API Key 所属账号相同的账号，付款由官方平台完成，回到应用刷新余额。API 余额与原聊天服务额度分别管理。

当前聊天使用固定的官方 `api.deepseek.com` 公共 Messages API 和 `deepseek-flash`。原“深度思考”和“智能搜索”开关映射到 API 对应能力。没有增加私人 API 中转服务器。

## UI2 更新

- 额度页复用原 APK 的 Compose 顶部栏和返回组件，包含原 32 dp 圆形返回按钮与原箭头图形。
- 复用原设置行、右侧箭头和数据管理页的开关组件。
- 充值、Key 管理和余额刷新采用设置列表形式，取消第一版的大蓝色按钮。
- 在原设置的“关于”分组增加“关于 DeepSeek API”，展示原应用来源与个人改版说明。

## 数据

API Key 使用 AndroidKeyStore AES/GCM 加密后保存于应用私有目录。API 会话在本机保存，不同步到官方聊天云端。侧边栏适配了本地 API 会话与原有会话列表；共存版需要单独登录原聊天账号。

卸载或清除修改版应用数据会删除它保存的本地会话。此版本尚无专用导出和迁移功能。

## 验证范围

在隔离 Android 36 模拟器中测试过安装、启动、额度页面，以及真实 API 的流式回复、多轮上下文、深度思考、联网搜索和 `.txt` 文件上传读取。接口返回可由原 APK 的消息与会话序列化器解析。

这些结果不等于实体手机全部操作验收。原账号完整登录与聊天路由、编辑及重新生成等逐项 UI 操作、第三方登录、语音输入、朗读和通话均未完成全面验收。语音服务仍沿用原服务，没有完整改为公共 API。图片、PDF 等格式已有转换路径，仅 `.txt` 做过真实附件验证。

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

官方 API 参考：[构建聊天应用](https://api-docs.deepseek.com/zh-cn/guides/build_a_chatbot/)。
