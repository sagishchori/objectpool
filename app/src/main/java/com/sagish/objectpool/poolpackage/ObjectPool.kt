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
import java.util.*
import java.util.concurrent.TimeoutException

class ObjectPool<T>(private val config: PoolConfig) : AutoCloseable, PoolEntry.RecyclePoolEntry<T>{

    private val available = Stack<PoolEntry<T>>()
    private val inUse = WeakHashMap<T, PoolEntry<T>>()
    private val doCloseChannel = Channel<Unit>()
    private val hasClosed = Channel<Unit>()

    private val requestChannel = Channel<Channel<T>>()
    private val inUse2Channel = Channel<PoolEntry<T>>()
    private val receivedItemChannel = Channel<PoolEntry<T>>()
    private val release2Channel = Channel<PoolEntry<T>>()
    private val maybeExpired2Channel = Channel<PoolEntry<T>>()

    /**
     * Get an instance from the pool. If no instance available create new one. If the pool is
     * full throw PoolExhaustedException
     *
     * return the request object.
     */
    @Synchronized
    suspend fun pull2(item : T) : PoolEntry<T> {

        validate()
        return if (inUse.size < config.maxPoolCapacity) {
            internalPull(item)
        } else {
            return if (inUse.size < config.maxPoolCapacity) {
                Log.v("ObjectPoolTag", "The size of inUse is ${inUse.size}")
                internalPull(item)
            } else {
                Log.v("ObjectPoolTag", "The pool is full")
                Log.v("ObjectPoolTag", "The size of inUse is ${inUse.size}")
                throw PoolExhaustedException("The pool is full")
            }
        }
    }

    private suspend fun internalPull(item: T) : PoolEntry<T> {
        Log.v("ObjectPoolTag", "InternalPull was called for $item")
        CoroutineScope(config.dispatcher).launch {
            val channel = Channel<T>()

            requestChannel.send(channel)
            channel.send(item)
        }

        return receivedItemChannel.receive()
    }

    init {
        CoroutineScope(config.dispatcher).launch {
            whileSelect {

                // Request an item from pool
                requestChannel.onReceive { it ->
                    val v = it.receive()
                    Log.v("ObjectPoolTag", "requestChannel was called for $v")

                    // Try to get item from pool or create a new one
                    var entry = available.tryToPop()

                    if (entry != null) {
                        entry.value = v
                    } else {
                        entry = createNewObject(v)
                    }

                    CoroutineScope(config.dispatcher).launch {

                        // Send the item to inUse
                        inUse2Channel.send(entry!!)
                    }

                    true
                }

                // Set the item in inUse HashMap
                inUse2Channel.onReceive {

                    setItemInUse(it)
//                    inUse[it.value] = it
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

                maybeExpired2Channel.onReceive {instance ->
                    Log.v("ObjectPoolTag", "maybeExpiredChannel called")
                    if (System.currentTimeMillis() - instance.lastUsed > config.timeBeforeDispose && inUse.any { it.value == instance  }) {
                        close(instance)
                        inUse.remove(instance.value)

                        setItemAvailable(instance)

                        Log.v("ObjectPoolTag", "The size of available HashMap is ${available.size}")
                    }

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

        available.add(item)
    }

    /**
     * Set item to be in use.
     */
    @Synchronized
    private fun setItemInUse(item: PoolEntry<T>) {
        inUse[item.value] = item
    }

    /**
     * Instantiate a new PoolEntry<T>.
     *
     * @param item      The value to set
     */
    private fun createNewObject(item : T) : PoolEntry<T> {
        return PoolEntry(item, this)
    }

    /**
     * Validate if an items should be re-used.
     */
    private fun validate() = runBlocking {
        for(entry in inUse){
            CoroutineScope(config.dispatcher).launch {
                release2Channel.send(entry.value)
            }
        }
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
        val entry = inUse.remove(value.value)
        setItemAvailable(entry!!)
    }
}