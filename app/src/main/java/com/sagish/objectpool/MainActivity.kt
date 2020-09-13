package com.sagish.objectpool

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.sagish.objectpool.poolpackage.ObjectPool
import com.sagish.objectpool.poolpackage.PoolConfig
import com.sagish.objectpool.poolpackage.TestObject
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val config : PoolConfig = PoolConfig(2, 10000)
    private var pool : ObjectPool<String> = ObjectPool(config)
    private var intPool : ObjectPool<TestObject> = ObjectPool(config)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

    }
}