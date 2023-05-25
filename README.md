Experimental effort to make the work with remote data almost as simple as if it was just local cache.

Example:
val repository: WritableRemoteComplexDataSource = injectRepository()

// loads value to local cache if it wasn't loaded since the start of the application 
repository.sync(key, RemoteSyncTarget.Once)

// reads value from local cache
val value = repository.read(key)

// writes value to local cache, then enqueue remote call to send the value to server
// in simple case the second argument can just be the next value
// in complex logic it can have additional data and do additional checks (completely customizable) to properly send the data to the server
repository.write(key, ExampleWriteCommand(key, nextValue))

repository.state.listen(lifecycle) { networkingState ->
    // here we can handle any of repository's changes
    // that were made as the result of sync(), write() or other similar calls
}

Example: [ExampleViewModel.kt](app/src/main/java/net/rationalstargazer/networking/ExampleModel.kt)

Implementation for simple data: [WritableRemoteComplexDataSource.kt](remote/src/main/java/example/WritableRemoteComplexDataSource.kt)

Basic customizable implementation: [WritableRemoteComplexDataSourceImpl.kt](remote/src/main/java/net/rationalstargazer/remote/sync/WritableRemoteComplexDataSourceImpl.kt)

API:
[Repository](remote/src/main/java/net/rationalstargazer/remote/sync/BaseRemoteComplexDataSource.kt) and [data classes](remote/src/main/java/net/rationalstargazer/remote/sync/RemoteQueueHandler.kt)

https://github.com/RationalStargazer/events
