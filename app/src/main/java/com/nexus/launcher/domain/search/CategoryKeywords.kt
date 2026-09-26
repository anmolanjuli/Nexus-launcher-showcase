package com.nexus.launcher.domain.search

/**
 * The words [CategoryEngine] looks for in a package name and an app's name, per category.
 *
 * Matching is whole-word: a token only counts where it is not run together with more letters, so
 * "word" does not match "onepassword", "keep" does not match "keepass", and "line" does not match
 * "online". That is what a plain `contains` got wrong, sending password managers to Productivity
 * and every `*.online.*` app to Social.
 *
 * [ORDER] runs the most specific categories first: an app that says both "bank" and "news" is a
 * bank. Everything unmatched lands in Utilities, so tokens here should be ones that really do
 * name a kind of app.
 */
internal object CategoryKeywords {

    private val FINANCE = listOf(
        "bank", "banking", "wallet", "finance", "financial", "money", "invest", "investing",
        "stock", "stocks", "crypto", "bitcoin", "paypal", "venmo", "zelle", "gpay", "paytm",
        "phonepe", "spay", "robinhood", "coinbase", "binance", "insurance", "loan", "credit",
        "trading", "forex", "etrade", "fidelity", "schwab", "ameritrade", "chase", "wellsfargo",
        "bankofamerica", "capitalone", "amex", "mastercard", "revolut", "monzo", "wise",
        "budget", "expense", "accounting", "invoice", "tax", "esewa", "khalti", "upi",
    )

    private val SHOPPING = listOf(
        "shop", "shopping", "store", "amazon", "ebay", "etsy", "aliexpress", "alibaba", "temu",
        "shein", "wish", "walmart", "target", "costco", "ikea", "flipkart", "myntra", "lazada",
        "shopee", "daraz", "mercari", "wayfair", "bestbuy", "zalando", "asos", "cart", "deals",
        "coupon", "marketplace", "auction", "olx", "craigslist",
    )

    private val TRAVEL = listOf(
        "travel", "trip", "tripadvisor", "booking", "airbnb", "expedia", "hotel", "hotels",
        "flight", "flights", "airlines", "airline", "airways", "uber", "lyft", "ola", "grab",
        "bolt", "taxi", "transit", "metro", "railway", "rail", "train", "bus", "skyscanner",
        "kayak", "agoda", "trivago", "hostel", "roadtrip", "navigation", "maps", "waze",
        "rentalcars", "parking", "passport", "visa",
    )

    private val HEALTH = listOf(
        "health", "fitness", "workout", "exercise", "gym", "running", "runner", "strava",
        "fitbit", "garmin", "calorie", "diet", "nutrition", "yoga", "meditation", "mindfulness",
        "calm", "headspace", "sleep", "period", "pregnancy", "doctor", "medical", "medicine",
        "pharmacy", "clinic", "hospital", "therapy", "steps", "pedometer", "heart", "bloodpressure",
    )

    private val FOOD = listOf(
        "food", "recipe", "recipes", "cooking", "kitchen", "restaurant", "menu", "delivery",
        "doordash", "ubereats", "grubhub", "deliveroo", "justeat", "swiggy", "zomato", "foodpanda",
        "starbucks", "mcdonalds", "dominos", "pizza", "coffee", "grocery", "groceries", "instacart",
    )

    private val WEATHER = listOf("weather", "forecast", "accuweather", "rain", "climate", "storm")

    private val EDUCATION = listOf(
        "education", "learn", "learning", "course", "courses", "study", "school", "college",
        "university", "classroom", "student", "teacher", "lesson", "lessons", "tutor", "exam",
        "quiz", "duolingo", "khan", "coursera", "udemy", "edx", "brilliant", "anki", "flashcard",
        "dictionary", "language", "wikipedia", "wolfram",
    )

    private val NEWS = listOf(
        "news", "newspaper", "headlines", "times", "guardian", "bbc", "cnn", "reuters", "nytimes",
        "bloomberg", "feedly", "rss", "magazine", "article", "articles", "blog", "medium",
        "pocket", "kindle", "ebook", "ebooks", "books", "book", "reader", "reading", "comics",
        "manga", "audiobook", "audible", "wattpad", "goodreads",
    )

    private val PHOTOGRAPHY = listOf(
        "camera", "photo", "photos", "photography", "gallery", "lightroom", "snapseed", "vsco",
        "picsart", "photoeditor", "collage", "selfie", "lens", "filters", "darkroom", "raw",
    )

    private val MUSIC = listOf(
        "music", "audio", "spotify", "soundcloud", "tidal", "deezer", "pandora", "shazam",
        "audiomack", "poweramp", "equalizer", "podcast", "podcasts", "radio", "fm", "mp3",
        "playlist", "lastfm", "bandcamp", "metronome", "tuner", "karaoke",
    )

    private val VIDEO = listOf(
        "video", "videos", "youtube", "netflix", "hulu", "disney", "hbomax", "max", "peacock",
        "paramount", "appletv", "primevideo", "twitch", "crunchyroll", "funimation", "plex",
        "kodi", "stremio", "nuvio", "iptv", "player", "vlc", "mxplayer", "streaming", "stream",
        "movies", "movie", "cinema", "tv",
    )

    private val GAMES = listOf(
        "game", "games", "gaming", "chess", "puzzle", "arcade", "casino", "poker", "sudoku",
        "solitaire", "candy", "clash", "minecraft", "roblox", "fortnite", "pubg", "freefire",
        "subway", "temple", "angry", "playstation", "xbox", "steam", "epicgames", "supercell",
        "rovio", "gameloft", "ubisoft", "nintendo", "emulator", "rpg", "shooter", "racing",
    )

    private val SOCIAL = listOf(
        "instagram", "facebook", "twitter", "whatsapp", "telegram", "snapchat", "tiktok",
        "linkedin", "discord", "reddit", "pinterest", "messenger", "messages", "messaging",
        "signal", "viber", "wechat", "kakao", "skype", "zoom", "meet", "teams", "threads",
        "mastodon", "bluesky", "tumblr", "dating", "bumble", "tinder", "hinge", "chat", "sms",
        "mms", "dialer", "contacts", "phone", "call", "calls", "line", "imo", "hangouts", "duo",
    )

    private val PRODUCTIVITY = listOf(
        "gmail", "email", "mail", "outlook", "docs", "sheets", "slides", "office", "word",
        "excel", "powerpoint", "notion", "evernote", "todoist", "trello", "asana", "slack",
        "calendar", "keep", "notes", "note", "tasks", "todo", "drive", "dropbox", "onedrive",
        "assistant", "copilot", "chatgpt", "gemini", "claude", "perplexity", "scanner", "scan",
        "pdf", "sign", "meetings", "workspace", "jira", "confluence", "github", "gitlab", "code",
        "terminal", "ssh",
    )

    /** Themes, icon packs, wallpapers, widgets, launchers — what a phone is dressed up with. */
    private val PERSONALIZATION = listOf(
        "theme", "themes", "themer", "iconpack", "icons", "icon", "wallpaper", "wallpapers",
        "walls", "launcher", "widget", "widgets", "kwgt", "klwp", "kustom", "zooper", "substratum",
        "nova", "lawnchair", "niagara", "smartlauncher", "poco", "oneui", "miui", "customization",
        "personalize", "personalization", "ringtone", "lockscreen", "fonts", "emoji", "sticker",
        "stickers", "keyboard", "tasker", "shortcuts",
    )

    private val UTILITIES = listOf(
        "chrome", "browser", "firefox", "opera", "brave", "duckduckgo", "clock", "deskclock",
        "alarm", "timer", "calculator", "files", "filemanager", "documentsui", "explorer",
        "downloads", "zip", "unzip", "backup", "cleaner", "battery", "vpn", "password",
        "bitwarden", "lastpass", "onepassword", "authenticator", "translate", "flashlight",
        "torch", "compass", "scale", "ruler", "remote", "printer", "bluetooth", "wifi",
        "recorder", "voice",
    )

    /** Genuine OS components — matched on the package name's start, not as words. */
    val SYSTEM_PREFIXES = listOf(
        "com.android.settings", "com.android.systemui",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.providers", "com.android.server", "com.android.shell",
        "com.android.storagemanager", "com.android.cellbroadcast", "com.android.emergency",
        "com.android.bluetooth", "com.android.nfc", "com.android.printspooler",
        "com.android.wallpaper", "com.android.traceur", "com.android.dynsystem",
        "com.google.android.gms", "com.google.android.gsf",
        "com.google.android.ext.services", "com.google.android.webview",
        "com.google.android.setupwizard", "com.google.android.overlay",
        "com.google.android.federatedcompute", "com.google.android.onetimeinitializer",
    )

    /**
     * Packages the word lists would read wrongly: Gmail's package says neither "gmail" nor
     * "mail", Google Photos would read as Media, Play Games as a store.
     */
    val EXACT = mapOf(
        "com.google.android.gm" to AppCategoryCatalog.PRODUCTIVITY,
        "com.google.android.apps.photos" to AppCategoryCatalog.PHOTOGRAPHY,
        "com.google.android.apps.maps" to AppCategoryCatalog.TRAVEL,
        "com.google.android.apps.nbu.paisa.user" to AppCategoryCatalog.FINANCE,
        "com.google.android.play.games" to AppCategoryCatalog.GAMES,
        "com.android.vending" to AppCategoryCatalog.UTILITIES,
        "com.google.android.apps.messaging" to AppCategoryCatalog.SOCIAL,
        "com.google.android.dialer" to AppCategoryCatalog.SOCIAL,
        "com.google.android.contacts" to AppCategoryCatalog.SOCIAL,
        "com.google.android.deskclock" to AppCategoryCatalog.UTILITIES,
        "com.google.android.calculator" to AppCategoryCatalog.UTILITIES,
        "com.google.android.apps.docs" to AppCategoryCatalog.PRODUCTIVITY,
        "com.google.android.apps.youtube.music" to AppCategoryCatalog.MUSIC,
    )

    /** Most specific first; a match stops the search. */
    private val ORDER: List<Pair<Int, List<String>>> = listOf(
        AppCategoryCatalog.FINANCE to FINANCE,
        AppCategoryCatalog.SHOPPING to SHOPPING,
        AppCategoryCatalog.TRAVEL to TRAVEL,
        AppCategoryCatalog.HEALTH to HEALTH,
        AppCategoryCatalog.FOOD to FOOD,
        AppCategoryCatalog.WEATHER to WEATHER,
        AppCategoryCatalog.EDUCATION to EDUCATION,
        AppCategoryCatalog.NEWS to NEWS,
        AppCategoryCatalog.PHOTOGRAPHY to PHOTOGRAPHY,
        AppCategoryCatalog.MUSIC to MUSIC,
        AppCategoryCatalog.VIDEO to VIDEO,
        AppCategoryCatalog.GAMES to GAMES,
        AppCategoryCatalog.PERSONALIZATION to PERSONALIZATION,
        AppCategoryCatalog.SOCIAL to SOCIAL,
        AppCategoryCatalog.PRODUCTIVITY to PRODUCTIVITY,
        AppCategoryCatalog.UTILITIES to UTILITIES,
    )

    /** The category [text] (a package name or an app name, lower-cased) names, or null. */
    fun match(text: String): Int? {
        if (text.isEmpty()) return null
        for ((id, tokens) in ORDER) {
            if (tokens.any { contains(text, it) }) return id
        }
        return null
    }

    /** [token] present in [text] as a whole word — letters either side disqualify it. */
    private fun contains(text: String, token: String): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf(token, from)
            if (at < 0) return false
            val before = if (at == 0) ' ' else text[at - 1]
            val afterIndex = at + token.length
            val after = if (afterIndex >= text.length) ' ' else text[afterIndex]
            if (!before.isLetter() && !after.isLetter()) return true
            from = at + 1
        }
    }
}
