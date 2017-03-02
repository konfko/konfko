package com.github.konfko.core.derived

import com.github.konfko.core.NoSuchSettingException
import com.github.konfko.core.Setting
import com.github.konfko.core.Settings
import com.github.konfko.core.ValuedSettings
import com.github.konfko.core.structured.findNested
import com.github.konfko.core.structured.mapifyNested
import com.github.konfko.core.structured.nestedToFlat
import java.util.*

/**
 * @author markopi
 */
/**
 * An abstract class for settings that have been derived from other settings.
 */
abstract class DerivedSettings : ValuedSettings() {
    /**
     * Root settings in the derived chain
     */
    val root: Settings get() = parentChain.last()

    /**
     * Chain of all parents. First element is the root, last element is the immediate parent of this settings
     */
    protected val parentChain: List<Settings> by lazy {
        var p: Settings = parent
        val parents = LinkedList<Settings>()
        parents.add(p)
        while (p is DerivedSettings) {
            p = p.parent
            parents.add(p)
        }
        parents.toList()
    }

    fun makeAbsoluteKey(key: String): String  {
        val p = parent
        if (p is DerivedSettings) {
            return p.makeAbsoluteKey(makeParentKey(key))
        } else
            return makeParentKey(key)
    }

    abstract protected fun makeParentKey(key: String): String

    override fun <T : Any> get(key: String, type: Class<T>): T =
            find(key, type) ?: throw NoSuchSettingException(makeAbsoluteKey(key))

    override fun <T : Any> find(key: String, type: Class<T>): T? =
            parent.find(makeParentKey(key), type)

    override fun <T : Any> bind(key: String, type: Class<T>): Setting<T> {
        return parent.bind(makeParentKey(key), type)
    }


    /**
     * Settings from which this settings was derived from
     */
    abstract val parent: Settings
}

abstract class DerivedValueSettings<out T>: DerivedSettings() {
    abstract protected val derivedValue: T?

    override val empty: Boolean get() = derivedValue == null

    override fun contains(key: String): Boolean = findNested(derivedValue, key) != null

    override fun toFlatMap() = nestedToFlat(derivedValue)

    override fun toNestedMap(): Map<String, Any> = mapifyNested(derivedValue)


}