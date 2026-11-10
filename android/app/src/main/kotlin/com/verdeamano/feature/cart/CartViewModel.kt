package com.verdeamano.feature.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdeamano.contracts.Cart
import com.verdeamano.core.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class CartViewModel @Inject constructor(private val repository: CommerceRepository) : ViewModel() {
    private val mutable = MutableStateFlow(UiState<Cart>())
    val state = mutable.asStateFlow()
    private val cached=MutableStateFlow<RecentCartSnapshot?>(null)
    val recent=cached.asStateFlow()
    init { load() }
    fun load() = operate { repository.cart() }
    fun quantity(id: String, quantity: Int) {if(cached.value==null)operate { repository.quantity(id, quantity) }}
    fun clear() {if(cached.value==null)operate { repository.clearCart() }}
    private fun operate(action: suspend () -> Cart) {
        if (mutable.value.loading) return
        val previous = mutable.value.data
        mutable.value = UiState(previous, loading = true)
        viewModelScope.launch {
            try { mutable.value = UiState(action());cached.value=null }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) {
                val connectionFailure=error is java.io.IOException || (error is retrofit2.HttpException && error.code()>=500)
                cached.value=if(connectionFailure)repository.recentCart() else null
                mutable.value = UiState(previous, error = error.userMessage())
            }
        }
    }
}
