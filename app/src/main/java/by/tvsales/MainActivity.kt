package by.tvsales

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

val TvStatsBg = Color(0xFFF0F4F8)
val TvStatsCard = Color(0xFFFFFFFF)
val TvStatsPrimary = Color(0xFF5B8DEF)
val TvStatsText = Color(0xFF1F2937)
val TvStatsTextSecondary = Color(0xFF6B7280)
val TvStatsGreen = Color(0xFF10B981)
val TvStatsRed = Color(0xFFEF4444)
val TvStatsOrange = Color(0xFFF59E0B)

val moneyFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("be", "BY")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

data class Sale(
    val model: String,
    val price: Double,
    val employee: String,
    val category: String = "tovar", // tovar / aks / service
    val productId: String = "",
    val productUrl: String = "",
    val accessories: List<AccessoryItem> = emptyList(),
    val services: List<ServiceItem> = emptyList()
) {
    val total: Double get() = price + accessories.sumOf { it.price } + services.sumOf { it.price }
    val accessorySum: Double get() = accessories.sumOf { it.price }
    val serviceSum: Double get() = services.sumOf { it.price }
}

data class EmployeePlan(
    val product: Double = 0.0,
    val accessories: Double = 0.0,
    val service: Double = 0.0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = TvStatsPrimary,
                    background = TvStatsBg,
                    surface = TvStatsCard,
                    onBackground = TvStatsText,
                    onSurface = TvStatsText
                )
            ) { App() }
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(DataStoreManager.defaultState) }
    var loaded by remember { mutableStateOf(false) }

    // Загрузка состояния
    LaunchedEffect(Unit) {
        DataStoreManager.getState(context).collect { s ->
            state = s
            loaded = true
        }
    }

    // Сохранение при изменении
    LaunchedEffect(state, loaded) {
        if (loaded) {
            DataStoreManager.saveState(context, state)
        }
    }

    val employees = state.employees
    var currentEmployee by remember { mutableStateOf("") }
    LaunchedEffect(employees) {
        if (currentEmployee.isEmpty() || currentEmployee !in employees) {
            currentEmployee = employees.firstOrNull() ?: ""
        }
    }

    Scaffold(
        topBar = {
            Box(
                Modifier.fillMaxWidth().background(TvStatsPrimary).statusBarsPadding()
                    .padding(vertical = 18.dp, horizontal = 20.dp)
            ) {
                Text("TV STATS", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, modifier = Modifier.align(Alignment.Center), letterSpacing = 2.sp)
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = TvStatsCard,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Triple("Парсер", Icons.Default.Search, 0),
                        Triple("Продажи", Icons.Default.List, 1),
                        Triple("Настройки", Icons.Default.Settings, 2)
                    ).forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = {
                                Icon(icon, label,
                                    tint = if (tab == index) TvStatsPrimary else TvStatsTextSecondary)
                            },
                            label = {
                                Text(label, fontSize = 11.sp,
                                    color = if (tab == index) TvStatsPrimary else TvStatsTextSecondary)
                            },
                            modifier = Modifier.width(88.dp),
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = TvStatsPrimary.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        },
        containerColor = TvStatsBg
    ) { p ->
        Box(Modifier.padding(p)) {
            when (tab) {
                0 -> ScanScreen(
                    employees = employees,
                    currentEmployee = currentEmployee,
                    onEmployeeChange = { currentEmployee = it },
                    onSaleAdded = { sale ->
                        state = state.copy(sales = state.sales + sale.toSaleData())
                        tab = 1
                    }
                )
                1 -> SalesScreen(
                    sales = state.sales.map { it.toSale() },
                    employees = employees,
                    onDelete = { sale ->
                        state = state.copy(sales = state.sales.filter { it.model != sale.model || it.price != sale.price })
                    },
                    onUpdate = { old, new ->
                        state = state.copy(sales = state.sales.map {
                            if (it.model == old.model && it.price == old.price) new.toSaleData() else it
                        })
                    }
                )
                2 -> SettingsScreen(
                    sales = state.sales.map { it.toSale() },
                    plans = state.plans.mapValues { EmployeePlan(it.value.product, it.value.accessories, it.value.service) },
                    employees = employees,
                    employeeBrands = state.employeeBrands,
                    onPlansChange = { newPlans ->
                        state = state.copy(plans = newPlans.mapValues { PlanData(it.value.product, it.value.accessories, it.value.service) })
                    },
                    onEmployeesChange = { newList -> state = state.copy(employees = newList) },
                    onBrandsChange = { newBrands -> state = state.copy(employeeBrands = newBrands) }
                )
            }
        }
    }
}

// ===== ПРЕОБРАЗОВАНИЯ =====
fun Sale.toSaleData(): SaleData = SaleData(
    model = model, price = price, employee = employee, category = category,
    productId = productId, productUrl = productUrl,
    accessories = accessories, services = services
)

fun SaleData.toSale(): Sale = Sale(
    model = model, price = price, employee = employee, category = category,
    productId = productId, productUrl = productUrl,
    accessories = accessories, services = services
)

// ===== ГЛАВНЫЙ ЭКРАН =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    employees: List<String>,
    currentEmployee: String,
    onEmployeeChange: (String) -> Unit,
    onSaleAdded: (Sale) -> Unit
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<FiveElementProduct>()) }
    var selectedProduct by remember { mutableStateOf<FiveElementProduct?>(null) }
    var message by remember { mutableStateOf("Введите модель телевизора") }
    var showCamera by remember { mutableStateOf(false) }

    // Иной товар
    var otherQuery by remember { mutableStateOf("") }
    var otherSuggestions by remember { mutableStateOf(listOf<FiveElementProduct>()) }
    var selectedOther by remember { mutableStateOf<FiveElementProduct?>(null) }
    var selectedCategory by remember { mutableStateOf("aks") } // aks / tovar
    var showOtherPanel by remember { mutableStateOf(false) }

    fun hideKeyboard() {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
    }

    fun searchWithVariants(model: String) {
        scope.launch {
            var found: List<FiveElementProduct> = emptyList()
            var usedModel = model
            for (variant in OcrHelper.generateSearchVariants(model)) {
                found = PriceRepository.searchTVs(variant)
                if (found.isNotEmpty()) { usedModel = variant; break }
            }
            if (found.isEmpty()) {
                query = model; suggestions = emptyList(); message = "Модель не найдена в каталоге"
            } else {
                query = usedModel; suggestions = found; message = "Распознано: $usedModel"
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                message = "Распознаю модель с фото..."
                val text = OcrHelper.recognizeText(context, uri)
                val model = OcrHelper.extractModel(text)
                if (model.isNotEmpty()) { selectedProduct = null; searchWithVariants(model) }
                else { message = "OCR: ${text.replace("\n", " ").take(150)}" }
            }
        }
    }

    LaunchedEffect(query) {
        if (query.length >= 3) { delay(600); suggestions = PriceRepository.searchTVs(query) } else suggestions = emptyList()
    }
    LaunchedEffect(otherQuery) {
        if (otherQuery.length >= 3) { delay(600); otherSuggestions = PriceRepository.searchOtherProducts(otherQuery) }
        else otherSuggestions = emptyList()
    }

    if (showCamera) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraScanner(
                onModelDetected = { model ->
                    selectedProduct = null; showCamera = false; searchWithVariants(model)
                },
                onDismiss = { showCamera = false }
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // === 1. МОДЕЛЬ ТВ ===
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
            elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Модель телевизора", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TvStatsText)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it; selectedProduct = null; message = "Введите модель..." },
                    label = { Text("Например: 55QLED780K") }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        hideKeyboard(); scope.launch { suggestions = PriceRepository.searchTVs(query) }
                    }),
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; suggestions = emptyList() }) {
                            Icon(Icons.Default.Clear, "Очистить")
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showCamera = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TvStatsGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.width(6.dp)); Text("Камера")
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(6.dp)); Text("Галерея")
                    }
                }
                AnimatedVisibility(suggestions.isNotEmpty() && selectedProduct == null) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(top = 8.dp)) {
                        items(suggestions.size) { index ->
                            val product = suggestions[index]
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                hideKeyboard(); selectedProduct = product; query = product.name
                                suggestions = emptyList(); message = "Выбрано: ${product.name}"
                            }, colors = CardDefaults.cardColors(containerColor = TvStatsBg)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(product.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TvStatsText)
                                    Text("${moneyFormat.format(product.price)} BYN", color = TvStatsPrimary,
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                selectedProduct?.let { sp ->
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = TvStatsBg)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sp.name, fontSize = 13.sp, color = TvStatsText)
                                Text("${moneyFormat.format(sp.price)} BYN", color = TvStatsPrimary,
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { selectedProduct = null; query = "" }) {
                                Icon(Icons.Default.Close, "Отмена", tint = TvStatsRed)
                            }
                        }
                    }
                }
            }
        }

        // === 2. ПОИСК ИНОГО ТОВАРА ===
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
            elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingCart, null, tint = TvStatsPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Поиск иного товара", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TvStatsText)
                }
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedCategory == "aks",
                        onClick = { selectedCategory = "aks" },
                        label = { Text("Акс") },
                        leadingIcon = { Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = selectedCategory == "tovar",
                        onClick = { selectedCategory = "tovar" },
                        label = { Text("Товар") },
                        leadingIcon = { Icon(Icons.Default.Inventory, null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = otherQuery,
                    onValueChange = { otherQuery = it; selectedOther = null },
                    label = { Text("Например: наушники, холодильник, порошок") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        hideKeyboard()
                        scope.launch { otherSuggestions = PriceRepository.searchOtherProducts(otherQuery) }
                    }),
                    trailingIcon = {
                        if (otherQuery.isNotEmpty()) IconButton(onClick = { otherQuery = ""; otherSuggestions = emptyList() }) {
                            Icon(Icons.Default.Clear, "Очистить")
                        }
                    }
                )

                AnimatedVisibility(otherSuggestions.isNotEmpty() && selectedOther == null) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(top = 8.dp)) {
                        items(otherSuggestions.size) { index ->
                            val product = otherSuggestions[index]
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                hideKeyboard(); selectedOther = product; otherQuery = product.name
                                otherSuggestions = emptyList()
                            }, colors = CardDefaults.cardColors(containerColor = TvStatsBg)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(product.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TvStatsText)
                                    Text("${moneyFormat.format(product.price)} BYN · ${if (product.category == "aks") "Акс" else "Товар"}",
                                        color = TvStatsPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                selectedOther?.let { sp ->
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = TvStatsBg)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sp.name, fontSize = 13.sp, color = TvStatsText)
                                Text("${moneyFormat.format(sp.price)} BYN", color = TvStatsPrimary,
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { selectedOther = null; otherQuery = "" }) {
                                Icon(Icons.Default.Close, "Отмена", tint = TvStatsRed)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val emp = currentEmployee.ifEmpty { employees.firstOrNull() ?: "" }
                            onSaleAdded(
                                Sale(
                                    model = sp.name,
                                    price = sp.price,
                                    employee = emp,
                                    category = selectedCategory,
                                    productId = sp.id,
                                    productUrl = sp.url
                                )
                            )
                            selectedOther = null; otherQuery = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TvStatsOrange),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp))
                        Text("Добавить ${if (selectedCategory == "aks") "акс" else "товар"}")
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Быстрое добавление сервиса
                Text("Добавить сервис", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TvStatsText)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 120.dp)) {
                    items(videoServices.size) { i ->
                        val svc = videoServices[i]
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                            val emp = currentEmployee.ifEmpty { employees.firstOrNull() ?: "" }
                            onSaleAdded(Sale(model = svc.name, price = svc.price, employee = emp, category = "service"))
                        }, colors = CardDefaults.cardColors(containerColor = TvStatsBg)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Subscriptions, null, tint = TvStatsPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(svc.name, Modifier.weight(1f), fontSize = 13.sp, color = TvStatsText)
                                Text("${moneyFormat.format(svc.price)} BYN", color = TvStatsPrimary,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // === 3. ПРОДАВЕЦ ===
        var empExpanded by remember { mutableStateOf(false) }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
            elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Кто продаёт?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TvStatsText)
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

        // === 4. ДОБАВИТЬ ТВ ===
        if (selectedProduct != null) {
            Button(
                onClick = {
                    val product = selectedProduct ?: return@Button
                    val emp = currentEmployee.ifEmpty { employees.firstOrNull() ?: "" }
                    onSaleAdded(
                        Sale(
                            model = product.name, price = product.price, employee = emp,
                            category = "tovar", productId = product.id, productUrl = product.url
                        )
                    )
                    message = "Добавлено: ${product.name}"
                    query = ""; selectedProduct = null
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TvStatsPrimary)
            ) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp))
                Text("Добавить ТВ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(message, fontSize = 14.sp, color = TvStatsTextSecondary,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

// ===== ПРОДАЖИ =====
@Composable
fun SalesScreen(
    sales: List<Sale>,
    employees: List<String>,
    onDelete: (Sale) -> Unit,
    onUpdate: (Sale, Sale) -> Unit
) {
    var selectedSale by remember { mutableStateOf<Sale?>(null) }
    var employeeDialogSale by remember { mutableStateOf<Sale?>(null) }

    Column(Modifier.padding(16.dp)) {
        Text("Статистика продаж", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TvStatsText)
        Spacer(Modifier.height(4.dp))
        Text("Всего: ${moneyFormat.format(sales.sumOf { it.total })} BYN",
            fontSize = 18.sp, color = TvStatsPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Последние 15 продаж", fontSize = 13.sp, color = TvStatsTextSecondary)
        Spacer(Modifier.height(8.dp))

        val recent = sales.takeLast(15).reversed()
        if (recent.isEmpty()) {
            Text("Пока нет продаж", color = TvStatsTextSecondary, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent) { sale ->
                    val catIcon = when (sale.category) {
                        "aks" -> Icons.Default.Build
                        "service" -> Icons.Default.Subscriptions
                        else -> Icons.Default.Inventory
                    }
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
                        elevation = CardDefaults.cardElevation(1.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selectedSale = sale }) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(catIcon, null, tint = TvStatsPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(sale.model, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TvStatsText)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${moneyFormat.format(sale.price)} BYN · ",
                                            fontSize = 13.sp, color = TvStatsPrimary, fontWeight = FontWeight.Bold)
                                        Text(sale.employee, fontSize = 13.sp, color = TvStatsPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { employeeDialogSale = sale })
                                    }
                                }
                                IconButton(onClick = { onDelete(sale) }) {
                                    Icon(Icons.Default.Delete, "Удалить", tint = TvStatsRed)
                                }
                            }
                            if (sale.accessories.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                sale.accessories.forEach { acc ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Build, null, tint = TvStatsTextSecondary, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("${acc.name} — ${moneyFormat.format(acc.price)} BYN",
                                            fontSize = 11.sp, color = TvStatsTextSecondary)
                                    }
                                }
                            }
                            if (sale.services.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                sale.services.forEach { svc ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Subscriptions, null, tint = TvStatsTextSecondary, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("${svc.name} — ${moneyFormat.format(svc.price)} BYN",
                                            fontSize = 11.sp, color = TvStatsTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    employeeDialogSale?.let { sale ->
        AlertDialog(
            onDismissRequest = { employeeDialogSale = null },
            title = { Text("Кто продал: ${sale.model}", fontSize = 15.sp, color = TvStatsText) },
            text = {
                Column {
                    employees.forEach { name ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                onUpdate(sale, sale.copy(employee = name))
                                employeeDialogSale = null
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (name == sale.employee) TvStatsPrimary.copy(alpha = 0.15f) else TvStatsBg
                            )
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (name == sale.employee) {
                                    Icon(Icons.Default.Check, null, tint = TvStatsPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TvStatsText)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { employeeDialogSale = null }) { Text("Отмена") }
            }
        )
    }

    selectedSale?.let { sale ->
        AccessoryPanel(sale, { selectedSale = null }, { updated -> onUpdate(sale, updated); selectedSale = null })
    }
}

// ===== ПАНЕЛЬ АКС / СЕРВИС =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessoryPanel(sale: Sale, onDismiss: () -> Unit, onSave: (Sale) -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val accessories = remember { mutableStateListOf<AccessoryItem>().apply { addAll(sale.accessories) } }
    val services = remember { mutableStateListOf<ServiceItem>().apply { addAll(sale.services) } }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<FiveElementProduct>()) }
    var searching by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf("bracket") }
    var warrantyOptions by remember { mutableStateOf(listOf<WarrantyOption>()) }
    var loadingWarranty by remember { mutableStateOf(false) }

    LaunchedEffect(sale.productUrl) {
        if (sale.productUrl.isNotEmpty()) {
            loadingWarranty = true
            warrantyOptions = PriceRepository.fetchWarrantyByUrl(sale.productUrl)
            loadingWarranty = false
        }
    }

    fun hideKeyboardNow() {
        keyboard?.hide(); focusManager.clearFocus(force = true)
        scope.launch { delay(80); keyboard?.hide(); focusManager.clearFocus(force = true) }
    }

    fun doSearch() {
        if (searchQuery.length < 3) return
        hideKeyboardNow()
        searching = true
        scope.launch {
            searchResults = if (searchMode == "bracket") PriceRepository.searchBrackets(searchQuery)
            else PriceRepository.searchSoundbars(searchQuery)
            searching = false; hideKeyboardNow()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Акс и сервис: ${sale.model}", fontSize = 15.sp, color = TvStatsText) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {

                if (accessories.isNotEmpty() || services.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = TvStatsPrimary.copy(alpha = 0.05f))) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Добавлено:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TvStatsPrimary)
                            accessories.forEachIndexed { i, acc ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Build, null, tint = TvStatsPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${acc.name} · ${moneyFormat.format(acc.price)} BYN", Modifier.weight(1f), fontSize = 12.sp)
                                    IconButton(onClick = { accessories.removeAt(i) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, "Удалить", tint = TvStatsRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            services.forEachIndexed { i, svc ->
                                val ic = when {
                                    svc.name.startsWith("Гарантия") -> Icons.Default.VerifiedUser
                                    else -> Icons.Default.Subscriptions
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(ic, null, tint = TvStatsPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${svc.name} · ${moneyFormat.format(svc.price)} BYN", Modifier.weight(1f), fontSize = 12.sp)
                                    IconButton(onClick = { services.removeAt(i) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, "Удалить", tint = TvStatsRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = TvStatsPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Расширенная гарантия", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TvStatsText)
                }
                if (loadingWarranty) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = TvStatsPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Загружаю гарантию...", fontSize = 12.sp, color = TvStatsTextSecondary)
                    }
                } else if (warrantyOptions.isEmpty()) {
                    Text("Гарантия для этой модели не найдена", fontSize = 12.sp, color = TvStatsTextSecondary)
                } else {
                    val selectedWarrantyName = services.firstOrNull { it.name.startsWith("Гарантия +") }?.name
                    warrantyOptions.forEach { w ->
                        val name = "Гарантия +${w.years} год"
                        val isSelected = selectedWarrantyName == name
                        Card(Modifier.fillMaxWidth().clickable {
                            services.removeAll { it.name.startsWith("Гарантия +") }
                            services.add(ServiceItem(name, w.price))
                        }, colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) TvStatsPrimary.copy(alpha = 0.15f) else TvStatsBg
                        )) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = TvStatsPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("+${w.years} год", Modifier.weight(1f), fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = TvStatsText)
                                Text("${moneyFormat.format(w.price)} BYN", color = TvStatsPrimary,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Build, null, tint = TvStatsPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Поиск аксессуаров", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TvStatsText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = searchMode == "bracket",
                        onClick = { searchMode = "bracket"; searchResults = emptyList() },
                        label = { Text("Кронштейны") },
                        leadingIcon = { Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = searchMode == "soundbar",
                        onClick = { searchMode = "soundbar"; searchResults = emptyList() },
                        label = { Text("Саундбары") },
                        leadingIcon = { Icon(Icons.Default.Speaker, null, modifier = Modifier.size(16.dp)) }
                    )
                }

                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    label = { Text(if (searchMode == "bracket") "Кронштейн..." else "Саундбар...") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { hideKeyboardNow(); doSearch() })
                )

                Button(
                    onClick = { hideKeyboardNow(); doSearch() },
                    enabled = searchQuery.length >= 3 && !searching,
                    colors = ButtonDefaults.buttonColors(containerColor = TvStatsPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("Ищу...")
                    } else {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Найти")
                    }
                }

                searchResults.forEach { prod ->
                    Card(Modifier.fillMaxWidth().clickable {
                        accessories.add(AccessoryItem(prod.name, prod.price))
                        searchResults = emptyList(); searchQuery = ""; hideKeyboardNow()
                    }, colors = CardDefaults.cardColors(containerColor = TvStatsBg)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            Icon(if (searchMode == "bracket") Icons.Default.Build else Icons.Default.Speaker,
                                null, tint = TvStatsPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(prod.name, fontSize = 13.sp, color = TvStatsText)
                                Text("${moneyFormat.format(prod.price)} BYN", color = TvStatsPrimary,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Subscriptions, null, tint = TvStatsPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Подписки и сервисы", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TvStatsText)
                }
                videoServices.forEach { svc ->
                    Card(Modifier.fillMaxWidth().clickable {
                        if (services.none { it.name == svc.name }) services.add(svc)
                    }, colors = CardDefaults.cardColors(containerColor = TvStatsBg)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Subscriptions, null, tint = TvStatsPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(svc.name, Modifier.weight(1f), fontSize = 13.sp, color = TvStatsText)
                            Text("${moneyFormat.format(svc.price)} BYN", color = TvStatsPrimary,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                hideKeyboardNow()
                onSave(sale.copy(accessories = accessories.toList(), services = services.toList()))
            }, colors = ButtonDefaults.buttonColors(containerColor = TvStatsPrimary)) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ===== НАСТРОЙКИ =====
@Composable
fun SettingsScreen(
    sales: List<Sale>,
    plans: Map<String, EmployeePlan>,
    employees: List<String>,
    employeeBrands: Map<String, String>,
    onPlansChange: (Map<String, EmployeePlan>) -> Unit,
    onEmployeesChange: (List<String>) -> Unit,
    onBrandsChange: (Map<String, String>) -> Unit
) {
    var selectedEmployee by remember { mutableStateOf<String?>(null) }
    var editingEmployeeIndex by remember { mutableStateOf<Int?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingBrandIndex by remember { mutableStateOf<Int?>(null) }
    var editingBrand by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Статистика брендов
    val brandCounts = mutableMapOf<String, Int>()
    var otherCount = 0
    sales.filter { it.category == "tovar" && it.model.contains("Телевизор", ignoreCase = true) }
        .forEach { sale ->
            val brand = detectBrand(sale.model)
            if (brand == "TCL" || brand == "LG" || brand == "Quantum") {
                brandCounts[brand] = (brandCounts[brand] ?: 0) + 1
            } else {
                otherCount++
            }
        }
    val totalTV = brandCounts.values.sum() + otherCount

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Результат сотрудника", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TvStatsText)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // === СТАТИСТИКА БРЕНДОВ ===
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
            elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Статистика брендов ТВ", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TvStatsText)
                Spacer(Modifier.height(8.dp))
                Text("Всего ТВ: $totalTV", fontSize = 14.sp, color = TvStatsTextSecondary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = TvStatsOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Иные ТВ: $otherCount", fontSize = 15.sp, color = TvStatsOrange, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                brandCounts.forEach { (brand, count) ->
                    Text("$brand: $count", fontSize = 14.sp, color = TvStatsPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }

        // === СОТРУДНИКИ ===
        employees.forEachIndexed { empIndex, name ->
            val employeeSales = sales.filter { it.employee == name }
            val factProduct = employeeSales.sumOf { it.price }
            val factAccessories = employeeSales.sumOf { it.accessorySum }
            val factService = employeeSales.sumOf { it.serviceSum }
            val brand = employeeBrands[name] ?: ""
            val brandSold = brandCounts[brand] ?: 0

            val plan = plans[name] ?: EmployeePlan()
            val planAccessories = if (plan.accessories > 0) plan.accessories else plan.product * 0.17
            val planService = if (plan.service > 0) plan.service else plan.product * 0.07

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
                elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TvStatsText)
                            if (brand.isNotEmpty()) {
                                Text("$brand · продано: $brandSold ТВ", fontSize = 13.sp, color = TvStatsPrimary,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                        IconButton(onClick = { editingEmployeeIndex = empIndex; editingName = name }) {
                            Icon(Icons.Default.Person, "Изменить имя", tint = TvStatsPrimary)
                        }
                        IconButton(onClick = { editingBrandIndex = empIndex; editingBrand = brand }) {
                            Icon(Icons.Default.Star, "Изменить бренд", tint = TvStatsOrange)
                        }
                        IconButton(onClick = {
                            selectedEmployee = if (selectedEmployee == name) null else name
                        }) {
                            Icon(if (selectedEmployee == name) Icons.Default.KeyboardArrowUp else Icons.Default.Edit,
                                "Редактировать план", tint = TvStatsPrimary)
                        }
                    }

                    AnimatedVisibility(selectedEmployee == name) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(Modifier.height(4.dp))
                            var productInput by remember(name) { mutableStateOf(plan.product.toInt().toString()) }
                            var accInput by remember(name) { mutableStateOf(planAccessories.toInt().toString()) }
                            var srvInput by remember(name) { mutableStateOf(planService.toInt().toString()) }

                            OutlinedTextField(value = productInput,
                                onValueChange = { new -> productInput = new.filter { it.isDigit() } },
                                label = { Text("План товар, BYN") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus(force = true) }))
                            OutlinedTextField(value = accInput,
                                onValueChange = { new -> accInput = new.filter { it.isDigit() } },
                                label = { Text("План аксессуары, BYN") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus(force = true) }))
                            OutlinedTextField(value = srvInput,
                                onValueChange = { new -> srvInput = new.filter { it.isDigit() } },
                                label = { Text("План доп. сервис, BYN") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus(force = true) }))
                            Button(onClick = {
                                val p = productInput.toDoubleOrNull() ?: plan.product
                                val a = accInput.toDoubleOrNull() ?: planAccessories
                                val s = srvInput.toDoubleOrNull() ?: planService
                                onPlansChange(plans + (name to EmployeePlan(p, a, s)))
                                selectedEmployee = null; keyboard?.hide(); focusManager.clearFocus(force = true)
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TvStatsPrimary)) { Text("Сохранить") }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    MetricRow("Товар", factProduct, plan.product)
                    Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
                    MetricRow("Аксессуары", factAccessories, planAccessories)
                    Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
                    MetricRow("Доп. сервис", factService, planService)
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
            elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("О приложении", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TvStatsText)
                Text("Версия: 1.19.0", fontSize = 14.sp, color = TvStatsTextSecondary)
                Text("Разработчик: Матранг", fontSize = 14.sp, color = TvStatsTextSecondary)
                Text("Сотрудники: ${employees.joinToString(", ")}", fontSize = 14.sp, color = TvStatsTextSecondary)
                Text("Источник: 5element.by", fontSize = 14.sp, color = TvStatsTextSecondary)
            }
        }
    }

    // Диалог изменения имени
    editingEmployeeIndex?.let { idx ->
        AlertDialog(
            onDismissRequest = { editingEmployeeIndex = null },
            title = { Text("Изменить имя сотрудника", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = editingName, onValueChange = { editingName = it },
                    label = { Text("Имя") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    val newList = employees.toMutableList()
                    if (idx in newList.indices && editingName.isNotBlank()) {
                        val oldName = newList[idx]
                        newList[idx] = editingName.trim()
                        onEmployeesChange(newList)
                        // Обновляем бренд
                        val newBrands = employeeBrands.toMutableMap()
                        employeeBrands[oldName]?.let { newBrands[editingName.trim()] = it }
                        newBrands.remove(oldName)
                        onBrandsChange(newBrands)
                    }
                    editingEmployeeIndex = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editingEmployeeIndex = null }) { Text("Отмена") } }
        )
    }

    // Диалог изменения бренда
    editingBrandIndex?.let { idx ->
        AlertDialog(
            onDismissRequest = { editingBrandIndex = null },
            title = { Text("Бренд сотрудника", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("TCL", "LG", "Quantum", "Samsung", "Hisense", "Haier", "Другой").forEach { b ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            editingBrand = b
                        }, colors = CardDefaults.cardColors(
                            containerColor = if (b == editingBrand) TvStatsPrimary.copy(alpha = 0.15f) else TvStatsBg
                        )) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (b == editingBrand) {
                                    Icon(Icons.Default.Check, null, tint = TvStatsPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(b, fontSize = 15.sp, color = TvStatsText)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val empName = employees.getOrNull(idx) ?: return@Button
                    onBrandsChange(employeeBrands + (empName to editingBrand))
                    editingBrandIndex = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editingBrandIndex = null }) { Text("Отмена") } }
        )
    }
}

// ===== ОПРЕДЕЛЕНИЕ БРЕНДА =====
fun detectBrand(modelName: String): String {
    val u = modelName.uppercase()
    return when {
        u.contains("SAMSUNG") || u.contains("UE") || u.contains("QE") -> "Samsung"
        u.contains("LG") || u.contains("QNED") || u.contains("OLED") || u.contains("NANO") -> "LG"
        u.contains("TCL") || u.contains("QLED") || u.contains("P79") || u.contains("MQLED") -> "TCL"
        u.contains("QUANTUM") || u.contains("КВАНТУМ") -> "Quantum"
        u.contains("HISENSE") -> "Hisense"
        u.contains("HAIER") -> "Haier"
        u.contains("XIAOMI") -> "Xiaomi"
        u.contains("SONY") -> "Sony"
        u.contains("PHILIPS") -> "Philips"
        else -> "Иной"
    }
}

// ===== МЕТРИКА =====
@Composable
fun MetricRow(title: String, fact: Double, plan: Double) {
    val percent = if (plan > 0) (fact / plan * 100).coerceAtMost(999.0) else 0.0
    val color = when {
        percent < 20.0 -> TvStatsRed
        percent < 60.0 -> TvStatsOrange
        else -> TvStatsGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TvStatsText)
            Spacer(Modifier.height(6.dp))
            Text("Результат", fontSize = 12.sp, color = TvStatsTextSecondary)
            Text(moneyFormat.format(fact), fontSize = 22.sp, color = color, fontWeight = FontWeight.Bold)
        }
        CircularProgress(percent = percent, color = color, modifier = Modifier.size(110.dp))
    }
}

@Composable
fun CircularProgress(percent: Double, color: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val size = this.size.minDimension - stroke
            drawArc(color = Color(0xFFE8EAF0), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size, size))
            val sweep = (percent / 100.0 * 360.0).coerceAtMost(360.0).toFloat()
            drawArc(color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size, size))
        }
        Text("%.1f%%".format(percent), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TvStatsText)
    }
}
