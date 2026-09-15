package com.procrastilearn.play

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class PlayMetadataValidatorTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `accepts localized text and assets with variable paragraphs and screenshot counts`() {
    createValidLocale("en-US", title = "ProcrastiLearn", paragraphs = 1, screenshotCount = 2, color = Color.BLUE)
    createValidLocale("fr-FR", title = "Apprendre demain", paragraphs = 7, screenshotCount = 4, color = Color.RED)

    assertEquals(emptyList(), validator().validateListings())
  }

  @Test
  fun `reports invalid UTF-8 missing text invalid images and broken symlinks together`() {
    val locale = createValidLocale("en-US")
    locale.resolve("title.txt").writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
    Files.delete(locale.resolve("short_description.txt"))
    locale.resolve("images/icon.png").writeText("not an image")
    Files.delete(locale.resolve("images/phoneScreenshots/2.png"))
    Files.createSymbolicLink(locale.resolve("images/phoneScreenshots/broken.png"), Path.of("missing.png"))

    val errors = validator().validateListings()

    assertContains(errors, "en-US/title.txt is not valid UTF-8")
    assertContains(errors, "en-US/short_description.txt is missing")
    assertContains(errors, "en-US/images/icon.png is not a valid")
    assertContains(errors, "en-US/images/phoneScreenshots/broken.png is a broken symbolic link")
    assertContains(errors, "has 1 screenshots; expected 2..8")
  }

  @Test
  fun `enforces text boundaries by Unicode code point`() {
    val locale = createValidLocale("en-US")
    locale.resolve("title.txt").writeText("😀".repeat(PlayListingRequirements.TITLE_MAX_CHARACTERS))
    assertEquals(emptyList(), validator().validateListings())

    locale.resolve("title.txt").writeText("😀".repeat(PlayListingRequirements.TITLE_MAX_CHARACTERS + 1))
    assertContains(validator().validateListings(), "has 31 characters; maximum is 30")
  }

  @Test
  fun `rejects missing empty invalid and oversized current changelogs`() {
    createValidLocale("en-US")
    createValidLocale("fr-FR")
    createValidLocale("de-DE")
    createValidLocale("uk")
    val versionCode = 42
    changelog("en-US", versionCode).writeText("Ready")
    changelog("fr-FR", versionCode).writeText("")
    changelog("de-DE", versionCode).writeBytes(byteArrayOf(0x80.toByte()))
    changelog("uk", versionCode).writeText("x".repeat(PlayListingRequirements.CHANGELOG_MAX_CHARACTERS + 1))

    val errors = validator().validateChangelogs(versionCode)

    assertContains(errors, "fr-FR/changelogs/42.txt is empty")
    assertContains(errors, "de-DE/changelogs/42.txt is not valid UTF-8")
    assertContains(errors, "uk/changelogs/42.txt has 501 characters; maximum is 500")
  }

  @Test
  fun `reports a missing current changelog`() {
    createValidLocale("en-US")

    assertContains(validator().validateChangelogs(99), "en-US/changelogs/99.txt is missing")
  }

  private fun createValidLocale(
    name: String,
    title: String = "Title $name",
    paragraphs: Int = 2,
    screenshotCount: Int = 2,
    color: Color = Color.GREEN,
  ): Path {
    val locale = temporaryDirectory.resolve(name)
    locale.resolve("images/phoneScreenshots").createDirectories()
    locale.resolve("changelogs").createDirectories()
    locale.resolve("title.txt").writeText(title)
    locale.resolve("short_description.txt").writeText("Short description")
    locale.resolve("full_description.txt").writeText((1..paragraphs).joinToString("\n\n") { "Paragraph $it" })
    writeImage(locale.resolve("images/icon.png"), 512, 512, alpha = true, color)
    writeImage(locale.resolve("images/featureGraphic.png"), 1024, 500, alpha = false, color)
    (1..screenshotCount).forEach {
      writeImage(locale.resolve("images/phoneScreenshots/$it.png"), 320, 640, alpha = false, color)
    }
    return locale
  }

  private fun changelog(
    locale: String,
    versionCode: Int,
  ): Path = temporaryDirectory.resolve("$locale/changelogs/$versionCode.txt")

  private fun validator() = PlayMetadataValidator(temporaryDirectory)

  private fun writeImage(
    path: Path,
    width: Int,
    height: Int,
    alpha: Boolean,
    color: Color,
  ) {
    val image = BufferedImage(width, height, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = color
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()
    ImageIO.write(image, "png", path.toFile())
  }

  private fun assertContains(
    errors: List<String>,
    expected: String,
  ) {
    assertTrue(errors.any { expected in it }, "Expected '$expected' in:\n${errors.joinToString("\n")}")
  }
}
