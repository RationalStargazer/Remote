package net.rationalstargazer.remote.sync

interface Repository<Key, Value> {
    
    /**
     * Provides a read access to a fixed snapshot of the underlying data to get multiple values with guaranteed consistency.
     * Depending on implementation it can be done by locking repository for the time of execution of the `block` of code
     * or by maintaining separated snapshots of the data.
     *
     * Do not keep the instance of Reader after the `block`.
     * Implementors are advised to return null values if tried to be used after the block.
     *
     * [read] is more convenient way to get a single value.
     *
     * ```
     * val repository: Repository<String, Value>
     * val xCoordinate: Value
     * val yCoordinate: Value
     *
     * repository.access { reader ->
     *     // all data that is read with the provided reader inside curly braces is consistent
     *     // xCoordinates and yCoordinates will get values that repository has at this moment
     *     // (the moment before the first line inside the braces)
     *     xCoordinate = reader.read("x")
     *
     *     // as read is suspend function the value of "y" in repository can be changed to this time by concurrent code
     *     // nevertheless yCoordinate will receive the value that was at the moment of the opening curly brace
     *     yCoordinate = reader.read("y")
     * }
     * ```
     */
    suspend fun access(block: suspend (Reader<Key, Value>) -> Unit)
    
    suspend fun read(key: Key): Value?
    
    // suspend fun read(keys: Set<Key>): Map<Key, Value>
    
    interface Writable<Key, Value> : Repository<Key, Value>, Writer<Key, Value> {
    
        /**
         * Write access to write multiple values at the same time.
         * Depending on implementation it can be done by locking repository for the time of execution of the `block` of code
         * or by maintaining separated snapshots of the data.
         *
         * Do not keep the instance of Writer after the `block`.
         * Implementors are advised to ignore write() calls and return null values if tried to be used after the block.
         *
         * [write] is more convenient way to write a single value.
         */
        suspend fun writeAccess(block: suspend (Writer<Key, Value>) -> Unit)
    }
    
    /**
     * Can read a data source. Some of Reader-s can have limited life time
     * ([Repository.access] usually provides Reader with the life time limited by access{} curly braces).
     * It is an error to use Reader outside of its life time.
     * Implementors are advised to return null values or throw IllegalStateException when called outside of the life time.
     */
    interface Reader<Key, Value> {
        suspend fun read(key: Key): Value?
    }
    
    /**
     * Can read from and write to a data source. Some of Writer-s can have limited life time
     * ([Writable.writeAccess] usually provides Writer with the life time limited by writeAccess{} curly braces).
     * It is an error to use Writer outside of its life time.
     * Implementors are advised to ignore the call or throw IllegalStateException when the write() is called outside of the life time.
     */
    interface Writer<Key, Value> : Reader<Key, Value> {
        suspend fun write(key: Key, value: Value)
    }
}