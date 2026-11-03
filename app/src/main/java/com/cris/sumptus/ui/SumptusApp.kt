package com.cris.sumptus.ui

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cris.sumptus.CategorySpendSummary
import com.cris.sumptus.CategoryBudgetSummary
import com.cris.sumptus.InvestmentMovement
import com.cris.sumptus.InvestmentOverview
import com.cris.sumptus.InvestmentSummary
import com.cris.sumptus.BudgetOverview
import com.cris.sumptus.MainViewModel
import com.cris.sumptus.MonthExpenseComparison
import com.cris.sumptus.RecentExpense
import com.cris.sumptus.SumptusUiState
import com.cris.sumptus.TimelineEntry
import com.cris.sumptus.data.ExpenseEntry
import com.cris.sumptus.data.ExtraEntry
import com.cris.sumptus.data.IncomeType
import com.cris.sumptus.formatExpenseInputDate
import com.cris.sumptus.i18n.AppLanguage
import com.cris.sumptus.i18n.LocalStrings
import com.cris.sumptus.i18n.Strings
import com.cris.sumptus.parseExpenseInputDate
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.absoluteValue

private enum class SumptusSection(
    val label: (Strings) -> String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    RESUMEN({ it.tabSummary }, Icons.Rounded.Home),
    ANADIR({ it.tabAdd }, Icons.Rounded.AddCircle),
    HISTORIAL({ it.tabHistory }, Icons.AutoMirrored.Rounded.ReceiptLong),
    PRESUPUESTO({ it.tabBudget }, Icons.Rounded.PieChart),
    INVERSIONES({ it.tabInvestments }, Icons.AutoMirrored.Rounded.TrendingUp),
    CATEGORIAS({ it.tabSettings }, Icons.Rounded.Settings),
}

private enum class EntryMode {
    GASTO,
    SUELDO,
    EXTRA,
}

private sealed interface EditorTarget {
    data class ExpenseTarget(val expense: ExpenseEntry) : EditorTarget
    data class ExtraTarget(val extra: ExtraEntry) : EditorTarget
}

private val str: Strings
    @Composable @ReadOnlyComposable get() = LocalStrings.current

private val expenseColor = Color(0xFFB25D66)
private val incomeColor = Color(0xFF4E8063)

@Composable
fun SumptusApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentSection by rememberSaveable { mutableStateOf(SumptusSection.RESUMEN) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                SumptusSection.entries.forEach { section ->
                    NavigationBarItem(
                        selected = currentSection == section,
                        onClick = { currentSection = section },
                        icon = { Icon(section.icon, contentDescription = section.label(str)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            when (currentSection) {
                SumptusSection.RESUMEN -> SummaryScreen(
                    uiState = uiState,
                    onSelectMonth = viewModel::selectMonth,
                    onPreviousMonth = viewModel::showPreviousMonth,
                    onNextMonth = viewModel::showNextMonth,
                    onSetCurrentBalance = viewModel::setCurrentBalance,
                )

                SumptusSection.ANADIR -> AddEntryScreen(
                    uiState = uiState,
                    editorTarget = editorTarget,
                    onSaveExpense = { existingExpense, amount, category, notes, occurredOn ->
                        val saved = viewModel.saveExpense(
                            existingExpense = existingExpense,
                            amountInput = amount,
                            category = category,
                            notes = notes,
                            occurredOnInput = occurredOn,
                        )
                        if (saved) {
                            editorTarget = null
                            currentSection = SumptusSection.HISTORIAL
                        }
                        saved
                    },
                    onSaveIncome = { existingIncome, amount, notes, occurredOn, type ->
                        val saved = viewModel.saveIncome(
                            existingExtra = existingIncome,
                            amountInput = amount,
                            notes = notes,
                            occurredOnInput = occurredOn,
                            type = type,
                        )
                        if (saved) {
                            editorTarget = null
                            currentSection = SumptusSection.HISTORIAL
                        }
                        saved
                    },
                    onCancelEdit = {
                        editorTarget = null
                        currentSection = SumptusSection.HISTORIAL
                    },
                    onOpenCategories = { currentSection = SumptusSection.CATEGORIAS },
                )

                SumptusSection.HISTORIAL -> HistoryScreen(
                    uiState = uiState,
                    onSelectMonth = viewModel::selectMonth,
                    onPreviousMonth = viewModel::showPreviousMonth,
                    onNextMonth = viewModel::showNextMonth,
                    onEditEntry = { entry ->
                        editorTarget = when (entry) {
                            is TimelineEntry.ExpenseItem -> EditorTarget.ExpenseTarget(entry.expense)
                            is TimelineEntry.ExtraItem -> EditorTarget.ExtraTarget(entry.extra)
                        }
                        currentSection = SumptusSection.ANADIR
                    },
                    onDeleteEntry = { entry ->
                        when (entry) {
                            is TimelineEntry.ExpenseItem -> viewModel.deleteExpense(entry.expense.id)
                            is TimelineEntry.ExtraItem -> viewModel.deleteIncome(entry.extra)
                        }
                    },
                )

                SumptusSection.PRESUPUESTO -> BudgetScreen(
                    uiState = uiState,
                    onSelectMonth = viewModel::selectMonth,
                    onPreviousMonth = viewModel::showPreviousMonth,
                    onNextMonth = viewModel::showNextMonth,
                    onSetCategoryBudget = viewModel::setCategoryBudget,
                    onSetGeneralExtraLimit = viewModel::setGeneralExtraLimit,
                )

                SumptusSection.INVERSIONES -> InvestmentsScreen(
                    overview = uiState.investmentOverview,
                    onSaveTransfer = viewModel::saveInvestmentTransfer,
                    onSetPotValue = viewModel::setGeneralPotValue,
                    onAddInvestment = viewModel::addInvestment,
                    onSaveAllocation = viewModel::saveInvestmentAllocation,
                    onSetInvestmentValue = viewModel::setInvestmentValue,
                    onDeleteInvestment = viewModel::deleteInvestment,
                    onDeleteMovement = viewModel::deleteInvestmentMovement,
                )

                SumptusSection.CATEGORIAS -> SettingsScreen(
                    uiState = uiState,
                    onAddCategory = viewModel::addCategory,
                    onDeleteCategory = viewModel::deleteCategory,
                    onSetCategoryColor = viewModel::setCategoryColor,
                    onSetDarkTheme = viewModel::setDarkTheme,
                    onSetLanguage = viewModel::setLanguage,
                    onExportBackup = viewModel::exportBackup,
                    onImportBackup = viewModel::importBackup,
                )
            }
        }
    }
}

@Composable
private fun SummaryScreen(
    uiState: SumptusUiState,
    onSelectMonth: (YearMonth) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSetCurrentBalance: (String) -> Boolean,
) {
    var editingBalance by rememberSaveable { mutableStateOf(false) }
    var balanceInput by rememberSaveable { mutableStateOf("") }

    if (editingBalance) {
        AlertDialog(
            onDismissRequest = { editingBalance = false },
            title = { Text(str.balanceOf(uiState.monthLabel)) },
            text = {
                OutlinedTextField(
                    value = balanceInput,
                    onValueChange = { balanceInput = it },
                    label = { Text(str.amount) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onSetCurrentBalance(balanceInput)) {
                            editingBalance = false
                        }
                    },
                ) {
                    Text(str.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBalance = false }) {
                    Text(str.cancel)
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MonthSelectorCard(
                selectedMonth = uiState.selectedMonth,
                monthLabel = uiState.monthLabel,
                onSelectMonth = onSelectMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )
        }

        item {
            SummaryTotalsCard(
                balanceCents = uiState.currentBalanceCents,
                expenseCents = uiState.monthExpenseTotalCents,
                incomeCents = uiState.monthIncomeTotalCents,
                netCents = uiState.monthNetCents,
                onEditBalance = {
                    balanceInput = formatAmountInput(uiState.currentBalanceCents)
                    editingBalance = true
                },
            )
        }

        item {
            ThreeMonthExpenseCard(uiState.lastThreeMonthExpenses, uiState.categoryColors)
        }

        item {
            RecentExpensesCard(uiState.recentExpenses, uiState.categoryColors)
        }

    }
}

@Composable
private fun AddEntryScreen(
    uiState: SumptusUiState,
    editorTarget: EditorTarget?,
    onSaveExpense: (ExpenseEntry?, String, String, String, String) -> Boolean,
    onSaveIncome: (ExtraEntry?, String, String, String, IncomeType) -> Boolean,
    onCancelEdit: () -> Unit,
    onOpenCategories: () -> Unit,
) {
    val editingExpense = (editorTarget as? EditorTarget.ExpenseTarget)?.expense
    val editingExtra = (editorTarget as? EditorTarget.ExtraTarget)?.extra
    val availableCategories = remember(uiState.categories, editingExpense?.category) {
        buildList {
            addAll(uiState.categories)
            val editingCategory = editingExpense?.category
            if (!editingCategory.isNullOrBlank() && none { it.equals(editingCategory, ignoreCase = true) }) {
                add(editingCategory)
            }
        }
    }

    var mode by rememberSaveable { mutableStateOf(EntryMode.GASTO) }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var notesInput by rememberSaveable { mutableStateOf("") }
    var dateInput by rememberSaveable { mutableStateOf(formatExpenseInputDate(LocalDate.now())) }
    var selectedCategory by rememberSaveable { mutableStateOf(availableCategories.firstOrNull().orEmpty()) }

    LaunchedEffect(editorTarget, availableCategories) {
        when {
            editingExpense != null -> {
                mode = EntryMode.GASTO
                amountInput = formatAmountInput(editingExpense.amountCents)
                notesInput = editingExpense.notes
                dateInput = formatExpenseInputDate(editingExpense.occurredOn)
                selectedCategory = editingExpense.category
            }

            editingExtra != null -> {
                mode = if (editingExtra.type == IncomeType.SALARY) EntryMode.SUELDO else EntryMode.EXTRA
                amountInput = formatAmountInput(editingExtra.amountCents)
                notesInput = editingExtra.notes
                dateInput = formatExpenseInputDate(editingExtra.occurredOn)
                selectedCategory = availableCategories.firstOrNull().orEmpty()
            }

            else -> {
                amountInput = ""
                notesInput = ""
                dateInput = formatExpenseInputDate(LocalDate.now())
                selectedCategory = availableCategories.firstOrNull().orEmpty()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (editorTarget != null) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancelEdit) {
                        Text(str.cancel)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = mode == EntryMode.GASTO,
                            onClick = { mode = EntryMode.GASTO },
                            label = { Text(str.expense) },
                        )
                        FilterChip(
                            selected = mode == EntryMode.SUELDO,
                            onClick = {
                                mode = EntryMode.SUELDO
                            },
                            label = { Text(str.salary) },
                        )
                        FilterChip(
                            selected = mode == EntryMode.EXTRA,
                            onClick = { mode = EntryMode.EXTRA },
                            label = { Text(str.extra) },
                        )
                    }

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(str.amount) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { dateInput = formatExpenseInputDate(LocalDate.now()) }) {
                            Text(str.today)
                        }
                        TextButton(onClick = { dateInput = formatExpenseInputDate(LocalDate.now().minusDays(1)) }) {
                            Text(str.yesterday)
                        }
                        DateWheelButton(
                            date = parseExpenseInputDate(dateInput) ?: LocalDate.now(),
                            onDateSelected = { dateInput = formatExpenseInputDate(it) },
                        )
                    }

                    if (mode == EntryMode.GASTO) {
                        if (availableCategories.isEmpty()) {
                            EmptyCategoryState(onOpenCategories = onOpenCategories)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = str.category,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                CategoryDropdown(
                                    categories = availableCategories,
                                    selectedCategory = selectedCategory,
                                    categoryColors = uiState.categoryColors,
                                    onSelectCategory = { selectedCategory = it },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text(str.note) },
                    )

                    Button(
                        onClick = {
                            val saved = when (mode) {
                                EntryMode.GASTO -> onSaveExpense(
                                    editingExpense,
                                    amountInput,
                                    selectedCategory,
                                    notesInput,
                                    dateInput,
                                )
                                EntryMode.SUELDO -> onSaveIncome(
                                    editingExtra,
                                    amountInput,
                                    notesInput,
                                    dateInput,
                                    IncomeType.SALARY,
                                )
                                EntryMode.EXTRA -> onSaveIncome(
                                    editingExtra,
                                    amountInput,
                                    notesInput,
                                    dateInput,
                                    IncomeType.EXTRA,
                                )
                            }

                            if (saved) {
                                amountInput = ""
                                notesInput = ""
                                dateInput = formatExpenseInputDate(LocalDate.now())
                                selectedCategory = availableCategories.firstOrNull().orEmpty()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                mode == EntryMode.SUELDO && editingExtra != null -> str.saveSalary
                                mode == EntryMode.SUELDO -> str.addSalary
                                mode == EntryMode.EXTRA && editingExtra != null -> str.saveExtra
                                mode == EntryMode.EXTRA -> str.addExtra
                                editingExpense != null -> str.saveExpense
                                else -> str.addExpense
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    uiState: SumptusUiState,
    onSelectMonth: (YearMonth) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEditEntry: (TimelineEntry) -> Unit,
    onDeleteEntry: (TimelineEntry) -> Unit,
) {
    val expandedGroups = remember { mutableStateListOf<String>() }
    val incomes = uiState.historyEntries.filterIsInstance<TimelineEntry.ExtraItem>()
    val salaries = incomes.filter { it.extra.type == IncomeType.SALARY }
    val extras = incomes.filter { it.extra.type == IncomeType.EXTRA }
    val expenseGroups = uiState.historyEntries
        .filterIsInstance<TimelineEntry.ExpenseItem>()
        .groupBy { it.expense.category }
        .toList()
        .sortedByDescending { (_, entries) -> entries.sumOf { it.amountCents } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MonthSelectorCard(
                selectedMonth = uiState.selectedMonth,
                monthLabel = uiState.monthLabel,
                onSelectMonth = onSelectMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )
        }

        if (uiState.historyEntries.isEmpty()) {
            item {
                EmptyCard(
                    title = str.noEntries,
                )
            }
        } else {
            if (salaries.isNotEmpty()) {
                item(key = "salary-group") {
                    HistoryGroupHeader(
                        title = str.salary,
                        totalCents = salaries.sumOf { it.amountCents },
                        color = incomeColor,
                        amountColor = incomeColor,
                        expanded = "salary" in expandedGroups,
                        onToggle = {
                            if ("salary" in expandedGroups) expandedGroups.remove("salary") else expandedGroups.add("salary")
                        },
                    )
                }
                if ("salary" in expandedGroups) {
                    items(salaries, key = ::timelineKey) { entry ->
                        HistoryRow(entry, uiState.categoryColors, onEdit = { onEditEntry(entry) }, onDelete = { onDeleteEntry(entry) })
                    }
                }
            }

            if (extras.isNotEmpty()) {
                item(key = "extra-group") {
                    HistoryGroupHeader(
                        title = str.extras,
                        totalCents = extras.sumOf { it.amountCents },
                        color = incomeColor,
                        amountColor = incomeColor,
                        expanded = "extra" in expandedGroups,
                        onToggle = {
                            if ("extra" in expandedGroups) expandedGroups.remove("extra") else expandedGroups.add("extra")
                        },
                    )
                }
                if ("extra" in expandedGroups) {
                    items(extras, key = ::timelineKey) { entry ->
                        HistoryRow(entry, uiState.categoryColors, onEdit = { onEditEntry(entry) }, onDelete = { onDeleteEntry(entry) })
                    }
                }
            }

            expenseGroups.forEach { (category, entries) ->
                val groupId = "expense:$category"
                item(key = groupId) {
                    HistoryGroupHeader(
                        title = category,
                        totalCents = entries.sumOf { it.amountCents },
                        color = categoryColor(category, uiState.categoryColors),
                        amountColor = expenseColor,
                        expanded = groupId in expandedGroups,
                        onToggle = {
                            if (groupId in expandedGroups) expandedGroups.remove(groupId) else expandedGroups.add(groupId)
                        },
                    )
                }
                if (groupId in expandedGroups) {
                    items(entries, key = ::timelineKey) { entry ->
                        HistoryRow(entry, uiState.categoryColors, onEdit = { onEditEntry(entry) }, onDelete = { onDeleteEntry(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetScreen(
    uiState: SumptusUiState,
    onSelectMonth: (YearMonth) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSetCategoryBudget: (String, String) -> Boolean,
    onSetGeneralExtraLimit: (String) -> Boolean,
) {
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var editingBudget by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.categories) {
        if (selectedCategory !in uiState.categories) {
            selectedCategory = uiState.categories.firstOrNull().orEmpty()
        }
    }
    LaunchedEffect(selectedCategory, uiState.selectedMonth, uiState.budgetOverview.categories) {
        amountInput = uiState.budgetOverview.categories
            .firstOrNull { it.category == selectedCategory }
            ?.let { formatAmountInput(it.baseCents) }
            .orEmpty()
    }

    if (editingBudget) {
        AlertDialog(
            onDismissRequest = { editingBudget = false },
            title = { Text(str.monthlyLimit) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryDropdown(
                        categories = uiState.categories,
                        selectedCategory = selectedCategory,
                        categoryColors = uiState.categoryColors,
                        onSelectCategory = { selectedCategory = it },
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(str.amount) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onSetCategoryBudget(selectedCategory, amountInput)) editingBudget = false
                }) {
                    Text(str.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBudget = false }) {
                    Text(str.cancel)
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MonthSelectorCard(
                selectedMonth = uiState.selectedMonth,
                monthLabel = uiState.monthLabel,
                onSelectMonth = onSelectMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )
        }

        if (uiState.categories.isEmpty()) {
            item { EmptyCard(title = str.noCategories) }
        } else {
            item {
                BudgetTotalsCard(
                    overview = uiState.budgetOverview,
                    extraLimitCents = uiState.generalExtraLimitCents,
                    onSetExtraLimit = onSetGeneralExtraLimit,
                )
            }
            if (uiState.budgetOverview.categories.isEmpty()) {
                item { EmptyCard(title = str.noBudgets) }
            } else {
                items(uiState.budgetOverview.categories, key = { it.category }) { budget ->
                    BudgetRow(
                        budget = budget,
                        color = categoryColor(budget.category, uiState.categoryColors),
                        onClick = {
                            selectedCategory = budget.category
                            amountInput = formatAmountInput(budget.baseCents)
                            editingBudget = true
                        },
                    )
                }
            }
            val newCategory = uiState.categories.firstOrNull { category ->
                uiState.budgetOverview.categories.none { it.category == category }
            }
            if (newCategory != null) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = {
                            selectedCategory = newCategory
                            amountInput = ""
                            editingBudget = true
                        }) {
                            Icon(Icons.Rounded.AddCircle, contentDescription = str.addLimit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(
    budget: CategoryBudgetSummary,
    color: Color,
    onClick: () -> Unit,
) {
    val remainingColor = if (budget.remainingCents >= 0L) incomeColor else expenseColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(budget.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
                Text(
                    str.baseSpent(formatCurrency(budget.baseCents), formatCurrency(budget.spentCents)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatSignedCurrency(budget.remainingCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = remainingColor,
                )
            }
        }
    }
}

@Composable
private fun BudgetTotalsCard(
    overview: BudgetOverview,
    extraLimitCents: Long?,
    onSetExtraLimit: (String) -> Boolean,
) {
    val carryoverColor = if (overview.carryoverCents >= 0L) incomeColor else expenseColor
    val remainingColor = if (overview.remainingCents >= 0L) incomeColor else expenseColor
    var editingExtraLimit by rememberSaveable { mutableStateOf(false) }
    var extraLimitInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(extraLimitCents) {
        extraLimitInput = extraLimitCents?.let(::formatAmountInput).orEmpty()
    }

    if (editingExtraLimit) {
        AlertDialog(
            onDismissRequest = { editingExtraLimit = false },
            title = { Text(str.generalExtraLimit) },
            text = {
                OutlinedTextField(
                    value = extraLimitInput,
                    onValueChange = { extraLimitInput = it },
                    label = { Text(str.amount) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onSetExtraLimit(extraLimitInput)) editingExtraLimit = false
                }) {
                    Text(str.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingExtraLimit = false }) {
                    Text(str.cancel)
                }
            },
        )
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(str.generalExtra, style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { editingExtraLimit = true }) {
                        Icon(Icons.Rounded.AddCircle, contentDescription = str.editGeneralExtraLimit)
                    }
                }
                Text(
                    formatSignedCurrency(overview.carryoverCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = carryoverColor,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(str.available, style = MaterialTheme.typography.labelLarge)
                Text(
                    formatSignedCurrency(overview.remainingCents),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = remainingColor,
                )
                Text(
                    str.baseSpent(formatCurrency(overview.baseCents), formatCurrency(overview.spentCents)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun CategoryDropdown(
    categories: List<String>,
    selectedCategory: String,
    categoryColors: Map<String, Long>,
    onSelectCategory: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                selectedCategory.ifBlank { str.category },
                modifier = Modifier.weight(1f),
                color = categoryColor(selectedCategory, categoryColors),
            )
            Icon(Icons.Rounded.ExpandMore, contentDescription = str.showCategories)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category, color = categoryColor(category, categoryColors)) },
                    onClick = {
                        onSelectCategory(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    uiState: SumptusUiState,
    onAddCategory: (String) -> Boolean,
    onDeleteCategory: (String) -> Unit,
    onSetCategoryColor: (String, Long) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
) {
    var newCategoryInput by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var pendingCategoryDeletion by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImportUri = uri }

    LaunchedEffect(uiState.categories) {
        if (selectedCategory !in uiState.categories) {
            selectedCategory = uiState.categories.firstOrNull().orEmpty()
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(str.replaceData) },
            text = { Text(str.replaceDataText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImportBackup(uri)
                        pendingImportUri = null
                    },
                ) {
                    Text(str.import)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(str.cancel)
                }
            },
        )
    }

    pendingCategoryDeletion?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingCategoryDeletion = null },
            title = { Text(str.deleteNamed(category)) },
            text = { Text(str.deleteCategoryText) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCategory(category)
                    pendingCategoryDeletion = null
                }) {
                    Text(str.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryDeletion = null }) {
                    Text(str.cancel)
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    FilterChip(
                        selected = uiState.isDarkTheme,
                        onClick = { onSetDarkTheme(!uiState.isDarkTheme) },
                        label = { Text(str.darkMode) },
                    )
                    Text(str.languageLabel, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { language ->
                            FilterChip(
                                selected = uiState.language == language,
                                onClick = { onSetLanguage(language) },
                                label = { Text(language.displayName) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(str.newCategory) },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            if (onAddCategory(newCategoryInput)) {
                                newCategoryInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(str.addCategory)
                    }
                    if (uiState.categories.isNotEmpty()) {
                        CategoryDropdown(
                            categories = uiState.categories,
                            selectedCategory = selectedCategory,
                            categoryColors = uiState.categoryColors,
                            onSelectCategory = { selectedCategory = it },
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            categoryColorPalette.forEach { colorArgb ->
                                val color = Color(colorArgb.toInt())
                                Box(
                                    modifier = Modifier
                                        .size(if (categoryColor(selectedCategory, uiState.categoryColors) == color) 28.dp else 22.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { onSetCategoryColor(selectedCategory, colorArgb) },
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { pendingCategoryDeletion = selectedCategory },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                            Text(str.deleteCategory)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { exportLauncher.launch("sumptus-backup.json") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(str.export)
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(str.import)
                }
            }
        }
    }
}

@Composable
private fun MonthSelectorCard(
    selectedMonth: YearMonth,
    monthLabel: String,
    onSelectMonth: (YearMonth) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = str.previousMonth)
                }
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = str.nextMonth)
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleMonths(selectedMonth), key = { it.toString() }) { month ->
                    FilterChip(
                        selected = month == selectedMonth,
                        onClick = { onSelectMonth(month) },
                        label = { Text(formatShortMonth(month)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryTotalsCard(
    balanceCents: Long,
    expenseCents: Long,
    incomeCents: Long,
    netCents: Long,
    onEditBalance: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditBalance),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(str.balance, style = MaterialTheme.typography.labelLarge)
                Text(str.edit, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = formatCurrency(balanceCents),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetric(str.expenses, formatCurrency(expenseCents), expenseColor, Modifier.weight(1f))
                SummaryMetric(str.income, formatCurrency(incomeCents), incomeColor, Modifier.weight(1f))
            }
            SummaryMetric(
                label = str.net,
                value = formatSignedCurrency(netCents),
                color = if (netCents >= 0L) incomeColor else expenseColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ThreeMonthExpenseCard(
    comparisons: List<MonthExpenseComparison>,
    categoryColors: Map<String, Long>,
) {
    val expandedMonths = remember { mutableStateListOf<String>() }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(str.spendingByMonth, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            comparisons.asReversed().forEach { comparison ->
                val key = comparison.month.toString()
                val expanded = key in expandedMonths
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (expanded) expandedMonths.remove(key) else expandedMonths.add(key)
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatShortMonth(comparison.month), modifier = Modifier.weight(1f))
                    Text(
                        formatCurrency(comparison.totalCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = expenseColor,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) str.hideCategories else str.showCategories,
                        tint = expenseColor,
                    )
                }
                if (expanded) {
                    if (comparison.categories.isEmpty()) {
                        Text(str.noExpenses, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        comparison.categories.forEach { category ->
                            ExpenseCategoryRow(category, categoryColors)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseCategoryRow(
    summary: CategorySpendSummary,
    categoryColors: Map<String, Long>,
) {
    val color = categoryColor(summary.category, categoryColors)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(summary.category, style = MaterialTheme.typography.bodyMedium, color = color)
            Text(formatCurrency(summary.totalCents), style = MaterialTheme.typography.bodyMedium, color = expenseColor)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(summary.share.coerceIn(0.04f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun RecentExpensesCard(
    expenses: List<RecentExpense>,
    categoryColors: Map<String, Long>,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visibleExpenses = if (expanded) expenses else expenses.take(3)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(str.latestExpenses, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (visibleExpenses.isEmpty()) {
                Text(str.noExpenses, style = MaterialTheme.typography.bodyMedium)
            } else {
                visibleExpenses.forEach { expense ->
                    RecentExpenseRow(expense, categoryColors)
                }
            }
            if (expenses.size > 3) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) str.showFewerExpenses else str.showMoreExpenses,
                            tint = expenseColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentExpenseRow(
    recentExpense: RecentExpense,
    categoryColors: Map<String, Long>,
) {
    val expense = recentExpense.expense
    val color = categoryColor(expense.category, categoryColors)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                expense.notes.ifBlank { expense.category },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(expense.category, style = MaterialTheme.typography.bodySmall, color = color)
                Text(
                    expense.occurredOn.format(DateTimeFormatter.ofPattern("d MMM", str.locale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                formatCurrency(expense.amountCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = expenseColor,
            )
            Text(
                formatCurrency(recentExpense.balanceAfterCents),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: TimelineEntry,
    categoryColors: Map<String, Long>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val amountColor = when (entry) {
        is TimelineEntry.ExpenseItem -> expenseColor
        is TimelineEntry.ExtraItem -> incomeColor
    }
    val subtitleColor = when (entry) {
        is TimelineEntry.ExpenseItem -> categoryColor(entry.expense.category, categoryColors)
        is TimelineEntry.ExtraItem -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(historyAccent(entry, categoryColors).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = historyBadge(entry),
                    color = historyAccent(entry, categoryColors),
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = historyTitle(entry),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = historySubtitle(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (entry is TimelineEntry.ExtraItem) {
                        formatSignedCurrency(entry.amountCents)
                    } else {
                        formatCurrency(entry.amountCents)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                )
                Text(
                    text = entry.occurredOn.format(DateTimeFormatter.ofPattern("d MMM", str.locale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = str.edit)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = str.delete)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryGroupHeader(
    title: String,
    totalCents: Long,
    color: Color,
    amountColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            Text(
                text = formatCurrency(totalCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                contentDescription = if (expanded) str.hide(title) else str.show(title),
            )
        }
    }
}

@Composable
private fun EmptyCard(
    title: String,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmptyCategoryState(onOpenCategories: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = str.noCategoriesYet,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(onClick = onOpenCategories) {
                Text(str.createCategories)
            }
        }
    }
}

@Composable
private fun DateWheelButton(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            ).apply {
                datePicker.calendarViewShown = false
                datePicker.spinnersShown = true
            }.show()
        },
    ) {
        Text(date.format(DateTimeFormatter.ofPattern("d MMM", str.locale)))
    }
}

private fun visibleMonths(selectedMonth: YearMonth): List<YearMonth> {
    val currentMonth = YearMonth.now()
    return listOf(-2L, -1L, 0L, 1L, 2L)
        .map { selectedMonth.plusMonths(it) }
        .filter { it <= currentMonth }
        .distinct()
}

private fun timelineKey(entry: TimelineEntry): String = when (entry) {
    is TimelineEntry.ExpenseItem -> "expense:${entry.id}"
    is TimelineEntry.ExtraItem -> "extra:${entry.id}"
}

private fun historyBadge(entry: TimelineEntry): String = when (entry) {
    is TimelineEntry.ExpenseItem -> entry.expense.category.take(1)
    is TimelineEntry.ExtraItem -> "+"
}

private fun historyAccent(entry: TimelineEntry, categoryColors: Map<String, Long>): Color = when (entry) {
    is TimelineEntry.ExpenseItem -> categoryColor(entry.expense.category, categoryColors)
    is TimelineEntry.ExtraItem -> incomeColor
}

@Composable
private fun historyTitle(entry: TimelineEntry): String = when (entry) {
    is TimelineEntry.ExpenseItem -> entry.expense.notes.ifBlank { str.expense }
    is TimelineEntry.ExtraItem -> entry.extra.notes.ifBlank { incomeLabel(entry.extra.type) }
}

@Composable
private fun historySubtitle(entry: TimelineEntry): String = when (entry) {
    is TimelineEntry.ExpenseItem -> entry.expense.category
    is TimelineEntry.ExtraItem -> incomeLabel(entry.extra.type)
}

@Composable
private fun incomeLabel(type: IncomeType): String = if (type == IncomeType.SALARY) str.salary else str.extra

private fun formatCurrency(amountCents: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).apply {
        currency = Currency.getInstance("EUR")
    }
    return formatter.format(amountCents / 100.0)
}

private fun formatSignedCurrency(amountCents: Long): String {
    val prefix = if (amountCents > 0) "+" else ""
    return prefix + formatCurrency(amountCents)
}

private fun formatAmountInput(amountCents: Long): String = String.format(
    Locale.US,
    "%.2f",
    amountCents / 100.0,
)

@Composable
private fun formatShortMonth(month: YearMonth): String {
    val locale = str.locale
    val formatter = DateTimeFormatter.ofPattern("MMM yy", locale)
    return month.atDay(1).format(formatter).replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(locale)
        } else {
            char.toString()
        }
    }
}

private val categoryColorPalette = listOf(
    0xFFC87870L,
    0xFF5C8E7EL,
    0xFF6E86B3L,
    0xFFC58A50L,
    0xFF9B709DL,
    0xFF4E9A9AL,
    0xFFB56F8EL,
    0xFF788769L,
)

private fun categoryColor(category: String, categoryColors: Map<String, Long>): Color {
    val colorArgb = categoryColors[category.lowercase(Locale.ROOT)]
        ?: categoryColorPalette[category.lowercase(Locale.ROOT).hashCode().absoluteValue % categoryColorPalette.size]
    return Color(colorArgb.toInt())
}

private sealed interface InvestmentDialog {
    data class Transfer(val toInvestments: Boolean) : InvestmentDialog
    data object PotValue : InvestmentDialog
    data object NewInvestment : InvestmentDialog
    data class Actions(val investmentId: String) : InvestmentDialog
    data class Allocate(val investmentId: String, val intoInvestment: Boolean) : InvestmentDialog
    data class Value(val investmentId: String) : InvestmentDialog
    data class ConfirmDelete(val investmentId: String) : InvestmentDialog
    data class ConfirmDeleteMovement(val movement: InvestmentMovement) : InvestmentDialog
}

@Composable
private fun InvestmentsScreen(
    overview: InvestmentOverview,
    onSaveTransfer: (amount: String, notes: String, date: String, toInvestments: Boolean) -> Boolean,
    onSetPotValue: (String) -> Boolean,
    onAddInvestment: (name: String, initialAmount: String) -> Boolean,
    onSaveAllocation: (investmentId: String, amount: String, intoInvestment: Boolean) -> Boolean,
    onSetInvestmentValue: (investmentId: String, amount: String) -> Boolean,
    onDeleteInvestment: (String) -> Unit,
    onDeleteMovement: (InvestmentMovement) -> Unit,
) {
    var dialog by remember { mutableStateOf<InvestmentDialog?>(null) }
    var showAllMovements by rememberSaveable { mutableStateOf(false) }
    val close = { dialog = null }
    fun summaryOf(id: String): InvestmentSummary? = overview.investments.firstOrNull { it.investment.id == id }

    when (val current = dialog) {
        null -> Unit
        is InvestmentDialog.Transfer -> TransferDialog(
            toInvestments = current.toInvestments,
            availableCents = if (current.toInvestments) null else overview.potValueCents,
            onDismiss = close,
            onSave = { amount, notes, date ->
                if (onSaveTransfer(amount, notes, date, current.toInvestments)) close()
            },
        )
        InvestmentDialog.PotValue -> AmountDialog(
            title = str.potValueTitle,
            supportingText = str.potValueHint,
            initialValue = formatAmountInput(overview.potValueCents),
            onDismiss = close,
            onSave = { if (onSetPotValue(it)) close() },
        )
        InvestmentDialog.NewInvestment -> NewInvestmentDialog(
            availableCents = overview.potValueCents,
            onDismiss = close,
            onSave = { name, amount -> if (onAddInvestment(name, amount)) close() },
        )
        is InvestmentDialog.Actions -> {
            val summary = summaryOf(current.investmentId)
            if (summary == null) {
                close()
            } else {
                AlertDialog(
                    onDismissRequest = close,
                    title = { Text(summary.investment.name) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            InvestmentAction(str.investMore) {
                                dialog = InvestmentDialog.Allocate(current.investmentId, intoInvestment = true)
                            }
                            InvestmentAction(str.withdrawToPot) {
                                dialog = InvestmentDialog.Allocate(current.investmentId, intoInvestment = false)
                            }
                            InvestmentAction(str.updateValue) { dialog = InvestmentDialog.Value(current.investmentId) }
                            InvestmentAction(str.delete, color = expenseColor) {
                                dialog = InvestmentDialog.ConfirmDelete(current.investmentId)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = close) { Text(str.close) } },
                )
            }
        }
        is InvestmentDialog.Allocate -> {
            val summary = summaryOf(current.investmentId)
            AmountDialog(
                title = if (current.intoInvestment) str.investIn(summary?.investment?.name.orEmpty()) else str.withdrawFrom(summary?.investment?.name.orEmpty()),
                supportingText = if (current.intoInvestment) {
                    str.availableInPot(formatCurrency(overview.potValueCents))
                } else {
                    str.backToPotWithValue(formatCurrency(summary?.valueCents ?: 0L))
                },
                initialValue = "",
                onDismiss = close,
                onSave = { if (onSaveAllocation(current.investmentId, it, current.intoInvestment)) close() },
            )
        }
        is InvestmentDialog.Value -> {
            val summary = summaryOf(current.investmentId)
            AmountDialog(
                title = str.valueOf(summary?.investment?.name.orEmpty()),
                supportingText = str.investedColon(formatCurrency(summary?.investedCents ?: 0L)),
                initialValue = formatAmountInput(summary?.valueCents ?: 0L),
                onDismiss = close,
                onSave = { if (onSetInvestmentValue(current.investmentId, it)) close() },
            )
        }
        is InvestmentDialog.ConfirmDelete -> {
            val summary = summaryOf(current.investmentId)
            AlertDialog(
                onDismissRequest = close,
                title = { Text(str.deleteQuestion(summary?.investment?.name.orEmpty())) },
                text = {
                    Text(str.deleteInvestmentText(formatCurrency(summary?.valueCents ?: 0L)))
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteInvestment(current.investmentId)
                        close()
                    }) { Text(str.delete, color = expenseColor) }
                },
                dismissButton = { TextButton(onClick = close) { Text(str.cancel) } },
            )
        }
        is InvestmentDialog.ConfirmDeleteMovement -> AlertDialog(
            onDismissRequest = close,
            title = { Text(str.deleteMovementQuestion) },
            text = { Text("${movementTitle(current.movement)}: ${formatCurrency(current.movement.amountCents.absoluteValue)}") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMovement(current.movement)
                    close()
                }) { Text(str.delete, color = expenseColor) }
            },
            dismissButton = { TextButton(onClick = close) { Text(str.cancel) } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(str.investments, style = MaterialTheme.typography.labelLarge)
                    Text(
                        formatCurrency(overview.totalValueCents),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryMetric(
                            str.contributed,
                            formatCurrency(overview.contributedCents),
                            MaterialTheme.colorScheme.onSurface,
                            Modifier.weight(1f),
                        )
                        SummaryMetric(
                            str.returnLabel,
                            formatSignedCurrency(overview.totalGainCents),
                            if (overview.totalGainCents >= 0L) incomeColor else expenseColor,
                            Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { dialog = InvestmentDialog.Transfer(toInvestments = true) }, modifier = Modifier.weight(1f)) {
                            Text(str.moveFromBalance)
                        }
                        OutlinedButton(onClick = { dialog = InvestmentDialog.Transfer(toInvestments = false) }, modifier = Modifier.weight(1f)) {
                            Text(str.backToBalance)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dialog = InvestmentDialog.PotValue },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(str.generalAccount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            str.interestTapToUpdate(formatSignedCurrency(overview.potGainCents)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        formatCurrency(overview.potValueCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        items(overview.investments, key = { it.investment.id }) { summary ->
            InvestmentRow(summary = summary, onClick = { dialog = InvestmentDialog.Actions(summary.investment.id) })
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { dialog = InvestmentDialog.NewInvestment }) {
                    Icon(Icons.Rounded.AddCircle, contentDescription = null)
                    Text("  " + str.newInvestment)
                }
            }
        }

        if (overview.movements.isNotEmpty()) {
            item {
                val visibleMovements = if (showAllMovements) overview.movements else overview.movements.take(5)
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(str.movements, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        visibleMovements.forEach { movement ->
                            InvestmentMovementRow(
                                movement = movement,
                                onDelete = { dialog = InvestmentDialog.ConfirmDeleteMovement(movement) },
                            )
                        }
                        if (overview.movements.size > 5) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                IconButton(onClick = { showAllMovements = !showAllMovements }) {
                                    Icon(
                                        imageVector = if (showAllMovements) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = if (showAllMovements) str.showLess else str.showMore,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvestmentAction(label: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    )
}

@Composable
private fun InvestmentRow(summary: InvestmentSummary, onClick: () -> Unit) {
    val gainCents = summary.valueCents - summary.investedCents
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    summary.investment.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    str.invested(formatCurrency(summary.investedCents)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCurrency(summary.valueCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    formatSignedCurrency(gainCents),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (gainCents >= 0L) incomeColor else expenseColor,
                )
            }
        }
    }
}

@Composable
private fun InvestmentMovementRow(movement: InvestmentMovement, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(movementTitle(movement), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(
                    movement.occurredOn.format(DateTimeFormatter.ofPattern("d MMM yyyy", str.locale)),
                    movement.notes,
                ).filter { it.isNotBlank() }.joinToString(" | "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatCurrency(movement.amountCents.absoluteValue), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = str.deleteMovement)
        }
    }
}

@Composable
private fun movementTitle(movement: InvestmentMovement): String = when (movement) {
    is InvestmentMovement.Transfer -> if (movement.amountCents >= 0L) str.balanceToPot else str.potToBalance
    is InvestmentMovement.Allocation -> {
        val name = movement.investmentName.ifBlank { str.investmentFallback }
        if (movement.amountCents >= 0L) str.potTo(name) else str.toPot(name)
    }
}

@Composable
private fun AmountDialog(
    title: String,
    supportingText: String?,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(str.amount) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(input) }) { Text(str.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
    )
}

@Composable
private fun TransferDialog(
    toInvestments: Boolean,
    availableCents: Long?,
    onDismiss: () -> Unit,
    onSave: (amount: String, notes: String, date: String) -> Unit,
) {
    var amountInput by rememberSaveable { mutableStateOf("") }
    var notesInput by rememberSaveable { mutableStateOf("") }
    var dateInput by rememberSaveable { mutableStateOf(formatExpenseInputDate(LocalDate.now())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (toInvestments) str.moveToInvestments else str.backToBalance) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    availableCents?.let { str.availableInPot(formatCurrency(it)) }
                        ?: str.transferHint,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(str.amount) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text(str.note) },
                    singleLine = true,
                )
                DateWheelButton(
                    date = parseExpenseInputDate(dateInput) ?: LocalDate.now(),
                    onDateSelected = { dateInput = formatExpenseInputDate(it) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(amountInput, notesInput, dateInput) }) { Text(str.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
    )
}

@Composable
private fun NewInvestmentDialog(
    availableCents: Long,
    onDismiss: () -> Unit,
    onSave: (name: String, initialAmount: String) -> Unit,
) {
    var nameInput by rememberSaveable { mutableStateOf("") }
    var amountInput by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str.newInvestment) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(str.investmentNameLabel) },
                    singleLine = true,
                )
                Text(str.availableInPot(formatCurrency(availableCents)), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(str.initialAmountOptional) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(nameInput, amountInput) }) { Text(str.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
    )
}
