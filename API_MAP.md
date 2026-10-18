# 酷安 v6 API 地图

> 来源：`Coolapk-UWP-revived/CoolapkUWP/CoolapkUWP/Helpers/UriHelper.cs`（`GetTemplate` 全量 73 个 `UriType`）
> 基线：`https://api.coolapk.com`（备用 `https://api2.coolapk.com`）
> 实测状态：✅ 已实测 200 ／ ⚠️ 需要 Cookie/验证码 ／ ❓ 未实测

## 一、必须的请求头

```http
X-App-Token:      v3<Base64(bcrypt)>          ← 见 core/TokenCreator.kt
X-App-Device:     <Base64 反转的设备指纹>       ← 见 core/DeviceInfo.kt
X-App-Id:         com.coolapk.market
X-App-Mode:       universal
X-App-Channel:    coolapk
X-App-Version:    16.6.4
X-App-Code:       2609291
X-Api-Supported:  2609291
X-Api-Version:    16
X-Sdk-Int:        33（Android API Level）
X-Sdk-Locale:     zh-CN
X-Dark-Mode:      0/1
X-Requested-With: XMLHttpRequest              ← 缺了必报「请求方式错误」
Accept:           application/json
User-Agent:       Dalvik/2.1.0 (...) +CoolMarket/16.6.4-2609291-universal
Cookie:           <登录态>
```

## 二、端点表

| 分类 | 端点 | 参数 | 状态 |
|---|---|---|:--:|
| 首页 | `/v6/main/init` | – | ✅ |
| 首页 | `/v6/main/indexV8` | – | ✅ |
| 通用 | `/v6/page/dataList` | `url`、`page` | ⚠️ 部分 url 返回 -3「无法访问该数据」 |
| 搜索 | `/v6/search` | `type=feed/user/feedTopic/8`、`searchValue`、`page`、`showAnonymous=-1` | ✅ |
| 搜索 | `/v6/search/suggestSearchWordsNew` | `searchValue`、`type=app` | ✅ |
| 搜索 | `/v6/feed/searchTag` | `q`、`page` | ❓ |
| 搜索 | `/v6/user/search` | `q`、`page` | ❓ |
| 动态 | `/v6/feed/detail` | `id` | ⚠️ 需验证码 |
| 动态 | `/v6/feed/replyList` | `id`、`listType`、`page`、`discussMode`、`feedType`、`blockStatus`、`fromFeedAuthor` | ⚠️ 需验证码 |
| 动态 | `/v6/feed/hotReplyList` | `id`、`page`、`discussMode=1` | ⚠️ |
| 动态 | `/v6/feed/likeList` | `id`、`listType=lastupdate_desc`、`page` | ⚠️ |
| 动态 | `/v6/feed/forwardList` | `id`、`type`、`page` | ❓ |
| 动态 | `/v6/feed/changeHistoryList` | `id` | ❓ |
| 动态 | `/v6/feed/{like|unlike}` | `id`（POST） | 需登录 |
| 动态 | `/v6/feed/createFeed` | POST 表单 | 需登录 |
| 动态 | `/v6/feed/reply` | `id`、`type=feed/reply`（POST） | 需登录 |
| 投票 | `/v6/vote/commentList` | `fid`、`page` | ❓ |
| 用户 | `/v6/user/space` | `uid` | ✅ |
| 用户 | `/v6/user/profile` | `uid` | ✅ |
| 用户 | `/v6/user/{feedList|replyList|...}` | `uid`、`page`、`isIncludeTop=1` | ✅（空列表时返回 `[]`） |
| 用户 | `/v6/user/hitHistoryList` / `recentHistoryList` | `page` | 需登录 |
| 用户 | `/v6/user/{follow|unfollow}` | `uid`（POST） | 需登录 |
| 话题 | `/v6/topic/tagDetail`、`/v6/topic/newTagDetail` | `tag` | ✅（tag 不对返回 -5） |
| 话题 | `/v6/topic/tagFeedList`、`/v6/topic/deviceFeedList` | `tag`、`page`、`listType`、`blockStatus` | ✅ |
| 应用 | `/v6/apk/detail` | `id=包名`、`installed=0` | ✅ |
| 应用 | `/v6/product/detail` | `id` 或 `name` | ❓ |
| 应用 | `/v6/product/changeFollowStatus` | POST | 需登录 |
| 应用 | `/v6/apk/commentList` | 经 `dataList` 包装 | ⚠️ |
| 收藏 | `/v6/collection/detail|itemList|list` | `id`/`uid`、`page` | ❓ |
| 收藏 | `/v6/collection/{follow|unFollow|like|unLike}` | POST | 需登录 |
| 头条号 | `/v6/dyh/detail`、`/v6/dyhArticle/list` | `dyhId`、`type`、`page` | ❓ |
| 消息 | `/v6/message/list` | `page` | 需登录 |
| 消息 | `/v6/notification/{类型}` | `page` | 需登录 |
| 消息 | `/v6/notification/checkCount` | – | 需登录 |
| 账号 | `/v6/account/checkLoginInfo` | – | ✅ |
| 账号 | `/v6/account/loadConfig` | `key=my_page_card_config` | 需登录 |
| 账号 | `/v6/account/requestValidate` | POST | 需登录 |
| 上传 | `/v6/upload/ossUploadPrepare` | POST | 需登录 |
| 上传 | `/v6/feed/uploadImage` | `fieldName=picFile`、`uploadDir` | 需登录 |
| 开发者 | `/v6/../do?c=apk&m=myList&listType=&p=` | 老接口 | ❓ |
| 第三方 | `qapi.ithome.com/api/content/getcontentdetail` | `id` | 外部 |
| 第三方 | `api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/get_dynamic_detail` | – | 外部 |

## 三、返回体约定

- 成功：`{"data": [...] }` 或 `{"data": {...}}`
- 失败：`{"status": -x, "error": -x, "message": "...", "messageStatus": "..."}`
  - `-3` 无法访问该数据
  - `-5` 话题不存在
  - `1000` 请求方式错误（**多半是缺 `X-Requested-With`**）
- 验证码：`{"message":"当前访问需要验证码","messageStatus":"err_request_captcha_v2","messageExtra":"{\"captchaType\":\"NEC\",\"captchaId\":\"...\"}"}`

## 四、登录方式（UWP 原实现）

`https://account.coolapk.com/auth/loginByCoolapk` → 成功后拿到 Cookie 串（`uid`、`username`、`token`、`SESSID` 等），
Android 端建议用 WebView 打开该页，`onPageFinished` 后取 `CookieManager.getCookie()` 回填（见 `ApiClient.saveCookie`）。