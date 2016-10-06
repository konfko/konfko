package com.github.konfko.core

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KProperty

/**
 * @author markopi
 */


/**
 * A setting which can be listened to changed.
 * Separate from [Setting] as this interface is used both for optional and mandatory setting
 *
 */
interface ListenableSetting<out T> {
    /**
     * Retrieves the current value of this setting. During updates, it retains the old value until all listeners
     * have executed successfully
     *
     * Reading this property is generally quite efficient, typically comparable to a volatile variable access
     *
     * @throws NoSuchSettingException if the setting does not exist, and the setting is not optional
     *          (it implements [Setting], where [T] is not nullable)
     * @throws SettingTypeConversionException if the setting value cannot be converted to this type
     */
    val value: T

    /**
     * Registers a listener that listens to setting updates.
     *
     *The listener receives only the new setting value as parameter
     *
     * This is just a wrapper around [onRefresh]
     */
    fun onUpdate(listener: (T) -> Unit): ListenableSetting<T>

    /**
     * Registers a listener that listens to setting refresh.
     *
     * This is triggered whenever a setting is added, removed or updated. The listener receives
     * both old an new setting values, one of which can be null
     *
     */
    fun onRefresh(listener: (SettingUpdate<T>) -> Unit): ListenableSetting<T>

    /**
     * Serves to enable delegation to this setting. Treats the setting as required. Delegates directly to [value].
     *
     * @throws NoSuchSettingException if the setting does not exist, and the setting is not optional
     * @throws SettingTypeConversionException if the setting value cannot be converted to this type
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T
}


/**
 * A handle to a single setting. It can represent any value for which a type converter exists.
 *
 * If the settings this was created from are reloadable, this holds a mutable view of the underlying setting value
 *
 * Can be used in several ways:
 *  * direct access to the setting (required or optional)
 *  * as a kotlin property delegate to the underlying value (by default the value is required, but optional is also supported)
 *  * as an observer of changes to the underlying setting (if settings are reloadable)
 *
 *  IMPORTANT: Even if this is only used to listen to change event, subscriber must still hold a reference to this
 *  object, otherwise it will be garbage collected and no more notifications will arrive. Holding a reference to
 *  [optional] is also enough to achieve this
 *
 *  If the Setting is not reloadable, listeners can still be registered normally, they will just receive
 *  no notifications.
 *
 * If any of registered listeners throws an exception, the value fields will NOT be updated.
 *
 * @see ListenableSetting
 */
interface Setting<out T : Any> : ListenableSetting<T> {
    /**
     * Retrieves the current value of this setting. During updates, it retains the old value until all listeners
     * have executed successfully
     *
     * Reading this property is generally quite efficient, typically comparable to a volatile variable access
     *
     * @throws NoSuchSettingException if the setting does not exist
     * @throws SettingTypeConversionException if the setting value cannot be converted to this type
     */
    override val value: T

    /**
     * Returns a delegate to this setting. Treats the setting as optional and returns null if it does not exist.
     * For an mandatory setting delegate, just delegate to this Setting object.

     */
    val optional: OptionalSetting<T>
}

/**
 * A handle to a setting that might not exist. It can only be created by [Setting.optional]
 *
 */
class OptionalSetting<out T : Any> internal constructor(private val parent: AbstractSetting<T>) : ListenableSetting<T?> {
    /**
     * Retrieves the current value of this setting. During updates, it retains the old value until all listeners
     * have executed successfully. This is
     *
     * Reading this property is generally quite efficient, typically comparable to a volatile variable access
     *
     * This is an optional view of the setting value, so it never throws NoSuchSettingException, it just returns null
     * when the setting does not exist
     *
     * @throws SettingTypeConversionException if the setting value cannot be converted to this type
     */
    override val value: T?
        get() = parent.valueOrNull
    // should return null when conversion failed?
//        get() = try {
//            parent.valueOrNull
//        } catch (e: SettingTypeConversionException) {
//            LOG.warn("Could not convert optional value to required type. Returning null instead.", e)
//            null
//        }

    override fun onUpdate(listener: (T?) -> Unit): OptionalSetting<T> {
        parent.registerListener { update ->
            listener(update.new)
        }
        return this
    }


    override fun onRefresh(listener: (SettingUpdate<T?>) -> Unit): OptionalSetting<T> {
        parent.registerListener(listener)
        return this
    }

    /**
     * Returns the current value of the setting or null. Delegates directly to [value]

     * @throws SettingTypeConversionException if the setting value cannot be converted to this type
     *
     */
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = value

//    private companion object {
//        val LOG = LoggerFactory.getLogger(OptionalSetting::class.java)
//    }
}

/**
 * Parameter to setting listeners when a setting is updated.
 *
 * Holds old and new values of the setting.
 *
 * Normally the values should be different, however the check for equality is only done on the raw setting value,
 * and it is possible that a type converter will convert two setting values to the same value.
 * For example, strings "1" and "01" both convert to integer 1, and you would get an update where old==new
 */
data class SettingUpdate<out T>(val old: T, val new: T)

/**
 * Abstract base class for [Setting]. Mostly deals with listeners and delegation
 */
abstract class AbstractSetting<T : Any>() : Setting<T> {
    private val listeners = CopyOnWriteArrayList<(SettingUpdate<T?>) -> Unit>()

    // We create a new value for each invocation because we assume this will be rarely used.
    // OptionalSetting keeps a reference to this, so keeping just a reference to optional will prevent this setting
    // from being garbage collected
    override val optional: OptionalSetting<T> get() = OptionalSetting(this)

    /**
     * Retrieves the current value of this setting, or null if there isn't any. During updates, it retains the old
     * value until all listeners have executed successfully
     *
     * Reading this property is generally quite efficient, typically comparable to a volatile variable access
     *
     * @throws SettingTypeConversionException if the setting value cannot be converted to this type
     */
    abstract internal val valueOrNull: T?

    @Volatile private var lastNonNullValue: T? = null

    override fun onUpdate(listener: (T) -> Unit): Setting<T> {
        registerListener { update ->
            if (update.new != null) listener.invoke(update.new)
        }
        return this
    }

    override fun onRefresh(listener: (SettingUpdate<T>) -> Unit): Setting<T> {
        registerListener { update ->
            val oldValue = update.old ?: lastNonNullValue
            if (oldValue != null && update.new != null) {
                listener(SettingUpdate(oldValue, update.new))
            }
        }
        return this
    }

    internal fun registerListener(listener: (SettingUpdate<T?>) -> Unit) {
        listeners.add(listener)
    }

    protected fun notifyListeners(update: SettingUpdate<T?>) {
        listeners.forEach { listener -> listener(update) }
        if (update.new != null) {
            lastNonNullValue = update.new
        }
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

/**
 * A setting with a value that never changes. Can still throw a SettingsException if there was an error obtaining the value
 *
 * This class is returned when settings are not reloadable
 */
class ConstantSetting<T : Any>(private val propertyValue: T?, private val conversionError: SettingsException? = null) : AbstractSetting<T>() {
    init {
        check(if (conversionError != null) propertyValue == null else true, { "Cannot have a property value when there was a conversion error" })
    }

    override val value: T
        get() = valueOrNull ?: throw  NoSuchSettingException("(constant)")
    override val valueOrNull: T?
        get() = if (conversionError != null) throw conversionError else propertyValue
}

/**
 * A setting with a value which can be manually updated. Intended for testing.
 *
 * Can represent both an existing and absent setting (when the value given is null).
 *
 * As per [Setting] contract, trying to retrieve a null setting will throw NoSuchSettingException,
 * for retrieval of optional setting values use [Setting.optional]
 */
class DetachedSetting<T : Any, in U : T?>(propertyValue: U?) : AbstractSetting<T>() {
    override val value: T
        get() = valueOrNull ?: throw  NoSuchSettingException("(detached)")
    @Volatile override var valueOrNull: T? = propertyValue
        private set

    /**
     * Updates the setting to the new value.
     *
     * Also notifies listeners about the value change. This is done synchronously
     */
    fun update(newValue: U?) {
        val oldValue = valueOrNull
        // do not call listeners if trying to update with exact same instance
        if (oldValue === newValue) return

        notifyListeners(SettingUpdate(oldValue, newValue))
        valueOrNull = newValue
    }
}

