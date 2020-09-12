package com.sagish.objectpool.extentions

import java.util.*

fun <T> Stack<T>.tryToPop() = if (empty()) null else pop()