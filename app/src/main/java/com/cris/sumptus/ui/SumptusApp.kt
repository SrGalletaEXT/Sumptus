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
import com.cris.sumptus.parseExpenseInputDate
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.absoluteValue

private enum class SumptusSection(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    RESUMEN("Resumen", Icons.Rounded.Home),
    ANADIR("Añadir", Icons.Rounded.AddCircle),
    HISTORIAL("Historial", Icons.AutoMirrored.Rounded.ReceiptLong),
    PRESUPUESTO("Presupuesto", Icons.Rounded.PieChart),
    CATEGORIAS("Categorías", Icons.Rounded.Settings),
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
                        icon = { Icon(section.icon, contentDescription = section.label) },
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

                SumptusSection.CATEGORIAS -> SettingsScreen(
                    uiState = uiState,
                    onAddCategory = viewModel::addCategory,
                    onDeleteCategory = viewModel::deleteCategory,
                    onSetCategoryColor = viewModel::setCategoryColor,
                    onSetDarkTheme = viewModel::setDarkTheme,
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
            title = { Text("Saldo de ${uiState.monthLabel}") },
            text = {
                OutlinedTextField(
                    value = balanceInput,
                    onValueChange = { balanceInput = it },
                    label = { Text("Importe") },
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
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBalance = false }) {
                    Text("Cancelar")
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
                        Text("Cancelar")
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
                            label = { Text("Gasto") },
                        )
                        FilterChip(
                            selected = mode == EntryMode.SUELDO,
                            onClick = {
                                mode = EntryMode.SUELDO
                            },
                            label = { Text("Sueldo") },
                        )
                        FilterChip(
                            selected = mode == EntryMode.EXTRA,
                            onClick = { mode = EntryMode.EXTRA },
                            label = { Text("Extra") },
                        )
                    }

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Importe") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { dateInput = formatExpenseInputDate(LocalDate.now()) }) {
                            Text("Hoy")
                        }
                        TextButton(onClick = { dateInput = formatExpenseInputDate(LocalDate.now().minusDays(1)) }) {
                            Text("Ayer")
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
                                    text = "Categoría",
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
                        label = { Text("Nota") },
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
                                mode == EntryMode.SUELDO && editingExtra != null -> "Guardar sueldo"
                                mode == EntryMode.SUELDO -> "Añadir sueldo"
                                mode == EntryMode.EXTRA && editingExtra != null -> "Guardar extra"
                                mode == EntryMode.EXTRA -> "Añadir extra"
                                editingExpense != null -> "Guardar gasto"
                                else -> "Añadir gasto"
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
                    title = "Sin movimientos",
                )
            }
        } else {
            if (salaries.isNotEmpty()) {
                item(key = "salary-group") {
                    HistoryGroupHeader(
                        title = "Sueldo",
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
                        title = "Extras",
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
            title = { Text("Límite mensual") },
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
                        label = { Text("Importe") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onSetCategoryBudget(selectedCategory, amountInput)) editingBudget = false
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBudget = false }) {
                    Text("Cancelar")
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
            item { EmptyCard(title = "Sin categorías") }
        } else {
            item {
                BudgetTotalsCard(
                    overview = uiState.budgetOverview,
                    extraLimitCents = uiState.generalExtraLimitCents,
                    onSetExtraLimit = onSetGeneralExtraLimit,
                )
            }
            if (uiState.budgetOverview.categories.isEmpty()) {
                item { EmptyCard(title = "Sin presupuestos") }
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
                            Icon(Icons.Rounded.AddCircle, contentDescription = "Añadir límite")
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
                    "Base ${formatCurrency(budget.baseCents)} | Gastado ${formatCurrency(budget.spentCents)}",
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
            title = { Text("Límite extra general") },
            text = {
                OutlinedTextField(
                    value = extraLimitInput,
                    onValueChange = { extraLimitInput = it },
                    label = { Text("Importe") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onSetExtraLimit(extraLimitInput)) editingExtraLimit = false
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingExtraLimit = false }) {
                    Text("Cancelar")
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
                    Text("Extra general", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { editingExtraLimit = true }) {
                        Icon(Icons.Rounded.AddCircle, contentDescription = "Editar límite extra general")
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
                Text("Disponible", style = MaterialTheme.typography.labelLarge)
                Text(
                    formatSignedCurrency(overview.remainingCents),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = remainingColor,
                )
                Text(
                    "Base ${formatCurrency(overview.baseCents)} | Gastado ${formatCurrency(overview.spentCents)}",
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
                selectedCategory.ifBlank { "Categoría" },
                modifier = Modifier.weight(1f),
                color = categoryColor(selectedCategory, categoryColors),
            )
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Mostrar categorías")
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
            title = { Text("Reemplazar datos") },
            text = { Text("Se sustituirán todos los movimientos y categorías actuales.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImportBackup(uri)
                        pendingImportUri = null
                    },
                ) {
                    Text("Importar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

    pendingCategoryDeletion?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingCategoryDeletion = null },
            title = { Text("Eliminar $category") },
            text = { Text("La categoría dejará de estar disponible en los nuevos gastos y presupuestos.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCategory(category)
                    pendingCategoryDeletion = null
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryDeletion = null }) {
                    Text("Cancelar")
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
                        label = { Text("Modo noche") },
                    )
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nueva categoría") },
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
                        Text("Añadir categoría")
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
                            Text("Eliminar categoría")
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
                    Text("Exportar")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Importar")
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
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Mes anterior")
                }
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Mes siguiente")
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
                Text("Saldo", style = MaterialTheme.typography.labelLarge)
                Text("Editar", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = formatCurrency(balanceCents),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetric("Gastos", formatCurrency(expenseCents), expenseColor, Modifier.weight(1f))
                SummaryMetric("Ingresos", formatCurrency(incomeCents), incomeColor, Modifier.weight(1f))
            }
            SummaryMetric(
                label = "Neto",
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
            Text("Gasto por mes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                        contentDescription = if (expanded) "Ocultar categorías" else "Mostrar categorías",
                        tint = expenseColor,
                    )
                }
                if (expanded) {
                    if (comparison.categories.isEmpty()) {
                        Text("Sin gastos", style = MaterialTheme.typography.bodyMedium)
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
            Text("Últimos gastos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (visibleExpenses.isEmpty()) {
                Text("Sin gastos", style = MaterialTheme.typography.bodyMedium)
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
                            contentDescription = if (expanded) "Mostrar menos gastos" else "Mostrar más gastos",
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
                    expense.occurredOn.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES"))),
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
                    text = entry.occurredOn.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES"))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar")
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
                contentDescription = if (expanded) "Ocultar $title" else "Mostrar $title",
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
                text = "No hay categorías.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(onClick = onOpenCategories) {
                Text("Crear categorías")
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
        Text(date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES"))))
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

private fun historyTitle(entry: TimelineEntry): String = when (entry) {
    is TimelineEntry.ExpenseItem -> entry.expense.notes.ifBlank { "Gasto" }
    is TimelineEntry.ExtraItem -> entry.extra.notes.ifBlank { incomeLabel(entry.extra.type) }
}

private fun historySubtitle(entry: TimelineEntry): String = when (entry) {
    is TimelineEntry.ExpenseItem -> entry.expense.category
    is TimelineEntry.ExtraItem -> incomeLabel(entry.extra.type)
}

private fun incomeLabel(type: IncomeType): String = if (type == IncomeType.SALARY) "Sueldo" else "Extra"

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

private fun formatShortMonth(month: YearMonth): String {
    val locale = Locale.forLanguageTag("es-ES")
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
