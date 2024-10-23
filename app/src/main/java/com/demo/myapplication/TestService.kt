package com.demo.myapplication

import android.util.Log
import binderservice.AppGlobals
import binderservice.BinderService

@BinderService(name = "test", process = ":test")
class TestService : IMyAidlInterface.Stub() {
    override fun test() {
        Log.i("TestService", "test end ${AppGlobals.processName}")
    }
}