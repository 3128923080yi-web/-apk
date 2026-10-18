# CoolapkPort · 酷安 UWP 的 Android 移植工程（MVP）

> 来源工程：`Coolapk-UWP-revived.zip`（UWP / C# / XAML，1520 文件，主工程 ≈26,500 行 C#、82 个 XAML 页面）
> 目标：把酷安第三方客户端的能力搬到 Android（compileSdk/targetSdk 36，即 Android 16），UI 用 Jetpack Compose 重写。

---

## ✅ 一、已完成（本仓库里能跑的成果）

| # | 成果 | 说明 |
|:--:|---|---|
| 1 | **API 全量提取** | 从 `Helpers/UriHelper.cs` 提取 65+ 个 `UriType` → v6 端点，见 `API_MAP.md` |
| 2 | **X-App-Token v3 算法跑通** | `tools/coolapk_client.py`（Python 参考实现）实测 `api.coolapk.com` 返回 **200 + 真实数据** |
| 3 | **Kotlin 版 TokenCreator** | `core/TokenCreator.kt`，含 bcrypt（at.favre.lib）+ salt 掩码修正 |
| 4 | **请求头装配** | `core/CoolapkHeaderInterceptor.kt`，含 **关键坑**：必须带 `X-Requested-With: XMLHttpRequest` |
| 5 | **设备指纹 / X-App-Device** | `core/DeviceInfo.kt`，对齐 C# `DeviceInfo.ToString()` + Base64 + 反转 |
| 6 | **Retrofit API 层** | `api/CoolapkApi.kt`（20+ 接口）+ 持久化 CookieJar |
| 7 | **Compose MVP 界面** | 首页（indexV8 卡片）/ 搜索（feed 列表）/ 动态详情 / 我的（设备码、token、Cookie 登录） |
| 8 | **Gradle 工程可导入** | AGP 8.7.3 + Kotlin 2.0.21 + Compose BOM |

### 🔑 实测踩到的两个大坑（写进代码注释了）

1. **缺 `X-Requested-With: XMLHttpRequest` → 网关拒绝**
   返回 `{"status":1000,"message":"请求方式错误"}`（HTTP 404）。加上后立刻 200。这与 token 无关，第一次排查很容易误判成 token 算法错。
2. **`feed/detail`、`feed/replyList` 会要求验证码**
   返回 `err_request_captcha_v2`（NEC 验证码，`captchaId` 附带）。必须先 WebView 过验证拿 Cookie，再带 Cookie 请求。

---

## 📁 二、目录结构

```
coolapk-port/
├─ Coolapk-UWP-revived/           ← 原始 UWP 源码（参考用）
├─ tools/
│  ├─ token_test.py               ← Token v3 算法最小验证脚本
│  └─ coolapk_client.py           ← 可用的 API 客户端（探针/回归测试）
└─ android/                       ← Android 工程
   ├─ settings.gradle.kts / build.gradle.kts / gradle.properties
   └─ app/
      ├─ build.gradle.kts
      └─ src/main/
         ├─ AndroidManifest.xml
         ├─ res/values/themes.xml
         └─ java/com/coolapk/port/
            ├─ core/  Constants.kt · TokenCreator.kt · DeviceInfo.kt · CoolapkHeaderInterceptor.kt
            ├─ api/   CoolapkApi.kt · ApiClient.kt
            └─ ui/    MainActivity.kt · JsonExt.kt
```

---

## 🛠 三、怎么编译出 APK

> **本机实测状态（2026-10-18，Android aarch64 + proot Ubuntu 24）**
> - ✅ 已装好：JDK17、Gradle 8.9（`/root/gradle-8.9`）、Android SDK（`/root/android-sdk`：platform-tools / platforms;android-36 / build-tools;36.0.0）
> - ✅ `:app:compileDebugKotlin` **BUILD SUCCESSFUL**（Kotlin 代码无编译错误）
> - ❌ `:app:processDebugResources` 失败：**AAPT2 只有 x86_64 版本**，本机是 aarch64，无法执行
>   （同理 `platform-tools/adb` 也跑不了）。要用本机出包，必须提供 aarch64 版 `aapt2`，然后用
>   `android.aapt2FromMavenOverride=/path/to/aapt2` 覆盖。这是唯一剩余卡点。

在电脑上（x86_64）：
```bash
# JDK 17 + Android SDK Platform 36 + Build-Tools 36
cd android
gradle wrapper --gradle-version 8.9   # 首次生成 wrapper（或直接用 Android Studio 打开）
./gradlew assembleDebug               # 产物: app/build/outputs/apk/debug/app-debug.apk
```

本机（aarch64，网络可用镜像）已有的编译命令：
```bash
cd android
echo "sdk.dir=/root/android-sdk" > local.properties
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ANDROID_HOME=/root/android-sdk
/root/gradle-8.9/bin/gradle --no-daemon :app:compileDebugKotlin -x :app:processDebugResources
```
> 注：`settings.gradle.kts` 已加入阿里云镜像（services.gradle.org / github.com 在本机网络被墙）。

命令行验证算法（无需编译）：
```bash
cd tools && python3 coolapk_client.py     # 应打印多个 [200] 及 JSON
```

---

## 🗺 四、移植路线与进度

| 阶段 | 内容 | 状态 |
|---|---|:--:|
| P0 | 源码分析 / API 提取 / token 算法验证 | ✅ 完成 |
| P1 | 网络层 + 设备指纹 + Cookie（能拿到真实数据） | ✅ 完成 |
| P2 | MVP 界面：首页 / 搜索 / 详情 / 我的 | ✅ 完成（骨架） |
| P3 | WebView 登录 + NEC 验证码过验证，写操作（点赞/关注/评论） | ⬜ 待做（关键卡点） |
| P4 | 富文本/图文混排渲染、图片九宫格、视频播放器 | ⬜ 待做 |
| P5 | 消息通知、私信、发布动态、图片上传（OSS 直传 `/v6/upload/ossUploadPrepare`） | ⬜ 待做 |
| P6 | 深色模式、字体缩放、无障碍、性能（分页缓存/Paging3） | ⬜ 待做 |

### 剩余工作量的现实评估（相对 UWP 原版全功能）
- 登录 + 验证码 + 写操作：约 **30%** 工作量，且是**技术风险最高**的部分
- 信息流渲染（酷安用的是服务端下发 entity 卡片体系，**需要一套通用渲染器**）：约 **35%**
- 周边页面（用户/话题/应用/收藏/历史/搜索聚合）：约 **25%**
- 打磨：约 10%
> 结论：MVP 网络层已通，但"全功能 Android 客户端"仍需数月。

---

## ⚠️ 五、风险与合规

- 这是**非官方第三方客户端**，仅限个人学习研究，**禁止商用、禁止分发上架**。
- 接口随时可能变更（v6 → 更高版本），`X-App-Version` / `X-App-Code` 需跟随官方版本更新。
- 使用自己账号登录，存在账号安全与被风控的可能；不要用主账号做自动化高频请求。
- 不要把 Cookie / token 硬编码进仓库或分享给别人。

---

## 📌 六、下一步建议

1. 先在电脑上 `gradlew assembleDebug` 跑通 MVP，确认首页/搜索能出数据；
2. 再做 P3：登录 WebView + Cookie 回填 + 验证码兜底（这一步通了，写操作才有意义）；
3. 渲染器建议做成「entityTemplate → Composable 映射表」，与 UWP 的 `Controls` 目录一一对应，可复用其布局思路（82 个 XAML 页面可当作设计稿）。