package com.ledgerline.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.ui.theme.LocalLedgerColors

/** Picks an icon from the category name (and its parent), so custom categories get a sensible icon too. */
object CategoryVisuals {
    private val rules: List<Pair<List<String>, ImageVector>> = listOf(
        listOf("grocer", "supermarket") to Icons.Filled.LocalGroceryStore,
        listOf("gas", "fuel", "petrol") to Icons.Filled.LocalGasStation,
        listOf("coffee", "cafe") to Icons.Filled.LocalCafe,
        listOf("restaurant", "food", "dining", "delivery", "snack", "fast") to Icons.Filled.Restaurant,
        listOf("rent", "mortgage", "household", "home") to Icons.Filled.Home,
        listOf("utilit", "electric", "water") to Icons.Filled.Bolt,
        listOf("internet", "wifi", "broadband") to Icons.Filled.Wifi,
        listOf("phone", "mobile") to Icons.Filled.PhoneAndroid,
        listOf("insurance", "health", "medical", "pharma") to Icons.Filled.HealthAndSafety,
        listOf("subscription", "streaming") to Icons.Filled.Subscriptions,
        listOf("car", "transport", "transit", "ride", "parking", "maintenance") to Icons.Filled.DirectionsCar,
        listOf("credit card") to Icons.Filled.CreditCard,
        listOf("education", "school", "tuition") to Icons.Filled.School,
        listOf("loan", "debt") to Icons.Filled.AccountBalance,
        listOf("salary", "paycheck", "income") to Icons.Filled.Payments,
        listOf("shop", "cloth", "electronic") to Icons.Filled.ShoppingBag,
        listOf("entertain", "movie", "fun") to Icons.Filled.Movie,
        listOf("travel", "flight", "hotel") to Icons.Filled.Flight,
        listOf("personal") to Icons.Filled.Person,
        listOf("unplanned") to Icons.Filled.Whatshot,
        listOf("transfer") to Icons.Filled.SwapHoriz,
        listOf("monthly", "bill") to Icons.AutoMirrored.Filled.ReceiptLong,
    )

    fun icon(categoryId: String?, categories: List<Category>): ImageVector {
        val category = categories.firstOrNull { it.id == categoryId } ?: return Icons.Filled.Category
        val parent = category.parentId?.let { id -> categories.firstOrNull { it.id == id } }
        // The subcategory name is more specific, so it wins over the parent's.
        return match(category.name) ?: parent?.let { match(it.name) } ?: Icons.Filled.Category
    }

    fun forName(name: String): ImageVector = match(name) ?: Icons.Filled.Category

    private fun match(name: String): ImageVector? {
        val label = name.lowercase()
        return rules.firstOrNull { (keys, _) -> keys.any { it in label } }?.second
    }

    fun forType(type: TransactionType): ImageVector = when (type) {
        TransactionType.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
        TransactionType.TRANSFER -> Icons.Filled.SwapHoriz
        TransactionType.PAYMENT -> Icons.Filled.CreditCard
        TransactionType.ADJUSTMENT -> Icons.Filled.Tune
        TransactionType.REFUND -> Icons.Filled.Payments
        TransactionType.EXPENSE -> Icons.Filled.ShoppingBag
    }

    fun forAccount(type: AccountType): ImageVector = when (type) {
        AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
        AccountType.SAVINGS -> Icons.Filled.Savings
        AccountType.CASH_WALLET, AccountType.PREPAID -> Icons.Filled.AccountBalanceWallet
        AccountType.INVESTMENT -> Icons.AutoMirrored.Filled.TrendingUp
        else -> Icons.Filled.AccountBalance
    }
}

/** Amount color by transaction type: green for money in, red for money out, slate for moves between accounts. */
@Composable
fun amountColor(type: TransactionType, balanceEffectMinor: Long): Color {
    val colors = LocalLedgerColors.current
    return when (type) {
        TransactionType.TRANSFER, TransactionType.PAYMENT -> colors.transfer
        else -> if (balanceEffectMinor >= 0) colors.positive else colors.negative
    }
}

/** Icon for a transaction row: the category icon when there is one, otherwise one for the type. */
fun rowIcon(type: TransactionType, categoryId: String?, categories: List<Category>): ImageVector =
    if (categoryId == null || type == TransactionType.TRANSFER || type == TransactionType.PAYMENT) {
        CategoryVisuals.forType(type)
    } else {
        CategoryVisuals.icon(categoryId, categories)
    }
