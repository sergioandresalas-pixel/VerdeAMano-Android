package com.verdeamano.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogData(val products: List<Product> = emptyList(), val categories: List<Category> = emptyList(), val total: Int = 0, val page: Int = 1)
@HiltViewModel class CatalogViewModel @Inject constructor(private val repository: CommerceRepository) : ViewModel() {
    private val mutable = MutableStateFlow(UiState<CatalogData>())
    val state = mutable.asStateFlow()
    var query = MutableStateFlow(""); private set
    var category = MutableStateFlow<String?>(null); private set
    private var request: Job? = null
    var minimum:Long?=null;private set
    var maximum:Long?=null;private set
    var available:Boolean?=null;private set
    var sort:CatalogSort=CatalogSort.NAME;private set
    private var featured:Boolean?=null
    init { load() }
    fun home(value:Boolean){val filter=if(value)true else null;if(featured!=filter){featured=filter;load()}}
    fun filters(minimum:String,maximum:String,available:Boolean,sort:CatalogSort){
        val min=minimum.takeIf{it.isNotBlank()}?.toLongOrNull();val max=maximum.takeIf{it.isNotBlank()}?.toLongOrNull()
        if((minimum.isNotBlank()&&min==null)||(maximum.isNotBlank()&&max==null)||(min!=null&&min<0)||(max!=null&&max<0)||(min!=null&&max!=null&&min>max)){
            mutable.value=UiState(mutable.value.data,error="Indica un rango de precios COP válido.");return
        }
        this.minimum=min;this.maximum=max;this.available=if(available)true else null;this.sort=sort;load()
    }
    fun filter(query: String = this.query.value, category: String? = this.category.value) {
        this.query.value = query; this.category.value = category
        load(debounce = true)
    }
    fun load(next: Boolean = false, debounce: Boolean = false) {
        request?.cancel()
        val previous = mutable.value.data
        mutable.value = UiState(data = previous, loading = true)
        request = viewModelScope.launch {
            try {
                if (debounce) delay(300)
                val categories = previous?.categories ?: repository.categories()
                val page = if (next) (previous?.page ?: 0) + 1 else 1
                val result = repository.catalog(query.value, category.value, page,minimum,maximum,available,sort,featured)
                val products = (if (next) previous?.products.orEmpty() + result.items else result.items).distinctBy { it.id }
                mutable.value = UiState(CatalogData(products, categories, result.total, page))
            } catch (error: Exception) { mutable.value = UiState(previous, error = error.userMessage()) }
        }
    }
}
