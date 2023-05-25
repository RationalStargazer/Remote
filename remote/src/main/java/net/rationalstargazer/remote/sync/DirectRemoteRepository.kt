package net.rationalstargazer.remote.sync

import net.rationalstargazer.remote.RemoteData

interface DirectRemoteRepository {
    interface Sender<Key, Value, Result> {
        suspend fun send(key: Key, value: Value): RemoteData<Result>
    }

    interface Receiver<Key, Value> {
        suspend fun get(key: Key): RemoteData<Value>
    }

    interface SenderReceiver<Key, Value, SendResult> : Sender<Key, Value, SendResult>, Receiver<Key, Value>
}