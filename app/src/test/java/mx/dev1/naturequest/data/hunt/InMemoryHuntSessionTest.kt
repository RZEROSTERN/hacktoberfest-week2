package mx.dev1.naturequest.data.hunt

import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.PlaceType
import mx.dev1.naturequest.testing.FakeTimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryHuntSessionTest {
    private val settings = HuntSettings(PlaceType.PARK, HuntLength.SHORT, AgeRange.AGES_7_TO_9)
    private val clock = FakeTimeSource(now = 10_000_000L)

    @Test
    fun `there is no hunt until one starts`() {
        assertNull(InMemoryHuntSession(clock).state.value)
    }

    @Test
    fun `start numbers the items from zero and none are found`() {
        val session = InMemoryHuntSession(clock)

        session.start(settings, listOf("A red leaf", "A feather"))

        val hunt = session.state.value!!
        assertEquals(settings, hunt.settings)
        assertEquals(listOf(0, 1), hunt.items.map { it.id })
        assertEquals(listOf("A red leaf", "A feather"), hunt.items.map { it.text })
        assertTrue(hunt.items.none { it.found })
    }

    @Test
    fun `marking one item found leaves the others alone`() {
        val session = InMemoryHuntSession(clock)
        session.start(settings, listOf("A red leaf", "A feather", "Bark"))

        session.markFound(1, photoPath = "/cache/item-1.jpg")

        val items = session.state.value!!.items
        assertEquals(listOf(false, true, false), items.map { it.found })
        assertEquals("/cache/item-1.jpg", items[1].photoPath)
        assertNull(items[0].photoPath)
    }

    @Test
    fun `an item can be found without a saved photo`() {
        val session = InMemoryHuntSession(clock)
        session.start(settings, listOf("A red leaf"))

        session.markFound(0, photoPath = null)

        assertTrue(session.state.value!!.items.single().found)
    }

    @Test
    fun `marking an item when there is no hunt does nothing`() {
        val session = InMemoryHuntSession(clock)
        session.markFound(0, null)
        assertNull(session.state.value)
    }

    @Test
    fun `clear forgets the hunt`() {
        val session = InMemoryHuntSession(clock)
        session.start(settings, listOf("A red leaf"))

        session.clear()

        assertNull(session.state.value)
        assertFalse(session.state.value != null)
    }

    @Test
    fun `the clock starts with the hunt`() {
        val session = InMemoryHuntSession(clock)

        session.start(settings, listOf("A red leaf"))

        val hunt = session.state.value!!
        assertEquals(10_000_000L, hunt.startedAtMillis)
        assertFalse(hunt.finished)
        assertEquals(0, hunt.minutesOutside)
    }

    @Test
    fun `finish stops the clock and keeps the hunt for the summary`() {
        val session = InMemoryHuntSession(clock)
        session.start(settings, listOf("A red leaf", "A feather"))
        session.markFound(0, "/cache/item-0.jpg")

        clock.now += 23 * 60_000L + 30_000L // 23 min 30 s later
        session.finish()
        clock.now += 60 * 60_000L // time keeps passing after the hunt

        val hunt = session.state.value!!
        assertTrue(hunt.finished)
        assertEquals(23, hunt.minutesOutside)
        assertEquals(1, hunt.foundCount)
        assertEquals(2, hunt.items.size)
    }

    @Test
    fun `finishing twice does not move the end time`() {
        val session = InMemoryHuntSession(clock)
        session.start(settings, listOf("A red leaf"))
        clock.now += 5 * 60_000L
        session.finish()

        clock.now += 10 * 60_000L
        session.finish()

        assertEquals(5, session.state.value!!.minutesOutside)
    }

    @Test
    fun `finishing when there is no hunt does nothing`() {
        val session = InMemoryHuntSession(clock)
        session.finish()
        assertNull(session.state.value)
    }

    @Test
    fun `allFound is true only when every item is found`() {
        val session = InMemoryHuntSession(clock)
        session.start(settings, listOf("A red leaf", "A feather"))
        assertFalse(session.state.value!!.allFound)

        session.markFound(0, null)
        assertFalse(session.state.value!!.allFound)

        session.markFound(1, null)
        assertTrue(session.state.value!!.allFound)
    }
}
