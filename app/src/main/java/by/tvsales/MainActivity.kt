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

val employees = listOf("Егор", "Максим", "Вова")

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
    var tab by remember { mutableIntStateOf(0) }
    var sales by remember { mutableStateOf(listOf<Sale>()) }
    var currentEmployee by remember { mutableStateOf(employees.first()) }
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
                Modifier.fillMaxWidth().background(TvStatsPrimary).statusBarsPadding()
                    .padding(vertical = 18.dp, horizontal = 20.dp)
            ) {
                Text("TV STATS", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, modifier = Modifier.align(Alignment.Center), letterSpacing = 2.sp)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = TvStatsCard, tonalElevation = 8.dp) {
                listOf(
                    Triple("Парсер", Icons.Default.Search, 0),
                    Triple("Продажи", Icons.Default.List, 1),
                    Triple("Настройки", Icons.Default.Settings, 2)
                ).forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, label, tint = if (tab == index) TvStatsPrimary else TvStatsTextSecondary) },
                        label = { Text(label, color = if (tab == index) TvStatsPrimary else TvStatsTextSecondary) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = TvStatsPrimary.copy(alpha = 0.1f))
                    )
                }
            }
        },
        containerColor = TvStatsBg
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
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<FiveElementProduct>()) }
    var selectedProduct by remember { mutableStateOf<FiveElementProduct?>(null) }
    var message by remember { mutableStateOf("Введите модель телевизора") }
    var showCamera by remember { mutableStateOf(false) }

    fun hideKeyboard() {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    fun searchWithVariants(model: String) {
        scope.launch {
            var found: List<FiveElementProduct> = emptyList()
            var usedModel = model
            for (variant in OcrHelper.generateSearchVariants(model)) {
                found = PriceRepository.searchTVs(variant)
                if (found.isNotEmpty()) {
                    usedModel = variant
                    break
                }
            }
            if (found.isEmpty()) {
                query = model
                suggestions = emptyList()
                message = "Модель не найдена в каталоге"
            } else {
                query = usedModel
                suggestions = found
                message = "Распознано: $usedModel"
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
                if (model.isNotEmpty()) {
                    selectedProduct = null
                    searchWithVariants(model)
                } else {
                    message = "OCR: ${text.replace("\n", " ").take(150)}"
                }
            }
        }
    }

    LaunchedEffect(query) {
        if (query.length >= 3) { delay(600); suggestions = PriceRepository.searchTVs(query) } else suggestions = emptyList()
    }

    if (showCamera) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraScanner(
                onModelDetected = { model ->
                    selectedProduct = null
                    showCamera = false
                    searchWithVariants(model)
                },
                onDismiss = { showCamera = false }
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        hideKeyboard()
                        scope.launch { suggestions = PriceRepository.searchTVs(query) }
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
                        Spacer(Modifier.width(6.dp))
                        Text("Камера")
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Галерея")
                    }
                }
                AnimatedVisibility(suggestions.isNotEmpty() && selectedProduct == null) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp).padding(top = 8.dp)) {
                        items(suggestions.size) { index ->
                            val product = suggestions[index]
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                hideKeyboard()
                                selectedProduct = product
                                query = product.name
                                suggestions = emptyList()
                                message = "Выбрано: ${product.name}"
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
            }
        }

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

        selectedProduct?.let { sp ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
                elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Выбрано", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TvStatsTextSecondary)
                    Text(sp.name, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = TvStatsText)
                    Text("${moneyFormat.format(sp.price)} BYN", color = TvStatsPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }

        Button(
            onClick = {
                val product = selectedProduct ?: return@Button
                onSaleAdded(Sale(product.name, product.price, currentEmployee,
                    productId = product.id, productUrl = product.url))
                message = "Добавлено: ${product.name}"
                query = ""; selectedProduct = null
            },
            enabled = selectedProduct != null,
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TvStatsPrimary)
        ) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp))
            Text("Добавить", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Text(message, fontSize = 14.sp, color = TvStatsTextSecondary,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
fun SalesScreen(sales: List<Sale>, onDelete: (Sale) -> Unit, onUpdate: (Sale, Sale) -> Unit) {
    var selectedSale by remember { mutableStateOf<Sale?>(null) }

    Column(Modifier.padding(16.dp)) {
        Text("Статистика продаж", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TvStatsText)
        Spacer(Modifier.height(4.dp))
        Text("Сумма ТВ: ${moneyFormat.format(sales.sumOf { it.price })} BYN",
            fontSize = 18.sp, color = TvStatsPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Нажми на ТВ, чтобы добавить акс/сервис", fontSize = 13.sp, color = TvStatsTextSecondary)
        Spacer(Modifier.height(8.dp))

        val recent = sales.takeLast(5).reversed()
        if (recent.isEmpty()) {
            Text("Пока нет продаж", color = TvStatsTextSecondary, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent) { sale ->
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
                        elevation = CardDefaults.cardElevation(1.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selectedSale = sale }) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(sale.model, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TvStatsText)
                                    Text("ТВ: ${moneyFormat.format(sale.price)} BYN · ${sale.employee}",
                                        fontSize = 13.sp, color = TvStatsPrimary, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { onDelete(sale) }) {
                                    Icon(Icons.Default.Delete, "Удалить", tint = TvStatsRed)
                                }
                            }
                            if (sale.accessories.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                sale.accessories.forEach { acc ->
                                    Text("  🔊 ${acc.name} — ${moneyFormat.format(acc.price)} BYN",
                                        fontSize = 11.sp, color = TvStatsTextSecondary)
                                }
                            }
                            if (sale.services.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                sale.services.forEach { svc ->
                                    Text("  🎬 ${svc.name} — ${moneyFormat.format(svc.price)} BYN",
                                        fontSize = 11.sp, color = TvStatsTextSecondary)
                                }
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

    fun hideKeyboard() {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    fun doSearch() {
        if (searchQuery.length < 3) return
        hideKeyboard()
        searching = true
        scope.launch {
            searchResults = if (searchMode == "bracket")
                PriceRepository.searchBrackets(searchQuery)
            else
                PriceRepository.searchSoundbars(searchQuery)
            searching = false
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
                                    Text("🔊 ${acc.name} · ${moneyFormat.format(acc.price)} BYN",
                                        Modifier.weight(1f), fontSize = 12.sp)
                                    IconButton(onClick = { accessories.removeAt(i) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, "Удалить", tint = TvStatsRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            services.forEachIndexed { i, svc ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎬 ${svc.name} · ${moneyFormat.format(svc.price)} BYN",
                                        Modifier.weight(1f), fontSize = 12.sp)
                                    IconButton(onClick = { services.removeAt(i) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, "Удалить", tint = TvStatsRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                Text("🛡️ Расширенная гарантия", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TvStatsText)
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
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = TvStatsText)
                                Text("${moneyFormat.format(w.price)} BYN", color = TvStatsPrimary,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text("🔍 Поиск аксессуаров", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TvStatsText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = searchMode == "bracket",
                        onClick = { searchMode = "bracket"; searchResults = emptyList() },
                        label = { Text("Кронштейны") }
                    )
                    FilterChip(
                        selected = searchMode == "soundbar",
                        onClick = { searchMode = "soundbar"; searchResults = emptyList() },
                        label = { Text("Саундбары") }
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(if (searchMode == "bracket") "Кронштейн..." else "Саундбар...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() })
                )

                Button(
                    onClick = { doSearch() },
                    enabled = searchQuery.length >= 3 && !searching,
                    colors = ButtonDefaults.buttonColors(containerColor = TvStatsPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (searching) "Ищу..." else "Найти") }

                if (searching) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = TvStatsPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Ищу...", fontSize = 12.sp, color = TvStatsTextSecondary)
                    }
                }

                searchResults.forEach { prod ->
                    Card(Modifier.fillMaxWidth().clickable {
                        accessories.add(AccessoryItem(prod.name, prod.price))
                        searchResults = emptyList(); searchQuery = ""
                        hideKeyboard()
                    }, colors = CardDefaults.cardColors(containerColor = TvStatsBg)) {
                        Column(Modifier.padding(8.dp)) {
                            Text(prod.name, fontSize = 13.sp, color = TvStatsText)
                            Text("${moneyFormat.format(prod.price)} BYN", color = TvStatsPrimary,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text("🎬 Подписки и сервисы", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TvStatsText)
                videoServices.forEach { svc ->
                    Card(Modifier.fillMaxWidth().clickable {
                        if (services.none { it.name == svc.name }) services.add(svc)
                    }, colors = CardDefaults.cardColors(containerColor = TvStatsBg)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                onSave(sale.copy(accessories = accessories.toList(), services = services.toList()))
            }, colors = ButtonDefaults.buttonColors(containerColor = TvStatsPrimary)) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun SettingsScreen(
    sales: List<Sale>,
    plans: Map<String, EmployeePlan>,
    onPlansChange: (Map<String, EmployeePlan>) -> Unit
) {
    var selectedEmployee by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Результат сотрудника", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TvStatsText)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        employees.forEach { name ->
            val employeeSales = sales.filter { it.employee == name }
            val factProduct = employeeSales.sumOf { it.price }
            val factAccessories = employeeSales.sumOf { it.accessorySum }
            val factService = employeeSales.sumOf { it.serviceSum }

            val plan = plans[name] ?: EmployeePlan()
            val planAccessories = if (plan.accessories > 0) plan.accessories else plan.product * 0.17
            val planService = if (plan.service > 0) plan.service else plan.product * 0.07

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
                elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f), color = TvStatsText)
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

                            OutlinedTextField(
                                value = productInput,
                                onValueChange = { new -> productInput = new.filter { it.isDigit() } },
                                label = { Text("План товар, BYN") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    keyboard?.hide(); focusManager.clearFocus()
                                })
                            )
                            OutlinedTextField(
                                value = accInput,
                                onValueChange = { new -> accInput = new.filter { it.isDigit() } },
                                label = { Text("План аксессуары, BYN") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    keyboard?.hide(); focusManager.clearFocus()
                                })
                            )
                            OutlinedTextField(
                                value = srvInput,
                                onValueChange = { new -> srvInput = new.filter { it.isDigit() } },
                                label = { Text("План доп. сервис, BYN") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    keyboard?.hide(); focusManager.clearFocus()
                                })
                            )
                            Button(
                                onClick = {
                                    val p = productInput.toDoubleOrNull() ?: plan.product
                                    val a = accInput.toDoubleOrNull() ?: planAccessories
                                    val s = srvInput.toDoubleOrNull() ?: planService
                                    onPlansChange(plans + (name to EmployeePlan(p, a, s)))
                                    selectedEmployee = null
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TvStatsPrimary)
                            ) { Text("Сохранить") }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    MetricRow("Товар", factProduct, plan.product)
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    MetricRow("Аксессуары", factAccessories, planAccessories)
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    MetricRow("Доп. сервис", factService, planService)
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TvStatsCard),
            elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("О приложении", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TvStatsText)
                Text("Версия: 1.13.0", fontSize = 14.sp, color = TvStatsTextSecondary)
                Text("Разработчик: Матранг", fontSize = 14.sp, color = TvStatsTextSecondary)
                Text("Сотрудники: ${employees.joinToString(", ")}", fontSize = 14.sp, color = TvStatsTextSecondary)
                Text("Источник: 5element.by", fontSize = 14.sp, color = TvStatsTextSecondary)
            }
        }
    }
}

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
            drawArc(
                color = Color(0xFFE8EAF0), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size, size)
            )
            val sweep = (percent / 100.0 * 360.0).coerceAtMost(360.0).toFloat()
            drawArc(
                color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size, size)
            )
        }
        Text("%.1f%%".format(percent), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TvStatsText)
    }
}
