package com.sagish.objectpool.extentions

/**
 * Use this to call the close() function of AutoCloseable.
 */
fun <T> close(item: T) =
    (item as? AutoCloseable)?.close()