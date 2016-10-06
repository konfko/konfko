package com.github.konfko.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * @author markopi
 */
class OptionalSettingTest {

    @Test
    fun `onUpdate is called on any change`() {
        var event: Any?

        val setting = DetachedSetting(24)
        setting.optional.onUpdate { event = it }

        // update
        event = Unit
        setting.update(12)
        assertEquals(12, event)

        // remove
        event = Unit // something not null
        setting.update(null)
        assertNull(event)

        // un-remove
        event = Unit
        setting.update(39)
        assertEquals(39, event)

        // update again
        event = Unit
        setting.update(13)
        assertEquals(13, event)
    }

    @Test
    fun `onRefresh is called on any change`() {
        var event: Pair<Int?, Int?>?

        val setting = DetachedSetting(24)
        setting.optional.onRefresh { event = it.old to it.new }

        // update
        event = null
        setting.update(12)
        assertEquals(24 to 12, event)

        // remove
        event = null
        setting.update(null)
        assertEquals(12 to null, event)

        // un-remove
        event = null
        setting.update(39)
        assertEquals(null to 39, event)

        // update again
        event = null
        setting.update(13)
        assertEquals(39 to 13, event)
    }

}