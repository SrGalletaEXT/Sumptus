package com.cris.sumptus.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class AppLanguage(val tag: String, val displayName: String) {
    ES("es-ES", "Español"),
    EN("en-GB", "English"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /** Spanish when the device is in Spanish, English otherwise. */
        fun systemDefault(): AppLanguage = if (Locale.getDefault().language == "es") ES else EN
    }
}

/** Every user-facing text, in Spanish and English side by side. */
class Strings(val language: AppLanguage) {
    val locale: Locale get() = language.locale
    private fun t(es: String, en: String) = if (language == AppLanguage.ES) es else en

    // Navigation
    val tabSummary get() = t("Resumen", "Summary")
    val tabAdd get() = t("Añadir", "Add")
    val tabHistory get() = t("Historial", "History")
    val tabBudget get() = t("Presupuesto", "Budget")
    val tabInvestments get() = t("Inversiones", "Investments")
    val tabSettings get() = t("Ajustes", "Settings")

    // Common
    val amount get() = t("Importe", "Amount")
    val save get() = t("Guardar", "Save")
    val cancel get() = t("Cancelar", "Cancel")
    val close get() = t("Cerrar", "Close")
    val delete get() = t("Eliminar", "Delete")
    val edit get() = t("Editar", "Edit")
    val note get() = t("Nota", "Note")
    val category get() = t("Categoría", "Category")
    val available get() = t("Disponible", "Available")
    fun deleteNamed(name: String) = t("Eliminar $name", "Delete $name")
    fun show(what: String) = t("Mostrar $what", "Show $what")
    fun hide(what: String) = t("Ocultar $what", "Hide $what")
    val showMore get() = t("Mostrar más", "Show more")
    val showLess get() = t("Mostrar menos", "Show less")
    val previousMonth get() = t("Mes anterior", "Previous month")
    val nextMonth get() = t("Mes siguiente", "Next month")

    // Summary
    fun balanceOf(month: String) = t("Saldo de $month", "Balance for $month")
    val balance get() = t("Saldo", "Balance")
    val expenses get() = t("Gastos", "Expenses")
    val income get() = t("Ingresos", "Income")
    val net get() = t("Neto", "Net")
    val spendingByMonth get() = t("Gasto por mes", "Spending by month")
    val noExpenses get() = t("Sin gastos", "No expenses")
    val latestExpenses get() = t("Últimos gastos", "Latest expenses")
    val showMoreExpenses get() = t("Mostrar más gastos", "Show more expenses")
    val showFewerExpenses get() = t("Mostrar menos gastos", "Show fewer expenses")
    val showCategories get() = t("Mostrar categorías", "Show categories")
    val hideCategories get() = t("Ocultar categorías", "Hide categories")

    // Add entry
    val expense get() = t("Gasto", "Expense")
    val salary get() = t("Sueldo", "Salary")
    val extra get() = t("Extra", "Extra")
    val extras get() = t("Extras", "Extras")
    val today get() = t("Hoy", "Today")
    val yesterday get() = t("Ayer", "Yesterday")
    val saveSalary get() = t("Guardar sueldo", "Save salary")
    val addSalary get() = t("Añadir sueldo", "Add salary")
    val saveExtra get() = t("Guardar extra", "Save extra")
    val addExtra get() = t("Añadir extra", "Add extra")
    val saveExpense get() = t("Guardar gasto", "Save expense")
    val addExpense get() = t("Añadir gasto", "Add expense")
    val noCategoriesYet get() = t("No hay categorías.", "No categories yet.")
    val createCategories get() = t("Crear categorías", "Create categories")

    // History
    val noEntries get() = t("Sin movimientos", "No entries")

    // Budget
    val monthlyLimit get() = t("Límite mensual", "Monthly limit")
    val noCategories get() = t("Sin categorías", "No categories")
    val noBudgets get() = t("Sin presupuestos", "No budgets")
    val addLimit get() = t("Añadir límite", "Add limit")
    fun baseSpent(base: String, spent: String) = t("Base $base | Gastado $spent", "Base $base | Spent $spent")
    val generalExtraLimit get() = t("Límite extra general", "General extra limit")
    val generalExtra get() = t("Extra general", "General extra")
    val editGeneralExtraLimit get() = t("Editar límite extra general", "Edit general extra limit")

    // Settings
    val replaceData get() = t("Reemplazar datos", "Replace data")
    val replaceDataText get() = t(
        "Se sustituirán todos los movimientos y categorías actuales.",
        "All current entries and categories will be replaced.",
    )
    val import get() = t("Importar", "Import")
    val export get() = t("Exportar", "Export")
    val deleteCategoryText get() = t(
        "La categoría dejará de estar disponible en los nuevos gastos y presupuestos.",
        "The category will no longer be available for new expenses and budgets.",
    )
    val darkMode get() = t("Modo noche", "Dark mode")
    val languageLabel get() = t("Idioma", "Language")
    val newCategory get() = t("Nueva categoría", "New category")
    val addCategory get() = t("Añadir categoría", "Add category")
    val deleteCategory get() = t("Eliminar categoría", "Delete category")

    // Investments
    val investments get() = t("Inversiones", "Investments")
    val generalAccount get() = t("Cuenta general", "General account")
    val contributed get() = t("Aportado", "Contributed")
    val returnLabel get() = t("Rentabilidad", "Return")
    val moveFromBalance get() = t("Mover desde saldo", "Move from balance")
    val backToBalance get() = t("Volver al saldo", "Back to balance")
    val moveToInvestments get() = t("Mover a inversiones", "Move to investments")
    fun interestTapToUpdate(gain: String) = t("Intereses $gain | Toca para actualizar", "Interest $gain | Tap to update")
    val newInvestment get() = t("Nueva inversión", "New investment")
    val movements get() = t("Movimientos", "Movements")
    val deleteMovement get() = t("Eliminar movimiento", "Delete movement")
    val deleteMovementQuestion get() = t("¿Eliminar movimiento?", "Delete movement?")
    val potValueTitle get() = t("Valor de la cuenta general", "General account value")
    val potValueHint get() = t("Pon lo que tienes ahora, intereses incluidos.", "Enter what you have now, interest included.")
    val investMore get() = t("Invertir más", "Invest more")
    val withdrawToPot get() = t("Retirar a la cuenta general", "Withdraw to general account")
    val updateValue get() = t("Actualizar valor", "Update value")
    fun investIn(name: String) = t("Invertir en $name", "Invest in $name")
    fun withdrawFrom(name: String) = t("Retirar de $name", "Withdraw from $name")
    fun availableInPot(amount: String) = t("Disponible en la cuenta general: $amount", "Available in general account: $amount")
    fun backToPotWithValue(value: String) = t(
        "Vuelve a la cuenta general. Valor actual: $value",
        "Goes back to the general account. Current value: $value",
    )
    fun valueOf(name: String) = t("Valor de $name", "Value of $name")
    fun investedColon(amount: String) = t("Invertido: $amount", "Invested: $amount")
    fun invested(amount: String) = t("Invertido $amount", "Invested $amount")
    fun deleteQuestion(name: String) = t("¿Eliminar $name?", "Delete $name?")
    fun deleteInvestmentText(value: String) = t(
        "Se borra con todos sus movimientos y valores; lo invertido vuelve a la cuenta general. Valor actual: $value.",
        "It is deleted with all its movements and values; the money put in goes back to the general account. Current value: $value.",
    )
    fun valueUpdatedFor(name: String) = t("Valor de $name", "$name value")
    val balanceToPot get() = t("Saldo → Cuenta general", "Balance → General account")
    val potToBalance get() = t("Cuenta general → Saldo", "General account → Balance")
    val investmentFallback get() = t("Inversión", "Investment")
    fun potTo(name: String) = t("Cuenta general → $name", "General account → $name")
    fun toPot(name: String) = t("$name → Cuenta general", "$name → General account")
    val transferHint get() = t(
        "Se resta del saldo y va a la cuenta general. No cuenta como gasto.",
        "Taken from the balance into the general account. Not counted as an expense.",
    )
    val investmentNameLabel get() = t("Nombre (ETF, acción...)", "Name (ETF, stock...)")
    val initialAmountOptional get() = t("Importe inicial (opcional)", "Initial amount (optional)")

    // Feedback messages
    val chooseCategory get() = t("Elige categoría.", "Choose a category.")
    val invalidAmount get() = t("Importe no válido.", "Invalid amount.")
    val invalidDate get() = t("Fecha no válida.", "Invalid date.")
    val expenseSaved get() = t("Gasto guardado.", "Expense saved.")
    val expenseUpdated get() = t("Gasto actualizado.", "Expense updated.")
    val expenseDeleted get() = t("Gasto eliminado.", "Expense deleted.")
    val salarySaved get() = t("Sueldo guardado.", "Salary saved.")
    val salaryUpdated get() = t("Sueldo actualizado.", "Salary updated.")
    val salaryDeleted get() = t("Sueldo eliminado.", "Salary deleted.")
    val extraSaved get() = t("Extra guardado.", "Extra saved.")
    val extraUpdated get() = t("Extra actualizado.", "Extra updated.")
    val extraDeleted get() = t("Extra eliminado.", "Extra deleted.")
    val invalidBalance get() = t("Saldo no válido.", "Invalid balance.")
    val balanceAdjusted get() = t("Saldo ajustado.", "Balance adjusted.")
    val typeCategory get() = t("Escribe una categoría.", "Type a category.")
    val categoryExists get() = t("La categoría ya existe.", "That category already exists.")
    val categoryAdded get() = t("Categoría añadida.", "Category added.")
    val categoryDeleted get() = t("Categoría eliminada.", "Category deleted.")
    val invalidBudget get() = t("Presupuesto no válido.", "Invalid budget.")
    val budgetSaved get() = t("Presupuesto guardado.", "Budget saved.")
    val invalidLimit get() = t("Límite no válido.", "Invalid limit.")
    val limitSaved get() = t("Límite guardado.", "Limit saved.")
    val dataExported get() = t("Datos exportados.", "Data exported.")
    val exportFailed get() = t("No se pudo exportar.", "Export failed.")
    val dataImported get() = t("Datos importados.", "Data imported.")
    val invalidBackup get() = t("Copia no válida.", "Invalid backup.")
    val importFailed get() = t("No se pudo importar.", "Import failed.")
    val notEnoughInPot get() = t("No hay tanto en la cuenta general.", "Not enough in the general account.")
    val investmentWorthLess get() = t("La inversión no vale tanto.", "The investment isn't worth that much.")
    val valueUpdated get() = t("Valor actualizado.", "Value updated.")
    val typeName get() = t("Escribe un nombre.", "Type a name.")
    val investmentExists get() = t("Esa inversión ya existe.", "That investment already exists.")
    val investmentAdded get() = t("Inversión añadida.", "Investment added.")
    val investmentDeleted get() = t("Inversión eliminada.", "Investment deleted.")
    val movedToInvestments get() = t("Movido a inversiones.", "Moved to investments.")
    val returnedToBalance get() = t("Devuelto al saldo.", "Returned to balance.")
    val moneyInvested get() = t("Dinero invertido.", "Money invested.")
    val returnedToPot get() = t("Devuelto a la cuenta general.", "Returned to the general account.")
    val movementDeleted get() = t("Movimiento eliminado.", "Movement deleted.")
}

val LocalStrings = staticCompositionLocalOf { Strings(AppLanguage.systemDefault()) }
