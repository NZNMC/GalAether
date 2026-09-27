package com.galstruo.app.data.popular

import com.galstruo.app.data.ymgal.GameItem

/**
 * 热门经典:人工精选的公认名作清单。
 * 月幕开放接口没有热门榜/排行接口,所以把清单(游戏 ID、标题、封面、限制级标记)
 * 提前解析好写死在应用里,首页直接展示,不依赖网络。ID 来自月幕数据库。
 */
data class PopularGame(
    val gid: Long,
    val title: String,
    val coverUrl: String,
    val restricted: Boolean,
)

object PopularGames {

    val list: List<PopularGame> = listOf(
        PopularGame(10008, "CLANNAD", "https://cdn.ymgal.games/archive/main/7e/7e63dc3f64e04f9c8c5637ed0e83877c.webp", false),
        PopularGame(10793, "Fate/stay night", "https://cdn.ymgal.games/archive/main/9a/9a26402dabea43c2aba486a6ac87b07a.webp", true),
        PopularGame(22374, "千恋*万花", "https://cdn.ymgal.games/archive/main/94/94a00e91bf5e44d78506a89ba97d6f34.webp", true),
        PopularGame(28027, "RIDDLE JOKER", "https://cdn.ymgal.games/archive/main/66/66169727906449bb87bad0b59503cf89.webp", true),
        PopularGame(23682, "魔女的夜宴", "https://cdn.ymgal.games/archive/main/a0/a057bbc19b9f41a2868ee415721b84e1.webp", true),
        PopularGame(34600, "星光咖啡馆与死神之蝶", "https://cdn.ymgal.games/archive/main/77/7750de4f89434965b8bd96f891537541.webp", true),
        PopularGame(26435, "Summer Pockets", "https://cdn.ymgal.games/archive/main/31/31aa7bde9c0c4855905d83308341b86b.webp", false),
        PopularGame(35584, "ATRI -My Dear Moments-", "https://cdn.ymgal.games/archive/main/9a/9afc3996bc334e318f48132d4c5fc978.webp", false),
        PopularGame(33130, "苍之彼方的四重奏", "https://cdn.ymgal.games/archive/main/ee/eeb661c225224f799943a41e14d93c67.webp", true),
        PopularGame(34698, "9-nine-雪色雪花雪之痕", "https://cdn.ymgal.games/archive/main/b2/b2a589e6718c434eb3f955811ce0dfd7.webp", true),
        PopularGame(11843, "命运石之门", "https://cdn.ymgal.games/archive/main/b6/b6ea9e9e21d84736b1c742c5d832b3c3.webp", true),
        PopularGame(10788, "月姬", "https://cdn.ymgal.games/archive/main/51/516cac7308be4a74a88311c09d0224d9.webp", true),
        PopularGame(11043, "魔法使之夜", "https://cdn.ymgal.games/archive/main/37/373cc0695942446395e119843b16f346.webp", false),
        PopularGame(17452, "白色相簿2", "https://cdn.ymgal.games/archive/main/34/343a7521543f4a919bfa93aa402eda38.webp", true),
        PopularGame(13457, "秽翼的尤斯蒂娅", "https://cdn.ymgal.games/archive/main/f9/f9bd150f739348b9af77defc922c6ff8.webp", true),
        PopularGame(12882, "美好的每一天", "https://cdn.ymgal.games/archive/main/46/46cb63425d854ca894e989dd033b63dd.webp", true),
        PopularGame(10848, "沙耶之歌", "https://cdn.ymgal.games/archive/main/7d/7de5e73b963d4310b38ed86669eac606.webp", true),
        PopularGame(10811, "planetarian ~星之梦~", "https://cdn.ymgal.games/archive/main/f1/f10dbe1fc53647e989febc1415a9de01.webp", false),
        PopularGame(10791, "水仙 narcissu", "https://cdn.ymgal.games/archive/main/e6/e681c236be434841aea98cc0703fbe0f.webp", false),
        PopularGame(10799, "Ever17", "https://cdn.ymgal.games/archive/main/69/69f7ce89e57b4ab8ab8c57b33e7c1e0e.webp", false),
        PopularGame(14622, "G弦上的魔王", "https://cdn.ymgal.games/archive/main/04/04586a2f81734c8aa265965d95d788a0.webp", true),
        PopularGame(11010, "Rewrite", "https://cdn.ymgal.games/archive/main/08/080d3b37e4e54e5eba9db43056a6b7da.webp", false),
        PopularGame(10800, "Little Busters!", "https://cdn.ymgal.games/archive/main/ca/ca7c4334f4484a348396b655a4f9148a.webp", true),
        PopularGame(31147, "近月少女的礼仪", "https://cdn.ymgal.games/archive/main/3d/3d99dc3f2888479f98eadb3501c50713.webp", true),
        PopularGame(22489, "美少女万华镜 -罪与罚的少女-", "https://cdn.ymgal.games/archive/main/77/77c382d8e10d4cc893768f94c87df650.webp", true),
        PopularGame(11876, "装甲恶鬼村正", "https://cdn.ymgal.games/archive/main/61/61f06bd526c049a6a1008ddee150653b.webp", true),
        PopularGame(14935, "灰色的果实", "https://cdn.ymgal.games/archive/main/e9/e956bcbd92fb4792b1344af7e2a69cfc.webp", true),
        PopularGame(10021, "Kanon", "https://cdn.ymgal.games/archive/main/a6/a6d1736728f44af2b2efae2fabc408f0.webp", true),
        PopularGame(27663, "三色绘恋", "https://cdn.ymgal.games/archive/main/d0/d02fade203ae4a579203a559bb44c83f.webp", false),
        PopularGame(37053, "恋爱绮谭", "https://cdn.ymgal.games/archive/main/58/584e730a89ca4260878ec8f43e80c39d.webp", false),
        PopularGame(30214, "青夏轨迹", "https://cdn.ymgal.games/archive/main/7f/7fbbf98d63a4409badfad7714487634c.webp", true),
        PopularGame(37792, "候鸟", "https://cdn.ymgal.games/archive/main/eb/ebbd5906110c45e99b3e67b40d753740.webp", false),
        PopularGame(14887, "蝶之毒 华之锁", "https://cdn.ymgal.games/archive/main/d2/d24ec6667e79404d85cf6dee5f59de29.jpg", true),
    )

    fun toGameItem(game: PopularGame): GameItem = GameItem(
        id = game.gid,
        name = game.title,
        mainImg = game.coverUrl,
        restricted = game.restricted,
    )
}
