package com.nexus.launcher.feed

data class CuratedFeedSource(
    val name: String,
    val category: String,
    val url: String
)

object CuratedFeedSources {

    val CATEGORIES = listOf(
        "WORLD",
        "TECHNOLOGY",
        "FINANCE",
        "SPORTS",
        "ENTERTAINMENT"
    )

    val ALL_SOURCES: List<CuratedFeedSource> = listOf(
        // World News (5 sources)
        CuratedFeedSource("NPR", "WORLD", "https://feeds.npr.org/1001/rss.xml"),
        CuratedFeedSource("ABC News", "WORLD", "https://feeds.abcnews.com/abcnews/topstories"),
        CuratedFeedSource("The Guardian", "WORLD", "https://www.theguardian.com/world/rss"),
        CuratedFeedSource("The New York Times", "WORLD", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"),
        CuratedFeedSource("BBC News", "WORLD", "https://feeds.bbci.co.uk/news/world/rss.xml"),

        // Technology (5 sources)
        CuratedFeedSource("The Verge", "TECHNOLOGY", "https://www.theverge.com/rss/index.xml"),
        CuratedFeedSource("Ars Technica", "TECHNOLOGY", "https://feeds.arstechnica.com/arstechnica/index"),
        CuratedFeedSource("Wired", "TECHNOLOGY", "https://www.wired.com/feed/rss"),
        CuratedFeedSource("Gizmodo", "TECHNOLOGY", "https://gizmodo.com/rss"),
        CuratedFeedSource("Android Authority", "TECHNOLOGY", "https://www.androidauthority.com/feed"),

        // Finance (4 sources)
        CuratedFeedSource("Yahoo Finance", "FINANCE", "https://finance.yahoo.com/news/rssindex"),
        CuratedFeedSource("Forbes", "FINANCE", "https://www.forbes.com/business/feed"),
        CuratedFeedSource("CNBC", "FINANCE", "https://www.cnbc.com/id/100003114/device/rss/rss.html"),
        CuratedFeedSource("Fortune", "FINANCE", "https://fortune.com/feed"),

        // Sports (4 sources)
        CuratedFeedSource("MLB", "SPORTS", "https://www.mlb.com/feeds/news/rss.xml"),
        CuratedFeedSource("The Guardian Sport", "SPORTS", "https://www.theguardian.com/sport/rss"),
        CuratedFeedSource("The Guardian Football", "SPORTS", "https://www.theguardian.com/football/rss"),
        CuratedFeedSource("BBC Sport Football", "SPORTS", "https://www.bbc.co.uk/sport/football/rss.xml"),

        // Entertainment (4 sources)
        CuratedFeedSource("Variety", "ENTERTAINMENT", "https://variety.com/feed/"),
        CuratedFeedSource("The Hollywood Reporter", "ENTERTAINMENT", "https://www.hollywoodreporter.com/feed/"),
        CuratedFeedSource("Rolling Stone", "ENTERTAINMENT", "https://www.rollingstone.com/feed/"),
        CuratedFeedSource("Deadline", "ENTERTAINMENT", "https://deadline.com/feed/")
    )

    fun getSourcesForCategory(category: String): List<CuratedFeedSource> {
        return ALL_SOURCES.filter { it.category.equals(category, ignoreCase = true) }
    }
}
