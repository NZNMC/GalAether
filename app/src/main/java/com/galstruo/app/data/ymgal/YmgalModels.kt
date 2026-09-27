package com.galstruo.app.data.ymgal

/** 列表/搜索结果条目(搜索接口用 id,发行日期与随机接口用 gid,两者兼容) */
data class GameItem(
    val id: Long = 0,
    val gid: Long = 0,
    val name: String? = null,
    val chineseName: String? = null,
    val mainName: String? = null,
    val orgName: String? = null,
    val mainImg: String? = null,
    val releaseDate: String? = null,
    val haveChinese: Boolean = false,
    val state: String? = null,
    val restricted: Boolean = false,  // 限制级(发行区间接口返回,搜索/随机接口没有此字段)
    val score: String? = null,        // 评分(接口返回字符串,如 "7.8" 或 "0" 表示无评分)
) {
    val gameId: Long get() = if (id != 0L) id else gid
    val displayName: String get() = mainName ?: chineseName ?: name ?: "未知游戏"
    val coverUrl: String?
        get() = mainImg?.let { if (it.startsWith("http")) it else "https://cdn.ymgal.games/$it" }
}

/** 搜索分页结果 */
data class SearchPage(
    val result: List<GameItem>? = null,
    val total: Int = 0,
    val hasNext: Boolean = false,
    val pageNum: Int = 1,
    val pageSize: Int = 20,
)

data class GameDetailData(val game: GameDetail? = null)

/** 游戏详情(仅包含当前用到的字段,Gson 自动忽略其余) */
data class GameDetail(
    val gid: Long = 0,
    val name: String? = null,
    val chineseName: String? = null,
    val introduction: String? = null,
    val mainImg: String? = null,
    val publishTime: String? = null,
    val releaseDate: String? = null,
    val haveChinese: Boolean = false,
    val restricted: Boolean = false,
    val state: String? = null,
    val website: List<WebsiteLink>? = null,
    val releases: List<Release>? = null,
) {
    val displayName: String get() = chineseName ?: name ?: "未知游戏"
    val coverUrl: String?
        get() = mainImg?.let { if (it.startsWith("http")) it else "https://cdn.ymgal.games/$it" }
}

/** 官网链接(详情接口的 website 字段是对象数组) */
data class WebsiteLink(
    val title: String? = null,
    val link: String? = null,
)

data class Release(
    val id: Long? = null,
    val releaseName: String? = null,
    val relatedLink: String? = null,
    val platform: String? = null,
    val releaseLanguage: String? = null,
    val restrictionLevel: String? = null,
)
