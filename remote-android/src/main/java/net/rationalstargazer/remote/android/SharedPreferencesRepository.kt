package net.rationalstargazer.remote.android

import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.rationalstargazer.remote.sync.Repository

class SharedPreferencesSimpleRepository(sharedPreferences: SharedPreferences) : Repository.Writable<String, String> {
    
    override suspend fun access(block: suspend (Repository.Reader<String, String>) -> Unit) {
        mutex.withLock {
            val access = AccessImpl(prefs)
            block(access)
            access.close()
        }
    }
    
    override suspend fun writeAccess(block: suspend (Repository.Writer<String, String>) -> Unit) {
        mutex.withLock {
            val access = AccessImpl(prefs)
            block(access)
            access.close()
        }
    }
    
    override suspend fun read(key: String): String? {
        var value: String? = null
        
        access {
            value = it.read(key)
        }
        
        return value
    }
    
    override suspend fun write(key: String, value: String) {
        writeAccess {
            it.write(key, value)
        }
    }
    
    private val mutex = Mutex()
    private val prefs: SharedPreferences = sharedPreferences
    
    private class AccessImpl(
        private val prefs: SharedPreferences
    ) : Repository.Writer<String, String> {
        
        private val cache = mutableMapOf<String, String>()
        
        override suspend fun read(key: String): String? {
            if (closed) {
                throw IllegalStateException("attempt to read from closed Repository.Reader")
            }
            
            val s = cache[key]
            if (s != null) {
                return s
            }
            
            return prefs.getString(key, null)
        }
    
        override suspend fun write(key: String, value: String) {
            if (closed) {
                throw IllegalStateException("attempt to write to closed Repository.Writer")
            }
    
            cache[key] = value
            editor.putString(key, value)
        }
        
        fun close() {
            if (closed) {
                return
            }
            
            editor.apply()
        }
        
        private var closed: Boolean = false
        
        private val editor: SharedPreferences.Editor by lazy {
            prefs.edit()
        }
    }
}