package net.rationalstargazer.remote

import net.rationalstargazer.types.Either

sealed class RemoteData<out T> {
    
    companion object {
        fun connectionError(details: String? = null, causedBy: Throwable? = null): RemoteData.Fail =
            RemoteData.Fail(RemoteData.FailSubtype.CONNECTION_ERROR, details, causedBy)
    
        fun invalidResult(details: String? = null, causedBy: Throwable? = null): RemoteData.Fail =
            RemoteData.Fail(RemoteData.FailSubtype.INVALID_RESULT, details, causedBy)
    
        fun notAuthorized(details: String? = null, causedBy: Throwable? = null): RemoteData.Fail =
            RemoteData.Fail(RemoteData.FailSubtype.NOT_AUTHORIZED, details, causedBy)
    }
    
    data class Data<out T>(val data: T) : RemoteData<T>()
    
    data class Fail(
        val subtype: FailSubtype,
        val details: String? = null,
        val causedBy: Throwable? = null
    ) : RemoteData<Nothing>()
    
    enum class FailSubtype {
        NO_CONNECTION,
        CONNECTION_ERROR,
        NOT_AUTHORIZED,
        INVALID_RESULT
    }
    
    fun <R> mapData(block: (T) -> R): RemoteData<R> =
        when (this) {
            is Data -> Data(block(this.data))
            is Fail -> this
        }
    
    fun <T2, R> combineWith(other: RemoteData<T2>, mapper: (T, T2) -> R): RemoteData<R> =
        when (this) {
            is Data -> {
                when (other) {
                    is Data -> Data(mapper(this.data, other.data))
                    is Fail -> other
                }
            }
            
            is Fail -> this
        }
}

fun <T, R> RemoteData<T>.mapData(dataBranch: (RemoteData.Data<T>) -> RemoteData<R>): RemoteData<R> =
    when (this) {
        is RemoteData.Data -> dataBranch(this)
        is RemoteData.Fail -> this
    }

fun <R> RemoteData<R>.mapFail(failBranch: (RemoteData.Fail) -> RemoteData<R>): RemoteData<R> =
    when (this) {
        is RemoteData.Data -> this
        is RemoteData.Fail -> failBranch(this)
    }

fun <T, R> RemoteData<T>.handle(
    dataBranch: (T) -> R,
    failBranch: (RemoteData.Fail) -> R
): R =
    when (this) {
        is RemoteData.Data -> dataBranch(this.data)
        is RemoteData.Fail -> failBranch(this)
    }