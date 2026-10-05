package mx.dev1.naturequest.domain.hunt

import org.junit.Assert.assertEquals
import org.junit.Test

class MedalTest {
    @Test
    fun `finding everything is gold`() {
        assertEquals(Medal.GOLD, Medal.forResult(found = 5, total = 5))
        assertEquals(Medal.GOLD, Medal.forResult(found = 12, total = 12))
    }

    @Test
    fun `finding at least half is silver`() {
        assertEquals(Medal.SILVER, Medal.forResult(found = 4, total = 8))
        assertEquals(Medal.SILVER, Medal.forResult(found = 7, total = 8))
        assertEquals(Medal.SILVER, Medal.forResult(found = 3, total = 5))
    }

    @Test
    fun `finding less than half is still bronze`() {
        assertEquals(Medal.BRONZE, Medal.forResult(found = 2, total = 5))
        assertEquals(Medal.BRONZE, Medal.forResult(found = 3, total = 8))
    }

    @Test
    fun `going outside always earns at least bronze, even with nothing found`() {
        assertEquals(Medal.BRONZE, Medal.forResult(found = 0, total = 8))
    }

    @Test
    fun `an empty hunt is bronze, not gold`() {
        assertEquals(Medal.BRONZE, Medal.forResult(found = 0, total = 0))
    }
}
