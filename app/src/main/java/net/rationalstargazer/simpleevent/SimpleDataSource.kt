package net.rationalstargazer.simpleevent

interface SimpleDataSource<T> {
    val value: T

    fun addListener(listener: (/*valueAtTimeOfChange*/ T) -> Unit)
    fun removeListener(listener: (/*valueAtTimeOfChange*/ T) -> Unit)
}

interface SimpleDynamicData<T> : SimpleDataSource<T> {
    override var value: T
}

class SimpleDynamicDataRawImpl<T>(defaultValue: T) : SimpleDataSource<T> {

    private var _value: T = defaultValue

    override var value: T
        get() = _value

        set(value) {
            if (value == _value) return
            changeSilently(value)
            notify(value)
        }

    override fun addListener(listener: (T) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun removeListener(listener: (T) -> Unit) {
        TODO("Not yet implemented")
    }

    fun changeSilently(nextValue: T) {
        _value = nextValue
    }

    fun notify(value: T) {
        if (alreadyNotifying) {
            //TODO: improve logging
            throw IllegalStateException("recursive events are not allowed")
        }

        alreadyNotifying = true

        val current = ArrayList(listeners)
        current.forEach {
            it(value)
        }

        alreadyNotifying = false
    }

    private var alreadyNotifying: Boolean = false
    private val listeners: MutableList<(T) -> Unit> = mutableListOf()
}