package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Gomov : MainAPI() {
    override var mainUrl = "https://gomov.bio"
    override var name = "Gomov"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "page/%d/?s&search=advanced&post_type=movie" to "Movies",
        "category/western-series/page/%d/" to "Western Series",
        "tv/page/%d/" to "Tv Shows",
        "category/korean-series/page/%d/" to "Korean Series",
        "category/chinese-series/page/%d/" to "Chinese Series",
        "category/india-series/page/%d/" to "India Series",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("article.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.attr("src"))
        val quality = this.select("div.gmr-qual").text().trim()
        return if (quality.isEmpty()) {
            val episode = this.select("div.gmr-numbeps > span").text().toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    private fun Element.toBottomSearchResult(): SearchResponse? {
        val title = this.selectFirst("a > span.idmuvi-rp-title")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = fixUrl(this.selectFirst("a > img")?.attr("data-src").toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?s=$query&post_type[]=post&post_type[]=tv").document.select("article.item")
            .mapNotNull {
                it.toSearchResult()
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("h1.entry-title")?.text()?.substringBefore("Season")?.trim()
                .toString()
        val poster =
            fixUrl(document.selectFirst("figure.pull-left > img")?.attr("src").toString())
        val tags = document.select("span.gmr-movie-genre:contains(Genre:) > a").map { it.text() }

        val year =
            document.select("span.gmr-movie-genre:contains(Year:) > a").text().trim().toIntOrNull()
        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div[itemprop=description] > p")?.text()?.trim()
        val trailer = document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")?.attr("href")
        val rating =
            document.selectFirst("div.gmr-meta-rating > span[itemprop=ratingValue]")?.text()
                ?.toRatingInt()
        val actors = document.select("div.gmr-moviedata").last()?.select("span[itemprop=actors]")
            ?.map { it.select("a").text() }

        val recommendations = document.select("div.idmuvi-rp ul li").mapNotNull {
            it.toBottomSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.vid-episodes div.episode").map { eps ->
                val href = fixUrl(eps.select("a").attr("href"))
                val episode = eps.attr("data-epi").toIntOrNull()
                val season = eps.attr("data-sea").toIntOrNull()
                Episode(
                    href,
                    season = season,
                    episode = episode,
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        val id = document.selectFirst("div#muvipro_player_content_id")!!.attr("data-id")

        document.select("div.tab-content-ajax").apmap {
            val server = app.post(
                "$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf("action" to "muvipro_player_content", "tab" to it.attr("id"), "post_id" to id)
            ).document.select("iframe").attr("src")

            loadExtractor(httpsify(server), "$mainUrl/", subtitleCallback, callback)
        }

        return true

    }

}

class Filelions : Filesim() {
    override val name = "Filelions"
    override var mainUrl = "https://filelions.to"
}

class Likessb : StreamSB() {
    override var name = "Likessb"
    override var mainUrl = "https://likessb.com"
}

class DbGdriveplayer : Gdriveplayer() {
    override var mainUrl = "https://database.gdriveplayer.us"
}