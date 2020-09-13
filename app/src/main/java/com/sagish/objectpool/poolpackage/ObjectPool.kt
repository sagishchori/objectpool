package com.sagish.objectpool.poolpackage

import android.util.Log
import com.sagish.objectpool.exceptions.PoolExhaustedException
import com.sagish.objectpool.extentions.close
import com.sagish.objectpool.extentions.tryToPop
import kotlinx.coroutines.*
//import com.sagish.objectpool.extentions.tryToPop
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.whileSelect
import java.lang.NullPointerException
import java.util.*
import java.util.concurrent.TimeoutException

class ObjectPool<T : Any>(private val config: PoolConfig) : AutoCloseable, PoolEntry.RecyclePoolEntry<T> {

    private val available = Stack<PoolEntry<T>>()
    private val inUse = WeakHashMap<Int, PoolEntry<T>>()
    private val doCloseChannel = Channel<Unit>()
    private val hasClosed = Channel<Unit>()

    private val requestChannel = Channel<Channel<T>>()
    private val request2Channel = Channel<T?>()
    private val inUse2Channel = Channel<PoolEntry<T>>()
    private val receivedItemChannel = Channel<PoolEntry<T>>()
    private val release2Channel = Channel<PoolEntry<T>>()
    private val maybeExpired2Channel = Channel<PoolEntry<T>>()
    private var poolSize = 0

    /**
     * Get an empty instance from the pool.
     */
    suspend fun pull(): PoolEntry<T> {
        return if (inUse.size < config.maxPoolCapacity) {
            internalEmptyPull()
        } else if (inUse.size == config.maxPoolCapacity) {
            validate()
            internalEmptyPull()
        } else {
            throw PoolExhaustedException("The pool is full")
        }
    }

    private suspend fun internalEmptyPull(): PoolEntry<T> {
        CoroutineScope(config.dispatcher).launch {
            val channel : T? = null

            request2Channel.send(channel)
        }

        return receivedItemChannel.receive()
    }

    /**
     * Get an instance from the pool. If no instance available create new one. If the pool is
     * full throw PoolExhaustedException
     *
     * return the request object.
     */
    @Synchronized
    suspend fun pull2(item: T): PoolEntry<T> {

        return if (inUse.size < config.maxPoolCapacity) {
            internalPull(item)
        } else if (inUse.size == config.maxPoolCapacity) {
            validate()
            internalPull(item)
        } else {
            throw PoolExhaustedException("The pool is full")
        }
    }

    private suspend fun internalPull(item: T): PoolEntry<T> {
        Log.v("ObjectPoolTag", "InternalPull was called for $item")
        CoroutineScope(config.dispatcher).launch {
            val channel = Channel<T>()

            requestChannel.send(channel)
            channel.send(item)
        }

        val receivedItem = receivedItemChannel.receive()
        receivedItem.value = item

        return receivedItem
    }

    private fun getItemId(): Int {
        return try {
            val id = config.maxPoolCapacity - poolSize - 1
            if (id < 0) {
                throw IndexOutOfBoundsException("Pool size exceeded it size")
            } else {
                id
            }
        } catch (e : IndexOutOfBoundsException) {
            Log.v("ObjectPoolTag", e.message)
        }
    }

    init {
        CoroutineScope(config.dispatcher).launch {
            whileSelect {

                // Request an empty instance from pool
                if (poolSize < config.maxPoolCapacity) {
                    request2Channel.onReceive {

                        // Try to get item from pool or create a new one
                        var entry = available.tryToPop()

                        if (entry == null) {
                            entry = PoolEntry(getItemId(), this@ObjectPool)
                        } else {
                            entry.lastUsed = System.currentTimeMillis()
                            entry.recycleInterface = this@ObjectPool
                        }

                        CoroutineScope(config.dispatcher).launch {

                            // Send the item to inUse
                            inUse2Channel.send(entry!!)
                        }

                        true
                    }
                }

                // Request an item from pool
                if (poolSize < config.maxPoolCapacity) {
                    requestChannel.onReceive { it ->
                        val v = it.receive()
                        Log.v("ObjectPoolTag", "requestChannel was called for $v")

                        // Try to get item from pool or create a new one
                        var entry = available.tryToPop()

                        if (entry != null) {
                            entry!!.value = v
                            entry!!.lastUsed = System.currentTimeMillis()
                            entry!!.recycleInterface = this@ObjectPool
                        } else {
                            entry = createNewObject(v)
                        }

                        CoroutineScope(config.dispatcher).launch {

                            // Send the item to inUse
                            inUse2Channel.send(entry!!)
                        }

                        true
                    }
                }

                // Set the item in inUse HashMap
                inUse2Channel.onReceive {

                    if(setItemInUse(it))
                        CoroutineScope(config.dispatcher).launch {

                            // Send the item to the callee
                            receivedItemChannel.send(it)
                        }

                    true
                }

                // Return an object to pool
                release2Channel.onReceive { instance ->
                    Log.v("ObjectPoolTag", "releaseChannel called")
                    instance.lastUsed = System.currentTimeMillis()
                    CoroutineScope(config.dispatcher).launch {
                        delay(config.timeBeforeDispose)
                        maybeExpired2Channel.send(instance)
                    }

                    Log.v("ObjectPoolTag", "The size of available HashMap is ${available.size}")
                    true
                }

                maybeExpired2Channel.onReceive { instance ->
                    Log.v("ObjectPoolTag", "maybeExpiredChannel called")
                    if (System.currentTimeMillis() - instance.lastUsed > config.timeBeforeDispose && inUse.any { it.value == instance }) {
                        inUse.remove(instance.id)
                        poolSize--
                        Log.v("ObjectPoolTag", "item ${instance.value} removed")
                        close(instance)

                        setItemAvailable(instance)
                    }

                    Log.v("ObjectPoolTag", "The size of available HashMap is ${available.size}")
                    Log.v("ObjectPoolTag", "The size of inUse is ${inUse.size}")


                    true
                }

                // Close the pool
                doCloseChannel.onReceive {
                    Log.v("ObjectPoolTag", "doCloseChannel called")

                    // Close all object in use Return all instances to pool
                    inUse.forEach {
                        it.value.close()
                    }

                    inUse.clear()

                    Log.v("ObjectPoolTag", "The size of inUse HashMap is ${inUse.size}")

                    available.clear()

                    hasClosed.send(Unit)
                    Log.v("ObjectPoolTag", "hasClosed called")
                    Log.v("ObjectPoolTag", "The size of available HashMap is ${available.size}")
                    Log.v("ObjectPoolTag", "The size of inUse HashMap is ${inUse.size}")

                    false
                }
            }
        }
    }

    /**
     * Set item to be available again.
     */
    @Synchronized
    private fun setItemAvailable(item: PoolEntry<T>) {
        if (available.contains(item))
            return

        if (available.size < config.maxPoolCapacity)
            available.add(item)
    }

    /**
     * Set item to be in use.
     */
    @Synchronized
    private fun setItemInUse(item: PoolEntry<T>): Boolean {
        if (inUse.size < config.maxPoolCapacity) {
            inUse[item.id] = item
            poolSize++
            return true
        }
        return false
    }

    /**
     * Instantiate a new PoolEntry<T>.
     *
     * @param item      The value to set
     */
    private fun createNewObject(item : T) : PoolEntry<T> {
        return PoolEntry(getItemId(), item, this)
    }

    /**
     * Validate if an items should be re-used.
     */
    private suspend fun validate() {
        CoroutineScope(config.dispatcher).launch {
            for(entry in inUse){
                release2Channel.send(entry.value)
            }
        }.join()
    }

    /**
     * Close the pool.
     */
    override fun close() = runBlocking {
        doCloseChannel.send(Unit)
        select<Unit> {
            hasClosed.onReceive {
                onTimeout(10000) {
                    throw TimeoutException("didn't close")
                }
            }
        }
    }

    /**
     * Remove the entry from being use and return it to the pool.
     */
    override fun recycle(value: PoolEntry<T>) {
        val entry = inUse.remove(value.id)
        poolSize--
        setItemAvailable(entry!!)
    }
}