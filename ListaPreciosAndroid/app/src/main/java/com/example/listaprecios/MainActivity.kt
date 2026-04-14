package com.example.listaprecios

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppSection { PRODUCTOS, CLIENTES }

data class Producto(
    val codigo: String,
    val nombre: String,
    val descripcion: String,
    val precio: String,
    val foto: String
)

data class Cliente(
    val codigo: String,
    val nombre: String,
    val razonSocial: String,
    val saldoPendiente: String,
    val telefono: String,
    val observaciones: String
)

private const val PREFS_NAME = "lista_precios_prefs"
private const val REMOTE_PRODUCTS_FILE_NAME = "ultima_lista_productos_remota.xlsx"
private const val REMOTE_CLIENTS_FILE_NAME = "ultima_lista_clientes_remota.xlsx"

private const val PRODUCTS_FILE_URI = "products_file_uri"
private const val PRODUCTS_FILE_LABEL = "products_file_label"
private const val PRODUCTS_JSON = "products_json"
private const val PRODUCTS_REMOTE_URL = "products_remote_url"
private const val PRODUCTS_AUTO_UPDATE = "products_auto_update"
private const val PRODUCTS_LAST_REMOTE_HASH = "products_last_remote_hash"
private const val PRODUCTS_LAST_UPDATE_AT = "products_last_update_at"

private const val CLIENTS_FILE_URI = "clients_file_uri"
private const val CLIENTS_FILE_LABEL = "clients_file_label"
private const val CLIENTS_JSON = "clients_json"
private const val CLIENTS_REMOTE_URL = "clients_remote_url"
private const val CLIENTS_AUTO_UPDATE = "clients_auto_update"
private const val CLIENTS_LAST_REMOTE_HASH = "clients_last_remote_hash"
private const val CLIENTS_LAST_UPDATE_AT = "clients_last_update_at"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                ListaPreciosApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaPreciosApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { AppStorage(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var activeSection by remember { mutableStateOf(AppSection.PRODUCTOS) }

    var productQuery by remember { mutableStateOf("") }
    var clientQuery by remember { mutableStateOf("") }
    var isLoadingProducts by remember { mutableStateOf(false) }
    var isLoadingClients by remember { mutableStateOf(false) }
    var productError by remember { mutableStateOf<String?>(null) }
    var clientError by remember { mutableStateOf<String?>(null) }

    val productos = remember { mutableStateListOf<Producto>() }
    val clientes = remember { mutableStateListOf<Cliente>() }

    var productsFileLabel by remember { mutableStateOf("Ningún archivo seleccionado") }
    var productsFileUri by remember { mutableStateOf<String?>(null) }
    var productsRemoteUrl by remember { mutableStateOf("") }
    var productsAutoUpdate by remember { mutableStateOf(false) }
    var productsLastUpdatedAt by remember { mutableStateOf<String?>(null) }

    var clientsFileLabel by remember { mutableStateOf("Ningún archivo seleccionado") }
    var clientsFileUri by remember { mutableStateOf<String?>(null) }
    var clientsRemoteUrl by remember { mutableStateOf("") }
    var clientsAutoUpdate by remember { mutableStateOf(false) }
    var clientsLastUpdatedAt by remember { mutableStateOf<String?>(null) }

    var showProductsRemoteConfig by remember { mutableStateOf(false) }
    var showClientsRemoteConfig by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val state = storage.loadState()
        productos.clear(); productos.addAll(state.products.items)
        clientes.clear(); clientes.addAll(state.clients.items)

        productsFileLabel = state.products.fileLabel
        productsFileUri = state.products.fileUri
        productsRemoteUrl = state.products.remoteUrl.orEmpty()
        productsAutoUpdate = state.products.autoUpdateEnabled
        productsLastUpdatedAt = state.products.lastUpdatedAt

        clientsFileLabel = state.clients.fileLabel
        clientsFileUri = state.clients.fileUri
        clientsRemoteUrl = state.clients.remoteUrl.orEmpty()
        clientsAutoUpdate = state.clients.autoUpdateEnabled
        clientsLastUpdatedAt = state.clients.lastUpdatedAt

        if (productsAutoUpdate && productsRemoteUrl.isNotBlank()) {
            isLoadingProducts = true
            try {
                val result = withContext(Dispatchers.IO) {
                    fetchAndParseRemoteProducts(context, productsRemoteUrl, state.products.lastRemoteHash)
                }
                if (result.updated) {
                    productos.clear(); productos.addAll(result.items)
                    productsFileUri = result.localFileUri
                    productsFileLabel = result.label
                    productsLastUpdatedAt = nowText()
                    storage.saveProducts(
                        fileUri = result.localFileUri,
                        fileLabel = result.label,
                        items = result.items,
                        remoteUrl = productsRemoteUrl,
                        autoUpdateEnabled = productsAutoUpdate,
                        lastRemoteHash = result.hash,
                        lastUpdatedAt = productsLastUpdatedAt
                    )
                    snackbarHostState.showSnackbar("Productos actualizados desde internet")
                }
            } catch (_: Exception) {
            } finally {
                isLoadingProducts = false
            }
        }

        if (clientsAutoUpdate && clientsRemoteUrl.isNotBlank()) {
            isLoadingClients = true
            try {
                val result = withContext(Dispatchers.IO) {
                    fetchAndParseRemoteClients(context, clientsRemoteUrl, state.clients.lastRemoteHash)
                }
                if (result.updated) {
                    clientes.clear(); clientes.addAll(result.items)
                    clientsFileUri = result.localFileUri
                    clientsFileLabel = result.label
                    clientsLastUpdatedAt = nowText()
                    storage.saveClients(
                        fileUri = result.localFileUri,
                        fileLabel = result.label,
                        items = result.items,
                        remoteUrl = clientsRemoteUrl,
                        autoUpdateEnabled = clientsAutoUpdate,
                        lastRemoteHash = result.hash,
                        lastUpdatedAt = clientsLastUpdatedAt
                    )
                    snackbarHostState.showSnackbar("Clientes actualizados desde internet")
                }
            } catch (_: Exception) {
            } finally {
                isLoadingClients = false
            }
        }
    }

    suspend fun saveProducts(items: List<Producto>, label: String, fileUri: String? = productsFileUri, remoteHash: String? = storage.loadState().products.lastRemoteHash) {
        productos.clear(); productos.addAll(items)
        productsFileLabel = label
        productsLastUpdatedAt = nowText()
        storage.saveProducts(fileUri, label, items, productsRemoteUrl, productsAutoUpdate, remoteHash, productsLastUpdatedAt)
    }

    suspend fun saveClients(items: List<Cliente>, label: String, fileUri: String? = clientsFileUri, remoteHash: String? = storage.loadState().clients.lastRemoteHash) {
        clientes.clear(); clientes.addAll(items)
        clientsFileLabel = label
        clientsLastUpdatedAt = nowText()
        storage.saveClients(fileUri, label, items, clientsRemoteUrl, clientsAutoUpdate, remoteHash, clientsLastUpdatedAt)
    }

    suspend fun loadProductsExcel(uri: Uri, labelOverride: String? = null) {
        isLoadingProducts = true
        productError = null
        try {
            val loaded = withContext(Dispatchers.IO) { parseProductsExcel(context, uri) }
            val label = labelOverride ?: readableNameFromUri(uri) ?: "Excel de productos"
            productsFileUri = uri.toString()
            saveProducts(loaded, label, fileUri = uri.toString())
            snackbarHostState.showSnackbar("Productos actualizados correctamente")
        } catch (e: FileNotFoundException) {
            productError = "No se encontró el Excel de productos. Seleccionalo nuevamente."
        } catch (e: SecurityException) {
            productError = "La app no tiene permiso para abrir el Excel de productos anterior. Seleccionalo nuevamente."
        } catch (e: Exception) {
            productError = "No se pudo leer el Excel de productos. Usá columnas como: codigo, producto, descripcion, precio y foto."
        } finally {
            isLoadingProducts = false
        }
    }

    suspend fun loadClientsExcel(uri: Uri, labelOverride: String? = null) {
        isLoadingClients = true
        clientError = null
        try {
            val loaded = withContext(Dispatchers.IO) { parseClientsExcel(context, uri) }
            val label = labelOverride ?: readableNameFromUri(uri) ?: "Excel de clientes"
            clientsFileUri = uri.toString()
            saveClients(loaded, label, fileUri = uri.toString())
            snackbarHostState.showSnackbar("Clientes actualizados correctamente")
        } catch (e: FileNotFoundException) {
            clientError = "No se encontró el Excel de clientes. Seleccionalo nuevamente."
        } catch (e: SecurityException) {
            clientError = "La app no tiene permiso para abrir el Excel de clientes anterior. Seleccionalo nuevamente."
        } catch (e: Exception) {
            clientError = "No se pudo leer el Excel de clientes. Usá columnas como: nombre, razon_social y saldo_pendiente."
        } finally {
            isLoadingClients = false
        }
    }

    suspend fun refreshProductsFromRemote(showNoChanges: Boolean = true) {
        if (productsRemoteUrl.isBlank()) {
            snackbarHostState.showSnackbar("Primero configurá el enlace remoto de productos")
            return
        }
        isLoadingProducts = true
        productError = null
        try {
            val current = storage.loadState().products
            val result = withContext(Dispatchers.IO) { fetchAndParseRemoteProducts(context, productsRemoteUrl, current.lastRemoteHash) }
            if (result.updated) {
                productsFileUri = result.localFileUri
                saveProducts(result.items, result.label, result.localFileUri, result.hash)
                snackbarHostState.showSnackbar("Productos actualizados desde internet")
            } else if (showNoChanges) {
                snackbarHostState.showSnackbar("No hay cambios en productos")
            }
        } catch (e: Exception) {
            productError = e.message ?: "No se pudo actualizar productos desde internet"
        } finally {
            isLoadingProducts = false
        }
    }

    suspend fun refreshClientsFromRemote(showNoChanges: Boolean = true) {
        if (clientsRemoteUrl.isBlank()) {
            snackbarHostState.showSnackbar("Primero configurá el enlace remoto de clientes")
            return
        }
        isLoadingClients = true
        clientError = null
        try {
            val current = storage.loadState().clients
            val result = withContext(Dispatchers.IO) { fetchAndParseRemoteClients(context, clientsRemoteUrl, current.lastRemoteHash) }
            if (result.updated) {
                clientsFileUri = result.localFileUri
                saveClients(result.items, result.label, result.localFileUri, result.hash)
                snackbarHostState.showSnackbar("Clientes actualizados desde internet")
            } else if (showNoChanges) {
                snackbarHostState.showSnackbar("No hay cambios en clientes")
            }
        } catch (e: Exception) {
            clientError = e.message ?: "No se pudo actualizar clientes desde internet"
        } finally {
            isLoadingClients = false
        }
    }

    val productsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scope.launch { loadProductsExcel(uri, readableNameFromUri(uri) ?: "Excel de productos") }
        }
    }

    val clientsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scope.launch { loadClientsExcel(uri, readableNameFromUri(uri) ?: "Excel de clientes") }
        }
    }

    val filteredProducts = remember(productos, productQuery) {
        val q = productQuery.trim().lowercase()
        if (q.isBlank()) productos.toList() else productos.filter {
            listOf(it.codigo, it.nombre, it.descripcion, it.precio).joinToString(" ").lowercase().contains(q)
        }
    }

    val filteredClients = remember(clientes, clientQuery) {
        val q = clientQuery.trim().lowercase()
        if (q.isBlank()) clientes.toList() else clientes.filter {
            listOf(it.codigo, it.nombre, it.razonSocial, it.saldoPendiente, it.telefono, it.observaciones)
                .joinToString(" ")
                .lowercase()
                .contains(q)
        }
    }

    if (showProductsRemoteConfig) {
        RemoteConfigDialog(
            title = "Actualización automática de productos",
            initialUrl = productsRemoteUrl,
            autoUpdateEnabled = productsAutoUpdate,
            onDismiss = { showProductsRemoteConfig = false },
            onSave = { newUrl, autoEnabled ->
                productsRemoteUrl = newUrl.trim()
                productsAutoUpdate = autoEnabled
                val current = storage.loadState().products
                storage.saveProducts(current.fileUri, current.fileLabel, current.items, productsRemoteUrl, productsAutoUpdate, current.lastRemoteHash, current.lastUpdatedAt)
                showProductsRemoteConfig = false
                scope.launch { snackbarHostState.showSnackbar("Origen automático de productos guardado") }
            }
        )
    }

    if (showClientsRemoteConfig) {
        RemoteConfigDialog(
            title = "Actualización automática de clientes",
            initialUrl = clientsRemoteUrl,
            autoUpdateEnabled = clientsAutoUpdate,
            onDismiss = { showClientsRemoteConfig = false },
            onSave = { newUrl, autoEnabled ->
                clientsRemoteUrl = newUrl.trim()
                clientsAutoUpdate = autoEnabled
                val current = storage.loadState().clients
                storage.saveClients(current.fileUri, current.fileLabel, current.items, clientsRemoteUrl, clientsAutoUpdate, current.lastRemoteHash, current.lastUpdatedAt)
                showClientsRemoteConfig = false
                scope.launch { snackbarHostState.showSnackbar("Origen automático de clientes guardado") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Lista de Precios y Clientes") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTabs(activeSection = activeSection, onSelect = { activeSection = it })

            when (activeSection) {
                AppSection.PRODUCTOS -> {
                    DataSection(
                        sectionTitle = "Productos",
                        fileLabel = productsFileLabel,
                        remoteUrl = productsRemoteUrl,
                        autoUpdateEnabled = productsAutoUpdate,
                        lastUpdatedAt = productsLastUpdatedAt,
                        isLoading = isLoadingProducts,
                        error = productError,
                        query = productQuery,
                        queryLabel = "Buscar producto",
                        itemCountText = "Productos cargados: ${filteredProducts.size}",
                        emptyText = "Cargá un Excel de productos o configurá un enlace automático.",
                        onQueryChange = { productQuery = it },
                        onPickExcel = {
                            productsPicker.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel"
                            ))
                        },
                        onOpenRemoteConfig = { showProductsRemoteConfig = true },
                        onReloadLast = {
                            val uriText = productsFileUri
                            if (uriText.isNullOrBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Todavía no hay un Excel de productos guardado") }
                            } else {
                                scope.launch { loadProductsExcel(Uri.parse(uriText), productsFileLabel) }
                            }
                        },
                        onRemoteRefresh = { scope.launch { refreshProductsFromRemote() } },
                        canReloadLast = !productsFileUri.isNullOrBlank() && !isLoadingProducts,
                        canRemoteRefresh = productsRemoteUrl.isNotBlank() && !isLoadingProducts,
                        onRetry = {
                            scope.launch {
                                when {
                                    !productsFileUri.isNullOrBlank() -> loadProductsExcel(Uri.parse(productsFileUri!!), productsFileLabel)
                                    productsRemoteUrl.isNotBlank() -> refreshProductsFromRemote(showNoChanges = false)
                                }
                            }
                        }
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(filteredProducts) { ProductoCard(it) }
                        }
                    }
                }
                AppSection.CLIENTES -> {
                    DataSection(
                        sectionTitle = "Clientes",
                        fileLabel = clientsFileLabel,
                        remoteUrl = clientsRemoteUrl,
                        autoUpdateEnabled = clientsAutoUpdate,
                        lastUpdatedAt = clientsLastUpdatedAt,
                        isLoading = isLoadingClients,
                        error = clientError,
                        query = clientQuery,
                        queryLabel = "Buscar cliente o razón social",
                        itemCountText = "Clientes cargados: ${filteredClients.size}",
                        emptyText = "Cargá un Excel de clientes o configurá un enlace automático.",
                        onQueryChange = { clientQuery = it },
                        onPickExcel = {
                            clientsPicker.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel"
                            ))
                        },
                        onOpenRemoteConfig = { showClientsRemoteConfig = true },
                        onReloadLast = {
                            val uriText = clientsFileUri
                            if (uriText.isNullOrBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Todavía no hay un Excel de clientes guardado") }
                            } else {
                                scope.launch { loadClientsExcel(Uri.parse(uriText), clientsFileLabel) }
                            }
                        },
                        onRemoteRefresh = { scope.launch { refreshClientsFromRemote() } },
                        canReloadLast = !clientsFileUri.isNullOrBlank() && !isLoadingClients,
                        canRemoteRefresh = clientsRemoteUrl.isNotBlank() && !isLoadingClients,
                        onRetry = {
                            scope.launch {
                                when {
                                    !clientsFileUri.isNullOrBlank() -> loadClientsExcel(Uri.parse(clientsFileUri!!), clientsFileLabel)
                                    clientsRemoteUrl.isNotBlank() -> refreshClientsFromRemote(showNoChanges = false)
                                }
                            }
                        }
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(filteredClients) { ClienteCard(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTabs(activeSection: AppSection, onSelect: (AppSection) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TabButton("Productos", activeSection == AppSection.PRODUCTOS) { onSelect(AppSection.PRODUCTOS) }
        TabButton("Clientes", activeSection == AppSection.CLIENTES) { onSelect(AppSection.CLIENTES) }
    }
}

@Composable
private fun RowScope.TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun DataSection(
    sectionTitle: String,
    fileLabel: String,
    remoteUrl: String,
    autoUpdateEnabled: Boolean,
    lastUpdatedAt: String?,
    isLoading: Boolean,
    error: String?,
    query: String,
    queryLabel: String,
    itemCountText: String,
    emptyText: String,
    onQueryChange: (String) -> Unit,
    onPickExcel: () -> Unit,
    onOpenRemoteConfig: () -> Unit,
    onReloadLast: () -> Unit,
    onRemoteRefresh: () -> Unit,
    canReloadLast: Boolean,
    canRemoteRefresh: Boolean,
    onRetry: () -> Unit,
    listContent: @Composable () -> Unit
) {
    Text(sectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onPickExcel, modifier = Modifier.weight(1f)) {
            Text(if (fileLabel == "Ningún archivo seleccionado") "Seleccionar Excel" else "Actualizar Excel")
        }
        Button(onClick = onOpenRemoteConfig, modifier = Modifier.weight(1f)) {
            Text("Auto update")
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onReloadLast, enabled = canReloadLast, modifier = Modifier.weight(1f)) {
            Text("Recargar último")
        }
        Button(onClick = onRemoteRefresh, enabled = canRemoteRefresh, modifier = Modifier.weight(1f)) {
            Text("Buscar actualización")
        }
    }

    Text("Archivo actual: $fileLabel", style = MaterialTheme.typography.bodySmall)
    if (remoteUrl.isNotBlank()) {
        Text("Origen automático: ${shortenUrl(remoteUrl)}", style = MaterialTheme.typography.bodySmall)
        Text(
            if (autoUpdateEnabled) "Actualización automática activada" else "Actualización automática desactivada",
            style = MaterialTheme.typography.bodySmall
        )
    }
    lastUpdatedAt?.let { Text("Última actualización: $it", style = MaterialTheme.typography.bodySmall) }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(queryLabel) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    error?.let {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = onRetry) { Text("Intentar de nuevo") }
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Text(itemCountText, style = MaterialTheme.typography.bodySmall)
        listContent()
        if (itemCountText.endsWith(": 0")) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RemoteConfigDialog(
    title: String,
    initialUrl: String,
    autoUpdateEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var auto by remember { mutableStateOf(autoUpdateEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Link de Google Drive o servidor") },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Revisar cambios al abrir la app")
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                Text(
                    text = "Para Google Drive usá un enlace compartido del archivo. La app intenta convertirlo a descarga directa automáticamente.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url, auto) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ProductoCard(producto: Producto) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (producto.foto.isNotBlank() && URLUtil.isValidUrl(producto.foto)) {
                AsyncImage(
                    model = producto.foto,
                    contentDescription = producto.nombre,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Text("Sin foto", modifier = Modifier.padding(8.dp)) }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(producto.nombre.ifBlank { "Sin nombre" }, fontWeight = FontWeight.Bold)
                        if (producto.codigo.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Código: ${producto.codigo}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(producto.precio.ifBlank { "-" }, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(producto.descripcion.ifBlank { "Sin descripción" })
            }
        }
    }
}

@Composable
fun ClienteCard(cliente: Cliente) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(cliente.nombre.ifBlank { "Sin nombre" }, fontWeight = FontWeight.Bold)
                    if (cliente.razonSocial.isNotBlank()) {
                        Text(cliente.razonSocial, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("Saldo pendiente", style = MaterialTheme.typography.bodySmall)
                    Text(cliente.saldoPendiente.ifBlank { "-" }, fontWeight = FontWeight.Bold)
                }
            }
            if (cliente.codigo.isNotBlank()) Text("Código: ${cliente.codigo}", style = MaterialTheme.typography.bodySmall)
            if (cliente.telefono.isNotBlank()) Text("Tel: ${cliente.telefono}", style = MaterialTheme.typography.bodySmall)
            if (cliente.observaciones.isNotBlank()) Text(cliente.observaciones, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private suspend fun parseProductsExcel(context: Context, uri: Uri): List<Producto> = withContext(Dispatchers.IO) {
    val input = when (uri.scheme) {
        "file" -> File(requireNotNull(uri.path) { "Ruta inválida" }).inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }
    input.use {
        requireNotNull(it) { "No se pudo abrir el archivo" }
        it.useProductsExcelParser()
    }
}

private suspend fun parseClientsExcel(context: Context, uri: Uri): List<Cliente> = withContext(Dispatchers.IO) {
    val input = when (uri.scheme) {
        "file" -> File(requireNotNull(uri.path) { "Ruta inválida" }).inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }
    input.use {
        requireNotNull(it) { "No se pudo abrir el archivo" }
        it.useClientsExcelParser()
    }
}

private fun java.io.InputStream.useProductsExcelParser(): List<Producto> {
    WorkbookFactory.create(this).use { workbook ->
        val sheet = workbook.getSheetAt(0)
        val formatter = DataFormatter()
        val headerRow = sheet.getRow(0) ?: error("El Excel no tiene encabezados")
        val headers = (0 until headerRow.lastCellNum).associate { index ->
            index to formatter.formatCellValue(headerRow.getCell(index)).trim().lowercase()
        }

        fun valueOf(row: org.apache.poi.ss.usermodel.Row, vararg names: String): String {
            val idx = headers.entries.firstOrNull { entry -> names.any { it == entry.value } }?.key ?: return ""
            return formatter.formatCellValue(row.getCell(idx)).trim()
        }

        return buildList {
            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                val producto = Producto(
                    codigo = valueOf(row, "codigo", "cod", "sku", "id"),
                    nombre = valueOf(row, "producto", "nombre", "articulo"),
                    descripcion = valueOf(row, "descripcion", "detalle", "info"),
                    precio = valueOf(row, "precio", "valor", "importe"),
                    foto = valueOf(row, "foto", "imagen", "url_foto", "url_imagen")
                )
                if (producto.nombre.isNotBlank() || producto.codigo.isNotBlank()) add(producto)
            }
        }
    }
}

private fun java.io.InputStream.useClientsExcelParser(): List<Cliente> {
    WorkbookFactory.create(this).use { workbook ->
        val sheet = workbook.getSheetAt(0)
        val formatter = DataFormatter()
        val headerRow = sheet.getRow(0) ?: error("El Excel no tiene encabezados")
        val headers = (0 until headerRow.lastCellNum).associate { index ->
            index to formatter.formatCellValue(headerRow.getCell(index)).trim().lowercase()
        }

        fun valueOf(row: org.apache.poi.ss.usermodel.Row, vararg names: String): String {
            val idx = headers.entries.firstOrNull { entry -> names.any { it == entry.value } }?.key ?: return ""
            return formatter.formatCellValue(row.getCell(idx)).trim()
        }

        return buildList {
            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                val cliente = Cliente(
                    codigo = valueOf(row, "codigo", "cod", "id", "cliente_id"),
                    nombre = valueOf(row, "nombre", "cliente", "apellido_nombre"),
                    razonSocial = valueOf(row, "razon_social", "razon social", "empresa", "cliente_empresa"),
                    saldoPendiente = valueOf(row, "saldo_pendiente", "saldo pendiente", "saldo", "cuenta_corriente", "deuda"),
                    telefono = valueOf(row, "telefono", "tel", "celular", "whatsapp"),
                    observaciones = valueOf(row, "observaciones", "obs", "detalle", "notas")
                )
                if (cliente.nombre.isNotBlank() || cliente.razonSocial.isNotBlank()) add(cliente)
            }
        }
    }
}

private data class RemoteProductsResult(
    val updated: Boolean,
    val items: List<Producto>,
    val hash: String,
    val localFileUri: String,
    val label: String
)

private data class RemoteClientsResult(
    val updated: Boolean,
    val items: List<Cliente>,
    val hash: String,
    val localFileUri: String,
    val label: String
)

private fun fetchAndParseRemoteProducts(context: Context, remoteUrl: String, previousHash: String?): RemoteProductsResult {
    val file = File(context.filesDir, REMOTE_PRODUCTS_FILE_NAME)
    val bytes = downloadRemoteExcelBytes(remoteUrl)
    val hash = sha256(bytes)
    if (!previousHash.isNullOrBlank() && previousHash == hash && file.exists()) {
        return RemoteProductsResult(false, emptyList(), hash, file.toURI().toString(), "Excel remoto productos")
    }
    file.writeBytes(bytes)
    val items = file.inputStream().use { it.useProductsExcelParser() }
    return RemoteProductsResult(true, items, hash, file.toURI().toString(), "Excel remoto productos")
}

private fun fetchAndParseRemoteClients(context: Context, remoteUrl: String, previousHash: String?): RemoteClientsResult {
    val file = File(context.filesDir, REMOTE_CLIENTS_FILE_NAME)
    val bytes = downloadRemoteExcelBytes(remoteUrl)
    val hash = sha256(bytes)
    if (!previousHash.isNullOrBlank() && previousHash == hash && file.exists()) {
        return RemoteClientsResult(false, emptyList(), hash, file.toURI().toString(), "Excel remoto clientes")
    }
    file.writeBytes(bytes)
    val items = file.inputStream().use { it.useClientsExcelParser() }
    return RemoteClientsResult(true, items, hash, file.toURI().toString(), "Excel remoto clientes")
}

private fun downloadRemoteExcelBytes(remoteUrl: String): ByteArray {
    val normalizedUrl = normalizeRemoteExcelUrl(remoteUrl)
    val connection = (URL(normalizedUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15000
        readTimeout = 30000
        setRequestProperty("User-Agent", "ListaPreciosAndroid/1.0")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) error("El servidor devolvió código $code")
        return connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

private fun normalizeRemoteExcelUrl(url: String): String {
    val trimmed = url.trim()
    val regex = Regex("/d/([a-zA-Z0-9_-]+)")
    val idFromPath = regex.find(trimmed)?.groupValues?.getOrNull(1)
    val idFromQuery = Uri.parse(trimmed).getQueryParameter("id")
    val fileId = idFromPath ?: idFromQuery
    return if (trimmed.contains("drive.google.com") && !fileId.isNullOrBlank()) {
        "https://drive.google.com/uc?export=download&id=$fileId"
    } else trimmed
}

private fun shortenUrl(url: String): String = if (url.length <= 48) url else url.take(45) + "..."
private fun readableNameFromUri(uri: Uri): String? = uri.lastPathSegment
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun nowText(): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

private data class SectionState<T>(
    val fileUri: String?,
    val fileLabel: String,
    val items: List<T>,
    val remoteUrl: String?,
    val autoUpdateEnabled: Boolean,
    val lastRemoteHash: String?,
    val lastUpdatedAt: String?
)

private data class PersistedState(
    val products: SectionState<Producto>,
    val clients: SectionState<Cliente>
)

private class AppStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveProducts(
        fileUri: String?,
        fileLabel: String,
        items: List<Producto>,
        remoteUrl: String? = null,
        autoUpdateEnabled: Boolean = false,
        lastRemoteHash: String? = null,
        lastUpdatedAt: String? = null
    ) {
        prefs.edit()
            .putString(PRODUCTS_FILE_URI, fileUri)
            .putString(PRODUCTS_FILE_LABEL, fileLabel)
            .putString(PRODUCTS_JSON, productsToJson(items))
            .putString(PRODUCTS_REMOTE_URL, remoteUrl)
            .putBoolean(PRODUCTS_AUTO_UPDATE, autoUpdateEnabled)
            .putString(PRODUCTS_LAST_REMOTE_HASH, lastRemoteHash)
            .putString(PRODUCTS_LAST_UPDATE_AT, lastUpdatedAt)
            .apply()
    }

    fun saveClients(
        fileUri: String?,
        fileLabel: String,
        items: List<Cliente>,
        remoteUrl: String? = null,
        autoUpdateEnabled: Boolean = false,
        lastRemoteHash: String? = null,
        lastUpdatedAt: String? = null
    ) {
        prefs.edit()
            .putString(CLIENTS_FILE_URI, fileUri)
            .putString(CLIENTS_FILE_LABEL, fileLabel)
            .putString(CLIENTS_JSON, clientsToJson(items))
            .putString(CLIENTS_REMOTE_URL, remoteUrl)
            .putBoolean(CLIENTS_AUTO_UPDATE, autoUpdateEnabled)
            .putString(CLIENTS_LAST_REMOTE_HASH, lastRemoteHash)
            .putString(CLIENTS_LAST_UPDATE_AT, lastUpdatedAt)
            .apply()
    }

    fun loadState(): PersistedState {
        val products = SectionState(
            fileUri = prefs.getString(PRODUCTS_FILE_URI, null),
            fileLabel = prefs.getString(PRODUCTS_FILE_LABEL, "Ningún archivo seleccionado") ?: "Ningún archivo seleccionado",
            items = jsonToProducts(prefs.getString(PRODUCTS_JSON, "[]") ?: "[]"),
            remoteUrl = prefs.getString(PRODUCTS_REMOTE_URL, null),
            autoUpdateEnabled = prefs.getBoolean(PRODUCTS_AUTO_UPDATE, false),
            lastRemoteHash = prefs.getString(PRODUCTS_LAST_REMOTE_HASH, null),
            lastUpdatedAt = prefs.getString(PRODUCTS_LAST_UPDATE_AT, null)
        )
        val clients = SectionState(
            fileUri = prefs.getString(CLIENTS_FILE_URI, null),
            fileLabel = prefs.getString(CLIENTS_FILE_LABEL, "Ningún archivo seleccionado") ?: "Ningún archivo seleccionado",
            items = jsonToClients(prefs.getString(CLIENTS_JSON, "[]") ?: "[]"),
            remoteUrl = prefs.getString(CLIENTS_REMOTE_URL, null),
            autoUpdateEnabled = prefs.getBoolean(CLIENTS_AUTO_UPDATE, false),
            lastRemoteHash = prefs.getString(CLIENTS_LAST_REMOTE_HASH, null),
            lastUpdatedAt = prefs.getString(CLIENTS_LAST_UPDATE_AT, null)
        )
        return PersistedState(products, clients)
    }

    private fun productsToJson(items: List<Producto>): String {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().apply {
                put("codigo", it.codigo)
                put("nombre", it.nombre)
                put("descripcion", it.descripcion)
                put("precio", it.precio)
                put("foto", it.foto)
            })
        }
        return array.toString()
    }

    private fun clientsToJson(items: List<Cliente>): String {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().apply {
                put("codigo", it.codigo)
                put("nombre", it.nombre)
                put("razonSocial", it.razonSocial)
                put("saldoPendiente", it.saldoPendiente)
                put("telefono", it.telefono)
                put("observaciones", it.observaciones)
            })
        }
        return array.toString()
    }

    private fun jsonToProducts(json: String): List<Producto> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(Producto(
                    codigo = item.optString("codigo"),
                    nombre = item.optString("nombre"),
                    descripcion = item.optString("descripcion"),
                    precio = item.optString("precio"),
                    foto = item.optString("foto")
                ))
            }
        }
    }

    private fun jsonToClients(json: String): List<Cliente> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(Cliente(
                    codigo = item.optString("codigo"),
                    nombre = item.optString("nombre"),
                    razonSocial = item.optString("razonSocial"),
                    saldoPendiente = item.optString("saldoPendiente"),
                    telefono = item.optString("telefono"),
                    observaciones = item.optString("observaciones")
                ))
            }
        }
    }
}
