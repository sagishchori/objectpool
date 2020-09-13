package com.sagish.objectpool.poolpackage

class PoolEntry<T : Any>constructor(var lastUsed: Long = System.currentTimeMillis()) : AutoCloseable {

    var id : Int = 0
    var value: T? = null
    var recycleInterface : RecyclePoolEntry<T>? = null

    constructor(id: Int, value: T?, recycleInterface: RecyclePoolEntry<T>) : this() {
        this.id = id
        this.value = value
        this.recycleInterface = recycleInterface
    }

    constructor(id: Int, recycleInterface: RecyclePoolEntry<T>) : this() {
        this.id = id
        this.recycleInterface = recycleInterface
    }

    override fun close() {
//        recycleInterface.onClose(this)

        if (value is AutoCloseable) {
            try {
                (value as AutoCloseable).close()
            } catch (_: Throwable) {
            }
        }

        value = null
        recycleInterface = null
        lastUsed = 0
    }

    fun recycle() {

        recycleInterface?.recycle(this)
    }

    interface RecyclePoolEntry<T : Any> {

//        fun onClose(value: PoolEntry<T>)

        fun recycle(value: PoolEntry<T>)
    }
}