package com.josiel.organizeprocesso.data.util

import android.content.Context
import android.provider.Settings

/** Identificador estável do dispositivo, usado em `device_origin` (ARQUITETURA.md, seção 3). */
object DeviceId {
    fun obter(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "dispositivo-desconhecido"
}
