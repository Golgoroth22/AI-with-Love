package com.example.aiwithlove.util

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class TranslationService {
    private val ruToEn = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.RUSSIAN)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )
    private val enToRu = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build()
    )

    private val conditions = DownloadConditions.Builder().build()

    suspend fun toEnglish(text: String): String {
        ruToEn.downloadModelIfNeeded(conditions).await()
        return translatePreservingStructure(ruToEn, text)
    }

    suspend fun toRussian(text: String): String {
        enToRu.downloadModelIfNeeded(conditions).await()
        return translatePreservingStructure(enToRu, text)
    }

    private suspend fun translatePreservingStructure(translator: Translator, text: String): String {
        val translated = mutableListOf<String>()
        for (line in text.split('\n')) {
            translated.add(if (line.isBlank()) line else translator.translate(line).await())
        }
        return translated.joinToString("\n")
    }
}
