package com.galstruo.app.data.ymgal

import java.time.LocalDate
import java.time.YearMonth

/** 月幕数据仓库:界面层只与它打交道 */
object YmgalRepository {

    private val api = YmgalApi()

    suspend fun search(keyword: String, page: Int): SearchPage = api.searchGames(keyword, page)

    suspend fun detail(gid: Long): GameDetail = api.gameDetail(gid)

    /** 最近 [days] 天发行的游戏(接口要求区间不超过 50 天) */
    suspend fun latest(days: Long = 30): List<GameItem> {
        val end = LocalDate.now()
        val start = end.minusDays(days)
        return api.gamesBetween(start.toString(), end.toString())
    }

    /** 某个月发行的游戏(自然月最长 31 天,不超过接口 50 天区间限制) */
    suspend fun month(month: YearMonth): List<GameItem> =
        api.gamesBetween(month.atDay(1).toString(), month.atEndOfMonth().toString())

    suspend fun random(num: Int): List<GameItem> = api.randomGames(num)
}
