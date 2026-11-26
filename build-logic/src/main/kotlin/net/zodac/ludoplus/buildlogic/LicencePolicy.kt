package net.zodac.ludoplus.buildlogic

/**
 * Which licences an app may ship third-party code and assets under - the copyleft guard every app's licence report
 * (on Android, the AboutLibraries plugin's strict mode) enforces. Anything else fails the build.
 *
 * Only permissive licences - those that ask for attribution and nothing more - are allowed outright, because they place
 * no conditions on how this app itself is licensed. A copyleft (GPL, LGPL, AGPL, MPL, EPL, CDDL...) or unrecognised
 * licence has to be looked at by a person before it can ship: add it here only once its terms are understood - see
 * .claude/PUBLISHING.md.
 */
object LicencePolicy {

    val ALLOWED: List<String> = listOf("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")

    /** Licences allowed only for the components listed against them, never for anything else that turns up under one. */
    val ALLOWED_FOR: Map<String, List<String>> = mapOf(
        // The SIL Open Font License is copyleft on the font alone (a modified Sora must stay OFL and can't be sold by
        // itself) and places no conditions on the app it's bundled in.
        "OFL-1.1" to listOf("sora", "mathjax-fonts", "andika-font"),
        // The sound effects' Freesound sources (see app/licensing/asset-sources.json), all CC0, which asks for nothing and
        // places no condition on the app itself. (CC-BY would need a credit, a link, and a note of changes in its
        // libraries/ entry, and its licence text back in licenses/. Never CC-BY-SA - adaptations must stay under it - nor
        // anything NC; neither is allowed here.)
        "CC0-1.0" to listOf("freesound-588198", "freesound-695731", "freesound-596051", "freesound-351518", "freesound-563356", "freesound-715067"),
        // Google's branding guidelines for Play Games, for its controller badge: no pre-approval needed, and no condition on the
        // app's own licence, provided those guidelines are followed.
        "LicenseRef-Google-Play-Games-Branding" to listOf("google-play-games-branding-icons"),
        // Google's own terms for the Play SDKs (Play Games Services, the Play Billing Library, Google Mobile Ads and its
        // consent form, Nearby Connections, and what they pull in): proprietary, free to ship in an app, with no condition on the app's own
        // licence.
        "ASDKL" to listOf(
            "com.android.billingclient:billing",
            "com.android.billingclient:billing-ktx",
            "com.google.android.gms:play-services-ads-api",
            "com.google.android.gms:play-services-ads-identifier",
            "com.google.android.gms:play-services-appset",
            "com.google.android.gms:play-services-base",
            "com.google.android.gms:play-services-basement",
            "com.google.android.gms:play-services-games-v2",
            "com.google.android.gms:play-services-location",
            "com.google.android.gms:play-services-measurement-base",
            "com.google.android.gms:play-services-measurement-sdk-api",
            "com.google.android.gms:play-services-nearby",
            "com.google.android.gms:play-services-places-placereport",
            "com.google.android.gms:play-services-tasks",
            "com.google.android.play:hsdp",
            "com.google.android.ump:user-messaging-platform",
        ),
    )
}
