package com.verdeamano.core.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.verdeamano.BuildConfig
import com.verdeamano.contracts.PublicationState
import com.verdeamano.contracts.Product
import com.verdeamano.R
import java.text.NumberFormat
import java.util.Locale

fun cop(value: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).apply { maximumFractionDigits = 0 }.format(value)
@Composable fun BrandHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun LoadingState() { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
@Composable fun EmptyState(message: String) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotanicalIllustration(BotanicalMotif.SEED, Modifier.size(52.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun ErrorState(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = retry) { Text("Reintentar") }
    }
}
@Composable fun ProductImage(product: Product, modifier: Modifier = Modifier) {
    val imageUrl=product.imageUrl
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
        if (imageUrl != null) AsyncImage(model = productPhotoModel(imageUrl), contentDescription = product.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else {
            val illustration = when(product.categoryId.takeLast(1)) {
                "2" -> R.drawable.product_watering
                "3" -> R.drawable.product_tools
                else -> R.drawable.product_pot
            }
            Image(painterResource(illustration), "Ilustración de ${product.name}", Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
        }
    }
}
@Composable fun productPhotoModel(url:String):ImageRequest=ImageRequest.Builder(LocalContext.current)
    .data(if(url.startsWith("api/media/"))BuildConfig.API_BASE_URL+url else url)
    // Private draft pixels must not survive an account switch in shared memory/disk caches.
    .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
fun PublicationState.label():String=when(this){PublicationState.DRAFT->"Borrador";PublicationState.PENDING->"Pendiente de revisión";PublicationState.APPROVED->"Aprobado";PublicationState.REJECTED->"Rechazado";PublicationState.SUSPENDED->"Suspendido";PublicationState.ARCHIVED->"Archivado"}
@Composable fun ProductCard(product: Product, action: String = "Ver producto", onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        BoxWithConstraints {
            if (maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.3f) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProductImage(product, Modifier.fillMaxWidth().height(150.dp))
                    ProductCardInfo(product, action)
                }
            } else {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductImage(product, Modifier.size(96.dp))
                    Column(Modifier.weight(1f)) { ProductCardInfo(product, action) }
                }
            }
        }
    }
}

@Composable private fun ProductCardInfo(product: Product, action: String) {
    val available = product.active && product.publicationState == PublicationState.APPROVED && product.stock > 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(product.name, style = MaterialTheme.typography.titleMedium)
        Text(cop(product.priceCop), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        VerdeStatusChip(if(product.active && product.publicationState == PublicationState.APPROVED) if(product.stock == 0) "Agotado" else "${product.stock} disponibles" else product.publicationState.label(), available)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable fun VerdeStatusChip(label: String, positive: Boolean) {
    Surface(color = if(positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = if(positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
