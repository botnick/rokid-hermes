package com.botnick.rokidhermes

import android.app.Application

/**
 * Application class for Rokid Hermes. Kept minimal — connection state lives in
 * the UI layer (SettingsStore + ChatController); there is no global service.
 */
class RokidHermesApp : Application()
