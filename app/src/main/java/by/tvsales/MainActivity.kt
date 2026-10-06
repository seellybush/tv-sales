package by.tvsales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

data class Product(val code: String, val model: String, val price: Double)
data class Sale(val product: Product, val serial: String, val employee: String, val surface: String)

val employees = listOf("Егор", "Максим", "Вова")

val products = listOf(
    Product("TEST385079", "Quantum 55U6BQ", 1699.0),
    Product("TEST384531", "Samsung UE55M80HAUXPY", 2399.0),
    Product("TEST379514", "LG 55QNED72B6B", 2190.0)
)

val money: NumberFormat = NumberFormat.getCurrencyInstance(Locale("be", "BY"))

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { App() }
    }
}

@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    var sales by remember { mutableStateOf(listOf<Sale>()) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val tabs = listOf("Сканировать", "Продажи", "Планы", "Настройки")
                tabs.forEachIndexed { i, title ->
                    NavigationBarItem(
                        selected = i == tab,
                        onClick = { tab = i },
                        icon = {},
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p)) {
            when (tab) {
                0 -> Scan { sales = sales + it; tab = 1 }
                1 -> Sales(sales)
                2 -> Plans(sales)
                3 -> Settings()
            }
        }
    }
}

@Composable
fun Scan(save: (Sale) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var emp by remember { mutableStateOf("Егор") }
    var surface by remember { mutableStateOf("ТВ-зал") }
    var product by remember { mutableStateOf<Product?>(null) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Новая продажа", style = MaterialTheme.typography.headlineMedium)
        Text("Тестовые коды: TEST385079, TEST384531, TEST379514")

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Код / штрихкод") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                product = products.find { it.code == code.trim() }
                message = if (product == null) "Товар не найден" else "Товар найден"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Найти в локальной базе")
        }

        product?.let { p ->
            Button(
                onClick = {
                    loading = true
                    message = "Запрашиваю цену..."
                    scope.launch {
                        val result = PriceRepository.findPrice(p.model)
                        if (result != null) {
                            product = p.copy(price = result.first)
                            message = "Цена обновлена: ${result.first} BYN (${result.second})"
                        } else {
                            message = "Не удалось получить цену"
                        }
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Подтянуть цену из сети")
            }
        }

        product?.let { p ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(p.model, style = MaterialTheme.typography.titleLarge)
                    Text("${p.price} BYN")
                }
            }
        }

        OutlinedTextField(
            value = serial,
            onValueChange = { serial = it },
            label = { Text("Серийный номер") },
            modifier = Modifier.fillMaxWidth()
        )

        var open by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = open,
            onExpandedChange = { open = !open }
        ) {
            OutlinedTextField(
                value = emp,
                onValueChange = {},
                readOnly = true,
                label = { Text("Кто продал") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = open,
                onDismissRequest = { open = false }
            ) {
                employees.forEach { n ->
                    DropdownMenuItem(
                        text = { Text(n) },
                        onClick = { emp = n; open = false }
                    )
                }
            }
        }

        OutlinedTextField(
            value = surface,
            onValueChange = { surface = it },
            label = { Text("Поверхность") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                product?.let {
                    save(Sale(it, serial, emp, surface))
                    message = "Продажа сохранена"
                }
            },
            enabled = product != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Подтвердить")
        }

        Text(message)
    }
}

@Composable
fun Sales(sales: List<Sale>) {
    Column(Modifier.padding(20.dp)) {
        Text("Продажи", style = MaterialTheme.typography.headlineMedium)
        Text("Всего: ${sales.sumOf { it.product.price }} BYN")
        LazyColumn {
            items(sales) { s ->
                ListItem(
                    headlineContent = { Text(s.product.model) },
                    supportingContent = {
                        Text(s.product.price.toString() + " BYN · " + s.employee + " · " + s.surface + " · S/N: " + s.serial)
                    }
                )
            }
        }
    }
}

@Composable
fun Plans(sales: List<Sale>) {
    var plans by remember {
        mutableStateOf(mapOf("Егор" to 25000.0, "Максим" to 22000.0, "Вова" to 20000.0))
    }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Планы на месяц", style = MaterialTheme.typography.headlineMedium)
        employees.forEach { n ->
            val fact = sales.filter { it.employee == n }.sumOf { it.product.price }
            var input by remember { mutableStateOf((plans[n] ?: 0.0).toString()) }
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(n, style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("План BYN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Факт: " + fact + " BYN")
                    Button(
                        onClick = { input.toDoubleOrNull()?.let { x -> plans = plans + (n to x) } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить план")
                    }
                }
            }
        }
    }
}

@Composable
fun Settings() {
    Column(Modifier.padding(20.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Text("Версия 0.3.0")
        Text("Сотрудники: Егор, Максим, Вова")
    }
}
