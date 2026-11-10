package com.verdeamano.feature.seller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID

@HiltViewModel class SellerViewModel @Inject constructor(private val repository: CommerceRepository,private val publications:PublicationRepository) : ViewModel() {
    private val mutable = MutableStateFlow(UiState<List<Product>>())
    val state = mutable.asStateFlow()
    init { load() }
    fun load() = operate { repository.ownProducts() }
    private var retry:Pair<String,String>?=null
    fun publication(product:Product,state:PublicationState):Unit {
        if (mutable.value.loading) return
        val intent="${product.id}:${product.version}:$state"
        val request=retry?.takeIf{it.first==intent}?.second ?: UUID.randomUUID().toString().also{retry=intent to it}
        operate {
            val updated = publications.publication(product, PublicationInput(state, product.version, request))
            retry = null
            mutable.value.data.orEmpty().map { if (it.id == updated.id) updated else it }
        }
    }
    fun archive(product: Product) = publication(product,PublicationState.ARCHIVED)
    private fun operate(action: suspend () -> List<Product>) {
        if (mutable.value.loading) return
        val previous = mutable.value.data
        mutable.value = UiState(previous, loading = true)
        viewModelScope.launch {
            try { mutable.value = UiState(action()) }
            catch (error: Exception) { mutable.value = UiState(previous, error = error.userMessage()) }
        }
    }
}
data class EditorData(val categories: List<Category>, val product: Product? = null, val saved: Boolean = false)
@HiltViewModel class ProductEditorViewModel @Inject constructor(private val repository: CommerceRepository, handle: SavedStateHandle) : ViewModel() {
    private val id: String? = handle.get<String>("id")?.takeUnless { it == "new" }
    private val mutable = MutableStateFlow(UiState<EditorData>())
    private var retry:Pair<String,String>?=null
    val state = mutable.asStateFlow()
    init { load() }
    fun load() {
        mutable.value = UiState(loading = true)
        viewModelScope.launch {
            try {
                val categories = repository.categories()
                val product = id?.let { value -> repository.ownProducts().first { it.id == value } }
                mutable.value = UiState(EditorData(categories, product))
            } catch (error: Exception) { mutable.value = UiState(error = error.userMessage()) }
        }
    }
    fun save(input: ProductInput) {
        if (mutable.value.loading) return
        val previous = mutable.value.data ?: return
        Validation.product(input)?.let { mutable.value = UiState(previous, error = it); return }
        mutable.value = UiState(previous, loading = true)
        viewModelScope.launch {
            try {
                val intent=input.toString()+previous.product?.version
                val request=retry?.takeIf{it.first==intent}?.second ?: UUID.randomUUID().toString().also{retry=intent to it}
                repository.saveProduct(id, input.copy(version = previous.product?.version,requestId=request))
                retry=null
                mutable.value = UiState(previous.copy(saved = true))
            } catch (error: Exception) { mutable.value = UiState(previous, error = error.userMessage()) }
        }
    }
}
