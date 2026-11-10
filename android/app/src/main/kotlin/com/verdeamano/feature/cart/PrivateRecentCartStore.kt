package com.verdeamano.feature.cart

import android.content.Context
import android.util.AtomicFile
import com.verdeamano.core.data.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class PrivateRecentCartStore @Inject constructor(@ApplicationContext private val context:Context):RecentCartStore {
    private fun file(uid:String):AtomicFile {
        require(uid.isNotBlank() && uid.length<=128)
        val key=MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString(""){"%02x".format(it)}
        val directory=File(context.noBackupFilesDir,"recent-cart").apply{check(exists()||mkdirs())}
        return AtomicFile(File(directory,"$key.json"))
    }
    @Synchronized override fun read(uid:String):RecentCartSnapshot?=try {
        val atomic=file(uid)
        if(atomic.baseFile.length()>2097152)null else atomic.openRead().use{RecentCartCodec.decode(it.readBytes().toString(Charsets.UTF_8)).takeIf{snapshot->snapshot.valid(System.currentTimeMillis())}}
    }catch(_:Exception){null} // Missing/corrupt cache never grants a session or changes server state.
    @Synchronized override fun save(uid:String,cart:com.verdeamano.contracts.Cart) {
        val snapshot=RecentCartSnapshot.from(cart,System.currentTimeMillis());check(snapshot.valid(System.currentTimeMillis()))
        val atomic=file(uid);val stream=atomic.startWrite()
        try{stream.write(RecentCartCodec.encode(snapshot).toByteArray());atomic.finishWrite(stream)}catch(failure:Exception){atomic.failWrite(stream);throw failure}
    }
    @Synchronized override fun clear(uid:String){file(uid).delete()}
}
@Module @InstallIn(SingletonComponent::class) abstract class RecentCartModule {
    @Binds @Singleton abstract fun recentCart(store:PrivateRecentCartStore):RecentCartStore
}
