package com.verdeamano.core.data

import com.verdeamano.contracts.Cart
import kotlinx.serialization.json.*

/** Only public product labels and the last server quantities/prices; never identity, address, role or tokens. */
data class RecentCartRow(val productId:String,val name:String,val sku:String,val quantity:Int,val unitPriceCop:Long,val subtotalCop:Long)
data class RecentCartSnapshot(val savedAtEpochMillis:Long,val rows:List<RecentCartRow>) {
    val subtotalCop get()=rows.fold(0L){total,row->Math.addExact(total,row.subtotalCop)}
    fun valid(now:Long)=savedAtEpochMillis in (now-48*60*60*1000L)..now && rows.size<=1000 && rows.all{
        it.productId.length in 1..128 && it.name.length in 1..120 && it.sku.length<=40 && it.quantity in 1..100 && it.unitPriceCop in 1..100000000 && it.subtotalCop in 0..10000000000L
    }
    companion object {fun from(cart:Cart,now:Long)=RecentCartSnapshot(now,cart.items.map{RecentCartRow(it.product.id,it.product.name,it.product.sku,it.quantity,it.product.priceCop,it.subtotalCop)})}
}
interface RecentCartStore {fun read(uid:String):RecentCartSnapshot?;fun save(uid:String,cart:Cart);fun clear(uid:String)}
object NoRecentCartStore:RecentCartStore {override fun read(uid:String):RecentCartSnapshot?=null;override fun save(uid:String,cart:Cart)=Unit;override fun clear(uid:String)=Unit}

/** This local-only format does not depend on a serialization plugin in the Android application. */
object RecentCartCodec {
    fun encode(value:RecentCartSnapshot)=buildJsonObject{
        put("version",1);put("savedAtEpochMillis",value.savedAtEpochMillis)
        put("rows",buildJsonArray{value.rows.forEach{row->add(buildJsonObject{put("productId",row.productId);put("name",row.name);put("sku",row.sku);put("quantity",row.quantity);put("unitPriceCop",row.unitPriceCop);put("subtotalCop",row.subtotalCop)})}})
    }.toString()
    fun decode(value:String):RecentCartSnapshot {
        val root=Json.parseToJsonElement(value).jsonObject
        require(root.getValue("version").jsonPrimitive.int==1)
        return RecentCartSnapshot(root.getValue("savedAtEpochMillis").jsonPrimitive.long,root.getValue("rows").jsonArray.map{
            val row=it.jsonObject;RecentCartRow(row.getValue("productId").jsonPrimitive.content,row.getValue("name").jsonPrimitive.content,row.getValue("sku").jsonPrimitive.content,row.getValue("quantity").jsonPrimitive.int,row.getValue("unitPriceCop").jsonPrimitive.long,row.getValue("subtotalCop").jsonPrimitive.long)
        })
    }
}
