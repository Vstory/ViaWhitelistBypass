# ViaTune

Via 浏览器增强模块（libxposed API 102 / LSPosed）：

- 🎯 **解除白名单限制**：解除 Via 内置白名单，优酷 / 爱奇艺 / 芒果 TV / 腾讯视频 / B 站等恢复资源嗅探等能力
- 🔍 **列表搜索**：规则订阅源 / 脚本列表支持模糊搜索

> 曾用名 **Via Whitelist Bypass**，v3.2.0（10029）起更名 ViaTune。
> ⚠️ 仅针对**官网版 Via（包名 `mark.via`）**；需 LSPosed 1.9+（支持 libxposed API 102）。

## 🎯 功能一：白名单解除

模块解除 Via 内置的国内视频网站白名单限制，恢复资源嗅探等能力。

## 🔍 功能二：列表搜索

规则订阅页 / 脚本页右上角"＋"前新增**放大镜搜索按钮**：

- 输入关键词按名称**模糊过滤**：任意子串（`广告` → "CSDN**广告**完全过滤"）、**拼音缩写**（`gg` → 广告类）、**跳字**（`s x` → 命中缩写类）
- 点搜索结果直达条目；留空 / 点"清空"还原全列表；关闭页面自动清空。

## 要求

- Android 8.0+（API 26）
- LSPosed 1.9+
- Via 浏览器官网版（`mark.via`）

## 安装

1. 下载 [Releases](../../releases) 中的 APK（软件名 ViaTune）
2. 安装 APK
3. LSPosed 中启用模块（作用域自动声明 `mark.via`）
4. 强制停止 Via 后重新打开

## 验证

1. 在优酷 / B 站等站点点**资源嗅探**，能列出资源 = 生效 ✅（停用模块则提示"该网站不支持资源嗅探"）
2. LSPosed 日志搜 `ViaTune`：`ViaTune: hooks installed | r9.k whitelist bypass: 6/6 methods` = 生效；支持热重载

## 构建（Gradle）

```bash
./gradlew :app:assembleRelease   # 正式版
./gradlew :app:assembleDebug     # 调试版
```

## 免责声明

仅供学习与个人使用。使用本模块可能违反目标网站条款或 Via 软件许可，请自行承担风险。
