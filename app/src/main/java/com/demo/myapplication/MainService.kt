package com.demo.myapplication

import android.util.Log
import binderservice.AppGlobals
import binderservice.BinderService
import binderservice.ServiceManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@BinderService(name = "main")
class MainService : IMyAidlInterface.Stub() {
    override fun test() {
        GlobalScope.launch {
            Log.i("MainService", AppGlobals.processName)
            asInterface(ServiceManager.getService("test")).test()
        }
    }
}