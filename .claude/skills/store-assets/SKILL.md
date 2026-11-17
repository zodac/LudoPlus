---
name: store-assets
description: Generate and review the README's (and, from Phase 10, the Play listing's) phone and 10-inch tablet screenshots with scripts/generate-store-assets.sh. Details and traps in .claude/STORE_ASSETS.md.
---

# Store listing assets

Everything is rendered from the real app by Robolectric tests, then captioned and checked by the script. The output goes
into the gitignored `assets/<version>/` (Android's version), and none of it is committed. Read `.claude/STORE_ASSETS.md`
before changing scenes, captions, or the generator.

```sh
scripts/generate-store-assets.sh                     # the screenshots, a few minutes
scripts/generate-store-assets.sh --parts phone       # some of: phone, tablet
scripts/generate-store-assets.sh --no-render         # re-caption the last renders, no Gradle
```

The icon, the feature graphic, and the video come back with the Play listing (Phase 10 of `IMPLEMENTATION.md`); until
then the script refuses those parts. It checks every file against Play's rules and exits non-zero on a failure.
Intermediate files go to `build/store-assets/`.

## Each change to what the screenshots show

1. Run the script. Look at `contact-sheet.png`.
2. **Send it for review** before anything is pushed, and expect a round or two.
3. Check that `assets/full-description.txt` and `scripts/store-assets/captions.txt` are still true of the app. The
   banned-word rule applies to both.
4. After pushing, refresh the README's images: `gh workflow run readme-assets.yml` (on demand only before 1.0.0).
