package example

import net.rationalstargazer.types.Either
import net.rationalstargazer.types.handle

sealed class RemoteState<out T, out LastError : Any> {
    
    abstract val value: T
    abstract val inProgress: Boolean
    abstract val synced: Boolean
    abstract val lastError: LastError?
    
    abstract fun <R : Any> mapValue(mapper: (T) -> R): RemoteState<R?, LastError>
    
    abstract fun notEmptyOrNull(): RemoteState<T & Any, LastError>?
    
    abstract fun eitherDataOrNot(): Either<RemoteState<T & Any, LastError>, RemoteState.NotSynced<Nothing?, LastError>>
    
    fun <R> handle(synced: (Synced<T & Any>) -> R, notSynced: (NotSynced<T, LastError>) -> R): R {
        return when (this) {
            is Synced -> {
                synced(this.asNotEmpty())
            }
            
            is NotSynced -> {
                notSynced(this)
            }
        }
    }
    
    data class Synced<out T>(
        override val value: T & Any,
        override val inProgress: Boolean
    ) : RemoteState<T & Any, Nothing>() {
        override val synced: Boolean = true
        override val lastError: Nothing? = null
        
        override fun <R : Any> mapValue(mapper: (T & Any) -> R): Synced<R> {
            return Synced(mapper(value), inProgress)
        }
    
        override fun notEmptyOrNull(): Synced<T & Any> {
            return mapValue { value }
        }
        
        fun asNotEmpty(): Synced<T & Any> {
            return notEmptyOrNull()
        }
    
        override fun eitherDataOrNot(): Either.Left<RemoteState<T & Any, Nothing>> {
            return Either.Left(this)
        }
    }
    
    data class NotSynced<out T, out LastError : Any>(
        override val value: T,
        override val inProgress: Boolean,
        override val lastError: LastError?
    ) : RemoteState<T, LastError>() {
        
        override val synced: Boolean = false
        
        override fun <R : Any> mapValue(mapper: (T) -> R): NotSynced<R, LastError> {
            return map(mapper)
        }
    
        fun <R> mapValueNullable(mapper: (T) -> R): NotSynced<R, LastError> {
            return map(mapper)
        }
        
        private fun <R> map(mapper: (T) -> R): NotSynced<R, LastError> {
            return NotSynced(mapper(value), inProgress, lastError)
        }
    
        override fun notEmptyOrNull(): NotSynced<T & Any, LastError>? {
            return if (value != null) {
                map { value }
            } else {
                null
            }
        }
    
        override fun eitherDataOrNot(): Either<RemoteState<T & Any, LastError>, NotSynced<Nothing?, LastError>> {
            return handle(
                {
                    eitherDataOrNot()
                },
        
                {
                    notEmptyOrNull()
                        ?.let {
                            Either.Left(it)
                        }
                        ?: Either.Right(it.mapValueNullable { null })
                }
            )
        }
    }
    
    sealed interface ByData<out T, out LastError : Any> {
        
        val value: T
        
        val inProgress: Boolean
            get() = bySync.inProgress
        
        val synced: Boolean
            get() = bySync.synced
        
        val lastError: LastError?
            get() = bySync.lastError
    
        val bySync: RemoteState<T, LastError>
    
        @JvmInline
        value class Data<out T, out LastError : Any> constructor(
            override val bySync: RemoteState<T & Any, LastError>
        ) : ByData<T, LastError> {
            
            override val value: T & Any
                get() = bySync.value
        }
    
        @JvmInline
        value class NoData<out LastError : Any> constructor(
            override val bySync: NotSynced<Nothing?, LastError>
        ) : ByData<Nothing?, LastError> {
    
            override val value: Nothing?
                get() = bySync.value
        }
    }
}

fun <T : Any, LastError : Any> RemoteState<T, LastError>.byData(): RemoteState.ByData.Data<T, LastError> {
    return RemoteState.ByData.Data(this)
}

fun <T, LastError : Any> RemoteState<T, LastError>.byData(): RemoteState.ByData<T?, LastError> {
    return eitherDataOrNot().handle({ RemoteState.ByData.Data(it) }, { RemoteState.ByData.NoData(it) },)
}