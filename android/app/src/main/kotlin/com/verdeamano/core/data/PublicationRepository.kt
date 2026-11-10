package com.verdeamano.core.data

import com.verdeamano.contracts.*
import com.verdeamano.core.network.VerdeApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class PublicationRepository @Inject constructor(private val api:VerdeApi) {
    suspend fun categories()=api.allCategories()
    suspend fun category(id:String?,input:CategoryInput)=if(id==null)api.createCategory(input) else api.updateCategory(id,input)
    suspend fun managed(id:String,admin:Boolean=false)=if(admin)api.supervisedProduct(id) else api.ownedProduct(id)
    suspend fun supervision(q:String?,category:String?,seller:String?,state:PublicationState?,page:Int)=api.supervisedProducts(q,category,seller,state,page)
    suspend fun sellers()=api.userPage(1,50,role="SELLER").items
    suspend fun publication(p:Product,input:PublicationInput,admin:Boolean=false)=api.publication(if(admin)"admin" else "seller",p.id,input)
    suspend fun inventory(p:Product,input:InventoryAdjustment,admin:Boolean=false)=api.inventory(if(admin)"admin" else "seller",p.id,input)
    suspend fun history(id:String,admin:Boolean=false)=api.inventoryHistory(if(admin)"admin" else "seller",id)
    suspend fun upload(p:Product,file:File,request:String,replace:String?):Product {
        val part=MultipartBody.Part.createFormData("file","photo.jpg",file.asRequestBody("image/jpeg".toMediaType()))
        return if(replace==null)api.uploadPhoto(p.id,p.version,request,part) else api.replacePhoto(p.id,replace,p.version,request,part)
    }
    suspend fun remove(p:Product,image:String,request:String)=api.removePhoto(p.id,image,MutationInput(p.version,request))
    suspend fun order(p:Product,ids:List<String>,request:String)=api.orderPhotos(p.id,ImageOrderInput(ids,p.version,request))
}
