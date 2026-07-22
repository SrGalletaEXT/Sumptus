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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.cris.sumptus.MainViewModel
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
                        label = { Text(section.label) },
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

                SumptusSection.CATEGORIAS -> SettingsScreen(
                    uiState = uiState,
                    onAddCategory = viewModel::addCategory,
                    onDeleteCategory = viewModel::deleteCategory,
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
            title = { Text("Saldo actual") },
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
            Column(horizontalAlignment = Alignment.End) {
                MetricGrid(
                    primary = MetricValue("Saldo", formatCurrency(uiState.currentBalanceCents)),
                    secondary = listOf(
                        MetricValue("Gastado", formatCurrency(uiState.monthExpenseTotalCents)),
                        MetricValue("Ingresos", formatCurrency(uiState.monthIncomeTotalCents)),
                        MetricValue("Neto", formatSignedCurrency(uiState.monthNetCents)),
                    ),
                    onEditPrimary = {
                        balanceInput = formatAmountInput(uiState.currentBalanceCents)
                        editingBalance = true
                    },
                )
            }
        }

        item {
            MonthComparisonCard(
                previousMonthLabel = formatShortMonth(uiState.selectedMonth.minusMonths(1)),
                previousMonthExpenseTotalCents = uiState.previousMonthExpenseTotalCents,
                previousMonthNetCents = uiState.previousMonthNetCents,
                expenseDeltaCents = uiState.expenseDeltaFromPreviousMonthCents,
            )
        }

        item {
            CategoryBreakdownCard(uiState.categorySummaries)
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
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    availableCategories.forEach { category ->
                                        FilterChip(
                                            selected = selectedCategory == category,
                                            onClick = { selectedCategory = category },
                                            label = { Text(category) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = categoryColor(category).copy(alpha = 0.18f),
                                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                            ),
                                        )
                                    }
                                }
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
                        color = Color(0xFF2E7D32),
                        expanded = "salary" in expandedGroups,
                        onToggle = {
                            if ("salary" in expandedGroups) expandedGroups.remove("salary") else expandedGroups.add("salary")
                        },
                    )
                }
                if ("salary" in expandedGroups) {
                    items(salaries, key = ::timelineKey) { entry ->
                        HistoryRow(entry, onEdit = { onEditEntry(entry) }, onDelete = { onDeleteEntry(entry) })
                    }
                }
            }

            if (extras.isNotEmpty()) {
                item(key = "extra-group") {
                    HistoryGroupHeader(
                        title = "Extras",
                        totalCents = extras.sumOf { it.amountCents },
                        color = Color(0xFF2E7D32),
                        expanded = "extra" in expandedGroups,
                        onToggle = {
                            if ("extra" in expandedGroups) expandedGroups.remove("extra") else expandedGroups.add("extra")
                        },
                    )
                }
                if ("extra" in expandedGroups) {
                    items(extras, key = ::timelineKey) { entry ->
                        HistoryRow(entry, onEdit = { onEditEntry(entry) }, onDelete = { onDeleteEntry(entry) })
                    }
                }
            }

            expenseGroups.forEach { (category, entries) ->
                val groupId = "expense:$category"
                item(key = groupId) {
                    HistoryGroupHeader(
                        title = category,
                        totalCents = entries.sumOf { it.amountCents },
                        color = categoryColor(category),
                        expanded = groupId in expandedGroups,
                        onToggle = {
                            if (groupId in expandedGroups) expandedGroups.remove(groupId) else expandedGroups.add(groupId)
                        },
                    )
                }
                if (groupId in expandedGroups) {
                    items(entries, key = ::timelineKey) { entry ->
                        HistoryRow(entry, onEdit = { onEditEntry(entry) }, onDelete = { onDeleteEntry(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    uiState: SumptusUiState,
    onAddCategory: (String) -> Boolean,
    onDeleteCategory: (String) -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
) {
    var newCategoryInput by rememberSaveable { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImportUri = uri }

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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        uiState.categories.forEach { category ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = categoryColor(category).copy(alpha = 0.14f),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = category,
                                        modifier = Modifier.padding(start = 12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    IconButton(onClick = { onDeleteCategory(category) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar $category")
                                    }
                                }
                            }
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
private fun MetricGrid(
    primary: MetricValue,
    secondary: List<MetricValue>,
    onEditPrimary: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEditPrimary),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = primary.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    )
                    Text(
                        text = "Editar",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    )
                }
                Text(
                    text = primary.value,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        secondary.chunked(2).forEach { metrics ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics.forEach { metric ->
                    MetricCard(metric = metric, modifier = Modifier.weight(1f))
                }
                if (metrics.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    metric: MetricValue,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MonthComparisonCard(
    previousMonthLabel: String,
    previousMonthExpenseTotalCents: Long,
    previousMonthNetCents: Long,
    expenseDeltaCents: Long,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Comparación",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            ComparisonRow("Mes anterior", previousMonthLabel)
            ComparisonRow("Gastado", formatCurrency(previousMonthExpenseTotalCents))
            ComparisonRow("Neto", formatSignedCurrency(previousMonthNetCents))
            ComparisonRow("Cambio", formatSignedCurrency(expenseDeltaCents))
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CategoryBreakdownCard(summaries: List<CategorySpendSummary>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Categorías",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            if (summaries.isEmpty()) {
                Text(
                    text = "Sin gastos en este mes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                CategoryDistribution(summaries)
                summaries.forEach { summary ->
                    CategoryBar(summary)
                }
            }
        }
    }
}

@Composable
private fun CategoryDistribution(summaries: List<CategorySpendSummary>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(999.dp)),
    ) {
        summaries.forEach { summary ->
            Box(
                modifier = Modifier
                    .weight(summary.share.coerceAtLeast(0.03f))
                    .fillMaxHeight()
                    .background(categoryColor(summary.category)),
            )
        }
    }
}

@Composable
private fun CategoryBar(summary: CategorySpendSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(categoryColor(summary.category)),
                )
                Text(
                    text = summary.category,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${(summary.share * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                Text(
                    text = formatCurrency(summary.totalCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(summary.share.coerceIn(0.04f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(categoryColor(summary.category)),
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: TimelineEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val amountColor = when (entry) {
        is TimelineEntry.ExpenseItem -> MaterialTheme.colorScheme.onSurface
        is TimelineEntry.ExtraItem -> Color(0xFF2E7D32)
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
                    .background(historyAccent(entry).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = historyBadge(entry),
                    color = historyAccent(entry),
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
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
            )
            Text(
                text = formatCurrency(totalCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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

private data class MetricValue(
    val label: String,
    val value: String,
)

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

private fun historyAccent(entry: TimelineEntry): Color = when (entry) {
    is TimelineEntry.ExpenseItem -> categoryColor(entry.expense.category)
    is TimelineEntry.ExtraItem -> Color(0xFF2E7D32)
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

private fun categoryColor(category: String): Color {
    val palette = listOf(
        Color(0xFF355C7D),
        Color(0xFFE56B6F),
        Color(0xFF577590),
        Color(0xFFF4A261),
        Color(0xFF2A9D8F),
        Color(0xFF8D6A9F),
        Color(0xFF6C757D),
        Color(0xFFBC6C25),
    )
    return palette[category.lowercase().hashCode().absoluteValue % palette.size]
}
