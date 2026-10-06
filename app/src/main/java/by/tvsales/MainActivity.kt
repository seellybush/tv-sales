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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// --- Цветовая палитра "Светлый лазурит" ---
val LightLazuriteBg = Color(0xFFF4F7FC)
val LightLazuriteCard = Color(0xFFFFFFFF)
val LightLazuritePrimary = Color(0xFF3A86FF) // Основной синий
val LightLazuritePrimaryDark = Color(0xFF2E6BCC)
val LightLazuriteText = Color(0xFF1A1A1A)
val LightLazuriteTextSecondary = Color(0xFF6E6E6E)
val LightLazuriteAccentGreen = Color(0xFF2ECC71)

// --- Модели данных (упрощенные) ---
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

    Scaffold(
        topBar = {
            // Заголовок "ПАРСЕР ЖОРИЧА"
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(LightLazuritePrimary)
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
                    Triple("Планы", Icons.Default.DateRange, 2),
                    Triple("Настройки", Icons.Default.Settings, 3)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label, tint = if(tab == index) LightLazuritePrimary else LightLazuriteTextSecondary) },
                        label = { Text(label, color = if(tab == index) LightLazuritePrimary else LightLazuriteTextSecondary) },
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
                0 -> ScanScreen { sales = sales + it; tab = 1 }
                1 -> SalesScreen(sales)
                2 -> PlansScreen(sales)
                3 -> SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(save: (Sale) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<OnlinerProductSummary>()) }
    var selectedProduct by remember { mutableStateOf<OnlinerProductSummary?>(null) }
    var parsedPrice by remember { mutableStateOf<Double?>(null) }
    var priceSource by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Введите модель телевизора") }
    var loading by remember { mutableStateOf(false) }

    // Дебаунс для поиска
    LaunchedEffect(query) {
        if (query.length >= 3) {
            delay(500)
            suggestions = PriceRepository.searchModels(query)
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
        // Карточка поиска
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Поиск модели", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = query,
                    onValueChange = { 
                        query = it
                        selectedProduct = null
                        parsedPrice = null
                        message = "Введите модель..."
                    },
                    label = { Text("Например: Samsung UE55") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; suggestions = emptyList() }) {
                                Icon(Icons.Default.Clear, "Очистить")
                            }
                        }
                    }
                )

                // Выпадающий список подсказок
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
                                        query = product.full_name
                                        suggestions = emptyList()
                                        message = "Выбрано: ${product.full_name}"
                                    },
                                colors = CardDefaults.cardColors(containerColor = LightLazuriteBg)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(product.full_name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    product.prices?.price_min?.let {
                                        Text("от $it BYN", color = LightLazuritePrimary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Карточка результата парсинга
        if (selectedProduct != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Выбранный товар", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(selectedProduct!!.full_name, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                    
                    if (parsedPrice != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = LightLazuriteAccentGreen)
                            Text(
                                text = "Цена в 5 элементе: ${parsedPrice} BYN",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightLazuriteAccentGreen
                            )
                        }
                        Text("Источник: $priceSource", fontSize = 12.sp, color = LightLazuriteTextSecondary)
                    }
                }
            }
        }

        // Кнопка парсинга
        Button(
            onClick = {
                val product = selectedProduct ?: return@Button
                loading = true
                message = "Парсинг цены в 5 элементе..."
                scope.launch {
                    val result = PriceRepository.findPriceIn5Element(product.full_name)
                    if (result != null) {
                        parsedPrice = result.first
                        priceSource = result.second
                        message = "Цена найдена!"
                    } else {
                        message = "5 элемент не торгует этой моделью, либо цена не найдена."
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
                Text("Спарсить цену", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Выбор сотрудника
        if (parsedPrice != null) {
            var emp by remember { mutableStateOf("Егор") }
            var expanded by remember { mutableStateOf(false) }
            
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = emp,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Продал сотрудник") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    employees.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { emp = name; expanded = false }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    save(Sale(selectedProduct!!.full_name, parsedPrice!!, emp))
                    query = ""; selectedProduct = null; parsedPrice = null; suggestions = emptyList()
                    message = "Продажа сохранена!"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LightLazuriteAccentGreen)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить продажу", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Сообщение статуса
        Text(
            text = message,
            fontSize = 14.sp,
            color = LightLazuriteTextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun SalesScreen(sales: List<Sale>) {
    Column(Modifier.padding(16.dp)) {
        Text("История продаж", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Всего: ${sales.sumOf { it.price }} BYN", fontSize = 16.sp, color = LightLazuritePrimary)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sales) { sale ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(sale.model, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("${sale.price} BYN · ${sale.employee}") },
                        trailingContent = {
                            Icon(Icons.Default.Check, null, tint = LightLazuriteAccentGreen)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlansScreen(sales: List<Sale>) {
    var plans by remember {
        mutableStateOf(mapOf("Егор" to 25000.0, "Максим" to 22000.0, "Вова" to 20000.0))
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Планы на месяц", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        employees.forEach { name ->
            val fact = sales.filter { it.employee == name }.sumOf { it.price }
            var input by remember { mutableStateOf((plans[name] ?: 0.0).toString()) }
            
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = LightLazuriteCard),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("План BYN") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    val percent = if ((plans[name] ?: 0.0) > 0) (fact / (plans[name] ?: 1.0) * 100) else 0.0
                    Text("Факт: $fact BYN", fontSize = 14.sp)
                    Text("Выполнение: ${"%.1f".format(percent)}%", 
                        color = if (percent >= 100) LightLazuriteAccentGreen else LightLazuritePrimary,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    Button(
                        onClick = { input.toDoubleOrNull()?.let { plans = plans + (name to it) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Сохранить") }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = LightLazuriteCard)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Версия приложения: 0.4.0")
                Text("Сотрудники: Егор, Максим, Вова")
                Text("Парсер: Onliner (5 элемент)")
            }
        }
    }
}
