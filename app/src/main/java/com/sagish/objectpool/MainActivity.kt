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

        trySomething()
        CoroutineScope(config.dispatcher).launch {

            val entry2 = pool.pull2("secondItem2")
            Log.v("ObjectPoolTag", "Item value is2: ${entry2?.value}")
            entry2.recycle()

            val entry21 = pool.pull2("secondItem21")
            Log.v("ObjectPoolTag", "Item value is21: ${entry21?.value}")
            entry21.recycle()

//            delay(15000)
            val entry22 = pool.pull2("secondItem22")
            Log.v("ObjectPoolTag", "Item value is22: ${entry22?.value}")

            val entry23 = pool.pull2("secondItem23")
            Log.v("ObjectPoolTag", "Item value is23: ${entry23?.value}")

            pool.close()








//            val entry2 = pool.pull("secondItem2")
//            Log.v("ObjectPoolTag", "Item value is22: ${entry2?.value}")
//
//            val entry1 = pool.pull("firstItem2")
//            Log.v("ObjectPoolTag", "Item value is12: ${entry1?.value}")
//
//            val entry3 = pool.pull("thirdItem")
//            Log.v("ObjectPoolTag", "Item value is33: ${entry3?.value}")

//            val intEntry1 = intPool.pull(TestObject(1))
//            val intEntry2 = intPool.pull(TestObject(2))
        }

    }

    private fun trySomething() {
        CoroutineScope(config.dispatcher).launch {
//            val entry2 = pool.pull("secondItem1")
//        Log.v("ObjectPoolTag", "Item value is21: ${entry2?.value}")
//
//            val entry1 = pool.pull("firstItem1")
//        Log.v("ObjectPoolTag", "Item value is11: ${entry1?.value}")

//            val intEntry1 = intPool.pull(TestObject(1))
//            val intEntry2 = intPool.pull(TestObject(2))

//            intEntry1?.value?.value = 8
//            intEntry2?.value?.value = 9
        }
    }
}