package net.rationalstargazer

interface ImmutableList<out E> : List<E>

fun <E> List<E>.considerImmutable(): ImmutableList<E> {
    return PrivateList(this)
}

fun <E>immutableListOf(vararg list: E): ImmutableList<E> {
    return PrivateArray(list.clone())
}

private class PrivateList<out E>(privateList: List<E>) : List<E> by privateList, ImmutableList<E>

private class PrivateArray<out E>(private val privateArray: Array<E>) : AbstractList<E>(), ImmutableList<E> {

    override val size: Int = privateArray.size

    override fun get(index: Int): E = privateArray[index]
}