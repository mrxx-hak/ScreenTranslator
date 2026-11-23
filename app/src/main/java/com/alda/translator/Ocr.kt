package com.alda.translator

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

object Ocr {
    fun bitmap(b:Bitmap,done:(Result<String>)->Unit) {
        val r=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        r.process(InputImage.fromBitmap(b,0)).addOnSuccessListener { r.close(); if(it.text.isBlank()) done(Result.failure(Exception("Текст не найден"))) else done(Result.success(it.text)) }
            .addOnFailureListener { r.close(); done(Result.failure(it)) }
    }
    fun uri(c:Context,u:Uri,done:(Result<String>)->Unit) {
        try {
            val image=InputImage.fromFilePath(c,u); val r=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            r.process(image).addOnSuccessListener { r.close(); if(it.text.isBlank()) done(Result.failure(Exception("Текст не найден"))) else done(Result.success(it.text)) }
                .addOnFailureListener { r.close(); done(Result.failure(it)) }
        } catch(e:Exception){done(Result.failure(e))}
    }
}
