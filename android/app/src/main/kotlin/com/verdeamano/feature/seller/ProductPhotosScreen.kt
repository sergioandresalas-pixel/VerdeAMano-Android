package com.verdeamano.feature.seller

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.verdeamano.contracts.ProductPhoto
import com.verdeamano.core.design.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun ProductPhotosScreen(viewModel:ProductPhotosViewModel){
    val state by viewModel.state.collectAsStateWithLifecycle();val message by viewModel.message.collectAsStateWithLifecycle()
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var previewPath by rememberSaveable{mutableStateOf<String?>(null)};var capturePath by rememberSaveable{mutableStateOf<String?>(null)}
    var replacement by rememberSaveable{mutableStateOf<String?>(null)};var preparing by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var removing by remember{mutableStateOf<ProductPhoto?>(null)}
    fun prepare(uri:android.net.Uri){preparing=true;error=null;scope.launch{
        try{val file=withContext(Dispatchers.IO){PhotoPreparation.prepare(context,uri)};previewPath?.let{File(it).delete()};previewPath=file.absolutePath}
        catch(e:Exception){error=e.message?:"No pudimos preparar la fotografía."}
        finally{capturePath?.let{File(it).delete()};capturePath=null;preparing=false}
    }}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
        val path=capturePath
        if(success&&path!=null)prepare(FileProvider.getUriForFile(context,"${context.packageName}.photos",File(path)))
        else{capturePath?.let{File(it).delete()};capturePath=null;error="Captura cancelada."}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null)prepare(uri) else error="Selección cancelada."}
    fun capture(){try{val file=PhotoPreparation.createCapture(context);capturePath=file.absolutePath;camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.photos",file))}catch(_:Exception){capturePath?.let{File(it).delete()};capturePath=null;error="La cámara no está disponible o no permite la captura."}}
    LaunchedEffect(Unit){withContext(Dispatchers.IO){PhotoPreparation.cleanExpired(context)}}
    DisposableEffect(Unit){onDispose{previewPath?.let{File(it).delete()};capturePath?.let{File(it).delete()}}}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item(key="header"){BrandHeader("Fotografías","JPEG o PNG · hasta 8 MB · máximo 6 fotos")}
        if(state.loading||preparing)item(key="loading"){LoadingState()};state.error?.let{item(key="server-error"){ErrorState(it,viewModel::load)}};error?.let{item(key="image-error"){Text(it,color=MaterialTheme.colorScheme.error)}};message?.let{item(key="notice"){Text(it)}}
        state.data?.let{p->
            item(key="product"){Text(p.name);Text("Las fotografías nuevas o modificadas requieren revisión de la publicación.")}
            if(replacement!=null)item(key="replace-selection"){Text("Has seleccionado una fotografía para sustituirla.");TextButton({replacement=null;error=null},enabled=!state.loading&&!preparing){Text("Cancelar sustitución")}}
            val enabled=!state.loading&&!preparing&&p.active&&p.publicationState!=com.verdeamano.contracts.PublicationState.SUSPENDED
            item(key="pick-actions"){Button({capture()},enabled=enabled&&(p.images.size<6||replacement!=null)){Text("Tomar fotografía")};OutlinedButton({try{picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}catch(_:Exception){error="No está disponible el selector de fotografías."}},enabled=enabled&&(p.images.size<6||replacement!=null)){Text("Elegir de galería")}}
            previewPath?.let{path->item(key="preview"){
                AsyncImage(File(path),"Previsualización de fotografía",Modifier.fillMaxWidth().height(240.dp),contentScale=ContentScale.Fit)
                Text(if(replacement==null)"Nueva fotografía" else "Sustituirá la fotografía seleccionada")
                Button({viewModel.upload(File(path),replacement){File(path).delete();previewPath=null;replacement=null}},enabled=enabled){Text("Confirmar y subir fotografía")}
                OutlinedButton({File(path).delete();previewPath=null;capture()},enabled=enabled){Text("Repetir fotografía")}
                TextButton({File(path).delete();previewPath=null;replacement=null},enabled=enabled){Text("Cancelar selección")}
            }}
            if(p.images.isEmpty())item(key="empty"){EmptyState("Este producto todavía no tiene fotografías.")}
            itemsIndexed(p.images,key={_,photo->photo.id}){index,photo->OutlinedCard(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    AsyncImage(productPhotoModel(photo.url),"Fotografía ${index+1}",Modifier.fillMaxWidth().height(180.dp),contentScale=ContentScale.Fit)
                    Text(if(photo.primary)"Imagen principal" else "Fotografía ${index+1}")
                    Row{if(index>0)TextButton({val ids=p.images.map{it.id}.toMutableList();ids.removeAt(index);ids.add(index-1,photo.id);viewModel.order(ids)},enabled=enabled){Text("Subir posición")};if(index<p.images.lastIndex)TextButton({val ids=p.images.map{it.id}.toMutableList();ids.removeAt(index);ids.add(index+1,photo.id);viewModel.order(ids)},enabled=enabled){Text("Bajar posición")}}
                    if(!photo.primary)TextButton({viewModel.order(listOf(photo.id)+p.images.filter{it.id!=photo.id}.map{it.id})},enabled=enabled){Text("Usar como principal")}
                    OutlinedButton({replacement=photo.id;error="Selecciona o captura la fotografía de sustitución."},enabled=enabled){Text("Sustituir fotografía ${index+1}")}
                    TextButton({removing=photo},enabled=enabled){Text("Eliminar fotografía ${index+1}")}
                }
            }}
        }
    }
    removing?.let{photo->AlertDialog(onDismissRequest={removing=null},title={Text("Retirar fotografía")},text={Text("Se retirará de la galería y se conservará su referencia histórica.")},confirmButton={TextButton({removing=null;viewModel.remove(photo.id)}){Text("Eliminar fotografía")}},dismissButton={TextButton({removing=null}){Text("Cancelar")}})}
}
