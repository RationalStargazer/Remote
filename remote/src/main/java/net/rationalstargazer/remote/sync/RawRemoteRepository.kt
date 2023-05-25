package net.rationalstargazer.remote.sync

interface RawRemoteRepository {
    interface Sender<Key, Value> {
        suspend fun send(key: Key, value: Value): Result<Value>
    }

    interface Receiver<Key, Value> {
        suspend fun get(key: Key): Result<Value>
    }

    interface SenderReceiver<Key, Value> : Sender<Key, Value>, Receiver<Key, Value>
}