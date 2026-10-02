# Project-specific R8 keep rules for the release build (see app/build.gradle.kts).
# Libraries ship their own rules, so this file only needs rules for the app's code
# that R8 can't see is used, such as code reached only through reflection. Keep each
# rule as narrow as possible and say what needs it. See ARCHITECTURE.md (Release build).
