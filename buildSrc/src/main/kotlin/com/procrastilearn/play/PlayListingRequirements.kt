package com.procrastilearn.play

object PlayListingRequirements {
  const val TITLE_MAX_CHARACTERS = 30
  const val SHORT_DESCRIPTION_MAX_CHARACTERS = 80
  const val FULL_DESCRIPTION_MAX_CHARACTERS = 4_000
  const val CHANGELOG_MAX_CHARACTERS = 500
  const val ICON_WIDTH = 512
  const val ICON_HEIGHT = 512
  const val ICON_MAX_BYTES = 1_048_576L
  const val FEATURE_GRAPHIC_WIDTH = 1_024
  const val FEATURE_GRAPHIC_HEIGHT = 500
  const val PHONE_SCREENSHOT_MIN_COUNT = 2
  const val PHONE_SCREENSHOT_MAX_COUNT = 8
  const val SCREENSHOT_MIN_DIMENSION = 320
  const val SCREENSHOT_MAX_DIMENSION = 3_840
  const val SCREENSHOT_MAX_ASPECT_RATIO = 2.0

  val textLimits =
    mapOf(
      "title.txt" to TITLE_MAX_CHARACTERS,
      "short_description.txt" to SHORT_DESCRIPTION_MAX_CHARACTERS,
      "full_description.txt" to FULL_DESCRIPTION_MAX_CHARACTERS,
    )

  val imageExtensions = setOf("png", "jpg", "jpeg")

  val officialSources =
    listOf(
      "https://support.google.com/googleplay/android-developer/answer/9859152",
      "https://support.google.com/googleplay/android-developer/answer/9866151",
      "https://support.google.com/googleplay/android-developer/answer/9859348",
    )
}
