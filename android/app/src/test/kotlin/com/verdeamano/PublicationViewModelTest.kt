package com.verdeamano

import androidx.lifecycle.SavedStateHandle
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.feature.catalog.CatalogViewModel
import com.verdeamano.feature.catalog.DetailViewModel
import com.verdeamano.feature.seller.*
import com.verdeamano.feature.admin.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PublicationViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val p=Product("p","seller","Vendedor QA","c","QA","Maceta QA","Descripción QA válida","Barro","20 cm",10000,5,publicationState=PublicationState.DRAFT)
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){Dispatchers.resetMain()}
    private fun failure(status:Int,message:String)=HttpException(Response.error<Product>(status,"""{"code":"invalid_input","message":"$message"}""".toResponseBody()))
    @Test fun archiveKeepsProductOnRejectionAndUsesConfirmedServerVersion() = runTest(dispatcher) {
        var reject = true
        val archived = p.copy(active = false, publicationState = PublicationState.ARCHIVED, version = 2)
        val requests = mutableListOf<String>()
        val api = object : TestApi() {
            override suspend fun ownProducts() = listOf(p)
            override suspend fun publication(scope: String, id: String, input: PublicationInput): Product {
                assertEquals("seller", scope)
                assertEquals(p.id, id)
                assertEquals(PublicationState.ARCHIVED, input.state)
                requests += input.requestId
                if (reject) throw failure(403, "Validación de solo lectura.")
                return archived
            }
        }
        val model = SellerViewModel(CommerceRepository(api), PublicationRepository(api))
        advanceUntilIdle()
        model.archive(p)
        advanceUntilIdle()
        assertEquals(listOf(p), model.state.value.data)
        assertEquals("Validación de solo lectura.", model.state.value.error)
        reject = false
        model.archive(p)
        advanceUntilIdle()
        assertEquals(listOf(archived), model.state.value.data)
        assertNull(model.state.value.error)
        assertEquals(1, requests.distinct().size)
    }

    @Test fun categoryLookupDoesNotHideAnAvailableProduct() = runTest(dispatcher) {
        var categoriesAvailable = true
        val api = object : TestApi() {
            override suspend fun product(id: String) = p
            override suspend fun categories(): List<Category> {
                if (!categoriesAvailable) throw IOException("offline")
                return listOf(Category(p.categoryId, "Macetas"))
            }
        }
        val model = DetailViewModel(CommerceRepository(api), SavedStateHandle(mapOf("id" to p.id)))
        advanceUntilIdle()
        assertEquals("Macetas", model.category.value)
        categoriesAvailable = false
        model.load()
        advanceUntilIdle()
        assertNull(model.category.value)
        assertEquals(p, model.state.value.data)
        assertNull(model.state.value.error)
    }
    @Test fun invalidPriceRangePreservesLoadedCatalogWithoutCallingFilterEndpoint()=runTest(dispatcher){
        var extraCalls=0
        val api=object:TestApi(){override suspend fun products(query:String?,category:String?,page:Int,pageSize:Int)=ProductPage(listOf(p),1,page,pageSize)
            override suspend fun catalog(q:String?,category:String?,page:Int,size:Int,minimum:Long?,maximum:Long?,available:Boolean?,sort:CatalogSort,featured:Boolean?):ProductPage{extraCalls++;error("unexpected")}}
        val vm=CatalogViewModel(CommerceRepository(api));advanceUntilIdle();vm.filters("20000","10000",false,CatalogSort.NAME);advanceUntilIdle()
        assertEquals(0,extraCalls);assertEquals(p,vm.state.value.data!!.products.single());assertTrue(vm.state.value.error!!.contains("rango"))
    }
    @Test fun priceAvailabilityAndSortReachApiAndPaginationKeepsFilter()=runTest(dispatcher){
        val pages=mutableListOf<Int>()
        val api=object:TestApi(){override suspend fun products(query:String?,category:String?,page:Int,pageSize:Int)=ProductPage(emptyList(),0,page,pageSize)
            override suspend fun catalog(q:String?,category:String?,page:Int,size:Int,minimum:Long?,maximum:Long?,available:Boolean?,sort:CatalogSort,featured:Boolean?):ProductPage{
                assertEquals(9000L,minimum);assertEquals(12000L,maximum);assertEquals(true,available);assertEquals(CatalogSort.PRICE_DESC,sort);pages+=page
                return ProductPage(listOf(p),2,page,size)
            }}
        val vm=CatalogViewModel(CommerceRepository(api));advanceUntilIdle();vm.filters("9000","12000",true,CatalogSort.PRICE_DESC);advanceUntilIdle();vm.load(next=true);advanceUntilIdle()
        assertEquals(listOf(1,2),pages);assertEquals(1,vm.state.value.data!!.products.size)
    }
    @Test fun uncertainPhotoUploadRetainsProductAndReusesSameRequestOnRetry()=runTest(dispatcher){
        val requests=mutableListOf<String>();var saved=false;var server=p
        val api=object:TestApi(){override suspend fun ownedProduct(id:String)=server
            override suspend fun uploadPhoto(id:String,version:Int,request:String,file:MultipartBody.Part):Product{requests+=request;assertEquals(1,version);server=p.copy(version=2);if(requests.size==1)throw IOException("timeout");return server}}
        val vm=ProductPhotosViewModel(PublicationRepository(api),SavedStateHandle(mapOf("id" to p.id)));advanceUntilIdle()
        vm.upload(File("preview.jpg"),null){saved=true};advanceUntilIdle();assertEquals(p,vm.state.value.data);assertFalse(saved);assertNotNull(vm.state.value.error);vm.load();advanceUntilIdle();assertEquals(2,vm.state.value.data!!.version)
        vm.upload(File("preview.jpg"),null){saved=true};advanceUntilIdle();assertEquals(2,requests.size);assertEquals(requests[0],requests[1]);assertTrue(saved);assertEquals(2,vm.state.value.data!!.version)
    }
    @Test fun uncertainStockWriteThenRefreshRetriesOriginalVersionWithoutApplyingDeltaTwice()=runTest(dispatcher){
        var server=p;val requests=mutableListOf<String>()
        val api=object:TestApi(){override suspend fun ownedProduct(id:String)=server;override suspend fun inventoryHistory(scope:String,id:String)=emptyList<InventoryRecord>()
            override suspend fun inventory(scope:String,id:String,input:InventoryAdjustment):Product{requests+=input.requestId;assertEquals(1,input.version);server=p.copy(stock=7,version=2);if(requests.size==1)throw IOException("uncertain commit");return server}}
        val vm=InventoryViewModel(PublicationRepository(api),SavedStateHandle(mapOf("id" to p.id,"scope" to "seller")));advanceUntilIdle()
        vm.adjust("2","Entrada QA verificada");advanceUntilIdle();assertNotNull(vm.state.value.error);vm.load();advanceUntilIdle();assertEquals(7,vm.state.value.data!!.product.stock)
        vm.adjust("2","Entrada QA verificada");advanceUntilIdle();assertEquals(requests[0],requests[1]);assertEquals(7,vm.state.value.data!!.product.stock)
    }
    @Test fun inventoryValidationErrorKeepsServerStockAndReadableMessage()=runTest(dispatcher){
        val api=object:TestApi(){override suspend fun ownedProduct(id:String)=p;override suspend fun inventoryHistory(scope:String,id:String)=emptyList<InventoryRecord>()
            override suspend fun inventory(scope:String,id:String,input:InventoryAdjustment):Product=throw failure(400,"El ajuste dejaría existencias inválidas.")}
        val vm=InventoryViewModel(PublicationRepository(api),SavedStateHandle(mapOf("id" to p.id,"scope" to "seller")));advanceUntilIdle();vm.adjust("-6","Salida QA inválida");advanceUntilIdle()
        assertEquals(5,vm.state.value.data!!.product.stock);assertEquals("El ajuste dejaría existencias inválidas.",vm.state.value.error);assertFalse(vm.state.value.loading)
    }
    @Test fun duplicateCategoryErrorPreservesListAndAllowsCorrection()=runTest(dispatcher){
        val c=Category("c","Macetas QA")
        val api=object:TestApi(){override suspend fun allCategories()=listOf(c);override suspend fun createCategory(input:CategoryInput):Category=throw failure(409,"El nombre ya existe.")}
        val vm=CategoryManagementViewModel(PublicationRepository(api));advanceUntilIdle();vm.save(null,c.name,true,"0");advanceUntilIdle()
        assertEquals(listOf(c),vm.state.value.data);assertEquals("El nombre ya existe.",vm.state.value.error)
    }
    @Test fun forbiddenSupervisionDoesNotDisplayPrivateData()=runTest(dispatcher){
        val api=object:TestApi(){override suspend fun supervisedProducts(q:String?,category:String?,seller:String?,state:PublicationState?,page:Int,size:Int):ProductPage=throw failure(403,"No tienes permiso.")}
        val vm=ProductSupervisionViewModel(PublicationRepository(api));advanceUntilIdle();assertNull(vm.state.value.data);assertEquals("No tienes permiso.",vm.state.value.error);assertFalse(vm.state.value.loading)
    }
}
