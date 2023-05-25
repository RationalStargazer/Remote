package net.rationalstargazer.remote

sealed class RemoteData<out T> {
    
    companion object {
        
        fun <T> data(value: T): RemoteData.Data<T> =
            Data(value)
        
        fun connectionError(details: String? = null, causedBy: Throwable? = null): Fail =
            Fail(FailSubtype.CONNECTION_ERROR, details, causedBy)
        
        fun invalidResult(details: String? = null, causedBy: Throwable? = null): Fail =
            Fail(FailSubtype.INVALID_RESULT, details, causedBy)
        
        fun notAuthorized(details: String? = null, causedBy: Throwable? = null): Fail =
            Fail(FailSubtype.NOT_AUTHORIZED, details, causedBy)
        
        inline fun <T, R> RemoteData<T>.whenData(dataBranch: (Data<T>) -> RemoteData<R>): RemoteData<R> =
            when (this) {
                is Data -> dataBranch(this)
                is Fail -> this
            }
        
        inline fun <T> RemoteData<T>.whenFail(failBranch: (Fail) -> RemoteData<T>): RemoteData<T> =
            when (this) {
                is Data -> this
                is Fail -> failBranch(this)
            }
        
        inline fun <T> RemoteData<T>.doWhenData(dataBranch: (Data<T>) -> Unit) {
            whenData {
                dataBranch(it)
                it
            }
        }
        
        inline fun <T, R> RemoteData<T>.handle(
            dataBranch: (T) -> R,
            failBranch: (Fail) -> R
        ): R =
            when (this) {
                is Data -> dataBranch(this.data)
                is Fail -> failBranch(this)
            }
    }
    
    data class Data<T>(val data: T) : RemoteData<T>()
    
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