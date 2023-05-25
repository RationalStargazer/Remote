package net.rationalstargazer.remote.sync

typealias RemoteQueueHandlerQueue<Key, Command> = List<RemoteSyncIdValue<RemoteQueueHandler.SyncCommand<Key, Command>>>

interface RemoteQueueHandler {

    data class State<Data, Key, Command>(
        /**
         * Additional data
         */
        val data: Data,

        /**
         * Commands queue.
         * In straightforward implementation commands are added to the end of the queue and executed sequentially.
         * A command is removed from the queue at the start of the command's handling.
         */
        val queue: RemoteQueueHandlerQueue<Key, Command>,

        /**
         * ElapsedRealTimes of the last successful command for each key are kept here.
         * Implementors often use this information to skip excessive Sync commands (see [SyncCommand.Sync.target]).
         */
        val lastSuccessfulElapsedRealTimes: Map<Key, Long>,

        /**
         * The time of last failed attempt for each key (both Sync and Send commands) is kept here.
         * Implementors often use this information to automatically retry failed operations.
         */
        val failedAttempts: Map<Key, TimeData>,

        /**
         * Failed Send commands are kept here in the same sequence as they were added to the queue.
         * Implementors use this to keep information about "locally ahead" values
         * (see [BaseRemoteComplexDataSource.LocalRemote] for details) to retry the Send during next [SyncCommand.Sync]
         * (see for details).
         */
        val failedSends: List<SyncCommand.Send<Key, Command>>
    ) {
        /**
         * Time. Use [Precise] to represent a time of something that occurred during current application run.
         * Precise is not applicable for representation of something that occurred during previous application runs,
         * use [UTC] for it.
         */
        sealed class TimeData {
            data class Precise(val elapsedRealTime: Long) : TimeData()
            
            data class UTC(val date: Long) : TimeData()
        }
    }
    
    sealed class SyncCommand<Key, out Command> {

        abstract val key: Key
    
        /**
         * Represents a command to synchronize local and remote value.
         *
         * First of all, if there are relevant failed [Send] attempts in [State.failedSends] list,
         * additional remote call(s) will be made first to retry relevant Send commands before handle the Sync command.
         * If any of the relevant Send commands will fail the Sync command will be treated as failed.
         * Each relevant Send command is treated as normal complete Send command,
         * they are executed in relevant sequence
         * and success or failure of each of them has the same effect as stand alone [Send] (see details).
         *
         * If relevant Send commands were already retried and failed recently
         * the handler may decide to do not retry them and terminate current Sync command instead
         * (Sync command will be treated as failed).
         *
         * If there are no failed Send attempts (or they were retried successfully), the Sync's [target] is checked.
         * If the value meets `target` conditions, the value is considered actual and no remote call will be made
         * (Sync command will be treated as skipped).
         *
         * Failed Sync command will lead to its key and time of fail will be added to [State.failedAttempts].
         *
         * Skipped Sync command doesn't change anything in [State.lastSuccessfulElapsedRealTimes] or
         * [State.failedAttempts] (it is neither success nor failure).
         *
         * Successful Sync command means
         * all relevant previously failed Send commands were retried and successfully completed,
         * LocalRemote.remote value is updated with the latest value from the server,
         * [State.failedAttempts] value for the Sync's key is removed from the map,
         * and [State.lastSuccessfulElapsedRealTimes] will be added
         * (with the value corresponding to the time just before the start of the handling).
         */
        data class Sync<Key>(override val key: Key, val target: RemoteSyncTarget) : SyncCommand<Key, Nothing>()
    
        /**
         * Represents a command to send "locally ahead" value (see [BaseRemoteComplexDataSource.LocalRemote]) to the server
         * (`command` describes value changes, usually in a way which is convenient to use in remote API,
         * in straightforward implementation it can be just "locally ahead" value itself).
         *
         * Failed Send command will lead to its key and time of fail will be added to [State.failedAttempts],
         * and the command will be added to [State.failedSends] if the list doesn't contain it yet.
         *
         * Implementations may choose to discard "locally ahead" changes after many failed send attempts
         * (LocalRemote.local value can be changed).
         *
         * Successful Send command means LocalRemote.remote value is updated in the result of interactions with the server,
         * [State.failedAttempts] value for the Send's key is removed from the map,
         * and [State.lastSuccessfulElapsedRealTimes] will be added
         * (with the value corresponding to the time just before the start of the handling).
         * Also LocalRemote.local value can be changed as the result.
         * (It is a Send command's responsibility to ensure consistency of LocalRemote's local and remote values.
         * For example to ensure the values really reflect actual values additional sync remote call can be made after the Send.)
         */
        data class Send<Key, out Command>(override val key: Key, val command: Command) : SyncCommand<Key, Command>()
    }

    sealed class QueueCommand<out StateData, Key, out Command> {
        data class Remote<Key, out Command>(
            val remote: RemoteSyncIdValue<SyncCommand<Key, Command>>
        ) : QueueCommand<Nothing, Key, Command>()

        data class StateChange<StateData, Key, Command>(
            val reducer: (State<StateData, Key, Command>) -> State<StateData, Key, Command>
        ) : QueueCommand<StateData, Key, Command>()
    }
}