package com.procrastilearn.app.data.parser.json

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.VocabularyExportItem
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    manifest = Config.NONE,
)
class JsonVocabularyParserTest {
    private val parser = JsonVocabularyParser()

    @Test
    fun `parseExport reads full export payload`() {
        val tempFile = File.createTempFile("vocab", ".json")
        tempFile.writeText(
            """
            [
              {
                "id": 1,
                "word": "Haus",
                "translation": "House",
                "createdAt": 10,
                "lastShownAt": null,
                "correctCount": 2,
                "incorrectCount": 1,
                "fsrsCardJson": "{\"c\":1}",
                "fsrsDueAt": 20
              }
            ]
            """.trimIndent(),
        )

        val result = parser.parseExport(tempFile)

        assertThat(result).containsExactly(
            VocabularyExportItem(
                id = 1,
                word = "Haus",
                translation = "House",
                createdAt = 10,
                lastShownAt = null,
                correctCount = 2,
                incorrectCount = 1,
                fsrsCardJson = "{\"c\":1}",
                fsrsDueAt = 20,
            ),
        )
    }

    @Test
    fun `parseExport reads bidirectional and backward fields when present in the payload`() {
        val tempFile = File.createTempFile("vocab", ".json")
        tempFile.writeText(
            """
            [
              {
                "id": 1,
                "word": "run",
                "translation": "бігати",
                "createdAt": 10,
                "lastShownAt": null,
                "correctCount": 2,
                "incorrectCount": 1,
                "fsrsCardJson": "{\"c\":1}",
                "fsrsDueAt": 20,
                "bidirectional": true,
                "backwardFsrsCardJson": "{\"c\":2}",
                "backwardFsrsDueAt": 30,
                "backwardCorrectCount": 4,
                "backwardIncorrectCount": 5,
                "backwardPromptOverride": "prompt",
                "backwardAnswerOverride": "answer"
              }
            ]
            """.trimIndent(),
        )

        val result = parser.parseExport(tempFile).single()

        assertThat(result.bidirectional).isTrue()
        assertThat(result.backwardFsrsCardJson).isEqualTo("{\"c\":2}")
        assertThat(result.backwardFsrsDueAt).isEqualTo(30)
        assertThat(result.backwardCorrectCount).isEqualTo(4)
        assertThat(result.backwardIncorrectCount).isEqualTo(5)
        assertThat(result.backwardPromptOverride).isEqualTo("prompt")
        assertThat(result.backwardAnswerOverride).isEqualTo("answer")
    }

    @Test
    fun `parseExport defaults bidirectional and backward fields when absent from the payload`() {
        val tempFile = File.createTempFile("vocab", ".json")
        tempFile.writeText(
            """
            [
              {
                "id": 1,
                "word": "Haus",
                "translation": "House",
                "createdAt": 10,
                "lastShownAt": null,
                "correctCount": 2,
                "incorrectCount": 1,
                "fsrsCardJson": "{\"c\":1}",
                "fsrsDueAt": 20
              }
            ]
            """.trimIndent(),
        )

        val result = parser.parseExport(tempFile).single()

        assertThat(result.bidirectional).isFalse()
        assertThat(result.backwardFsrsCardJson).isEmpty()
        assertThat(result.backwardFsrsDueAt).isEqualTo(0L)
        assertThat(result.backwardCorrectCount).isEqualTo(0)
        assertThat(result.backwardIncorrectCount).isEqualTo(0)
        assertThat(result.backwardPromptOverride).isNull()
        assertThat(result.backwardAnswerOverride).isNull()
    }

    @Test
    fun `parseExport reads the real device v1 export unchanged`() {
        val file = loadResource("exports/v1/real-device-export.json")

        val result = parser.parseExport(file)

        assertThat(result).containsExactly(*realDeviceExportItems().toTypedArray()).inOrder()
    }

    private fun loadResource(path: String): File {
        val url =
            checkNotNull(javaClass.classLoader?.getResource(path)) {
                "Resource at $path was not found in the test resources."
            }
        return File(url.toURI())
    }

    @Suppress("LongMethod")
    private fun realDeviceExportItems(): List<VocabularyExportItem> =
        listOf(
            VocabularyExportItem(
                id = 1,
                word = "test",
                translation = """test123""",
                createdAt = 1785010756357,
                lastShownAt = 1785011459260,
                correctCount = 1,
                incorrectCount = 0,
                fsrsCardJson = "{\"cardId\":-1695638824,\"difficulty\":6.243132948566265,\"due\":\"2026-07-25T20:36:29.260Z\",\"lastReview\":\"2026-07-25T20:30:59.260Z\",\"stability\":1.1771,\"state\":\"LEARNING\",\"step\":0}",
                fsrsDueAt = 1785011789260,
            ),
            VocabularyExportItem(
                id = 2,
                word = "foo",
                translation = """bar""",
                createdAt = 1785010760751,
                lastShownAt = 1785011460386,
                correctCount = 1,
                incorrectCount = 0,
                fsrsCardJson = "{\"cardId\":-1695634391,\"difficulty\":4.884631634813845,\"due\":\"2026-07-25T20:41:00.386080Z\",\"lastReview\":\"2026-07-25T20:31:00.386080Z\",\"stability\":3.2602,\"state\":\"LEARNING\",\"step\":1}",
                fsrsDueAt = 1785012060386,
            ),
            VocabularyExportItem(
                id = 3,
                word = "cat",
                translation = """Транскрипция: /kæt/

Нерегулярные формы: cats (мн.ч.)

Русский перевод: кот, кошка; котёнок — малыш; хитрый или коварный человек (перен.)

Объяснение:
  Cat — небольшое домашнее млекопитающее из семейства кошачьих, обычно с мягкой шерстью, острыми когтями и чувствительным поведением; употребляется для обозначения самца или самки в зависимости от контекста (обычно «cat» нейтрально), а для детёныша используется «kitten». Слово также применяется в переносном значении для описания человека с характерными чертами (например, независимого, хитрого или ловкого). В английском есть множество пород и терминов, связанных с кошками (tom, queen, kitten). От существительного образуются прилагательное «catlike» и глагол «to cat» в редких разговорных или шутливых контекстах; более распространённые производные — «catnip», «catwalk», «catnap». В разговорной и культурной лексике слово часто входит в идиомы и фразеологизмы.

Заметки по употреблению:
  - Нейтральный, бытовой стиль; слово уместно в разговорной и письменной речи.  
  - Для обозначения детёныша чаще используется «kitten», для самца — «tom» в контексте разведения.  
  - В переносном значении «cat» может быть как ласковым, так и пренебрежительным; при выборе синонимов учитывайте оттенок (например, «kitty» более уменьшительно-ласкательно, «tomcat» — подчёркивает пол и часто распущенное поведение).

Примеры:
1. The cat slept on the windowsill.
2. She adopted a stray cat from the shelter.
3. The kitten chased a ball of yarn.
4. A black cat crossed his path last night.
5. He is as curious as a cat.
6. The tom fought with another cat over territory.
7. She took a catnap after lunch.
8. The catlicked the milk from the saucer.
9. They built a cat tower for their pets.
10. The catwalk showed models wearing avant-garde fashion.""",
                createdAt = 1785010810566,
                lastShownAt = 1785011469180,
                correctCount = 0,
                incorrectCount = 1,
                fsrsCardJson = "{\"cardId\":-1695584574,\"difficulty\":7.0114,\"due\":\"2026-07-25T20:32:09.180957Z\",\"lastReview\":\"2026-07-25T20:31:09.180957Z\",\"stability\":0.2172,\"state\":\"LEARNING\",\"step\":0}",
                fsrsDueAt = 1785011529180,
            ),
            VocabularyExportItem(
                id = 4,
                word = "кошка",
                translation = """Английский перевод: cat

Транскрипция: /kæt/

Объяснение:
  Русское слово «кошка» означает домашнее или дикие́е животное из семейства кошачьих, обычно женского пола, но в обычной речи употребляется нейтрально для любого пола. В английском единственный нейтральный и общеупотребительный эквивалент — «cat». Это слово подходит для домашних питомцев, уличных кошек и разговора о виде в целом. Формальный или научный контекст может использовать «feline» как прилагательное или существительное, но для названия животного в повседневной речи всегда используется «cat». Слово нейтрально по стилю и уместно в любых ситуациях — от переписки до разговорной речи.

Русский контекст: кошка (обычное слово для домашнего питомца; может подразумевать самку, но часто используется в общем смысле)

Примеры:
1. The cat is sleeping on the sofa.
2. I adopted a stray cat from the shelter.
3. My cat loves to chase laser pointers.
4. That black cat crossed the street yesterday.
5. The cat climbed up the tree.
6. She fed the cat before leaving for work.
7. Our neighbor's cat visits every morning.
8. The cat purred when I stroked its fur.
9. Cats are known for their agility.
10. He bought a new toy for his cat.""",
                createdAt = 1785010837396,
                lastShownAt = null,
                correctCount = 0,
                incorrectCount = 0,
                fsrsCardJson = "{\"cardId\":-1695557743,\"difficulty\":null,\"due\":\"2026-07-25T20:20:37.394308Z\",\"lastReview\":null,\"stability\":null,\"state\":\"LEARNING\",\"step\":0}",
                fsrsDueAt = 0,
            ),
            VocabularyExportItem(
                id = 5,
                word = "собака",
                translation = """Китайский перевод: 狗

Транскрипция: /gǒu/

Объяснение:
  Слово 狗 — основной и универсальный перевод русского слова «собака». Подходит для обозначения любого домашнего или бездомного пса, как в разговорной, так и в письменной речи; нейтральное по стилю. Для щенка обычно добав词  小狗, для служебных или породистых собак возможны уточнения (比如警犬, 导盲犬)。Сленговые или пренебрежительные значения (как «сволочь») выражаются в китайском другими словами, а не напрямую через 狗。При описании породы обычно ставят перед 狗: 比熊狗 обычно говорят как 比熊犬或比熊犬 (см. словосочетания).

Русский контекст: собака — нейтральное слово для животного; включает домашних собак, бродячих собак и т. п.

Примеры:
1. 我家有一只狗。 (/Wǒ jiā yǒu yì zhī gǒu/)
2. 那条狗很友好。 (/Nà tiáo gǒu hěn yǒuhǎo/)
3. 小狗在院子里玩。 (/Xiǎo gǒu zài yuànzi lǐ wán/)
4. 这是一只导盲犬。 (/Zhè shì yì zhī dǎománg quǎn/)
5. 街上有很多流浪狗。 (/Jiē shàng yǒu hěn duō liúlàng gǒu/)
6. 他养了两条猎犬。 (/Tā yǎng le liǎng tiáo liè quǎn/)
7. 狗在门口睡觉。 (/Gǒu zài ménkǒu shuìjiào/)
8. 听见狗叫就知道有人来了。 (/Tīngjiàn gǒu jiào jiù zhīdào yǒu rén lái le/)
9. 请不要打扰那只正在哺乳的小狗。 (/Qǐng búyào dǎrǎo nà zhī zhèngzài bǔrǔ de xiǎo gǒu/)
10. 他把狗牵在绳子上。 (/Tā bǎ gǒu qiān zài shéngzi shàng/)""",
                createdAt = 1785010859128,
                lastShownAt = 1785011461880,
                correctCount = 1,
                incorrectCount = 0,
                fsrsCardJson = "{\"cardId\":-1695536013,\"difficulty\":2.482438522375997,\"due\":\"2026-08-06T20:31:01.880409Z\",\"lastReview\":\"2026-07-25T20:31:01.880409Z\",\"stability\":16.1507,\"state\":\"REVIEW\",\"step\":null}",
                fsrsDueAt = 1786048261880,
            ),
        )
}
