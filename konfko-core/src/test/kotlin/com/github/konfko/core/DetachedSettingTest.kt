package com.github.konfko.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author markopi
 */
class DetachedSettingTest {
    @Test
    fun `onUpdate is only called when new value is not null`() {
        val events = mutableMapOf<String, Int>()

        val setting = DetachedSetting(24).apply {
            onUpdate { events["1"] = it }
            onUpdate { events["2"] = it }
        }
        // update
        setting.update(12)
        assertThat(events).isEqualTo(mapOf(
                "1" to 12,
                "2" to 12))

        // remove
        events.clear()
        setting.update(null)
        assertThat(events).isEmpty()

        // un-remove
        setting.update(39)
        assertThat(events).isEqualTo(mapOf(
                "1" to 39,
                "2" to 39))

        // update again
        setting.update(13)
        assertThat(events).isEqualTo(mapOf(
                "1" to 13,
                "2" to 13))
    }

    @Test
    fun `onRefresh is only called when neither old and new values are null`() {
        val events = mutableMapOf<String, Pair<Int?, Int?>>()

        val setting = DetachedSetting(24).apply {
            onRefresh { events["1"] = it.old to it.new }
            onRefresh { events["2"] = it.old to it.new }
        }
        // update
        setting.update(12)
        assertThat(events).isEqualTo(mapOf(
                "1" to (24 to 12),
                "2" to (24 to 12)))

        // remove
        events.clear()
        setting.update(null)
        assertThat(events).isEmpty()

        // un-remove
        setting.update(39)
        // listener is called with last non-null value for in update.old
        assertThat(events).isEqualTo(mapOf(
                "1" to (12 to 39),
                "2" to (12 to 39)))

        // update again
        setting.update(13)
        assertThat(events).isEqualTo(mapOf(
                "1" to (39 to 13),
                "2" to (39 to 13)))
    }


}