package com.verdeamano.contracts

import kotlinx.serialization.Serializable

@Serializable enum class OrderStatus { PENDING, CONFIRMED, PREPARING, SHIPPED, DELIVERED, CANCELLED }
@Serializable data class DeliverySnapshot(val recipient:String,val phone:String,val department:String,val city:String,val line:String,
    val neighborhood:String="",val complement:String="",val instructions:String="")
@Serializable data class CheckoutLine(val item:OrderLine,val productVersion:Int)
@Serializable data class CheckoutPreviewInput(val addressId:String)
@Serializable data class CheckoutQuote(val id:String,val addressId:String,val delivery:DeliverySnapshot,val items:List<CheckoutLine>,
    val subtotalCop:Long,val shippingCop:Long,val taxCop:Long,val totalCop:Long,val currency:String="COP",val expiresAt:String,
    val policy:String="DEVELOPMENT_NO_CHARGE")
@Serializable data class OrderConfirmation(val quoteId:String,val requestId:String,val acceptedTerms:Boolean)
@Serializable data class PendingOrderConfirmation(val quote:CheckoutQuote,val input:OrderConfirmation)
@Serializable data class PendingPayment(val reference:String,val amountCop:Long,val expiresAt:String,
    val status:String="PENDING",val mode:String="SIMULATION_NO_CHARGE")
@Serializable data class OrderFulfillment(val sellerId:String,val sellerName:String,val status:OrderStatus,val version:Int,val tracking:String="")
@Serializable data class OrderEvent(val id:String,val action:String,val status:OrderStatus,val sellerId:String?,val note:String,val createdAt:String)
@Serializable data class Order(val id:String,val buyerId:String,val addressId:String,val items:List<OrderLine>,val totalCop:Long,val status:OrderStatus,
    val delivery:DeliverySnapshot?,val subtotalCop:Long,val shippingCop:Long,val taxCop:Long,val currency:String,val version:Int,
    val createdAt:String,val updatedAt:String,val fulfillments:List<OrderFulfillment>,val history:List<OrderEvent>,val payment:PendingPayment?,val canFulfill:Boolean=true)
@Serializable data class OrderSummary(val id:String,val status:OrderStatus,val totalCop:Long,val createdAt:String,val updatedAt:String,val itemCount:Int)
@Serializable data class OrderPage(val items:List<OrderSummary>,val total:Int,val page:Int,val pageSize:Int)
@Serializable data class OrderTransitionInput(val status:OrderStatus,val version:Int,val requestId:String,val note:String="",val tracking:String="")
@Serializable data class OrderCancellation(val version:Int,val requestId:String,val reason:String)

object OrderRules {
    fun total(lines:List<OrderLine>):Long {
        require(lines.isNotEmpty() && lines.size<=100) { "El carrito debe contener de 1 a 100 productos." }
        return lines.fold(0L){sum,line ->
            require(line.quantity in 1..100 && line.unitPriceCop in 1..100_000_000L){"Cantidad o precio inválido."}
            Math.addExact(sum,Math.multiplyExact(line.unitPriceCop,line.quantity.toLong()))
        }
    }
    fun canAdvance(from:OrderStatus,to:OrderStatus)=when(from){
        OrderStatus.PENDING -> to==OrderStatus.CONFIRMED
        OrderStatus.CONFIRMED -> to==OrderStatus.PREPARING
        OrderStatus.PREPARING -> to==OrderStatus.SHIPPED
        OrderStatus.SHIPPED -> to==OrderStatus.DELIVERED
        else -> false
    }
    fun canCancel(states:List<OrderStatus>)=states.isNotEmpty() && states.all{it in listOf(OrderStatus.PENDING,OrderStatus.CONFIRMED,OrderStatus.PREPARING)}
    fun aggregate(states:List<OrderStatus>):OrderStatus {
        require(states.isNotEmpty())
        if(states.all{it==OrderStatus.CANCELLED})return OrderStatus.CANCELLED
        require(states.none{it==OrderStatus.CANCELLED}) { "No se admiten cancelaciones parciales." }
        return states.minBy{it.ordinal}
    }
}
