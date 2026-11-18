# LudoPlus: Play Store publishing

What a Play Store submission needs beyond the normal build. The GitHub release pipeline is in `DESIGN.md`.

**Done**:

- the in-app licences page;
- the App Bundle, signing and the mapping archive;
- the store descriptions and the privacy policy (drafted);
- the listing graphics (`STORE_ASSETS.md`).

The rest is a checklist for when the submission happens.

## Outside this repo (the maintainer does these; ask, don't attempt)

In order:

1. **A Play Console developer account.** It costs $25 and identity verification takes days, and nothing else can start
   without it.
2. **A hosted privacy policy.** The draft is `docs/privacy-policy.md`.
   - GitHub Pages publishes it (`.github/workflows/pages.yml`, which runs when `docs/` changes; the Pages source must be
     "GitHub Actions") at <https://zodac.github.io/LudoPlus/privacy-policy>.
   - The contact is <zodacapps@gmail.com>.
   - **Update it** when billing changes, a new SDK or `INTERNET` use ships, or when `android:allowBackup` changes (it
     mentions backup). It describes both apps: ads and consent (section 3), Play Games (section 4), and the purchases
     (section 5); rewritten for them 2026-11-18, for the maintainer's review.
   - **Never edit its "Last updated" line.** `pages.yml` sets it as it publishes (`.github/scripts/stamp_privacy_date.sh`,
     tested by `scripts/tests/stamp-privacy-date-test.sh`): the publish date when the policy changed in that push, and
     otherwise the date already live, so another page's change or a manual re-publish doesn't move it.

**Console-only configuration**:

- **The listing.** Text, graphics, category and contact.
- **Declarations.** The content rating, data safety form, and target audience. Each is revisited when the purchase
    ships (`PURCHASES.md`, Phase 7). They're under Play Console > the app > **Policy and programs > App content**; the data safety answers are below.
- **Play App Signing.** Play takes the upload certificate from the first signed `.aab`
    (`keytool -export -rfc -alias upload` if App integrity asks first).
- **The upload key**, made as DiceFive's was (its certificate: `CN=DiceFiveUpload, O=zodac, C=NZ`, RSA 2048, 10,000
    days), on the maintainer's own machine:

    ```text
    keytool -genkeypair -v -keystore upload.jks -storetype PKCS12 -alias upload \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=LudoPlusUpload, O=zodac, C=NZ"
    ```

    - It asks only for the password: PKCS12 keeps one for the store and the key, so `RELEASE_KEYSTORE_PASSWORD` and
        `RELEASE_KEY_PASSWORD` are the same, and `RELEASE_KEY_ALIAS` is `upload`.
    - `RELEASE_KEYSTORE_BASE64` is `base64 -w0 upload.jks`. Back up the file and its password: a lost upload key needs
        Play support to reset.
- **Closed testing.** 12+ testers for 14+ days before requesting production.
- **Play Games Services.** Link it, register the SHA-1s and add testers (`PLAY_GAMES.md`).
- **The "Unlock all styles" product** and its license testers: done (`PURCHASES.md`).

## Data safety

Play Console > the app > Policy and programs > App content > **Data safety**. The answers are the maintainer's to give
(Google says the developer alone is responsible for them). This draft follows what the app sends while signed in to
Play Games (`PLAY_GAMES.md`) and Google's list for the SDK
([Play Games data disclosure](https://developer.android.com/games/pgs/data-collection)). Nothing leaves the device
until the player signs in.

- **Does the app collect or share user data?** Yes: the ad banner (AdMob) shares data with Google for ads, and Play
    Games and cloud save send data off the device.
- **Encrypted in transit?** Yes (AdMob and Play Games Services use HTTPS).
- **Account creation**: "My app does not allow users to create an account". LudoPlus has no account or identity of
    its own; sign-in uses the player's existing Google Play Games profile, which Google owns. So no account-deletion URL
    is required. Then:
    - **Can users log in with accounts created outside the app?** Yes, **Other**: "Optional sign-in with an existing
        Google Play Games profile, for achievements, leaderboards and cloud save."
- **Can users ask for deletion?** Yes: from their Play Games profile (one game's data, or the whole account) or their
    Google Account. If the form asks for a URL, give the privacy policy, whose section 3 says how.
- **Data types, all collected, none shared** (Google acting for the app isn't "sharing"), all **optional** (sign-in is
    the player's choice), purpose **App functionality** unless noted:
    - Personal info > **Name**: the player names typed in travel in the cloud save.
    - Personal info > **User IDs**: the Play Games player id and gamer name.
    - Photos and videos > none. The avatar is Google's, shown, not collected.
    - App activity > **In-app actions** (scores, achievements, progress counters) and **Other user-generated content**
        (the saved game: settings, styles, the game in progress).
    - App info and performance > **Crash logs** and **Diagnostics**: collected by the Play Games SDK itself, purpose
        Analytics (Google's list says "to improve the stability of our SDKs").
    - Financial info > **Purchase history**: the play app reads whether the player's Google Play account owns "Unlock all
        styles", and keeps one flag on the device. Purpose App functionality. Not linked to any server of ours, as there
        is none. Check Google's current guidance on whether a read through Play Billing counts as collecting.
- **Ads (AdMob)**, from 2026-11-18 (check against Google's own AdMob disclosure guide when filling the form):
    - Device or other IDs > **Device or other IDs** (the advertising ID): collected and **shared**, purpose
        **Advertising or marketing** (and Analytics, Fraud prevention, as Google lists for AdMob). Not optional in the form's
        sense: the banner is shown to everyone who hasn't bought "Remove ads".
    - Location > **Approximate location** (from the IP address): collected by the SDK for ads, as Google lists it.
    - App activity > **App interactions** (ad impressions and taps), and App info and performance > **Crash logs**,
        **Diagnostics**: collected by the SDK.
- **Nearby games** (from 2026-11-18): the phones send each other the game, the player names among it, directly, with no
    server; nothing reaches the developer. Google's guidance treats data sent device to device without passing through
    a server the developer controls as not collected; check the form's current wording when filling it in.
- **The crash page's GitHub report isn't collection by the app.** The player posts it from their own browser and account,
    the app sends nothing, and the play app has no `INTERNET` permission. The privacy policy still describes it.
- **Progress files aren't in the form.** They're only in the GitHub APK, which isn't on Play.
- **Revisit** when billing or analytics ship, or the cloud save gains a new kind of data.

## Store listing

- **Graphics**: generated (the `store-assets` skill). The hi-res icon follows the launcher icon. The video goes to
    YouTube; Play takes its URL.
- **Short description** (80 chars max), 55 chars:

    ```text
    The classic race to home, with a few twists of its own.
    ```

    It's the tagline: also `README.md`'s first line, the GitHub repo description, and the opening of the full
    description. Keep them the same.
- **Full description**: `assets/full-description.txt`, about 2650 of 4000 characters (2026-11-18). Keep its counts and
    lists true, and in step with `README.md`'s "What's in it":
    - the player counts and team set-ups under "The classic rules";
    - the built-in modes under "Three ways to play" (and that heading's count);
    - the house rules under "House rules", all nine, and custom modes;
    - "Play together nearby" (the play app's Nearby games, `NETPLAY.md`);
    - "Over 100 achievements" (110 today: 100 count towards completion, plus 10 Easter Eggs);
    - the style counts (8 boards, 7 sets of pawns, 35 dice, and 20 backgrounds), as `README.md` gives them;
    - what "Google Play Games" says sign-in brings (`PLAY_GAMES.md`);
    - the purchases ("Unlock all styles", "Remove ads"; `PURCHASES.md`) and the ad banner (`ADS.md`).
- **The banned-word rule applies.** Play also rejects listings that lean on another product's trademark. Use 3x/4x/5x.
- **Open-source notices are done in-app** (Settings > Licences, generated with a copyleft guard).
    - The build strips each AndroidX `META-INF/androidx/**/LICENSE.txt`, which is fine because the dialog ships the
        Apache-2.0 text once.
    - Sounds are credited (`app/licensing/asset-sources.json`).
    - Optional: link a hosted copy.
- **Allowing a new licence.** Read it before adding it to `LicencePolicy` (`build-logic/.../LicencePolicy.kt`).
    - LGPL, MPL and EPL can be workable, but carry real obligations (LGPL relinking is awkward with R8).
    - GPL and AGPL would force the whole app open.

## The app's own licence

**Decided: proprietary, all rights reserved** (`LICENSE`). The source is publicly readable.

**Why**, over PolyForm Strict and open source:

- the maintainer wants read-only code, with no reuse, no store forks and no contributions;
- plain copyright is the most tested footing;
- a permissive licence would allow styles-unlocked, ad-free rebuilds;
- the proprietary Google SDKs and the App Store conflict with GPL-style licences.

The sole holder can relicense later.

**The notice must exclude third-party parts**, which keep their own licences. `LICENSE` says so and names the font.

- **Sora stays OFL-1.1.** Condition 5: modified or not, it must be distributed under the OFL only. Our `sora.ttf` is a
    modified weight-700 instance. `README.md`, the listings and any EULA must never claim "all files" without that
    carve-out.
- **A cut-down font with Reserved Font Names** (DiceFive shipped MathJax's and Andika's for dice faces) is renamed, as
    the OFL requires of a modified font. None ships yet.
- **The OFL forbids selling the font on its own.** Bundling it in a paid app is fine.
- **Audio.**
    - CC0 needs nothing.
    - CC-BY needs a credit and can't take added restrictions, so it stays outside the all-rights-reserved scope.
    - CC-BY-SA adaptations stay CC-BY-SA.
    - **NC licences are incompatible with the purchase.**
- **Every library** in the licences dialog keeps its own licence.
- **App artwork** is `LicenseRef-LudoPlus-AllRightsReserved` in `asset-sources.json` (`APP_LICENSE` in
    `VerifyAssetSourcesTask`). Change both together.

**Still open:**

- **AI authorship.** Copyright in AI-generated material is unsettled. Worth a lawyer's view if the app earns real money.
- **The copyright line is a pseudonym**: "Copyright 2026 zodac.net" (in `LICENSE`, `README.md` and
    `asset-sources.json`).
    - It's valid as a known alternative designation, but enforcing it needs proof of who owns the domain.
    - Revisit when registering for payments, which exposes the legal name anyway:
        "Copyright `<legal name>` (zodac.net)".

## In-repo build changes

- **App Bundle**: done. `android-release.yml` builds the play flavour's (`bundlePlayRelease`) beside the github APK and
    attaches `LudoPlus-<version>.aab` (`DESIGN.md`, "Distribution"). `play-release.yml` uploads it to Play, on demand (`DESIGN.md`, "Release pipeline"),
    with "Sidekick is on by default" ticked in Play Console (`PLAY_GAMES.md`, "Release").
- **`INTERNET` permission**: not declared, and Play Games doesn't add it. The v2 SDK talks to the Play services app,
    which does the network work (`PLAY_GAMES.md`, "What Google provides"). The Billing Library's telemetry merges it,
    and the play manifest removes it (`PURCHASES.md`, "What Google provides").
- **Signing**: done. Play App Signing holds the app key. The upload key backs the `RELEASE_KEY*` secrets (alias
    `upload`, SHA-256 `DD:9E:C8:0C:…:1E:A6:18`); the maintainer keeps a copy, and Play support can reset it if lost.
- **`mapping.txt`**: done. Each release attaches `LudoPlus-<version>-mapping.txt`, and the `.aab` carries it too
    (`BUNDLE-METADATA/.../proguard.map`), which Play uses to deobfuscate vitals. The attached copy is for GitHub-APK
    traces.
- **Native debug symbols**: done. The app has no native code, but AndroidX ships some (DataStore, Compose
    graphics-path). The release build type's `ndk.debugSymbolLevel = "SYMBOL_TABLE"` puts their symbol tables in the
    `.aab` (`BUNDLE-METADATA/com.android.tools.build.debugsymbols/`), so Play needs no separate upload. This needs the NDK pinned by
    `ndkVersion`: the sandbox image bakes it and CI installs it. No library may be listed in `keepDebugSymbols`,
    because AGP leaves a kept library out of the extracted tables. Graphics-path ships pre-stripped, so its frames stay
    as addresses.

## The purchases, and the ad banner

- **The "Unlock all styles" purchase**, the app's only one: `PURCHASES.md` has the plan, the payments profile, the
    product, license testers and the declarations it changes. GitHub release APKs continue, with no purchase shown.
- **The ad banner** (`ADS.md`; the maintainer, 2026-11-18, reversing DiceFive's "No ads, ever"): AdMob in the play app.
    Play Console: "Contains ads: Yes"; Advertising ID declaration: yes, for advertising (the play manifest has `AD_ID`);
    target audience 13 and over. The github APK has no ad SDK, no `AD_ID`, and no `INTERNET` (`GithubFlavourTest`).

Release notes for each submission are written at release time, not stored here.
