package com.verdeamano.feature.commercial

import android.content.Context
import com.verdeamano.core.data.DeviceReceiptStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Contains only SDK-observed event IDs for one UID; never infers receipts from the inbox or legacy keys. */
@Singleton class AndroidDeviceReceiptStore @Inject constructor(@ApplicationContext context:Context):DeviceReceiptStore {
    private val preferences=context.getSharedPreferences("phase6b-device-receipts",Context.MODE_PRIVATE)
    private fun key(uid:String,id:String)="$uid:$id"
    override fun pending(uid:String)=preferences.all.filter{it.key.startsWith("$uid:")&&it.value==true}.keys.map{it.removePrefix("$uid:")}
    override fun record(uid:String,id:String){check(preferences.edit().putBoolean(key(uid,id),true).commit())}
    override fun acknowledged(uid:String,id:String){check(preferences.edit().remove(key(uid,id)).commit())}
}
@Module @InstallIn(SingletonComponent::class) abstract class DeviceReceiptModule {
    @Binds abstract fun receipts(store:AndroidDeviceReceiptStore):DeviceReceiptStore
}
