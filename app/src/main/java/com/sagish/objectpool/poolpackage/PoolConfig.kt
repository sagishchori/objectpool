package com.sagish.objectpool.poolpackage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class PoolConfig @JvmOverloads constructor(
    val maxPoolCapacity: Int,
    val timeBeforeDispose : Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * A default PoolConfig object
     */
    companion object {
        val default = PoolConfig( 50,  10000)
    }
}