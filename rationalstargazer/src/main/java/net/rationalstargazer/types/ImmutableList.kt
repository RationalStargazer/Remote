package net.rationalstargazer.types

interface RStaImmutableList<out E> : List<E>

fun <E> List<E>.toImmutable(): RStaImmutableList<E> {
    if (this is RStaImmutableList) {
        return this
    }

    return WrappedList(this.toList())
}

fun <E> List<E>.considerImmutable(): RStaImmutableList<E> {
    if (this is RStaImmutableList) {
        return this
    }

    return WrappedList(this)
}

fun <E>immutableListOf(vararg list: E): RStaImmutableList<E> {
    return WrappedArray(list.clone())  // is it really necessary to clone source array?
}

private class WrappedList<out E>(val privateList: List<E>) : List<E> by privateList, RStaImmutableList<E> {

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        return privateList == other
    }

    override fun hashCode(): Int {
        return privateList.hashCode()
    }
}

private class WrappedArray<out E>(private val privateArray: Array<E>) : AbstractList<E>(), RStaImmutableList<E> {

    override val size: Int = privateArray.size

    override fun get(index: Int): E = privateArray[index]
}