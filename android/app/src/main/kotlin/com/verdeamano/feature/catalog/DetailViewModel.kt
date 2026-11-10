package com.verdeamano.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdeamano.contracts.Product
import com.verdeamano.core.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class DetailViewModel @Inject constructor(private val repository: CommerceRepository, handle: SavedStateHandle) : ViewModel() {
    private val id: String = requireNotNull(handle["id"])
    private val mutable = MutableStateFlow(UiState<Product>())
    val state = mutable.asStateFlow()
    private val categoryMutable = MutableStateFlow<String?>(null)
    val category = categoryMutable.asStateFlow()
    private val actionMutable = MutableStateFlow(UiState<String>())
    val action = actionMutable.asStateFlow()
    init { load() }
    fun load() {
        if (mutable.value.loading) return
        categoryMutable.value = null
        mutable.value = UiState(loading = true)
        viewModelScope.launch {
            val product = try {
                repository.product(id)
            } catch (error: Exception) {
                mutable.value = UiState(error = error.userMessage())
                return@launch
            }
            mutable.value = UiState(product)
            categoryMutable.value = try {
                repository.categories().firstOrNull { it.id == product.categoryId }?.name
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null // Category metadata is optional; the product stays available.
            }
        }
    }
    fun add() {
        if (actionMutable.value.loading) return
        actionMutable.value = UiState(loading = true)
        viewModelScope.launch {
            try {
                val quantity = repository.cart().items.find { it.product.id == id }?.quantity ?: 0
                repository.quantity(id, quantity + 1)
                actionMutable.value = UiState("Producto agregado al carrito")
            } catch (error: Exception) { actionMutable.value = UiState(error = error.userMessage()) }
        }
    }
}
