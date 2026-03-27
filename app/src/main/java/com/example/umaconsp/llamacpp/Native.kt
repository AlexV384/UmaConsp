package com.example.umaconsp.llamacpp

import android.util.Log

const val TAG = "Native.kt"
class Native {
    var loadedModelAddr: Long = 0
    companion object {
        init {
            System.loadLibrary("LlamaCppApp")
        }
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
    external fun converseJni(addr: Long, image: ByteArray, callback: (String) -> Unit)
}