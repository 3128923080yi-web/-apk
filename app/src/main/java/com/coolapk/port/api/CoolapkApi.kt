package com.coolapk.port.api

import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 酷安 v6 API（从 CoolapkUWP/Helpers/UriHelper.cs 提取，均已实测可访问）
 *
 * 注意：feed/detail、feed/replyList 等接口在无登录/Cookie 时会返回
 *      "err_request_captcha_v2"（NEC 验证码），需要 WebView 过验证后带 Cookie 再请求。
 */
interface CoolapkApi {

    /** 首页初始化（热门搜索等卡片） */
    @GET("v6/main/init")
    suspend fun mainInit(): JsonElement

    /** 首页信息流 V8 */
    @GET("v6/main/indexV8")
    suspend fun indexV8(): JsonElement

    /** 搜索：type=feed / user / feedTopic / 8(全部) */
    @GET("v6/search")
    suspend fun search(
        @Query("type") type: String,
        @Query("searchValue") keyword: String,
        @Query("page") page: Int = 1,
        @Query("showAnonymous") showAnonymous: Int = -1,
    ): JsonElement

    /** 搜索联想词 */
    @GET("v6/search/suggestSearchWordsNew")
    suspend fun suggestWords(
        @Query("searchValue") keyword: String,
        @Query("type") type: String = "app",
    ): JsonElement

    @GET("v6/feed/detail")
    suspend fun feedDetail(@Query("id") id: String): JsonElement

    @GET("v6/feed/replyList")
    suspend fun feedReplies(
        @Query("id") id: String,
        @Query("page") page: Int = 1,
        @Query("listType") listType: String = "",
        @Query("discussMode") discussMode: Int = 1,
        @Query("feedType") feedType: String = "feed",
        @Query("blockStatus") blockStatus: Int = 0,
        @Query("fromFeedAuthor") fromFeedAuthor: Int = 0,
    ): JsonElement

    @GET("v6/user/space")
    suspend fun userSpace(@Query("uid") uid: String): JsonElement

    @GET("v6/user/feedList")
    suspend fun userFeeds(
        @Query("uid") uid: String,
        @Query("page") page: Int = 1,
        @Query("isIncludeTop") isIncludeTop: Int = 1,
    ): JsonElement

    @GET("v6/user/profile")
    suspend fun userProfile(@Query("uid") uid: String): JsonElement

    @GET("v6/apk/detail")
    suspend fun appDetail(@Query("id") packageName: String): JsonElement

    @GET("v6/feed/likeList")
    suspend fun likeList(@Query("id") id: String, @Query("page") page: Int = 1): JsonElement

    @GET("v6/topic/tagDetail")
    suspend fun tagDetail(@Query("tag") tag: String): JsonElement

    @GET("v6/topic/tagFeedList")
    suspend fun tagFeeds(
        @Query("tag") tag: String,
        @Query("page") page: Int = 1,
        @Query("listType") listType: String = "lastupdate_desc",
        @Query("blockStatus") blockStatus: Int = 0,
    ): JsonElement

    /** 通用分页数据入口：url 形如 %23/feed/dyhArticleList */
    @GET("v6/page/dataList")
    suspend fun dataList(@Query("url") url: String, @Query("page") page: Int = 1): JsonElement

    @GET("v6/account/checkLoginInfo")
    suspend fun checkLoginInfo(): JsonElement

    @GET("v6/notification/checkCount")
    suspend fun notificationCount(): JsonElement

    // ---- 需要登录态的写操作 ----

    @POST("v6/feed/like")
    suspend fun likeFeed(@Query("id") id: String): JsonElement

    @POST("v6/feed/unlike")
    suspend fun unlikeFeed(@Query("id") id: String): JsonElement

    @POST("v6/user/follow")
    suspend fun followUser(@Query("uid") uid: String): JsonElement

    @POST("v6/user/unfollow")
    suspend fun unfollowUser(@Query("uid") uid: String): JsonElement
}