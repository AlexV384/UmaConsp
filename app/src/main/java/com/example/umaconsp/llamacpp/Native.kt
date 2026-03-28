package com.example.umaconsp.llamacpp

import android.util.Log

const val TAG = "Native.kt"
object Native {
    var loadedModelAddr: Long = 0
    init {
        System.loadLibrary("LlamaCppApp")
    }
    external fun loadModelJni(path: String): Long
    fun loadModelPub(path: String) {
        if (loadedModelAddr != 0L){
            unloadModelPub()
        }
        Log.d(TAG,"Loading model: $path ...")
        val addr = loadModelJni(path)
        Log.d(TAG, "Model pointer: $addr")
        loadedModelAddr = addr
    }
    external fun unloadModelJni(addr: Long)
    fun unloadModelPub(){
        Log.d(TAG, "Unloading model")
        unloadModelJni(loadedModelAddr)
        loadedModelAddr = 0
    }
    external fun converseJni(addr: Long, image: ByteArray, callback: TokenCallback)
    fun conversePub(image: ByteArray, callback: (String) -> Unit){
        if (loadedModelAddr != 0L){
            converseJni(loadedModelAddr, image, object : TokenCallback {
                override fun onToken(token: String) = callback(token)
            })
        } else {
            Log.d(TAG, "Cannot converse when model is not loaded")
        }
    }
    interface TokenCallback {
        fun onToken(token: String)
    }
}