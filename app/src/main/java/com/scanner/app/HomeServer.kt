package com.scanner.app

/** A path on the home stack, from the server address set in Settings (trailing slashes tolerated). */
fun homeUrl(baseUrl: String, path: String): String = baseUrl.trim().trimEnd('/') + path
