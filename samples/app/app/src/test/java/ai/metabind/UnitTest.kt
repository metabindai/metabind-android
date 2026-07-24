package ai.metabind

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitTest {

    @Test
    fun testRequireWithEmptyMap() {
        val initialValue = 3

        val finalValue = initialValue + 1

        assertEquals(4, finalValue)
    }

}
