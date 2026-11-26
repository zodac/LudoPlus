# LudoPlus: the ad banner

**Built (Phase 11, 2026-11-18); no AdMob account yet.** The maintainer's decision of 2026-11-18, reversing DiceFive's
"Never ads": one AdMob banner in the play app. This file is the one source on the subject. Commits go under `[Ads]`.

## What the player sees

- **Where**: at the foot of every page but the game's (Game Over included) and the tutorial's, under the page rather
    than over it, as wide as the screen and as tall as AdMob's anchored adaptive banner for that width. The page above it
    leaves the navigation bar's inset to the banner, which sits above the bar.
- **Its slot is there from the first frame**, empty (the page's surface colour) until the ad comes, so no page moves for
    it: wherever there's an ad network (`Ads.available`, known at launch) and ads aren't removed. The slot stays, empty,
    while no ad can be shown (offline, or no consent yet).
- **Under the achievement banners**: they're windows of their own at the foot of the screen, so they cover it. While
    any is up (`AchievementBannersShowing`, set by `AchievementBannerHost`), a layer over the ad takes every touch, so a
    tap meant for the achievement can't open an ad (the maintainer's ask).
- **Gone** with "Remove ads" or the combined purchase (`Entitlement.NO_ADS`, `PURCHASES.md`). The cached
    entitlement is read with the container (`AppContainer.adsRemoved`), and no page is drawn until it has been, so a buyer
    never sees the slot come and go, nor anyone else the page move. A read that fails counts as not removed.
- **The github app never has ads**: no SDK, no `INTERNET`, no `AD_ID` (`GithubFlavourTest`).
- **TalkBack** reads the ad as AdMob's own view presents it; nothing of the app's is added.

## Consent

Google's User Messaging Platform (UMP). At each launch the consent information is brought up to date and, where the law
asks for it (the UK and the EEA) and the player hasn't answered, Google's form is shown. AdMob starts only once
`canRequestAds`; until then the slot is empty. Where the law asks for a way back to the choices,
Settings shows **Ad privacy choices** (`Ads.privacyOptionsRequired`), which opens Google's form again. The forms'
messages are configured in AdMob's Privacy & messaging page, not here.

The listing's target audience is **13 and over** (the maintainer, 2026-11-18), so ads are personalised where consent
allows, and Google's Families policy doesn't apply. A change to include children would need child-directed requests
and a certified SDK.

## Code

```text
app/shared/.../ads/Ads.kt               Ads (available, Banner, privacyOptionsRequired, showPrivacyOptions), NoAds, LocalAds,
                                        AchievementBannersShowing - nothing here names Google
app/shared/.../ui/LudoPlusApp.kt        when the banner's slot is there
app/android/src/play/.../ads/AdMobAds   the only com.google.android.gms.ads and ump imports, in the play flavour alone
```

- `Distribution.ads` gives `AdMobAds.attach(activity)` in the play app (on a device with Google Play services; any build,
    a debug one showing test ads) and `NoAds` in the github app; `MainActivity` hands it to `LudoPlusApp`.
- **The decor first**: `attach` installs the window's decor view on the main thread before calling UMP, which reads it
    from its own thread. Called from `onCreate` before `setContent`, the two raced to build it: the 1.0.0 release's
    Baseline Profile journey got a black screen with no app content on its third launch (and a caught "Window couldn't
    find content container view" on its second).
- **Ids**: AdMob's app id (the manifest's `com.google.android.gms.ads.APPLICATION_ID`, from the `admobAppId` placeholder)
    and the banner's ad unit id (`BuildConfig.ADMOB_BANNER_ID`) come from CI's `ADMOB_APP_ID` and `ADMOB_BANNER_ID`
    repository variables (public ids, not secrets). Without them, Google's published test ids. **A debug build always
    asks for the test banner** (`AdMobAds.bannerId`): real ads must never be shown to or tapped by a developer. The release
    workflow refuses to build the Play bundle without real ids (`android-release.yml`, "Check the AdMob ids"), and
    builds none while they and `PLAY_LICENCE_KEY` are all unset.
- **The API alone** (2026-11-26): the app depends on `play-services-ads-api`, not `play-services-ads`, which adds AdMob's
    own copy of the ad code (~6,300 classes). Without it, the SDK loads Google Play services' copy
    (`com.google.android.gms.ads.dynamite`), which every device the play app reaches has. Play release APK 9,923,804 →
    6,656,596 bytes (-33%); a test banner checked on the sandbox emulator's Google APIs image. The app uses no mediation;
    before adding any, check whether its adapters need `play-services-ads` back.
- **Licences**: AdMob, UMP, and what they pull in are Google's ASDKL, allowed in `LicencePolicy`; `checker-qual` (MIT)
    is credited in `app/licensing/libraries/`.
- **The slot's height** is AdMob's own sum over the screen's width (`AdSize`), which needs no started SDK; the `AdView` is
    made only once the SDK has started.
- **Tests**: `AdBannerTest` (where the slot is, there from the first frame, gone for a buyer, untouchable under an
    achievement banner), `AdMobAdsTest` (the slot's height before AdMob starts), `PlayFlavourTest`, `GithubFlavourTest`.

## The maintainer's steps

1. An AdMob account, an app (linked to the Play listing once it exists), and an anchored adaptive banner ad unit.
2. Set the `ADMOB_APP_ID` and `ADMOB_BANNER_ID` repository variables.
3. AdMob > Privacy & messaging: a GDPR message (and any other region's the account needs), published.

   Steps 1-3 done, with a US state regulations message too (the maintainer, 2026-11-23); the app is linked to its Play
   listing once production is live.

   3a. **app-ads.txt**: AdMob's line (Apps > View all apps > app-ads.txt) served at `https://zodac.github.io/app-ads.txt`,
   from a `zodac/zodac.github.io` user-site repo: AdMob reads it from the root of the Play listing's developer website's
   host, which a project page (`/LudoPlus`) can't serve. Live from 2026-11-23; AdMob verifies it once the app is live on Play
   and linked (App settings > App store details), with the listing's website `https://zodac.github.io/LudoPlus/`.
4. Play Console: "Contains ads: Yes"; the Advertising ID declaration (used, for advertising); the data-safety answers
    (`PUBLISHING.md`); target audience 13 and over.
5. Publish the updated privacy policy (`docs/privacy-policy.md`) before the first version with ads.
6. Check on a phone: the banner on the menu and pages, none in a game, the consent form in a UK or EU region (AdMob's
    debug geography, or a VPN), and an achievement banner over the ad making it untouchable.
