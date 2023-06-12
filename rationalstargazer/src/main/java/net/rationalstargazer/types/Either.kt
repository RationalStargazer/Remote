package net.rationalstargazer.types

sealed class Either<out L, out R> {
    
    data class Left<out T>(val value: T) : Either<T, Nothing>()
    
    data class Right<out T>(val value: T) : Either<Nothing, T>()
    
    fun <LR> mapLeft(block: (L) -> LR): Either<LR, R> =
        when (this) {
            is Left -> Left(block(this.value))
            is Right -> this
        }
}

fun <L, R, ResultType> Either<L, R>.handle(
    left: (L) -> ResultType,
    right: (R) -> ResultType,
): ResultType {
    return when (this) {
        is Either.Left -> left(value)
        is Either.Right -> right(value)
    }
}

// fun <T, R> RemoteData<T>.mapData(dataBranch: (RemoteData.Data<T>) -> RemoteData<R>): RemoteData<R> =
//     when (this) {
//         is RemoteData.Data -> dataBranch(this)
//         is RemoteData.Fail -> this
//     }
//