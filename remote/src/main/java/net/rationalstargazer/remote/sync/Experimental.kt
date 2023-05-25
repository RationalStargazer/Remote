package net.rationalstargazer.remote.sync

// interface LocalListRepository {
//
//     interface ReadAccess<T> : Reader<T> {
//         suspend fun <R> sole(block: suspend (Reader<T>) -> R): R
//     }
//
//     interface WriteAccess<T> : ReaderWriter<T> {
//         suspend fun <R> sole(block: suspend (ReaderWriter<T>) -> R): R
//         val readOnlyAccess: ReadAccess<T>
//     }
//
//     interface Reader<T> : Repository.Reader<Int, T> {
//         val size: Int?
//
//         suspend fun findSize(): Int
//
//         suspend fun sublist(range: IntRange): List<T>
//
//         suspend fun getAll(): List<T>
//     }
//
//     interface Writer<T> : Repository.Writer<Int, T> {
//         fun add(value: T)
//
//         fun replaceAll(list: List<T>)
//     }
//
//     interface ReaderWriter<T> : Reader<T>, Writer<T>
// }