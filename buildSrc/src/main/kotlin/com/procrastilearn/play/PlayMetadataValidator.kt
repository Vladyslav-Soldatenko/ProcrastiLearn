package com.procrastilearn.play

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class PlayMetadataValidator(
  private val metadataRoot: Path,
) {
  fun validateListings(): List<String> {
    val errors = mutableListOf<String>()
    if (!Files.isDirectory(metadataRoot)) {
      return listOf("metadata directory is missing: ${metadataRoot.toAbsolutePath()}")
    }

    validateSymlinks(errors)
    val locales = discoverLocales()
    if (locales.isEmpty()) {
      errors += "no locale directories were found"
      return errors
    }

    locales.forEach { locale ->
      validateText(locale, errors)
      validateImages(locale, errors)
    }
    return errors.distinct()
  }

  fun validateChangelogs(versionCode: Int): List<String> {
    val errors = mutableListOf<String>()
    if (!Files.isDirectory(metadataRoot)) {
      return listOf("metadata directory is missing: ${metadataRoot.toAbsolutePath()}")
    }

    validateSymlinks(errors)
    val locales = discoverLocales()
    if (locales.isEmpty()) {
      errors += "no locale directories were found"
      return errors
    }

    locales.forEach { locale ->
      val relative = "$locale/changelogs/$versionCode.txt"
      val path = metadataRoot.resolve(relative)
      validateRequiredText(path, relative, PlayListingRequirements.CHANGELOG_MAX_CHARACTERS, errors)
    }
    return errors.distinct()
  }

  fun discoverLocales(): List<String> =
    Files.list(metadataRoot).use { paths ->
      paths
        .filter { Files.isDirectory(it) }
        .map { it.fileName.toString() }
        .sorted()
        .toList()
    }

  private fun validateText(
    locale: String,
    errors: MutableList<String>,
  ) {
    PlayListingRequirements.textLimits.forEach { (filename, limit) ->
      val relative = "$locale/$filename"
      validateRequiredText(metadataRoot.resolve(relative), relative, limit, errors)
    }
  }

  private fun validateRequiredText(
    path: Path,
    relative: String,
    limit: Int,
    errors: MutableList<String>,
  ) {
    if (Files.isSymbolicLink(path) && !Files.exists(path)) {
      return
    }
    if (!Files.isRegularFile(path)) {
      errors += "$relative is missing"
      return
    }

    val text = readUtf8(path, relative, errors) ?: return
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
      errors += "$relative is empty"
    }
    val characters = trimmed.codePointCount(0, trimmed.length)
    if (characters > limit) {
      errors += "$relative has $characters characters; maximum is $limit"
    }
  }

  private fun readUtf8(
    path: Path,
    relative: String,
    errors: MutableList<String>,
  ): String? =
    try {
      StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(Files.readAllBytes(path)))
        .toString()
    } catch (_: Exception) {
      errors += "$relative is not valid UTF-8 or could not be read"
      null
    }

  private fun validateImages(
    locale: String,
    errors: MutableList<String>,
  ) {
    validateIcon(locale, errors)
    validateFeatureGraphic(locale, errors)
    validateScreenshots(locale, errors)
  }

  private fun validateIcon(
    locale: String,
    errors: MutableList<String>,
  ) {
    val relative = "$locale/images/icon.png"
    val path = metadataRoot.resolve(relative)
    val image = readRequiredImage(path, relative, setOf("png"), errors) ?: return
    if (image.width != PlayListingRequirements.ICON_WIDTH || image.height != PlayListingRequirements.ICON_HEIGHT) {
      errors += "$relative is ${image.width}x${image.height}; expected 512x512"
    }
    if (!image.colorModel.hasAlpha()) {
      errors += "$relative must have an alpha channel"
    }
    if (image.colorModel.numColorComponents != 3) {
      errors += "$relative must use RGB color"
    }
    val size = Files.size(path)
    if (size > PlayListingRequirements.ICON_MAX_BYTES) {
      errors += "$relative is $size bytes; maximum is ${PlayListingRequirements.ICON_MAX_BYTES}"
    }
  }

  private fun validateFeatureGraphic(
    locale: String,
    errors: MutableList<String>,
  ) {
    val candidate = findSingleImage(locale, "images", "featureGraphic", errors) ?: return
    val (relative, path) = candidate
    val image = readRequiredImage(path, relative, PlayListingRequirements.imageExtensions, errors) ?: return
    if (image.width != PlayListingRequirements.FEATURE_GRAPHIC_WIDTH ||
      image.height != PlayListingRequirements.FEATURE_GRAPHIC_HEIGHT
    ) {
      errors += "$relative is ${image.width}x${image.height}; expected 1024x500"
    }
    validateRgbWithoutAlpha(image, relative, errors)
  }

  private fun validateScreenshots(
    locale: String,
    errors: MutableList<String>,
  ) {
    val relativeDirectory = "$locale/images/phoneScreenshots"
    val directory = metadataRoot.resolve(relativeDirectory)
    if (!Files.isDirectory(directory)) {
      errors += "$relativeDirectory is missing"
      return
    }

    val entries = Files.list(directory).use { paths -> paths.sorted().toList() }
    val screenshots =
      entries.filter { path ->
        Files.isRegularFile(path) && path.extension().lowercase() in PlayListingRequirements.imageExtensions
      }
    val unsupported = entries.filterNot { it in screenshots }
    unsupported.forEach { errors += "$relativeDirectory/${it.fileName} is not a supported screenshot file" }

    if (screenshots.size !in PlayListingRequirements.PHONE_SCREENSHOT_MIN_COUNT..PlayListingRequirements.PHONE_SCREENSHOT_MAX_COUNT) {
      errors +=
        "$relativeDirectory has ${screenshots.size} screenshots; expected " +
          "${PlayListingRequirements.PHONE_SCREENSHOT_MIN_COUNT}..${PlayListingRequirements.PHONE_SCREENSHOT_MAX_COUNT}"
    }

    screenshots.forEach { path ->
      val relative = "$relativeDirectory/${path.fileName}"
      val image = readRequiredImage(path, relative, PlayListingRequirements.imageExtensions, errors) ?: return@forEach
      val smallest = minOf(image.width, image.height)
      val largest = maxOf(image.width, image.height)
      if (smallest < PlayListingRequirements.SCREENSHOT_MIN_DIMENSION ||
        largest > PlayListingRequirements.SCREENSHOT_MAX_DIMENSION
      ) {
        errors += "$relative is ${image.width}x${image.height}; each dimension must be 320..3840"
      }
      if (smallest > 0 && largest.toDouble() / smallest > PlayListingRequirements.SCREENSHOT_MAX_ASPECT_RATIO) {
        errors += "$relative has an aspect ratio greater than 2:1"
      }
      validateRgbWithoutAlpha(image, relative, errors)
    }
  }

  private fun findSingleImage(
    locale: String,
    directory: String,
    basename: String,
    errors: MutableList<String>,
  ): Pair<String, Path>? {
    val relativeDirectory = "$locale/$directory"
    val parent = metadataRoot.resolve(relativeDirectory)
    if (!Files.isDirectory(parent)) {
      errors += "$relativeDirectory is missing"
      return null
    }
    val candidates =
      Files.list(parent).use { paths ->
        paths
          .filter {
            Files.isRegularFile(it) &&
              it.fileName.toString().substringBeforeLast(".") == basename &&
              it.extension().lowercase() in PlayListingRequirements.imageExtensions
          }.sorted()
          .toList()
      }
    if (candidates.size != 1) {
      errors += "$relativeDirectory/$basename must exist once as PNG or JPEG"
      return null
    }
    val path = candidates.single()
    return "$relativeDirectory/${path.fileName}" to path
  }

  private fun readRequiredImage(
    path: Path,
    relative: String,
    allowedExtensions: Set<String>,
    errors: MutableList<String>,
  ): BufferedImage? {
    if (Files.isSymbolicLink(path) && !Files.exists(path)) {
      return null
    }
    if (!Files.isRegularFile(path)) {
      errors += "$relative is missing"
      return null
    }
    val extension = path.extension().lowercase()
    if (extension !in allowedExtensions) {
      errors += "$relative has unsupported format .$extension"
      return null
    }
    return try {
      ImageIO.createImageInputStream(path.toFile()).use { input ->
        if (input == null) {
          errors += "$relative is not a readable image"
          return null
        }
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) {
          errors += "$relative is not a valid PNG or JPEG image"
          return null
        }
        val reader = readers.next()
        try {
          reader.input = input
          val detected = reader.formatName.lowercase().replace("jpg", "jpeg")
          val allowedFormats = allowedExtensions.map { it.replace("jpg", "jpeg") }.toSet()
          if (detected !in allowedFormats) {
            errors += "$relative is $detected; expected PNG or JPEG"
            return null
          }
          reader.read(0)
        } finally {
          reader.dispose()
        }
      }
    } catch (_: Exception) {
      errors += "$relative is not a valid image"
      null
    }
  }

  private fun validateRgbWithoutAlpha(
    image: BufferedImage,
    relative: String,
    errors: MutableList<String>,
  ) {
    if (image.colorModel.hasAlpha()) {
      errors += "$relative must not have an alpha channel"
    }
    if (image.colorModel.numColorComponents != 3) {
      errors += "$relative must use RGB color"
    }
  }

  private fun validateSymlinks(errors: MutableList<String>) {
    Files.walk(metadataRoot).use { paths ->
      paths
        .filter { Files.isSymbolicLink(it) && !Files.exists(it) }
        .sorted()
        .forEach { errors += "${metadataRoot.relativize(it)} is a broken symbolic link" }
    }
  }

  private fun Path.extension(): String = fileName.toString().substringAfterLast('.', "")
}
