package com.demo.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import binderservice.ServiceManager
import kotlinx.coroutines.launch

class TestA : ComponentActivity() {

    companion object {
        private const val TAG = "TestA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val service = ServiceManager.getService("main")
            IMyAidlInterface.Stub.asInterface(service).test()
            Log.i(TAG, service?.javaClass.toString())
        }
    }
}