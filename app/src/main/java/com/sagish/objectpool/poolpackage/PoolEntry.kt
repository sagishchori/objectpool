package com.sagish.objectpool.poolpackage

class PoolEntry<T>(var value: T, private var recycleInterface: RecyclePoolEntry<T>, var lastUsed: Long = System.currentTimeMillis()) : AutoCloseable {


    override fun close() {
//        recycleInterface.onClose(this)
        if (value is AutoCloseable) {
            try {
                (value as AutoCloseable).close()
            } catch (_: Throwable) {
            }
        }
    }

    fun recycle() {
        recycleInterface.recycle(this)
    }

    interface RecyclePoolEntry<T> {

//        fun onClose(value: PoolEntry<T>)

        fun recycle(value: PoolEntry<T>)
    }
}