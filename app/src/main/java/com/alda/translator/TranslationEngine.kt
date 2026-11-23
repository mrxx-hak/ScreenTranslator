package com.alda.translator

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationEngine {
    fun translate(text: String, from: String, to: String, done: (Result<String>) -> Unit) {
        if (text.isBlank()) { done(Result.failure(IllegalArgumentException("Введите текст."))); return }
        val source = code(from)
        val target = code(to)
        if (source == null || target == null) {
            done(Result.failure(IllegalArgumentException("Туркменский пока не поддерживается встроенной офлайн-моделью. Для него нужен отдельный онлайн-переводчик.")))
            return
        }
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        client.downloadModelIfNeeded().addOnSuccessListener {
            client.translate(text).addOnSuccessListener { result -> client.close(); done(Result.success(result)) }
                .addOnFailureListener { e -> client.close(); done(Result.failure(e)) }
        }.addOnFailureListener { e -> client.close(); done(Result.failure(e)) }
    }
    private fun code(label: String): String? = when(label) {
        "Китайский" -> TranslateLanguage.CHINESE
        "Русский" -> TranslateLanguage.RUSSIAN
        "Английский" -> TranslateLanguage.ENGLISH
        "Турецкий" -> TranslateLanguage.TURKISH
        else -> null
    }
}
