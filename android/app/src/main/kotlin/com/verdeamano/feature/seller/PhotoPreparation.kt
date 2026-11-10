package com.verdeamano.feature.seller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

class PhotoPreparationFailure(override val message:String):Exception(message)
object PhotoPreparation {
    const val MAX_BYTES=8*1024*1024
    fun createCapture(context:Context):File=File.createTempFile("capture-",".jpg",File(context.cacheDir,"product-captures").apply{mkdirs()})
    fun prepare(context:Context,uri:Uri):File {
        val bytes=try { context.contentResolver.openInputStream(uri)?.use { source ->
            val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val n=source.read(buffer);if(n<0)break;if(output.size()+n>MAX_BYTES)throw PhotoPreparationFailure("La fotografía debe pesar hasta 8 MB.");output.write(buffer,0,n)}
            output.toByteArray()
        } ?: throw PhotoPreparationFailure("No pudimos abrir la fotografía.") } catch(e:PhotoPreparationFailure){throw e}
        catch(_:Exception){throw PhotoPreparationFailure("No pudimos leer la fotografía seleccionada.")}
        val jpeg=bytes.size>=3&&bytes[0]==0xff.toByte()&&bytes[1]==0xd8.toByte()&&bytes[2]==0xff.toByte()
        val png=bytes.size>=8&&bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a))
        if(!jpeg&&!png)throw PhotoPreparationFailure("Selecciona una fotografía JPEG o PNG.")
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth !in 1..8000||bounds.outHeight !in 1..8000||bounds.outWidth.toLong()*bounds.outHeight>24_000_000)throw PhotoPreparationFailure("La fotografía supera las dimensiones admitidas.")
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048)sample*=2
        val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample}) ?: throw PhotoPreparationFailure("El archivo no contiene una fotografía válida.")
        var converted:Bitmap?=null;var file:File?=null
        try{
            val orientation=runCatching{ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix=Matrix().apply{
                when(orientation){2->setScale(-1f,1f);3->setRotate(180f);4->{setRotate(180f);postScale(-1f,1f)};5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)}
                val ratio=minOf(1f,1600f/maxOf(decoded.width,decoded.height));postScale(ratio,ratio)
            }
            val prepared=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true);converted=prepared
            val target=File.createTempFile("prepared-",".jpg",File(context.cacheDir,"product-captures").apply{mkdirs()});file=target
            target.outputStream().use{if(!prepared.compress(Bitmap.CompressFormat.JPEG,85,it))throw PhotoPreparationFailure("No pudimos preparar la fotografía.")}
            return target
        }catch(e:PhotoPreparationFailure){file?.delete();throw e}
        catch(_:Exception){file?.delete();throw PhotoPreparationFailure("No hay espacio suficiente para preparar la fotografía.")}
        finally{if(converted!==decoded)converted?.recycle();decoded.recycle()}
    }
    fun cleanExpired(context:Context) {
        val cutoff=System.currentTimeMillis()-24*60*60*1000
        File(context.cacheDir,"product-captures").listFiles().orEmpty().filter{it.isFile&&it.lastModified()<cutoff&&(it.name.startsWith("capture-")||it.name.startsWith("prepared-"))}.forEach{it.delete()}
    }
}
