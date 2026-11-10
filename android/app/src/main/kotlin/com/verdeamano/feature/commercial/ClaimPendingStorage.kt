package com.verdeamano.feature.commercial

import android.content.Context
import com.verdeamano.contracts.ClaimInput
import com.verdeamano.core.data.ClaimPendingStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class AndroidClaimPendingStore @Inject constructor(@ApplicationContext context:Context):ClaimPendingStore {
    private val preferences=context.getSharedPreferences("claim-confirmations",Context.MODE_PRIVATE)
    override fun read(uid:String)=preferences.getString(uid,null)?.let{Json.decodeFromString<ClaimInput>(it)}
    override fun save(uid:String,input:ClaimInput){if(!preferences.edit().putString(uid,Json.encodeToString(input)).commit())throw IOException("Claim request persistence failed")}
    override fun clear(uid:String){if(!preferences.edit().remove(uid).commit())throw IOException("Claim request cleanup failed")}
}
@Module @InstallIn(SingletonComponent::class) abstract class CommercialStorageModule {
    @Binds abstract fun pending(implementation:AndroidClaimPendingStore):ClaimPendingStore
}
