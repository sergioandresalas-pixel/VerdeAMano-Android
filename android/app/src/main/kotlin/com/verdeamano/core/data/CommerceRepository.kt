package com.verdeamano.core.data

import com.verdeamano.contracts.*
import com.verdeamano.core.network.VerdeApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class CommerceRepository @Inject constructor(private val api: VerdeApi,private val session:com.verdeamano.core.session.SessionStore=com.verdeamano.core.session.SessionStore(),private val recent:RecentCartStore=NoRecentCartStore) {
    private suspend fun cartOperation(action:suspend()->Cart):Cart {
        val uid=session.firebaseUid;val version=session.version;val result=action()
        if(uid!=session.firebaseUid||version!=session.version)throw kotlinx.coroutines.CancellationException("Account changed")
        if(uid!=null)try{recent.save(uid,result)}catch(_:Exception){ /* Successful server response remains authoritative if local storage is unavailable. */ }
        return result
    }
    fun recentCart()=session.firebaseUid?.let(recent::read)
    suspend fun me() = api.me()
    suspend fun categories() = api.categories()
    suspend fun products(query: String?, category: String?, page: Int) = api.products(query, category, page)
    suspend fun catalog(query:String?,category:String?,page:Int,minimum:Long?,maximum:Long?,available:Boolean?,sort:CatalogSort,featured:Boolean?)=
        if(minimum==null&&maximum==null&&available==null&&sort==CatalogSort.NAME&&featured==null)api.products(query,category,page)
        else api.catalog(query,category,page,minimum=minimum,maximum=maximum,available=available,sort=sort,featured=featured)
    suspend fun product(id: String) = api.product(id)
    suspend fun cart() = cartOperation{api.cart()}
    suspend fun quantity(id: String, quantity: Int) = cartOperation{if (quantity == 0) api.remove(id) else api.quantity(id, QuantityInput(quantity))}
    suspend fun clearCart() = cartOperation{api.clearCart()}
    suspend fun ownProducts() = api.ownProducts()
    suspend fun saveProduct(id: String?, input: ProductInput) = if (id == null) api.createProduct(input) else api.updateProduct(id, input)
    suspend fun archive(product: Product) = api.archive(product.id, product.version)
    suspend fun users() = api.users()
    suspend fun audits() = api.audits()
}
