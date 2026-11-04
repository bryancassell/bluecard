package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.text.IosLocaleFormats
import platform.Foundation.NSLocale

/** Formats in US English, for the report tests' typesetters. */
internal val usEnglish = IosLocaleFormats(NSLocale("en_US"))
