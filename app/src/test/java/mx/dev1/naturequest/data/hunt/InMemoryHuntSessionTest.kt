package mx.dev1.naturequest.data.hunt

import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryHuntSessionTest {
    private val settings = HuntSettings(PlaceType.PARK, HuntLength.SHORT, AgeRange.AGES_7_TO_9)

    @Test
    fun `there is no hunt until one starts`() {
        assertNull(InMemoryHuntSession().state.value)
    }

    @Test
    fun `start numbers the items from zero and none are found`() {
        val session = InMemoryHuntSession()

        session.start(settings, listOf("A red leaf", "A feather"))

        val hunt = session.state.value!!
        assertEquals(settings, hunt.settings)
        assertEquals(listOf(0, 1), hunt.items.map { it.id })
        assertEquals(listOf("A red leaf", "A feather"), hunt.items.map { it.text })
        assertTrue(hunt.items.none { it.found })
    }

    @Test
    fun `marking one item found leaves the others alone`() {
        val session = InMemoryHuntSession()
        session.start(settings, listOf("A red leaf", "A feather", "Bark"))

        session.markFound(1, photoPath = "/cache/item-1.jpg")

        val items = session.state.value!!.items
        assertEquals(listOf(false, true, false), items.map { it.found })
        assertEquals("/cache/item-1.jpg", items[1].photoPath)
        assertNull(items[0].photoPath)
    }

    @Test
    fun `an item can be found without a saved photo`() {
        val session = InMemoryHuntSession()
        session.start(settings, listOf("A red leaf"))

        session.markFound(0, photoPath = null)

        assertTrue(session.state.value!!.items.single().found)
    }

    @Test
    fun `marking an item when there is no hunt does nothing`() {
        val session = InMemoryHuntSession()
        session.markFound(0, null)
        assertNull(session.state.value)
    }

    @Test
    fun `end forgets the hunt`() {
        val session = InMemoryHuntSession()
        session.start(settings, listOf("A red leaf"))

        session.end()

        assertNull(session.state.value)
        assertFalse(session.state.value != null)
    }
}
