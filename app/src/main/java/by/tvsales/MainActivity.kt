package by.tvsales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

val LightLazuriteBg = Color(0xFFF4F7FC)
val LightLazuriteCard = Color(0xFFFFFFFF)
val LightLazuritePrimary = Color(0xFF3A86FF)
val LightLazuriteText = Color(0xFF1A1A1A)
val LightLazuriteTextSecondary = Color(0xFF6E6E6E)
val LightLazuriteAccentGreen = Color(0xFF2ECC71)
val LightLazuriteAccentRed = Color(0xFFE74C3C)
val LightLazuriteOrange = Color(0xFFF39C12)

data class Sale(
    val model: String,
    val price: Double,
    val employee: String,
    val productId: String = "",
    val accessories: List<AccessoryItem> = emptyList(),
    val services: List<ServiceItem> = emptyList()
) {
    val total: Double get() = price + accessories.sumOf { it.price } + services.sumOf { it.price }
    val accessorySum: Double get() = accessories.sumOf { it.price }
    val serviceSum: Double get() = services.sumOf { it.price }
}

// Планы: товар / акс / сервис
data class EmployeePlan(
    val product: Double = 0.0,
    val accessories: Double = 0.0,
    val service: Double = 0.0
)

val employees = listOf("Егор", "Максим", "Вова")
val money: NumberFormat = NumberFormat.getCurrencyInstance(Locale("be", "BY"))

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = LightLazuritePrimary,
                    background = LightLazuriteBg,
                    surface = LightLazuriteCard,
                    onBackground = LightLazuriteText,
                    onSurface = LightLazuriteText
                )
            ) { App() }
        }
    }
}

@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    var sales by remember { mutableStateOf(listOf<Sale>()) }
    var currentEmployee by remember { mutableStateOf(employees.first()) }

    // Планы: изначально у всех product=25000, accessories=17% (4250), service=7% (1750)
    var plans by remember {
        mutableStateOf(
            mapOf(
                "Егор" to EmployeePlan(25000.0, 25000.0 * 0.17, 25000.0 * 0.07),
                "Максим" to EmployeePlan(22000.0, 22000.0 * 0.17, 22000.0 * 0.07),
                "Вова" to EmployeePlan(20000.0, 20000.0 * 0.17, 20000.0 * 0.07)
            )
        )
    }

    Scaffold(
        topBar = {
            Box(
                Modifier.fillMaxWidth().background(LightLazuritePrimary).statusBarsPadding()
                    .padding(vertical = 16.dp, horizontal = 20.dp)
            ) {
                Text("ПАРСЕР ЖОРИЧА", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        },
        bottomBar = {
            NavigationBar(containerColor = LightLazuriteCard, tonalElevation = 8.dp) {
                listOf(
                    Triple("Парсер", Icons.Default.Search, 0),
                    Triple("Продажи", Icons.Default.List, 1),
                    Triple("Настройки", Icons.Default.Settings, 2)
                ).forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, label, tint = if (tab == index) LightLazuritePrimary else LightLazuriteTextSecondary) },
                        label = { Text(label, color = if (tab == index) LightLazuritePrimary else LightLazuriteTextSecondary) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = LightLazuritePrimary.copy(alpha = 0.1f))
                    )
                }
            }
        },
        containerColor = LightLazuriteBg
    ) { p ->
        Box(Modifier.padding(p)) {
            when (tab) {
                0 -> ScanScreen(currentEmployee, { currentEmployee = it }, { sales = sales + it; tab = 1 })
                1 -> SalesScreen(sales, { sale -> sales = sales - sale }, { old, new -> sales = sales.map { if (it == old) new else it } })
                2 -> SettingsScreen(sales, plans, { newPlans -> plans = newPlans })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(currentEmployee: String, onEmployeeChange: (String) -> Unit, onSaleAdded: (Sale) -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<FiveElementProduct>()) }
    var selectedProduct by remember { mutableStateOf<FiveElementProduct?>(null) }
    var message by remember { mutableStateOf("Введите модель телевизора") }

    LaunchedEffect(query) {
        if (query.length >= 3) { delay(600); suggestions = PriceRepository.searchTVs(query) } else suggestions = emptyList()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var empExpanded by remember { mutableStateOf(false) }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
            elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Кто продаёт?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(empExpanded, { empExpanded = !empExpanded }) {
                    OutlinedTextField(value = currentEmployee, onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(empExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    ExposedDropdownMenu(empExpanded, { empExpanded = false }) {
                        employees.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { onEmployeeChange(name); empExpanded = false })
                        }
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
            elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Модель телевизора", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it; selectedProduct = null; message = "Введите модель..." },
                    label = { Text("Например: 55QLED780K") }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide(); scope.launch { suggestions = PriceRepository.searchTVs(query) }
                    }),
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; suggestions = emptyList() }) {
                            Icon(Icons.Default.Clear, "Очистить")
                        }
                    }
                )
                AnimatedVisibility(suggestions.isNotEmpty() && selectedProduct == null) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp).padding(top = 8.dp)) {
                        items(suggestions.size) { index ->
                            val product = suggestions[index]
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                keyboard?.hide(); selectedProduct = product; query = product.name
                                suggestions = emptyList(); message = "Выбрано: ${product.name}"
                            }, colors = CardDefaults.cardColors(containerColor = LightLazuriteBg)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(product.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text("${product.price} BYN", color = LightLazuritePrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedProduct?.let { sp ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
                elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Выбрано", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LightLazuriteTextSecondary)
                    Text(sp.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Text("${sp.price} BYN", color = LightLazuritePrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Button(
            onClick = {
                val product = selectedProduct ?: return@Button
                onSaleAdded(Sale(product.name, product.price, currentEmployee, product.id))
                message = "Добавлено: ${product.name} · ${product.price} BYN"
                query = ""; selectedProduct = null
            },
            enabled = selectedProduct != null,
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp))
            Text("Добавить", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Text(message, fontSize = 14.sp, color = LightLazuriteTextSecondary,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
fun SalesScreen(sales: List<Sale>, onDelete: (Sale) -> Unit, onUpdate: (Sale, Sale) -> Unit) {
    var selectedSale by remember { mutableStateOf<Sale?>(null) }

    Column(Modifier.padding(16.dp)) {
        Text("Статистика продаж", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Всего: ${"%.2f".format(sales.sumOf { it.total })} BYN",
            fontSize = 18.sp, color = LightLazuritePrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Нажми на ТВ, чтобы добавить акс/сервис", fontSize = 13.sp, color = LightLazuriteTextSecondary)
        Spacer(Modifier.height(8.dp))

        val recent = sales.takeLast(5).reversed()
        if (recent.isEmpty()) {
            Text("Пока нет продаж", color = LightLazuriteTextSecondary, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent) { sale ->
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
                        elevation = CardDefaults.cardElevation(1.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selectedSale = sale }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sale.model, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("${sale.total} BYN · ${sale.employee}", fontSize = 12.sp, color = LightLazuriteTextSecondary)
                                if (sale.accessories.isNotEmpty() || sale.services.isNotEmpty()) {
                                    Text("+ акс: ${sale.accessorySum} BYN · сервис: ${sale.serviceSum} BYN",
                                        fontSize = 11.sp, color = LightLazuriteAccentGreen)
                                }
                            }
                            IconButton(onClick = { onDelete(sale) }) {
                                Icon(Icons.Default.Delete, "Удалить", tint = LightLazuriteAccentRed)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedSale?.let { sale ->
        AccessoryPanel(sale, { selectedSale = null }, { updated -> onUpdate(sale, updated); selectedSale = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessoryPanel(sale: Sale, onDismiss: () -> Unit, onSave: (Sale) -> Unit) {
    val scope = rememberCoroutineScope()
    val accessories = remember { mutableStateListOf<AccessoryItem>().apply { addAll(sale.accessories) } }
    val services = remember { mutableStateListOf<ServiceItem>().apply { addAll(sale.services) } }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<FiveElementProduct>()) }
    var searching by remember { mutableStateOf(false) }
    var warrantyOptions by remember { mutableStateOf(listOf<WarrantyOption>()) }
    var loadingWarranty by remember { mutableStateOf(false) }

    LaunchedEffect(sale.productId) {
        if (sale.productId.isNotEmpty()) {
            loadingWarranty = true
            warrantyOptions = PriceRepository.fetchWarranty(sale.productId)
            loadingWarranty = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Акс и сервис: ${sale.model}", fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {

                if (accessories.isNotEmpty() || services.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = LightLazuritePrimary.copy(alpha = 0.05f))) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Добавлено:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LightLazuritePrimary)
                            accessories.forEachIndexed { i, acc ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔊 ${acc.name} · ${acc.price} BYN", Modifier.weight(1f), fontSize = 12.sp)
                                    IconButton(onClick = { accessories.removeAt(i) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, "Удалить", tint = LightLazuriteAccentRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            services.forEachIndexed { i, svc ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎬 ${svc.name} · ${svc.price} BYN", Modifier.weight(1f), fontSize = 12.sp)
                                    IconButton(onClick = { services.removeAt(i) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, "Удалить", tint = LightLazuriteAccentRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                Text("🔧 Поиск кронштейнов", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                        label = { Text("Кронштейн...") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        searching = true
                        scope.launch { searchResults = PriceRepository.searchBrackets(searchQuery); searching = false }
                    }, enabled = searchQuery.length >= 3 && !searching) { Text("Найти") }
                }
                if (searching) CircularProgressIndicator(Modifier.size(24.dp))
                searchResults.forEach { prod ->
                    Card(Modifier.fillMaxWidth().clickable {
                        accessories.add(AccessoryItem(prod.name, prod.price))
                        searchResults = emptyList(); searchQuery = ""
                    }, colors = CardDefaults.cardColors(containerColor = LightLazuriteBg)) {
                        Column(Modifier.padding(8.dp)) {
                            Text(prod.name, fontSize = 13.sp)
                            Text("${prod.price} BYN", color = LightLazuritePrimary, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text("🛡️ Доп. гарантия", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (loadingWarranty) CircularProgressIndicator(Modifier.size(20.dp))
                else if (warrantyOptions.isEmpty()) Text("Гарантия не найдена", fontSize = 12.sp, color = LightLazuriteTextSecondary)
                else warrantyOptions.forEach { w ->
                    Card(Modifier.fillMaxWidth().clickable {
                        val name = "Доп. гарантия ${w.years} год(а)"
                        if (services.none { it.name == name }) services.add(ServiceItem(name, w.price))
                    }, colors = CardDefaults.cardColors(containerColor = LightLazuriteBg)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("На ${w.years} год(а)", Modifier.weight(1f), fontSize = 13.sp)
                            Text("${w.price} BYN", color = LightLazuritePrimary, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text("🎬 Подписки", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                videoServices.forEach { svc ->
                    Card(Modifier.fillMaxWidth().clickable {
                        if (services.none { it.name == svc.name }) services.add(svc)
                    }, colors = CardDefaults.cardColors(containerColor = LightLazuriteBg)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(svc.name, Modifier.weight(1f), fontSize = 13.sp)
                            Text("${svc.price} BYN", color = LightLazuritePrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(sale.copy(accessories = accessories.toList(), services = services.toList()))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ===== НАСТРОЙКИ С КРАСИВЫМИ КРУГОВЫМИ ПРОГРЕССАМИ =====
@Composable
fun SettingsScreen(
    sales: List<Sale>,
    plans: Map<String, EmployeePlan>,
    onPlansChange: (Map<String, EmployeePlan>) -> Unit
) {
    var selectedEmployee by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Результат сотрудника", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        employees.forEach { name ->
            val employeeSales = sales.filter { it.employee == name }
            val factProduct = employeeSales.sumOf { it.price }
            val factAccessories = employeeSales.sumOf { it.accessorySum }
            val factService = employeeSales.sumOf { it.serviceSum }

            val plan = plans[name] ?: EmployeePlan()

            // Автоматический расчёт: если план акс/сервис = 0, считаем 17% / 7% от факта товара
            val planAccessories = if (plan.accessories > 0) plan.accessories else plan.product * 0.17
            val planService = if (plan.service > 0) plan.service else plan.product * 0.07

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            selectedEmployee = if (selectedEmployee == name) null else name
                        }) {
                            Icon(
                                if (selectedEmployee == name) Icons.Default.KeyboardArrowUp else Icons.Default.Edit,
                                "Редактировать план",
                                tint = LightLazuritePrimary
                            )
                        }
                    }

                    // Если карточка раскрыта — показываем поля для редактирования плана
                    AnimatedVisibility(selectedEmployee == name) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(Modifier.height(4.dp))
                            var productInput by remember(name) { mutableStateOf(plan.product.toString()) }
                            var accInput by remember(name) { mutableStateOf(planAccessories.toString()) }
                            var srvInput by remember(name) { mutableStateOf(planService.toString()) }

                            OutlinedTextField(value = productInput, onValueChange = { productInput = it },
                                label = { Text("План товар, BYN") }, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp))
                            OutlinedTextField(value = accInput, onValueChange = { accInput = it },
                                label = { Text("План аксессуары, BYN") }, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp))
                            OutlinedTextField(value = srvInput, onValueChange = { srvInput = it },
                                label = { Text("План доп. сервис, BYN") }, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp))
                            Button(
                                onClick = {
                                    val p = productInput.toDoubleOrNull() ?: plan.product
                                    val a = accInput.toDoubleOrNull() ?: planAccessories
                                    val s = srvInput.toDoubleOrNull() ?: planService
                                    onPlansChange(plans + (name to EmployeePlan(p, a, s)))
                                    selectedEmployee = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Сохранить") }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // === ТРИ БЛОКА С КРУГАМИ ===
                    MetricRow("Товар", factProduct, plan.product, LightLazuriteOrange)
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    MetricRow("Аксессуары", factAccessories, planAccessories, LightLazuriteOrange)
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    MetricRow("Доп. сервис", factService, planService, LightLazuriteAccentGreen)
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
            elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("О приложении", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Версия: 0.7.0", fontSize = 14.sp)
                Text("Сотрудники: ${employees.joinToString(", ")}", fontSize = 14.sp)
                Text("Источник: 5element.by (Diginetica)", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun MetricRow(title: String, fact: Double, plan: Double, color: Color) {
    val percent = if (plan > 0) (fact / plan * 100).coerceAtMost(999.0) else 0.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Результат", fontSize = 12.sp, color = LightLazuriteTextSecondary)
            Text(
                "%.0f".format(fact),
                fontSize = 20.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        CircularProgress(
            percent = percent,
            color = color,
            modifier = Modifier.size(110.dp)
        )
    }
}

@Composable
fun CircularProgress(percent: Double, color: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val size = this.size.minDimension - stroke
            // Фон
            drawArc(
                color = Color(0xFFE8EAF0),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size, size)
            )
            // Прогресс
            val sweep = (percent / 100.0 * 360.0).coerceAtMost(360.0).toFloat()
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size, size)
            )
        }
        Text(
            "%.2f%%".format(percent),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = LightLazuriteText
        )
    }
}
