package com.sagish.objectpool.poolpackage

class TestObject(var value : Int): AutoCloseable{

    override fun close() {
        value = 0
    }
}