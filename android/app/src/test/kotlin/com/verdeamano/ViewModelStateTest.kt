package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.CommerceRepository
import com.verdeamano.feature.catalog.CatalogViewModel
import com.verdeamano.feature.cart.CartViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val product = Product("p1","s1","Vendedor","c1","sku1","Maceta","Maceta para balcón","Barro","20 x 20 cm",29900,10)
    @Before fun setupDispatcher() { Dispatchers.setMain(dispatcher) }
    @After fun restoreDispatcher() { Dispatchers.resetMain() }
    @Test fun `catalog exposes loading then loaded products`() = runTest(dispatcher) {
        val response = CompletableDeferred<ProductPage>()
        val api = object : TestApi() { override suspend fun products(query: String?, category: String?, page: Int, pageSize: Int) = response.await() }
        val viewModel = CatalogViewModel(CommerceRepository(api))
        runCurrent()
        assertTrue(viewModel.state.value.loading)
        response.complete(ProductPage(listOf(product),1,1,20))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.loading)
        assertEquals(product,viewModel.state.value.data?.products?.single())
        assertNull(viewModel.state.value.error)
    }
    @Test fun `catalog connection failure is distinct from an empty list and can retry`() = runTest(dispatcher) {
        var unavailable = true
        val api = object : TestApi() {
            override suspend fun products(query: String?, category: String?, page: Int, pageSize: Int): ProductPage {
                if (unavailable) throw IOException("Internal connection detail")
                return ProductPage(emptyList(),0,1,20)
            }
        }
        val viewModel = CatalogViewModel(CommerceRepository(api))
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.error!!.contains("Internal"))
        assertFalse(viewModel.state.value.loading)
        unavailable = false; viewModel.load(); advanceUntilIdle()
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.data!!.products.isEmpty())
    }
    @Test fun `overlapping catalog pages do not duplicate product cards`() = runTest(dispatcher) {
        val api = object : TestApi() { override suspend fun products(query: String?, category: String?, page: Int, pageSize: Int) = ProductPage(listOf(product),2,page,pageSize) }
        val viewModel = CatalogViewModel(CommerceRepository(api))
        advanceUntilIdle(); viewModel.load(next=true); advanceUntilIdle()
        assertEquals(listOf("p1"),viewModel.state.value.data!!.products.map { it.id })
    }
    @Test fun `latest search cancels previous request without a spurious error`() = runTest(dispatcher) {
        val api = object : TestApi() { override suspend fun products(query: String?, category: String?, page: Int, pageSize: Int) = ProductPage(if(query=="maceta") listOf(product) else emptyList(),if(query=="maceta") 1 else 0,page,pageSize) }
        val viewModel = CatalogViewModel(CommerceRepository(api))
        viewModel.filter(query="riego"); viewModel.filter(query="maceta"); advanceUntilIdle()
        assertNull(viewModel.state.value.error)
        assertEquals("p1",viewModel.state.value.data!!.products.single().id)
    }
    @Test fun `cart preserves loaded data after HTTP inventory conflict`() = runTest(dispatcher) {
        val cart = Cart(listOf(CartItem(product,2,59800)),59800)
        val api = object : TestApi() {
            override suspend fun cart() = cart
            override suspend fun quantity(id: String,input: QuantityInput): Cart = throw HttpException(Response.error<Cart>(409,"{\"code\":\"conflict\",\"message\":\"No hay existencias suficientes.\"}".toResponseBody()))
        }
        val viewModel = CartViewModel(CommerceRepository(api))
        advanceUntilIdle(); viewModel.quantity("p1",99); advanceUntilIdle()
        assertEquals(cart,viewModel.state.value.data)
        assertEquals("No hay existencias suficientes.",viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
    }
    @Test fun `cart blocks repeated mutations while a request is pending`() = runTest(dispatcher) {
        val response = CompletableDeferred<Cart>()
        var calls = 0
        val api = object : TestApi() {
            override suspend fun cart() = Cart()
            override suspend fun quantity(id: String,input: QuantityInput): Cart { calls++; return response.await() }
        }
        val viewModel = CartViewModel(CommerceRepository(api))
        advanceUntilIdle(); viewModel.quantity("p1",1); viewModel.quantity("p1",2); runCurrent()
        assertEquals(1,calls); assertTrue(viewModel.state.value.loading)
        response.complete(Cart(listOf(CartItem(product,1,29900)),29900)); advanceUntilIdle()
        assertEquals(29900L,viewModel.state.value.data?.subtotalCop)
    }
    @Test fun `malformed HTTP error response yields a safe message`() = runTest(dispatcher) {
        val api = object : TestApi() { override suspend fun cart(): Cart = throw HttpException(Response.error<Cart>(500,"<html>Technical stack trace</html>".toResponseBody())) }
        val viewModel = CartViewModel(CommerceRepository(api)); advanceUntilIdle()
        assertEquals("El servidor no pudo completar la operación.",viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
    }
}
