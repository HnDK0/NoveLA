package my.noveldokusha.core.domain

import java.io.IOException


class WebViewCookieManagerInitializationFailedException :
    IOException("Webview cookies not found for websited")

// ponytail: ported from Paras fork — accept an optional detail message so the
// Cloudflare interceptor can report *which* attempt or block type failed
// (e.g. "IP blocked by Cloudflare (error 1020/1015)"). The no-arg overload is
// preserved so existing call-sites keep compiling.
class CloudfareVerificationBypassFailedException : IOException {
    constructor() : super("Cloudfare verification failed")
    constructor(message: String) : super(message)
}
