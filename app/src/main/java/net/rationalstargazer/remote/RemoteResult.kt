package net.rationalstargazer.remote

sealed class RemoteResult<out SuccessType, out RejectType> {

    companion object {

        fun connectionError(details: String? = null, causedBy: Throwable? = null): Fail =
            Fail(FailSubtype.CONNECTION_ERROR, details, causedBy)

        fun invalidResult(details: String? = null, causedBy: Throwable? = null): Fail =
            Fail(FailSubtype.INVALID_RESULT, details, causedBy)

        fun notAuthorized(details: String? = null, causedBy: Throwable? = null): Fail =
            Fail(FailSubtype.NOT_AUTHORIZED, details, causedBy)

        inline fun <T, R, RejectType> RemoteResult<T, RejectType>.whenSuccess(
            successBranch: (Success<T>) -> RemoteResult<R, RejectType>
        ): RemoteResult<R, RejectType> {
            return when (this) {
                is Success -> successBranch(this)
                is Reject -> this
                is Fail -> this
            }
        }

        inline fun <T, R, SuccessType> RemoteResult<SuccessType, T>.whenReject(
            rejectBranch: (Reject<T>) -> RemoteResult<SuccessType, R>
        ): RemoteResult<SuccessType, R> {
            return when (this) {
                is Success -> this
                is Reject -> rejectBranch(this)
                is Fail -> this
            }
        }

        inline fun <S, R> RemoteResult<S, R>.whenFail(failBranch: (Fail) -> RemoteResult<S, R>): RemoteResult<S, R> {
            return when (this) {
                is Success -> this
                is Reject -> this
                is Fail -> failBranch(this)
            }
        }

        inline fun <T> RemoteResult<T, *>.doWhenSuccess(successBranch: (Success<T>) -> Unit) {
            whenSuccess {
                successBranch(it)
                it
            }
        }

        fun <SuccessA, SuccessB, SuccessR, RejectA, RejectB, RejectR> RemoteResult<SuccessA, RejectA>.combineWith(
            other: RemoteResult<SuccessB, RejectB>,
            successMapper: (SuccessA, SuccessB) -> SuccessR,
            rejectMapper: (RejectA?, RejectB?) -> RejectR,
        ): RemoteResult<SuccessR, RejectR> {
            return when (this) {
                is Success -> {
                    when (other) {
                        is Success -> Success(successMapper(this.data, other.data))
                        is Reject -> Reject(rejectMapper(null, other.info))
                        is Fail -> other
                    }
                }

                is Reject -> {
                    when (other) {
                        is Success -> Reject(rejectMapper(this.info, null))
                        is Reject -> Reject(rejectMapper(this.info, other.info))
                        is Fail -> other
                    }
                }

                is Fail -> this
            }
        }

        fun <SuccessA, SuccessB, SuccessR, RejectType> RemoteResult<SuccessA, RejectType>.combineWith(
            other: RemoteResult<SuccessB, RejectType>,
            mapper: (SuccessA, SuccessB) -> SuccessR
        ): RemoteResult<SuccessR, RejectType> {
            return when (this) {
                is Success -> {
                    when (other) {
                        is Success -> Success(mapper(this.data, other.data))
                        is Reject -> other
                        is Fail -> other
                    }
                }

                is Reject -> {
                    when (other) {
                        is Success -> this
                        is Reject -> this
                        is Fail -> other
                    }
                }

                is Fail -> this
            }
        }

        inline fun <SuccessType, RejectType, ResultType>RemoteResult<SuccessType, RejectType>.handle(
            successBranch: (SuccessType) -> ResultType,
            rejectBranch: (RejectType) -> ResultType,
            failBranch: (Fail) -> ResultType
        ): ResultType {
            return when (this) {
                is Success -> successBranch(this.data)
                is Reject -> rejectBranch(this.info)
                is Fail -> failBranch(this)
            }
        }

        inline fun <S, R> RemoteResult<S, R>.dropReject(rejectBranch: (Reject<R>) -> RemoteData<S>): RemoteData<S> {
            return when (this) {
                is Success -> RemoteData.Data(this.data)
                is Reject -> rejectBranch(this)
                is Fail -> RemoteData.Fail(this.subtype, this.details, this.causedBy)
            }
        }
    }

    data class Success<out SuccessType>(val data: SuccessType) : RemoteResult<SuccessType, Nothing>()

    data class Reject<out RejectType>(val info: RejectType) : RemoteResult<Nothing, RejectType>()

    data class Fail(
        val subtype: FailSubtype,
        val details: String? = null,
        val causedBy: Throwable? = null
    ) : RemoteResult<Nothing, Nothing>()

    enum class FailSubtype {
        NO_CONNECTION,
        CONNECTION_ERROR,
        NOT_AUTHORIZED,
        INVALID_RESULT
    }

    fun <MappedType> mapData(block: (SuccessType) -> MappedType): RemoteResult<MappedType, RejectType> {
        return when (this) {
            is Success -> Success(block(this.data))
            is Reject -> this
            is Fail -> this
        }
    }

    fun createInvalidResult(details: String? = null, causedBy: Throwable? = null): Fail =
        invalidResult(details, causedBy)
}

sealed class RemoteData<out T> {

    companion object {

        fun <T> data(value: T): RemoteData.Data<T> =
            Data(value)

        fun connectionError(details: String? = null, causedBy: Throwable? = null): Fail =
            Fail(RemoteResult.FailSubtype.CONNECTION_ERROR, details, causedBy)

        fun invalidResult(details: String? = null, causedBy: Throwable? = null): Fail =
            Fail(RemoteResult.FailSubtype.INVALID_RESULT, details, causedBy)

        fun notAuthorized(details: String? = null, causedBy: Throwable? = null): Fail =
            Fail(RemoteResult.FailSubtype.NOT_AUTHORIZED, details, causedBy)

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
        val subtype: RemoteResult.FailSubtype,
        val details: String? = null,
        val causedBy: Throwable? = null
    ) : RemoteData<Nothing>()

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

    fun toRemoteResult(): RemoteResult<T, Unit> {
        return when(this) {
            is Data -> RemoteResult.Success(this.data)
            is Fail -> RemoteResult.Fail(this.subtype, this.details, this.causedBy)
        }
    }

    fun createInvalidResult(details: String? = null, causedBy: Throwable? = null): Fail =
        invalidResult(details, causedBy)

}