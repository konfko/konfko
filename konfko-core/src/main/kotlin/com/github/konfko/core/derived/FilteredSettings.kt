package com.github.konfko.core.derived

import com.github.konfko.core.Settings
import com.github.konfko.core.structured.flatToNested

/**
 * @author markopi
 */
internal class FilteredSettings internal constructor(override val parent: Settings,
                                                     flatKeys: Boolean,
                                                     keyPredicate: (String) -> Boolean) :
        DerivedValueSettings<Any>() {
    override fun makeParentKey(key: String): String = key

    override val derivedValue: Any? = filterValue(flatKeys, keyPredicate)

    private fun filterValue(flatKeys: Boolean, keyPredicate: (String) -> Boolean): Any? {
        val value = if (flatKeys) parent.toFlatMap() else parent.toNestedMap()

        if (value.isEmpty()) return null
        val filtered = value.filterKeys(keyPredicate)
        // re-nesting is only needed if filtering on flat keys
        return if (flatKeys) flatToNested(filtered) else filtered
    }
}

fun Settings.filterTopLevelKeys(keyPredicate: (String) -> Boolean): DerivedSettings =
        FilteredSettings(this, false, keyPredicate)

fun Settings.filterFlatKeys(keyPredicate: (String) -> Boolean): DerivedSettings =
        FilteredSettings(this, true, keyPredicate)
