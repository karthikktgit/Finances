package com.ledgerline.app.domain.model

/** A group of bills and spending envelopes on Home. */
data class HomeGroup(val id: String, val name: String, val sortOrder: Int)

/** Where one bill or envelope sits. [itemKey] is "bill:<id>" or "budget:<id>". */
data class HomeGroupItem(val itemKey: String, val groupId: String, val sortOrder: Int)
