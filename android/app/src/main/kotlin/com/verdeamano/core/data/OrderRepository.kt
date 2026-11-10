package com.verdeamano.core.data

import com.verdeamano.contracts.*
import com.verdeamano.core.network.VerdeApi
import com.verdeamano.core.session.SessionStore
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface OrderPendingStore {
    fun read(uid:String):PendingOrderConfirmation?
    fun save(uid:String,value:PendingOrderConfirmation)
    fun clear(uid:String)
}
@Singleton class OrderRepository @Inject constructor(private val api:VerdeApi,private val session:SessionStore,private val pending:OrderPendingStore) {
    private fun uid()=session.firebaseUid?:throw IllegalStateException("No active identity")
    private suspend fun <T> scoped(action:suspend()->T):T {val identity=uid();val version=session.version;val value=action();if(identity!=session.firebaseUid||version!=session.version)throw CancellationException("Account changed");return value}
    fun pending()=pending.read(uid())
    suspend fun preview(address:String)=scoped{api.checkout(CheckoutPreviewInput(address))}
    suspend fun addresses()=scoped{api.addresses()}
    @Synchronized fun stage(quote:CheckoutQuote):PendingOrderConfirmation {
        val identity=uid();val existing=pending.read(identity)
        if(existing!=null){check(existing.quote.id==quote.id){"Resolve the previous confirmation first"};return existing}
        return PendingOrderConfirmation(quote,OrderConfirmation(quote.id,UUID.randomUUID().toString(),true)).also{pending.save(identity,it)}
    }
    suspend fun confirm():Order {
        val identity=uid();val input=requireNotNull(pending.read(identity)).input
        return scoped{api.confirmOrder(input)}.also{pending.clear(identity)}
    }
    fun discardRejected(){pending.clear(uid())}
    suspend fun list(page:Int,status:OrderStatus?)=scoped{api.orders(page=page,status=status)}
    suspend fun detail(id:String)=scoped{api.order(id)}
    suspend fun cancel(id:String,input:OrderCancellation)=scoped{api.cancelOrder(id,input)}
    suspend fun transition(id:String,seller:String,input:OrderTransitionInput)=scoped{api.transitionOrder(id,seller,input)}
}
