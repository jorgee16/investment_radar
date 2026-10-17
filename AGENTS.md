# Investment Radar — Codex Development Rules

## Goal
Build a maintainable cross-platform investment research application for desktop and Android. The app should combine live market/fundamental data from multiple providers, normalize it, score/valuate assets, preserve data provenance, and present the result in a clear responsive UI.

## Architecture constraints
- Keep investment scoring, valuation logic, normalized domain models, and provider-routing policy in C++ where practical.
- Android uses Kotlin for platform integration (Activity/WebView, networking/storage where appropriate, notifications, secure storage) and JNI/NDK to reach the shared C++ core.
- Desktop remains C++ with a native WebView host.
- Do not introduce Node.js, Electron, or a JavaScript server runtime.
- The shared web UI under `shared/web/` is used by both desktop and Android unless a platform-specific divergence is justified.
- Do not duplicate scoring or valuation formulas in Kotlin or JavaScript.

## Data-provider rules
- Provider-specific JSON/models must not leak into `InvestmentEngine`.
- Normalize provider responses into internal C++ domain models.
- Preserve provider/source provenance for every financial metric when possible.
- Missing data must remain missing/unknown; never fabricate a financial value to make a screen look complete.
- Demo data must only be used explicitly in demo/test/fallback paths and must be clearly identifiable.
- Maintain adapters for EODHD, Financial Modeling Prep (FMP), and Twelve Data behind a common interface.
- Provider routing/fallback belongs in `ProviderManager`, not in UI code.

## Secrets and security
- Never commit API keys, tokens, passwords, or populated config files.
- Never hardcode provider credentials in C++, Kotlin, JavaScript, Gradle, or CMake.
- Keep `.env`, local config, Android `local.properties`, SQLite caches, and generated keystore material out of Git.
- If changing Android credential storage, prefer Android Keystore-backed storage over plain SharedPreferences.

## Build and dependency policy
- C++ standard: C++20 unless there is a concrete reason to lower it.
- Prefer small, well-maintained dependencies and keep dependency additions explicit in CMake/Gradle.
- Avoid adding frameworks when the existing native/WebView architecture can solve the problem cleanly.

## Quality gates
Before completing a meaningful change:
1. Build the relevant C++ targets.
2. Run existing C++ tests and add tests for changed core behavior.
3. Validate JavaScript syntax for changes under `shared/web/`.
4. For Android changes, run the Gradle build when Android SDK/NDK are available.
5. Do not claim a build, APK, provider call, or integration was validated unless it actually was.

## Git workflow
- Work in focused branches such as `codex/provider-live-data`, `codex/valuation-engine`, or `codex/android-build`.
- Keep commits small and descriptive.
- Prefer pull requests for substantial work.
- Do not rewrite working architecture without documenting why.

## First task for a new agent
Before making broad changes, inspect the repository and produce a short status audit covering:
- what actually builds;
- which provider endpoints are live vs demo/fallback;
- current test coverage;
- desktop state;
- Android state;
- known security/credential-storage limitations;
- highest-value next steps.
