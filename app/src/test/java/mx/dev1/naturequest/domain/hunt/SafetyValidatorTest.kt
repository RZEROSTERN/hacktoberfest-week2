package mx.dev1.naturequest.domain.hunt

import mx.dev1.naturequest.domain.hunt.SafetyValidator.Reason
import mx.dev1.naturequest.domain.hunt.SafetyValidator.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyValidatorTest {
    private val validator = SafetyValidator()

    private fun assertRejected(text: String, reason: Reason = Reason.UNSAFE) {
        val verdict = validator.validate(text)
        assertTrue("Expected \"$text\" to be rejected but got $verdict", verdict is Verdict.Rejected)
        assertEquals("Wrong reason for \"$text\"", reason, (verdict as Verdict.Rejected).reason)
    }

    @Test
    fun `accepts the example items from the product brief`() {
        listOf(
            "a red leaf", "something with a spiral shape", "a feather",
            "bark with moss on it", "a flower with five petals",
        ).forEach { assertTrue("\"$it\" should be valid", validator.isSafe(it)) }
    }

    @Test
    fun `accepts safe Spanish items with accents`() {
        listOf(
            "una hoja roja", "algo con forma de espiral", "una pluma", "corteza con musgo",
            "una flor con cinco pétalos", "una gota de rocío brillante", "un árbol muy alto",
        ).forEach { assertTrue("\"$it\" should be valid", validator.isSafe(it)) }
    }

    @Test
    fun `rejects the unsafe items the model actually produced during the spike`() {
        assertRejected("A shape resembling a coiled snake")
        assertRejected("A patch of vibrant orange fungus")
        assertRejected("A bird's nest hidden in a hollow")
    }

    @Test
    fun `rejects asking to pick, touch, take or eat`() {
        assertRejected("Pick a yellow flower")
        assertRejected("Collect three leaves")
        assertRejected("Something soft to touch")
        assertRejected("Take a smooth stone")
        assertRejected("A leaf that feels rough")
        assertRejected("Taste a wild berry")
        assertRejected("Recoge una hoja seca")
        assertRejected("Toca la corteza")
        assertRejected("Prueba una fruta")
    }

    @Test
    fun `rejects animals, insects, mushrooms and fruit`() {
        assertRejected("A butterfly landing on a spider")
        assertRejected("A bee on a flower")
        assertRejected("A line of ants")
        assertRejected("A brown mushroom")
        assertRejected("Wild berries")
        assertRejected("Una hormiga en una hoja")
        assertRejected("Un hongo naranja")
        assertRejected("Un perro jugando")
        // Found on the phone: a cloud "shaped like a deer" got through while deer was missing from the list.
        assertRejected("Una nube con forma de ciervo")
        assertRejected("A cloud shaped like a deer")
        assertRejected("Un zorro entre los arbustos")
    }

    @Test
    fun `rejects water, roads, vehicles, heights and leaving the path`() {
        assertRejected("A fish in the lake")
        assertRejected("A ripple on the river")
        assertRejected("A red car")
        assertRejected("A sign by the road")
        assertRejected("A view from the cliff")
        assertRejected("Something next to the water")
        assertRejected("Something off the path")
        assertRejected("Climb a tree")
        assertRejected("Algo en el río")
        assertRejected("Un coche rojo")
        assertRejected("Algo fuera del camino")
    }

    @Test
    fun `rejects hiding places, people and hazards`() {
        assertRejected("Something under a rock")
        assertRejected("A bug inside a log")
        assertRejected("A child wearing red")
        assertRejected("Una persona con sombrero")
        assertRejected("A sharp piece of glass")
        assertRejected("Litter on the ground")
    }

    @Test
    fun `matches whole words only`() {
        // "plant" contains "ant", "carpet" contains "car", "petal" contains "pet": none should trip.
        assertTrue(validator.isSafe("A plant with wide leaves"))
        assertTrue(validator.isSafe("A carpet of fallen leaves"))
        assertTrue(validator.isSafe("A flower with a pink petal"))
        assertTrue(validator.isSafe("Something man-made and blue"))
    }

    @Test
    fun `ignores case and accents when matching`() {
        assertRejected("A SNAKE")
        assertRejected("Una ARAÑA")
        assertRejected("Un murciélago")
    }

    @Test
    fun `cleans the text it accepts`() {
        val verdict = validator.validate("  \"a red leaf.\"  ")
        assertEquals(Verdict.Valid("A red leaf"), verdict)
        assertEquals(Verdict.Valid("Something round"), validator.validate("something   round!"))
    }

    @Test
    fun `rejects empty, long and strange items`() {
        assertRejected("   ", Reason.EMPTY)
        assertRejected("\"\"", Reason.EMPTY)
        assertRejected("a ".repeat(40) + "leaf", Reason.TOO_LONG)
        assertRejected("one two three four five six seven eight nine ten eleven", Reason.TOO_LONG)
        assertRejected("a leaf <b>bold</b>", Reason.INVALID_CHARACTERS)
        assertRejected("a leaf http://evil.example", Reason.INVALID_CHARACTERS)
        assertRejected("3 red leaves", Reason.INVALID_CHARACTERS)
    }

    @Test
    fun `rejects repeats ignoring case, accents and punctuation`() {
        assertEquals(
            Verdict.Rejected(Reason.DUPLICATE),
            validator.validate("A red leaf!", existing = listOf("a red LEAF")),
        )
        assertEquals(
            Verdict.Rejected(Reason.DUPLICATE),
            validator.validate("Una flor con cinco pétalos", existing = listOf("una flor con cinco petalos")),
        )
        assertTrue(validator.validate("A yellow leaf", existing = listOf("A red leaf")) is Verdict.Valid)
    }

    @Test
    fun `reports which word made an item unsafe`() {
        val verdict = validator.validate("A coiled snake") as Verdict.Rejected
        assertEquals("snake", verdict.detail)
    }

    @Test
    fun `sentences may describe animals and say to take another photo`() {
        assertEquals(false, validator.containsUnsafeInstruction("I see a cute bee, not a leaf!"))
        assertEquals(false, validator.containsUnsafeInstruction("Take another photo of a red leaf."))
        assertEquals(false, validator.containsUnsafeInstruction("Prueba con otra foto de una hoja roja."))
        assertEquals(false, validator.containsUnsafeInstruction("Look up at the trees, you will feel proud!"))
        assertEquals(false, validator.containsUnsafeInstruction("¡Qué buen hallazgo!"))
    }

    @Test
    fun `sentences that tell a child to pick, touch, climb, eat or go near water or roads are unsafe`() {
        listOf(
            "Pick it up and take it home.", "Touch the bark gently.", "Climb the tree to see more.",
            "You can eat that berry.", "Walk to the river and look.", "Cross the street to find one.",
            "Grab a leaf from the branch.", "Smell the flower.", "Recoge una hoja.", "Toca la corteza.",
            "Trepa al árbol.", "Ve al lago.", "Come una baya.",
        ).forEach { assertTrue("\"$it\" should be unsafe", validator.containsUnsafeInstruction(it)) }
    }
}
