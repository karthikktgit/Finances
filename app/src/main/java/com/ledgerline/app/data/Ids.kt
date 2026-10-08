package com.ledgerline.app.data

import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}
