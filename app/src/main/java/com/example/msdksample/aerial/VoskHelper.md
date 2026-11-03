# 离线语音模型管理

## 作用与运行边界

`VoskHelper` 统一下载、加载中文 Vosk 模型并管理识别会话。当前 APK 仅打包 ARM64 语音库，初始化前检查主机首选 ABI；x86 模拟器的 ARM 转译环境直接返回不可用提示，保留文字指令，避免原生非法指令导致整个应用退出。ARM64 的现有下载和识别路径保持不变。

## 接入和回调

```kotlin
VoskHelper.init(context, object : VoskHelper.VoskInitCallback {
    override fun onDownloadStart() { }
    override fun onDownloadProgress(percent: Int) { }
    override fun onDownloadComplete() { }
    override fun onExtracting() { }
    override fun onInitialized() { /* 此后可以请求录音权限并启动识别。 */ }
    override fun onError(message: String) { /* 显示不可用原因，并允许输入文字。 */ }
})
```

`context: Context` 提供模型文件目录，`callback: VoskInitCallback` 接收下载状态和可用性。没有 UI props 或 emits；上例回调即对外通知接口。

## 方法

- `isModelReady(): Boolean` 查询模型是否已就绪。
- `isInitializing(): Boolean` 查询初始化是否进行中。
- `init(Context, VoskInitCallback): Unit` 初始化或返回不可用原因。
- `startListening(Context, VoskRecognitionListener): Unit` 在模型和录音权限可用后启动识别；回调包括部分文本、最终文本、错误和超时。
- `stopListening(): Unit` 停止当前识别服务。
- `destroy(): Unit` 停止识别并释放模型和识别器。

本次仅修复不兼容主机的原生加载边界；真实麦克风识别、模型分发和在线意图服务仍按 F06 的设备阶段要求验收。
