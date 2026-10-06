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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// --- Цветовая палитра "Светлый лазурит" ---
val LightLazuriteBg = Color(0xFFF4F7FC)
val LightLazuriteCard = Color(0xFFFFFFFF)
val LightLazuritePrimary = Color(0xFF3A86FF)
val LightLazuriteText = Color(0xFF1A1A1A)
val LightLazuriteTextSecondary = Color(0xFF6E6E6E)
val LightLazuriteAccentGreen = Color(0xFF2ECC71)
val LightLazuriteAccentRed = Color(0xFFE74C3C)

data class Sale(val model: String, val price: Double, val employee: String)

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
            ) {
                App()
            }
        }
    }
}

@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    var sales by remember { mutableStateOf(listOf<Sale>()) }
    var currentEmployee by remember { mutableStateOf(employees.first()) }

    Scaffold(
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(LightLazuritePrimary)
                    .statusBarsPadding()
                    .padding(vertical = 16.dp, horizontal = 20.dp)
            ) {
                Text(
                    text = "ПАРСЕР ЖОРИЧА",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = LightLazuriteCard, tonalElevation = 8.dp) {
                val items = listOf(
                    Triple("Парсер", Icons.Default.Search, 0),
                    Triple("Продажи", Icons.Default.List, 1),
                    Triple("Настройки", Icons.Default.Settings, 2)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                icon, contentDescription = label,
                                tint = if (tab == index) LightLazuritePrimary else LightLazuriteTextSecondary
                            )
                        },
                        label = {
                            Text(
                                label,
                                color = if (tab == index) LightLazuritePrimary else LightLazuriteTextSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = LightLazuritePrimary.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        },
        containerColor = LightLazuriteBg
    ) { p ->
        Box(Modifier.padding(p)) {
            when (tab) {
                0 -> ScanScreen(
                    currentEmployee = currentEmployee,
                    onEmployeeChange = { currentEmployee = it },
                    onSaleAdded = { sales = sales + it; tab = 1 }
                )
                1 -> SalesScreen(
                    sales = sales,
                    onDelete = { saleToRemove -> sales = sales - saleToRemove }
                )
                2 -> SettingsScreen(sales)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    currentEmployee: String,
    onEmployeeChange: (String) -> Unit,
    onSaleAdded: (Sale) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<FiveElementProduct>()) }
    var selectedProduct by remember { mutableStateOf<FiveElementProduct?>(null) }
    var message by remember { mutableStateOf("Введите модель телевизора") }
    var loading by remember { mutableStateOf(false) }

    // Автопоиск с задержкой (когда пользователь перестал печатать)
    LaunchedEffect(query) {
        if (query.length >= 3) {
            delay(600)
            suggestions = PriceRepository.searchTVs(query)
        } else {
            suggestions = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // === 1. ВЫБОР СОТРУДНИКА ===
        var empExpanded by remember { mutableStateOf(false) }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Кто продаёт?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = empExpanded,
                    onExpandedChange = { empExpanded = !empExpanded }
                ) {
                    OutlinedTextField(
                        value = currentEmployee,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(empExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = empExpanded,
                        onDismissRequest = { empExpanded = false }
                    ) {
                        employees.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onEmployeeChange(name)
                                    empExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // === 2. ПОИСК МОДЕЛИ ===
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Модель телевизора", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selectedProduct = null
                        message = "Введите модель..."
                    },
                    label = { Text("Например: Samsung UE55") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            scope.launch {
                                suggestions = PriceRepository.searchTVs(query)
                            }
                        }
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                suggestions = emptyList()
                            }) {
                                Icon(Icons.Default.Clear, "Очистить")
                            }
                        }
                    }
                )

                AnimatedVisibility(visible = suggestions.isNotEmpty() && selectedProduct == null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .padding(top = 8.dp)
                    ) {
                        items(suggestions) { product ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedProduct = product
                                        query = product.name
                                        suggestions = emptyList()
                                        message = "Выбрано: ${product.name}"
                                    },
                                colors = CardDefaults.cardColors(containerColor = LightLazuriteBg)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        product.name,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    product.price?.let {
                                        Text(
                                            "от $it BYN",
                                            color = LightLazuritePrimary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // === 3. ВЫБРАННЫЙ ТОВАР ===
        if (selectedProduct != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Выбрано", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LightLazuriteTextSecondary)
                    Text(selectedProduct!!.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                }
            }
        }

        // === 4. КНОПКА ПАРСИНГА ===
        Button(
            onClick = {
                val product = selectedProduct ?: return@Button
                loading = true
                message = "Парсинг цены в 5 элементе..."
                scope.launch {
                    val result = PriceRepository.findPriceIn5Element(product.id)
                    if (result != null) {
                        onSaleAdded(Sale(product.name, result.first, currentEmployee))
                        message = "Добавлено: ${result.first} BYN на $currentEmployee (${result.second})"
                        query = ""
                        selectedProduct = null
                    } else {
                        message = "5 элемент не торгует этой моделью"
                    }
                    loading = false
                }
            },
            enabled = selectedProduct != null && !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = message,
            fontSize = 14.sp,
            color = LightLazuriteTextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SalesScreen(sales: List<Sale>, onDelete: (Sale) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("Статистика продаж", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(4.dp))
        Text(
            "Всего: ${"%.2f".format(sales.sumOf { it.price })} BYN",
            fontSize = 18.sp,
            color = LightLazuritePrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))
        Text("Последние продажи", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))

        val recent = sales.takeLast(3).reversed()

        if (recent.isEmpty()) {
            Text(
                "Пока нет продаж",
                color = LightLazuriteTextSecondary,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent) { sale ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(sale.model, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    "${sale.price} BYN · ${sale.employee}",
                                    fontSize = 12.sp,
                                    color = LightLazuriteTextSecondary
                                )
                            }
                            IconButton(onClick = { onDelete(sale) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = LightLazuriteAccentRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(sales: List<Sale>) {
    var plans by remember {
        mutableStateOf(mapOf("Егор" to 25000.0, "Максим" to 22000.0, "Вова" to 20000.0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Планы на месяц", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                employees.forEach { name ->
                    val fact = sales.filter { it.employee == name }.sumOf { it.price }
                    var input by remember { mutableStateOf((plans[name] ?: 0.0).toString()) }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, fontWeight = FontWeight.Medium, fontSize = 15.sp)

                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            label = { Text("План BYN") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        val percent = if ((plans[name] ?: 0.0) > 0)
                            (fact / (plans[name] ?: 1.0) * 100) else 0.0

                        Text("Факт: $fact BYN", fontSize = 13.sp)
                        Text(
                            "Выполнение: ${"%.1f".format(percent)}%",
                            color = if (percent >= 100) LightLazuriteAccentGreen else LightLazuritePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        Button(
                            onClick = {
                                input.toDoubleOrNull()?.let { plans = plans + (name to it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Сохранить план")
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("О приложении", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Версия: 0.4.0", fontSize = 14.sp)
                Text("Сотрудники: ${employees.joinToString(", ")}", fontSize = 14.sp)
                Text("Источник цен: Onliner (5 элемент)", fontSize = 14.sp)
            }
        }
    }
}
