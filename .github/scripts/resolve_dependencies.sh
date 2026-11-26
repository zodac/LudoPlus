#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:  resolve_dependencies.sh
#
# Description:  Downloads every dependency android-release.yml's jobs need into Gradle's cache, building nothing, for
#               the jobs that save that cache (.github/actions/build-setup, "Name the Gradle cache"): android-release.yml's
#               dependencies job, and update-dependencies.yml's update job after a bump.
#
#               - `resolveDependencies` (build.gradle.kts) resolves each project's configurations.
#               - The tasks after it are the jobs' own, as a dry run: nothing runs, but storing the configuration
#                 cache resolves what AGP and KSP fetch through configurations of their own (aapt2, KSP's analysis
#                 API), which resolveDependencies can't see.
#
#               A task a job adds that isn't listed here still works: that job downloads what it needs itself.
#
# Usage:        .github/scripts/resolve_dependencies.sh   (from the repository root)
# ------------------------------------------------------------------------------
set -euo pipefail

./gradlew --dry-run resolveDependencies \
    :app:android:testPlayDebugUnitTest :app:android:testGithubDebugUnitTest :app:shared:testDebugUnitTest \
    :app:baselineprofile:compileNonMinifiedReleaseKotlin lintDebug lintRelease \
    :app:android:assemblePlayNonMinifiedRelease :app:baselineprofile:assembleNonMinifiedRelease \
    :app:android:generatePlayReleaseBaselineProfile \
    :app:android:assembleGithubRelease :app:android:bundlePlayRelease \
    --console=plain
