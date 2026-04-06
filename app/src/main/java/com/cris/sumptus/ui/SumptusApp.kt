package com.cris.sumptus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cris.sumptus.CategorySpendSummary
import com.cris.sumptus.MainViewModel
import com.cris.sumptus.SumptusUiState
import com.cris.sumptus.data.ExpenseCategory
import com.cris.sumptus.data.ExpenseEntry
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private enum class SumptusSection(
    val label: String,
    val icon: ImageVector,
) {
    RESUMEN("Resumen", Icons.Rounded.Home),
    NUEVO("Nuevo", Icons.Rounded.AddCircle),
    HISTORIAL("Historial", Icons.Rounded.ReceiptLong),
}

@Composable
fun SumptusApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentSection by rememberSaveable { mutableStateOf(SumptusSection.RESUMEN) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
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
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF7EF),
                            Color(0xFFFFE6D4),
                            Color(0xFFF6D2C1),
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                when (currentSection) {
                    SumptusSection.RESUMEN -> DashboardScreen(
                        uiState = uiState,
                        onOpenAddExpense = { currentSection = SumptusSection.NUEVO },
                    )

                    SumptusSection.NUEVO -> AddExpenseScreen(
                        onSaveExpense = { title, amount, category, notes ->
                            val saved = viewModel.addExpense(
                                title = title,
                                amountInput = amount,
                                category = category,
                                notes = notes,
                            )
                            if (saved) {
                                currentSection = SumptusSection.HISTORIAL
                            }
                            saved
                        },
                    )

                    SumptusSection.HISTORIAL -> HistoryScreen(
                        uiState = uiState,
                        onDeleteExpense = viewModel::deleteExpense,
                        onOpenAddExpense = { currentSection = SumptusSection.NUEVO },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    uiState: SumptusUiState,
    onOpenAddExpense: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeaderBlock(
                kicker = "Sumptus",
                title = "Tus gastos, claros y al dia",
                subtitle = "Una vista rapida para no perder el control del mes.",
            )
        }

        item {
            SummaryCard(uiState = uiState, onOpenAddExpense = onOpenAddExpense)
        }

        item {
            InsightCard(uiState.insight)
        }

        item {
            CategoryBreakdownCard(uiState.categorySummaries)
        }

        item {
            RecentPreviewCard(uiState.recentExpenses.take(4), onOpenAddExpense = onOpenAddExpense)
        }
    }
}

@Composable
private fun AddExpenseScreen(
    onSaveExpense: (String, String, ExpenseCategory, String) -> Boolean,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(ExpenseCategory.COMIDA) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeaderBlock(
                kicker = "Nuevo gasto",
                title = "Anota el movimiento mientras esta fresco",
                subtitle = "El objetivo del MVP es que registrar te lleve menos de diez segundos.",
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Concepto") },
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Importe") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text("Puedes escribir 12,50 o 12.50") },
                    )

                    Text(
                        text = "Categoria",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ExpenseCategory.entries) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = categoryColor(category).copy(alpha = 0.18f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text("Notas") },
                        placeholder = { Text("Opcional: supermercado, gasolina, cena con amigos...") },
                    )

                    Button(
                        onClick = {
                            val saved = onSaveExpense(title, amount, selectedCategory, notes)
                            if (saved) {
                                title = ""
                                amount = ""
                                notes = ""
                                selectedCategory = ExpenseCategory.COMIDA
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Guardar gasto")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    uiState: SumptusUiState,
    onDeleteExpense: (String) -> Unit,
    onOpenAddExpense: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeaderBlock(
                kicker = "Historial",
                title = "Todos tus movimientos recientes",
                subtitle = "Puedes revisar y borrar entradas locales mientras definimos la siguiente iteracion.",
            )
        }

        if (uiState.recentExpenses.isEmpty()) {
            item {
                EmptyHistoryCard(onOpenAddExpense = onOpenAddExpense)
            }
        } else {
            items(uiState.recentExpenses, key = { it.id }) { expense ->
                ExpenseHistoryRow(
                    expense = expense,
                    onDeleteExpense = { onDeleteExpense(expense.id) },
                )
            }
        }
    }
}

@Composable
private fun HeaderBlock(
    kicker: String,
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = kicker.uppercase(Locale("es", "ES")),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SummaryCard(
    uiState: SumptusUiState,
    onOpenAddExpense: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1F4E5F),
                            Color(0xFF206A5D),
                            Color(0xFFE07A5F),
                        ),
                    ),
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = if (uiState.monthLabel.isBlank()) "Este mes" else uiState.monthLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text = formatCurrency(uiState.monthTotalCents),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricChip(
                    label = "Ultimos 7 dias",
                    value = formatCurrency(uiState.weekTotalCents),
                )
                MetricChip(
                    label = "Gasto medio",
                    value = formatCurrency(uiState.averageExpenseCents),
                )
            }
            Button(
                onClick = onOpenAddExpense,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Registrar un gasto")
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
) {
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun InsightCard(text: String) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Lectura rapida",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(summaries: List<CategorySpendSummary>) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Distribucion del mes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            if (summaries.isEmpty()) {
                Text(
                    text = "Todavia no hay movimientos este mes. En cuanto registres algunos, veras aqui donde se te va el dinero.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            } else {
                summaries.forEach { summary ->
                    CategoryBar(summary)
                }
            }
        }
    }
}

@Composable
private fun CategoryBar(summary: CategorySpendSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary.category.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatCurrency(summary.totalCents),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(summary.share.coerceIn(0.06f, 1f))
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(categoryColor(summary.category)),
            )
        }
    }
}

@Composable
private fun RecentPreviewCard(
    expenses: List<ExpenseEntry>,
    onOpenAddExpense: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ultimos movimientos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (expenses.isEmpty()) {
                    TextButton(onClick = onOpenAddExpense) {
                        Text("Anotar")
                    }
                }
            }

            if (expenses.isEmpty()) {
                Text(
                    text = "Todavia no has registrado nada. Empezamos con el primero cuando quieras.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                expenses.forEach { expense ->
                    ExpensePreviewRow(expense)
                }
            }
        }
    }
}

@Composable
private fun ExpensePreviewRow(expense: ExpenseEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(categoryColor(expense.category).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = expense.category.label.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = categoryColor(expense.category),
                fontWeight = FontWeight.Bold,
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = expense.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = expense.category.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }

        Text(
            text = formatCurrency(expense.amountCents),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyHistoryCard(onOpenAddExpense: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "El historial todavia esta vacio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Cuando empieces a registrar gastos, apareceran aqui ordenados de mas reciente a mas antiguo.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenAddExpense) {
                Text("Crear el primer gasto")
            }
        }
    }
}

@Composable
private fun ExpenseHistoryRow(
    expense: ExpenseEntry,
    onDeleteExpense: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
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
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(categoryColor(expense.category).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = expense.category.label.take(1),
                    color = categoryColor(expense.category),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(expense.category.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = categoryColor(expense.category).copy(alpha = 0.15f),
                            labelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    Text(
                        text = expense.occurredOn.format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (expense.notes.isNotBlank()) {
                    Text(
                        text = expense.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatCurrency(expense.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDeleteExpense) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Eliminar gasto",
                    )
                }
            }
        }
    }
}

private fun formatCurrency(amountCents: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("es", "ES")).apply {
        currency = Currency.getInstance("EUR")
    }
    return formatter.format(amountCents / 100.0)
}

private fun categoryColor(category: ExpenseCategory): Color = when (category) {
    ExpenseCategory.HOGAR -> Color(0xFF355C7D)
    ExpenseCategory.COMIDA -> Color(0xFFE56B6F)
    ExpenseCategory.TRANSPORTE -> Color(0xFF577590)
    ExpenseCategory.OCIO -> Color(0xFFF4A261)
    ExpenseCategory.SALUD -> Color(0xFF2A9D8F)
    ExpenseCategory.SUSCRIPCIONES -> Color(0xFF8D6A9F)
    ExpenseCategory.OTROS -> Color(0xFF7A7A7A)
}
