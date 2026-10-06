package by.tvsales
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Product(val code:String,val model:String,val price:Double)
data class Sale(val product:Product,val serial:String,val employee:String,val surface:String)
val employees=listOf("Егор","Максим","Вова")
val products=listOf(Product("TEST385079","Quantum 55U6BQ",1699.0),Product("TEST384531","Samsung UE55M80HAUXPY",2399.0),Product("TEST379514","LG 55QNED72B6B",2190.0))
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{App()}}}
@Composable fun App(){var tab by remember{mutableIntStateOf(0)};var sales by remember{mutableStateOf(listOf<Sale>())};Scaffold(bottomBar={NavigationBar{listOf("Сканировать","Продажи","Планы","Настройки").forEachIndexed{i,t->NavigationBarItem(i==tab,{tab=i},icon={},label={Text(t)})}}}){p->Box(Modifier.padding(p).fillMaxSize()){when(tab){0->Scan{sales=sales+it;tab=1};1->Sales(sales);2->Plans(sales);3->Settings()}}}}
@Composable fun Scan(save:(Sale)->Unit){var code by remember{mutableStateOf("")};var serial by remember{mutableStateOf("")};var emp by remember{mutableStateOf("Егор")};var surface by remember{mutableStateOf("ТВ-зал")};var product by remember{mutableStateOf<Product?>(null)};var message by remember{mutableStateOf("")};Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Новая продажа",style=MaterialTheme.typography.headlineMedium);Text("Тестовые коды: TEST385079, TEST384531, TEST379514");OutlinedTextField(code,{code=it},label={Text("Код / штрихкод")},Modifier.fillMaxWidth());Button({product=products.find{it.code==code.trim()};message=if(product==null)"Товар не найден" else "Товар найден"},Modifier.fillMaxWidth()){Text("Найти")};product?.let{Card{Column(Modifier.padding(14.dp)){Text(it.model,style=MaterialTheme.typography.titleLarge);Text("${it.price} BYN")}}};OutlinedTextField(serial,{serial=it},label={Text("Серийный номер")},Modifier.fillMaxWidth());OutlinedTextField(emp,{emp=it},label={Text("Кто продал")},Modifier.fillMaxWidth());OutlinedTextField(surface,{surface=it},label={Text("Поверхность")},Modifier.fillMaxWidth());Button({product?.let{save(Sale(it,serial,emp,surface));message="Продажа сохранена"}},product!=null,Modifier.fillMaxWidth()){Text("Подтвердить")};Text(message)}}
@Composable fun Sales(sales:List<Sale>){Column(Modifier.padding(20.dp)){Text("Продажи",style=MaterialTheme.typography.headlineMedium);Text("Всего: ${sales.sumOf{it.product.price}} BYN");LazyColumn{items(sales){s->ListItem(headlineContent={Text(s.product.model)},supportingContent={Text("${s.product.price} BYN · ${s.employee} · ${s.surface}
S/N: ${s.serial}")})}}}}
@Composable fun Plans(sales:List<Sale>){var plans by remember{mutableStateOf(mapOf("Егор" to 25000.0,"Максим" to 22000.0,"Вова" to 20000.0))};Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Планы",style=MaterialTheme.typography.headlineMedium);employees.forEach{n->val fact=sales.filter{it.employee==n}.sumOf{it.product.price};var input by remember{mutableStateOf((plans[n]?:0.0).toString())};Card{Column(Modifier.padding(12.dp)){Text(n,style=MaterialTheme.typography.titleLarge);OutlinedTextField(input,{input=it},label={Text("План BYN")},Modifier.fillMaxWidth());Text("Факт: $fact BYN");Button({input.toDoubleOrNull()?.let{x->plans=plans+(n to x)}},Modifier.fillMaxWidth()){Text("Сохранить план")}}}}}}
@Composable fun Settings(){Column(Modifier.padding(20.dp)){Text("Настройки",style=MaterialTheme.typography.headlineMedium);Text("Версия 0.3.0")}}
