Experimental effort to make the work with remote data almost as simple as if it was just local cache.

Example:
```
val repository: WritableRemoteComplexDataSource = injectRepository()

// loads value to local cache if it wasn't loaded since the start of the application 
repository.sync(key, RemoteSyncTarget.Once)

// reads value from local cache
val value = repository.read(key)

// writes value to local cache, then enqueue remote call to send the value to server
// in simple case the second argument can just be the next value
// in complex logic it can have completely customazilbe logic with additional data and checks 
// to properly send the data to the server
repository.write(key, ExampleWriteCommand(key, nextValue))

repository.state.listen(lifecycle) { networkingState ->
    // here we can handle any of repository's changes
    // that were made as the result of sync(), write() or other similar calls
    
    // networkingState can be implemented to keep information about last network errors
    // to provide feedback to a user and implement smart retry logic 
    // that will not overwhelm server with infinite retry attempts if something goes wrong
}
```

Implementation for simple data: [WritableSimpleRemoteDataSource.kt](remote/src/main/java/example/WritableSimpleRemoteDataSource.kt)

Basic customizable implementation: [WritableRemoteComplexDataSourceImpl.kt](remote/src/main/java/net/rationalstargazer/remote/sync/WritableRemoteComplexDataSourceImpl.kt)

API: [repository](remote/src/main/java/net/rationalstargazer/remote/sync/BaseRemoteComplexDataSource.kt), [data classes](remote/src/main/java/net/rationalstargazer/remote/sync/RemoteQueueHandler.kt)

Based on my [Events and Observable Values](https://github.com/RationalStargazer/events)
