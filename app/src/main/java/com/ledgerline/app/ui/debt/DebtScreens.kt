package com.ledgerline.app.ui.debt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtDraft
import com.ledgerline.app.domain.model.DebtStrategy
import com.ledgerline.app.domain.model.DebtType
import com.ledgerline.app.domain.model.PayChoice
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RateType
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.DebtRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.debt.DebtForecast
import com.ledgerline.app.finance.debt.DebtPlanner
import com.ledgerline.app.finance.debt.ForecastInput
import com.ledgerline.app.finance.debt.RatePoint
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.components.AmountField
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.components.SectionLabel
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.navigation.DebtDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class DebtListViewModel @Inject constructor(
    private val debts: DebtRepository,
    accounts: AccountRepository,
    private val clock: AppClock,
) : ViewModel() {
    val rows = debts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts = accounts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var name by mutableStateOf("")
    var debtType by mutableStateOf(DebtType.CREDIT_CARD)
    var rateType by mutableStateOf(RateType.UNKNOWN)
    var accountId by mutableStateOf("")
    var paymentAccountId by mutableStateOf("")
    var balance by mutableStateOf(0L)
    var aprKnown by mutableStateOf(false)
    var apr by mutableStateOf("")
    var minimumKnown by mutableStateOf(false)
    var minimum by mutableStateOf(0L)
    var dueDay by mutableStateOf("")
    var priority by mutableStateOf(PaymentPriority.MANDATORY)
    var message by mutableStateOf<String?>(null)

    fun save() {
        viewModelScope.launch {
            val result = debts.save(draftFrom(null))
            message = if (result is SaveResult.Invalid) result.message else "Saved. Open it to add the statement details still missing."
            if (result is SaveResult.Saved) name = ""
        }
    }

    private fun draftFrom(existingId: String?) = DebtDraft(
        existingId = existingId,
        name = name,
        balanceMinor = balance,
        aprBasisPoints = apr.toBasisPoints(),
        aprKnown = aprKnown,
        minimumPaymentMinor = minimum,
        minimumKnown = minimumKnown,
        dueDayOfMonth = dueDay.toIntOrNull() ?: 0,
        priority = priority,
        debtType = debtType,
        rateType = rateType,
        accountId = accountId.ifBlank { null },
        paymentAccountId = paymentAccountId.ifBlank { null },
        statementCloseDay = null,
        originationOn = null,
        maturityOn = null,
        remainingTermMonths = null,
        feeMinor = 0,
        rateUpdatedOn = null,
        accruedInterestMinor = null,
        showInCalendar = true,
        purchaseAprBasisPoints = null,
        balanceTransferAprBasisPoints = null,
        cashAdvanceAprBasisPoints = null,
        graceOnPurchases = false,
        dayCountBasis = 365,
        recurringPaymentId = null,
        note = "",
    )
}

@Composable
fun DebtScreen(
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    viewModel: DebtListViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val currency = rows.firstOrNull()?.currency ?: "USD"
    val order = DebtPlanner.plan(rows, 0, DebtStrategy.AVALANCHE)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Balances you type here stay separate from account balances until you link an account and confirm a payment or a correction.")
        if (rows.isEmpty()) Text("No debts yet. Nothing is filled in for you.")
        rows.forEach { debt ->
            TextButton(onClick = { onOpen(debt.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(debt.name, style = MaterialTheme.typography.titleMedium)
                    Text("${debt.debtType.label()} · ${MoneyFormat.format(debt.balanceMinor, debt.currency)} · ${debt.missingSummary()}")
                }
            }
        }
        if (rows.size > 1) {
            Text(DebtPlanner.ASSUMPTION, style = MaterialTheme.typography.bodyMedium)
            Text("Estimated payoff order: ${order.payoffOrder.joinToString(", ").ifBlank { "Not enough data" }}")
        }
        HorizontalDivider()
        SectionLabel("Add a debt")
        OutlinedTextField(viewModel.name, { viewModel.name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        PickerField("Type", viewModel.debtType.label(), DebtType.entries.toList(), { it.label() }) { viewModel.debtType = it }
        PickerField("Rate", viewModel.rateType.label(), RateType.entries.toList(), { it.label() }) { viewModel.rateType = it }
        AccountPicker("Linked card or loan", viewModel.accountId, accounts) { viewModel.accountId = it }
        AccountPicker("Pay from", viewModel.paymentAccountId, accounts) { viewModel.paymentAccountId = it }
        Text("Current amount owed")
        AmountField(viewModel.balance, currency) { viewModel.balance = it }
        ToggleRow("I know the APR", viewModel.aprKnown) { viewModel.aprKnown = it }
        if (viewModel.aprKnown) {
            OutlinedTextField(viewModel.apr, { viewModel.apr = it }, Modifier.fillMaxWidth(), label = { Text("APR percent") }, singleLine = true)
        }
        ToggleRow("I know the minimum payment", viewModel.minimumKnown) { viewModel.minimumKnown = it }
        if (viewModel.minimumKnown) {
            Text("Minimum from the statement")
            AmountField(viewModel.minimum, currency) { viewModel.minimum = it }
        }
        OutlinedTextField(
            viewModel.dueDay,
            { viewModel.dueDay = it.filter(Char::isDigit).take(2) },
            Modifier.fillMaxWidth(),
            label = { Text("Due day") },
            singleLine = true,
        )
        PickerField("Priority", viewModel.priority.label(), PaymentPriority.entries.toList(), { it.label() }) { viewModel.priority = it }
        PrimaryButton("Save debt", onClick = viewModel::save)
        viewModel.message?.let { Text(it) }
    }
}

@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debts: DebtRepository,
    accounts: AccountRepository,
    transactions: TransactionRepository,
    recurring: RecurringRepository,
    private val clock: AppClock,
) : ViewModel() {
    val debtId = savedStateHandle.toRoute<DebtDetailRoute>().debtId
    val debt = debts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val plans = debts.observePlans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rates = debts.observeRates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settlements = debts.observeSettlements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts = accounts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions = transactions.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val schedules = recurring.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var ready by mutableStateOf(false)
    var name by mutableStateOf("")
    var debtType by mutableStateOf(DebtType.CREDIT_CARD)
    var rateType by mutableStateOf(RateType.UNKNOWN)
    var accountId by mutableStateOf("")
    var paymentAccountId by mutableStateOf("")
    var balance by mutableStateOf(0L)
    var aprKnown by mutableStateOf(false)
    var apr by mutableStateOf("")
    var minimumKnown by mutableStateOf(false)
    var minimum by mutableStateOf(0L)
    var dueDay by mutableStateOf("")
    var closeDay by mutableStateOf("")
    var priority by mutableStateOf(PaymentPriority.MANDATORY)
    var fee by mutableStateOf(0L)
    var showInCalendar by mutableStateOf(true)
    var grace by mutableStateOf(false)
    var purchaseApr by mutableStateOf("")
    var transferApr by mutableStateOf("")
    var cashApr by mutableStateOf("")
    var accruedKnown by mutableStateOf(false)
    var accrued by mutableStateOf(0L)
    var rateUpdated by mutableStateOf("")
    var origination by mutableStateOf("")
    var maturity by mutableStateOf("")
    var term by mutableStateOf("")
    var note by mutableStateOf("")
    var recurringId by mutableStateOf("")
    var planAmount by mutableStateOf(0L)
    var planDueDay by mutableStateOf("")
    var fixedAmount by mutableStateOf(0L)
    var extraAmount by mutableStateOf(0L)
    var targetMonth by mutableStateOf("")
    var interestCharge by mutableStateOf(0L)
    var newRate by mutableStateOf("")
    var newRateOn by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    val focusMonth: YearMonth get() = YearMonth.from(clock.today())

    fun current(): Debt? = debt.value.firstOrNull { it.id == debtId }

    fun load(item: Debt) {
        if (ready) return
        name = item.name
        debtType = item.debtType
        rateType = item.rateType
        accountId = item.accountId.orEmpty()
        paymentAccountId = item.paymentAccountId.orEmpty()
        balance = item.balanceMinor
        aprKnown = item.aprKnown
        apr = if (item.aprKnown) basisLabel(item.aprBasisPoints) else ""
        minimumKnown = item.minimumKnown
        minimum = item.minimumPaymentMinor
        dueDay = item.dueDayOfMonth.toString()
        closeDay = item.statementCloseDay?.toString().orEmpty()
        priority = item.priority
        fee = item.feeMinor
        showInCalendar = item.showInCalendar
        grace = item.graceOnPurchases
        purchaseApr = item.purchaseAprBasisPoints?.let(::basisLabel).orEmpty()
        transferApr = item.balanceTransferAprBasisPoints?.let(::basisLabel).orEmpty()
        cashApr = item.cashAdvanceAprBasisPoints?.let(::basisLabel).orEmpty()
        accruedKnown = item.accruedInterestMinor != null
        accrued = item.accruedInterestMinor ?: 0L
        rateUpdated = item.rateUpdatedOn?.toString().orEmpty()
        origination = item.originationOn?.toString().orEmpty()
        maturity = item.maturityOn?.toString().orEmpty()
        term = item.remainingTermMonths?.toString().orEmpty()
        note = item.note
        recurringId = item.recurringPaymentId.orEmpty()
        ready = true
    }

    fun save() {
        viewModelScope.launch {
            val result = debts.save(draft())
            message = if (result is SaveResult.Invalid) result.message else "Saved."
        }
    }

    fun toggleCalendar(show: Boolean) {
        showInCalendar = show
        viewModelScope.launch { debts.setShowInCalendar(debtId, show) }
    }

    fun saveMonth() {
        viewModelScope.launch {
            val due = planDueDay.toIntOrNull()?.let { day ->
                focusMonth.atDay(day.coerceIn(1, focusMonth.lengthOfMonth()))
            }
            val result = debts.savePlan(debtId, focusMonth, planAmount, due)
            message = if (result is SaveResult.Invalid) result.message else "Saved ${focusMonth}."
        }
    }

    fun markPaid() {
        viewModelScope.launch {
            val result = debts.markMonthPaid(debtId, focusMonth)
            message = if (result is SaveResult.Invalid) result.message else "Payment recorded. Undo is available."
        }
    }

    fun recordInterest() {
        viewModelScope.launch {
            val result = debts.recordInterest(debtId, interestCharge, clock.today())
            message = if (result is SaveResult.Invalid) result.message else "Interest recorded on the linked account."
        }
    }

    fun adopt() {
        viewModelScope.launch {
            val result = debts.adoptAccountOwed(debtId)
            message = if (result is SaveResult.Invalid) result.message else "Debt balance now matches the account."
            ready = false
        }
    }

    fun correctAccount() {
        viewModelScope.launch {
            val result = debts.postAccountCorrection(debtId)
            message = if (result is SaveResult.Invalid) result.message else "Account correction recorded. Undo is available."
        }
    }

    fun compare(choice: PayChoice, fixed: Long, extra: Long) {
        viewModelScope.launch {
            val item = current() ?: return@launch
            val text = withContext(Dispatchers.Default) {
                val compared = DebtForecast.compare(item.toInput(plans.value, rates.value, PayChoice.MINIMUM, 0, 0), choice, fixed, extra)
                comparisonText(compared, item.currency)
            }
            message = text
        }
    }

    fun estimateTarget(rawMonth: String) {
        viewModelScope.launch {
            val item = current() ?: return@launch
            val target = runCatching { YearMonth.parse(rawMonth) }.getOrNull()
            if (target == null) {
                message = "Enter the target month as YYYY-MM."
                return@launch
            }
            val payment = withContext(Dispatchers.Default) {
                DebtForecast.paymentToReach(item.toInput(plans.value, rates.value, PayChoice.MINIMUM, 0, 0), target)
            }
            message = if (payment == null) {
                "No estimated payment reaches that month with the APR and fees currently entered."
            } else {
                "Estimated fixed payment to finish by $target: ${MoneyFormat.format(payment, item.currency)}. This is not a guarantee."
            }
        }
    }

    fun addRate() {
        viewModelScope.launch {
            val on = newRateOn.toDateOrNull()
            val points = newRate.toBasisPoints()
            if (on == null || points == null) {
                message = "Enter the date as YYYY-MM-DD and the new APR."
                return@launch
            }
            val result = debts.addRateChange(debtId, on, points)
            message = if (result is SaveResult.Invalid) result.message else "Rate change saved from $on."
        }
    }

    private fun draft() = DebtDraft(
        existingId = debtId,
        name = name,
        balanceMinor = balance,
        aprBasisPoints = apr.toBasisPoints(),
        aprKnown = aprKnown,
        minimumPaymentMinor = minimum,
        minimumKnown = minimumKnown,
        dueDayOfMonth = dueDay.toIntOrNull() ?: 0,
        priority = priority,
        debtType = debtType,
        rateType = rateType,
        accountId = accountId.ifBlank { null },
        paymentAccountId = paymentAccountId.ifBlank { null },
        statementCloseDay = closeDay.toIntOrNull(),
        originationOn = origination.toDateOrNull(),
        maturityOn = maturity.toDateOrNull(),
        remainingTermMonths = term.toIntOrNull(),
        feeMinor = fee,
        rateUpdatedOn = rateUpdated.toDateOrNull(),
        accruedInterestMinor = if (accruedKnown) accrued else null,
        showInCalendar = showInCalendar,
        purchaseAprBasisPoints = purchaseApr.toBasisPoints(),
        balanceTransferAprBasisPoints = transferApr.toBasisPoints(),
        cashAdvanceAprBasisPoints = cashApr.toBasisPoints(),
        graceOnPurchases = grace,
        dayCountBasis = 365,
        recurringPaymentId = recurringId.ifBlank { null },
        note = note,
    )
}

@Composable
fun DebtDetailScreen(onBack: () -> Unit, viewModel: DebtDetailViewModel = hiltViewModel()) {
    val debts by viewModel.debt.collectAsStateWithLifecycle()
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val rates by viewModel.rates.collectAsStateWithLifecycle()
    val settlements by viewModel.settlements.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val match = debts.firstOrNull { it.id == viewModel.debtId }
    LaunchedEffect(match?.id) { match?.let(viewModel::load) }
    val currency = match?.currency ?: "USD"
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp, 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
        if (match == null) {
            Text("This debt is not on this device.")
            return@Column
        }
        Text(match.name, style = MaterialTheme.typography.headlineSmall)
        Text(match.missingSummary())
        val input = match.toInput(plans.filter { it.debtId == match.id }, rates.filter { it.debtId == match.id }, PayChoice.SAVED_PLAN, 0, 0)
        val projection = DebtForecast.project(input)
        SectionLabel("This plan")
        Text(projection.note)
        if (projection.missing.isNotEmpty()) Text("Still needed: ${projection.missing.joinToString(", ")}")
        projection.months.take(6).forEach { line ->
            Text(line.summary(currency))
        }
        if (projection.stoppedForShortfall) {
            Text("The payment does not cover interest or fees, so no payoff date is shown.")
        } else if (projection.payoffMonth != null && projection.totalInterestMinor != null) {
            Text("Estimated payoff ${projection.payoffMonth}. Estimated interest ${MoneyFormat.format(projection.totalInterestMinor, currency)}.")
        }
        HorizontalDivider()
        SectionLabel(viewModel.focusMonth.toString())
        val saved = plans.firstOrNull { it.debtId == match.id && it.yearMonth == viewModel.focusMonth }
        val settlement = settlements.firstOrNull { it.debtId == match.id && it.yearMonth == viewModel.focusMonth }
        Text(if (settlement == null) "Not paid" else "Paid ${MoneyFormat.format(settlement.amountMinor, currency)} on ${settlement.paidOn}")
        Text("Statement minimum: ${if (match.minimumKnown) MoneyFormat.format(match.minimumPaymentMinor, currency) else "Not entered"}")
        Text("Planned: ${saved?.plannedPaymentMinor?.let { MoneyFormat.format(it, currency) } ?: "Not set for this month"}")
        if (match.minimumKnown && saved != null) {
            val gap = saved.plannedPaymentMinor - match.minimumPaymentMinor
            Text("Planned minus minimum: ${MoneyFormat.format(gap, currency)}")
        }
        Text("Planned amount for this month")
        AmountField(viewModel.planAmount, currency) { viewModel.planAmount = it }
        OutlinedTextField(
            viewModel.planDueDay,
            { viewModel.planDueDay = it.filter(Char::isDigit).take(2) },
            Modifier.fillMaxWidth(),
            label = { Text("Due day this month, if different") },
            singleLine = true,
        )
        PrimaryButton("Save this month", onClick = viewModel::saveMonth)
        PrimaryButton("Mark this month paid", onClick = viewModel::markPaid)
        HorizontalDivider()
        SectionLabel("Compare estimates")
        Text("Fixed amount")
        AmountField(viewModel.fixedAmount, currency) { viewModel.fixedAmount = it }
        Text("Minimum plus extra")
        AmountField(viewModel.extraAmount, currency) { viewModel.extraAmount = it }
        TextButton(
            onClick = { viewModel.compare(PayChoice.FIXED, viewModel.fixedAmount, 0) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Compare minimum with the fixed amount") }
        TextButton(
            onClick = { viewModel.compare(PayChoice.MINIMUM_PLUS, 0, viewModel.extraAmount) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Compare minimum with minimum plus extra") }
        OutlinedTextField(viewModel.targetMonth, { viewModel.targetMonth = it }, Modifier.fillMaxWidth(), label = { Text("Target month YYYY-MM") }, singleLine = true)
        TextButton(onClick = { viewModel.estimateTarget(viewModel.targetMonth) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Estimate a payment for that month")
        }
        HorizontalDivider()
        val linked = accounts.firstOrNull { it.id == match.accountId }
        if (linked != null) {
            val owed = BalanceMath.amountOwedMinor(BalanceMath.currentBalanceMinor(linked, transactions))
            SectionLabel("Account link")
            Text("${linked.name} shows ${MoneyFormat.format(owed, currency)} owed.")
            Text("This debt record shows ${MoneyFormat.format(match.balanceMinor, currency)}.")
            if (owed != match.balanceMinor) Text("Those amounts differ. Choose a correction. Neither one is applied on its own.")
            TextButton(onClick = viewModel::adopt, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use the account amount on this debt") }
            TextButton(onClick = viewModel::correctAccount, modifier = Modifier.heightIn(min = 48.dp)) { Text("Correct the account to this debt balance") }
        }
        HorizontalDivider()
        SectionLabel("Confirmed interest")
        Text("Projected interest is not saved as a transaction. Enter interest only after it appears on a statement.")
        AmountField(viewModel.interestCharge, currency) { viewModel.interestCharge = it }
        PrimaryButton("Record interest charge", onClick = viewModel::recordInterest)
        if (match.rateType == RateType.VARIABLE || match.debtType == DebtType.EDUCATION_LOAN) {
            SectionLabel("Rate changes")
            rates.filter { it.debtId == match.id }.forEach { change ->
                Text("${change.effectiveOn} · ${basisLabel(change.aprBasisPoints)}%")
            }
            OutlinedTextField(viewModel.newRateOn, { viewModel.newRateOn = it }, Modifier.fillMaxWidth(), label = { Text("Effective date YYYY-MM-DD") }, singleLine = true)
            OutlinedTextField(viewModel.newRate, { viewModel.newRate = it }, Modifier.fillMaxWidth(), label = { Text("APR from that date") }, singleLine = true)
            PrimaryButton("Save rate change", onClick = viewModel::addRate)
        }
        HorizontalDivider()
        SectionLabel("Details")
        ToggleRow("Show in calendar", viewModel.showInCalendar, viewModel::toggleCalendar)
        OutlinedTextField(viewModel.name, { viewModel.name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        PickerField("Type", viewModel.debtType.label(), DebtType.entries.toList(), { it.label() }) { viewModel.debtType = it }
        PickerField("Rate type", viewModel.rateType.label(), RateType.entries.toList(), { it.label() }) { viewModel.rateType = it }
        AccountPicker("Linked card or loan", viewModel.accountId, accounts) { viewModel.accountId = it }
        AccountPicker("Pay from", viewModel.paymentAccountId, accounts) { viewModel.paymentAccountId = it }
        Text("Amount owed on this record")
        AmountField(viewModel.balance, currency) { viewModel.balance = it }
        ToggleRow("APR is known", viewModel.aprKnown) { viewModel.aprKnown = it }
        if (viewModel.aprKnown) {
            OutlinedTextField(viewModel.apr, { viewModel.apr = it }, Modifier.fillMaxWidth(), label = { Text("APR percent") }, singleLine = true)
        }
        ToggleRow("Minimum is known", viewModel.minimumKnown) { viewModel.minimumKnown = it }
        if (viewModel.minimumKnown) AmountField(viewModel.minimum, currency) { viewModel.minimum = it }
        OutlinedTextField(viewModel.dueDay, { viewModel.dueDay = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(), label = { Text("Due day") }, singleLine = true)
        OutlinedTextField(viewModel.closeDay, { viewModel.closeDay = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(), label = { Text("Statement closing day") }, singleLine = true)
        Text("Fee each month, if any")
        AmountField(viewModel.fee, currency) { viewModel.fee = it }
        ToggleRow("Grace period can apply to new purchases", viewModel.grace) { viewModel.grace = it }
        OutlinedTextField(viewModel.purchaseApr, { viewModel.purchaseApr = it }, Modifier.fillMaxWidth(), label = { Text("Purchase APR, if different") }, singleLine = true)
        OutlinedTextField(viewModel.transferApr, { viewModel.transferApr = it }, Modifier.fillMaxWidth(), label = { Text("Balance-transfer APR") }, singleLine = true)
        OutlinedTextField(viewModel.cashApr, { viewModel.cashApr = it }, Modifier.fillMaxWidth(), label = { Text("Cash-advance APR") }, singleLine = true)
        ToggleRow("Accrued interest is known", viewModel.accruedKnown) { viewModel.accruedKnown = it }
        if (viewModel.accruedKnown) AmountField(viewModel.accrued, currency) { viewModel.accrued = it }
        OutlinedTextField(viewModel.rateUpdated, { viewModel.rateUpdated = it }, Modifier.fillMaxWidth(), label = { Text("Rate updated YYYY-MM-DD") }, singleLine = true)
        OutlinedTextField(viewModel.origination, { viewModel.origination = it }, Modifier.fillMaxWidth(), label = { Text("Origination YYYY-MM-DD") }, singleLine = true)
        OutlinedTextField(viewModel.maturity, { viewModel.maturity = it }, Modifier.fillMaxWidth(), label = { Text("Maturity YYYY-MM-DD") }, singleLine = true)
        OutlinedTextField(viewModel.term, { viewModel.term = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("Remaining term in months") }, singleLine = true)
        OutlinedTextField(viewModel.note, { viewModel.note = it }, Modifier.fillMaxWidth(), label = { Text("Note") })
        val series = listOf("" to "No linked schedule") + schedules.map { it.id to it.name }
        PickerField(
            "Linked repeating payment",
            series.firstOrNull { it.first == viewModel.recurringId }?.second ?: "No linked schedule",
            series,
            { it.second },
        ) { viewModel.recurringId = it.first }
        PickerField("Priority", viewModel.priority.label(), PaymentPriority.entries.toList(), { it.label() }) { viewModel.priority = it }
        PrimaryButton("Save details", onClick = viewModel::save)
        viewModel.message?.let { Text(it) }
    }
}

@Composable
private fun AccountPicker(label: String, selected: String, accounts: List<Account>, onSelect: (String) -> Unit) {
    val options = listOf("" to "Not linked") + accounts.map { it.id to it.name }
    PickerField(label, options.firstOrNull { it.first == selected }?.second ?: "Not linked", options, { it.second }) {
        onSelect(it.first)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun Debt.toInput(plans: List<com.ledgerline.app.domain.model.DebtMonthPlan>, rates: List<com.ledgerline.app.domain.model.DebtRateChange>, choice: PayChoice, fixed: Long, extra: Long) = ForecastInput(
    openingMinor = balanceMinor,
    start = YearMonth.now(),
    monthCount = 360,
    dueDayOfMonth = dueDayOfMonth,
    aprKnown = aprKnown,
    aprBasisPoints = aprBasisPoints,
    rateUpdatedOn = rateUpdatedOn,
    rateChanges = rates.map { RatePoint(it.effectiveOn, it.aprBasisPoints) },
    dayCountBasis = dayCountBasis,
    minimumKnown = minimumKnown,
    minimumMinor = minimumPaymentMinor,
    feeMinor = feeMinor,
    accruedInterestMinor = accruedInterestMinor,
    plans = plans.associate { it.yearMonth to it.plannedPaymentMinor },
    dueOverrides = plans.mapNotNull { plan -> plan.dueOn?.let { plan.yearMonth to it } }.toMap(),
    choice = choice,
    fixedMinor = fixed,
    extraMinor = extra,
    statementCloseDay = statementCloseDay,
)

private fun com.ledgerline.app.finance.debt.MonthLine.summary(currency: String): String {
    val interest = interestMinor?.let { MoneyFormat.format(it, currency) } ?: "Interest unknown"
    val payment = paymentMinor?.let { MoneyFormat.format(it, currency) } ?: "Payment not set"
    val principal = principalPaidMinor?.let { MoneyFormat.format(it, currency) } ?: "—"
    val closing = closingMinor?.let { MoneyFormat.format(it, currency) } ?: "—"
    val shortfall = unpaidInterestMinor?.takeIf { it > 0 }?.let { " Unpaid interest ${MoneyFormat.format(it, currency)}." } ?: ""
    return "$yearMonth · open ${MoneyFormat.format(openingMinor, currency)} · $interest · pay $payment · principal $principal · close $closing.$shortfall"
}

private fun comparisonText(compared: com.ledgerline.app.finance.debt.ScenarioComparison, currency: String): String {
    val difference = compared.interestDifferenceMinor?.let { MoneyFormat.format(kotlin.math.abs(it), currency) }
    val direction = compared.interestDifferenceMinor?.let { if (it < 0) "less" else "more" }
    val payoff = compared.alternative.payoffMonth?.toString() ?: "not shown"
    return if (difference == null || direction == null) {
        "The comparison needs an APR and a payment amount. Missing: ${compared.alternative.missing.joinToString(", ")}"
    } else {
        "Estimated interest is $difference $direction than paying only the minimum. Estimated payoff: $payoff. This is not a guaranteed saving."
    }
}

private fun Debt.missingSummary(): String {
    val missing = mutableListOf<String>()
    if (accountId == null) missing += "linked account"
    if (paymentAccountId == null) missing += "paying account"
    if (!aprKnown) missing += "APR"
    if (!minimumKnown) missing += "minimum payment"
    if (statementCloseDay == null) missing += "statement closing day"
    if (debtType == DebtType.EDUCATION_LOAN && rateType == RateType.UNKNOWN) missing += "fixed or floating rate"
    return if (missing.isEmpty()) "Ready for an estimate" else "Needed: ${missing.joinToString(", ")}"
}

private fun DebtType.label() = when (this) {
    DebtType.CREDIT_CARD -> "Credit card"
    DebtType.EDUCATION_LOAN -> "Education loan"
    DebtType.OTHER -> "Other debt"
}

private fun RateType.label() = when (this) {
    RateType.FIXED -> "Fixed"
    RateType.VARIABLE -> "Floating"
    RateType.UNKNOWN -> "Rate type unknown"
}

private fun String.toBasisPoints(): Int? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    val number = trimmed.toBigDecimalOrNull() ?: return null
    return number.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt()
}

private fun String.toDateOrNull(): LocalDate? = runCatching { LocalDate.parse(trim()) }.getOrNull()

private fun basisLabel(points: Int): String =
    BigDecimal.valueOf(points.toLong()).movePointLeft(2).stripTrailingZeros().toPlainString()

