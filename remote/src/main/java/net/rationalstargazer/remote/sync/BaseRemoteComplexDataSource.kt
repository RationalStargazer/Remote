package net.rationalstargazer.remote.sync

import net.rationalstargazer.events.value.RStaValue
import net.rationalstargazer.remote.sync.BaseRemoteComplexDataSource.LocalRemote

/**
 * [RemoteComplexDataSource] with the ability to send locally updated data to the server.
 */
interface WritableRemoteComplexDataSource<StateData, Key, Value : Any, Command> :
    RemoteComplexDataSource<StateData, Key, Value, Command>,
    BaseWritableRemoteComplexDataSource<Key, Value, Command>

/**
 * Represents data source which is backed by remote source and local cache.
 *
 * [state] and [waitingCommands] are observable values that can be used to track ongoing activities.
 *
 * [read] is to read locally available values,
 *
 * [sync] is to keep local cache actual.
 *
 * See [BaseRemoteComplexDataSource] for details.
 *
 * See [BaseWritableRemoteComplexDataSource] for changeable data source.
 */
interface RemoteComplexDataSource<StateData, Key, Value : Any, Command> :
    BaseRemoteComplexDataSource<Key, Value> {
    
    val state: RStaValue<RemoteQueueHandler.State<StateData, Key, Command>>
    
    /**
     * Reads data from local cache along with the [state]
     */
    suspend fun readWithState(key: Key): Pair<RemoteQueueHandler.State<StateData, Key, Command>, Value?>
    
    /**
     * Reads data from local cache as [LocalRemote] along with the [state]
     */
    suspend fun readLocalRemoteWithState(key: Key): Pair<RemoteQueueHandler.State<StateData, Key, Command>, LocalRemote<Value>?>
}

/**
 * [BaseRemoteComplexDataSource] with the ability to send locally updated data to the server.
 */
interface BaseWritableRemoteComplexDataSource<Key, Value : Any, Command> : BaseRemoteComplexDataSource<Key, Value> {
    
    /**
     * Immediately applies changes (described in `command`) to internal cache,
     * and adds [RemoteQueueHandler.SyncCommand.Send] to the queue.
     * The call moves the value into "locally ahead" state until successful Send will happened.
     * (see [BaseRemoteComplexDataSource.LocalRemote] for details).
     */
    fun write(key: Key, command: Command): RemoteSyncId
}

/**
 * Represents data source which is backed by remote source and local cache.
 *
 * [read] is to read locally available values,
 *
 * [sync] is to keep local cache actual.
 *
 * `sync` doesn't start immediately,
 * instead all network related commands ([RemoteQueueHandler.SyncCommand]) are queued and executed sequentially
 */
interface BaseRemoteComplexDataSource<Key, Value : Any> {
    
    /**
     * `remote` property represents the last known remote value
     * (in straightforward implementations it is the value from the last successful [RemoteQueueHandler.SyncCommand.Sync] command).
     *
     * `local` represents the value from local cache.
     *
     * If `local` and `remote` values are the same it is called "synced" value.
     * If you never called [BaseWritableRemoteComplexDataSource.write] the value will always be "synced"
     * (or not exist if there were no successful Sync commands).
     *
     * If you called write() the value will be in "locally ahead" state until successful correspondent [RemoteQueueHandler.SyncCommand.Send] will happen.
     */
    data class LocalRemote<T>(val local: T, val remote: T)
    
    /**
     * Adds [RemoteQueueHandler.SyncCommand.Sync] (see for details) command to the queue.
     * @return unique identifier of the command
     */
    fun sync(key: Key, target: RemoteSyncTarget): RemoteSyncId
    
    /**
     * The same as [sync], but also suspends until the sync will be handled (successfully or not)
     */
    suspend fun waitForSync(key: Key, target: RemoteSyncTarget)
    
    /**
     * Reads data from local cache. The same as readLocalRemote()?.local
     */
    suspend fun read(key: Key): Value?
    
    /**
     * Reads data from local cache as [LocalRemote] (when you need to know remote value)
     */
    suspend fun readLocalRemote(key: Key): LocalRemote<Value>?
}