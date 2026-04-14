# ListaPreciosAndroid

Proyecto Android listo para abrir en Android Studio y compilar APK.

## Qué hace ahora

La app tiene 2 módulos:

### 1) Productos
- carga Excel manualmente
- busca por código, nombre, descripción o precio
- muestra precio, descripción y foto
- permite actualizar con un Excel nuevo
- puede actualizar desde Google Drive o servidor
- guarda la última lista cargada

### 2) Clientes
- carga Excel manualmente
- busca por nombre, razón social, código, teléfono o saldo
- muestra saldo pendiente de cuenta corriente
- permite actualizar con un Excel nuevo
- puede actualizar desde Google Drive o servidor
- guarda la última lista cargada

## Formato del Excel de productos

Usar la primera hoja del Excel.

Columnas recomendadas:
- `codigo`
- `producto`
- `descripcion`
- `precio`
- `foto`

También acepta variantes como:
- `cod`, `sku`, `id`
- `nombre`, `articulo`
- `detalle`, `info`
- `valor`, `importe`
- `imagen`, `url_foto`, `url_imagen`

La columna `foto` debe tener una URL pública.

## Formato del Excel de clientes

Usar la primera hoja del Excel.

Columnas recomendadas:
- `codigo`
- `nombre`
- `razon_social`
- `saldo_pendiente`
- `telefono`
- `observaciones`

También acepta variantes como:
- `cod`, `id`, `cliente_id`
- `cliente`, `apellido_nombre`
- `razon social`, `empresa`, `cliente_empresa`
- `saldo pendiente`, `saldo`, `cuenta_corriente`, `deuda`
- `tel`, `celular`, `whatsapp`
- `obs`, `detalle`, `notas`

## Actualización automática

Cada módulo tiene su propia configuración de actualización automática.

Podés usar:
- link compartido de Google Drive
- link directo de tu servidor

La app:
- intenta convertir enlaces de Google Drive a descarga directa
- revisa si el archivo cambió
- solo recarga cuando detecta cambios
- guarda copia local de la última versión descargada

## Cómo compilar el APK

1. Abrí Android Studio
2. Elegí **Open**
3. Seleccioná la carpeta `ListaPreciosAndroid`
4. Esperá la sincronización de Gradle
5. Ir a **Build > Build Bundle(s) / APK(s) > Build APK(s)**

## Requisitos

- Android Studio reciente
- conexión a internet para dependencias Gradle
- JDK que instala Android Studio

## Sugerencia de uso

- un Excel para productos
- otro Excel para clientes
- ambos pueden ser manuales o remotos
- así mantenés precios y cuenta corriente separados y más fáciles de actualizar
