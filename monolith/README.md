# Monolito SITM-MIO

Procesador monolítico para calcular la velocidad promedio por arco del grafo de rutas del SITM-MIO usando datagramas GPS históricos.

## Explicación general

El monolítico es la primera versión del procesador. Lee los datagramas GPS históricos, extrae bus, ruta aproximada, timestamp, latitud y longitud, asocia cada punto GPS al arco más cercano del grafo, calcula velocidad entre puntos consecutivos de cada bus y finalmente genera la velocidad promedio por arco.

Esta versión sirve como línea base para comparar después contra las versiones con `threadpool`, `master-worker`, `observer` u otros patrones arquitectónicos.

Explicación corta para socializar con el equipo:

> El monolítico procesa el CSV de datagramas de forma secuencial y por streaming, sin cargar todo en memoria. Por cada bus guarda la última posición válida, calcula la distancia y tiempo contra la nueva posición, asocia el punto GPS al arco más cercano del grafo y acumula velocidades por arco. Al final genera un CSV con la velocidad promedio por arco y un archivo de métricas para comparar rendimiento.

## Formato de entrada

### Grafo de arcos CSV

Archivo con encabezado:

```csv
arc_id,route_id,from_lat,from_lon,to_lat,to_lon
A1,T31,3.4516,-76.5320,3.4525,-76.5310
```

### Datagramas CSV

Archivo con encabezado:

```csv
bus_id,route_id,timestamp,latitude,longitude
B001,T31,2026-01-01T08:00:00,3.4517,-76.5319
```

El timestamp acepta formato ISO local (`yyyy-MM-ddTHH:mm:ss`) o con espacio (`yyyy-MM-dd HH:mm:ss`).

### Datagramas MiniPilot

El archivo `datagrams-MiniPilot.csv` no trae encabezado. Para leerlo use:

```powershell
java -cp monolith\out com.proyectomio.monolith.MonolithApp `
  --arcs <arcos_reales.csv> `
  --datagrams C:\Users\melyb\Downloads\datagrams-MiniPilot\datagrams-MiniPilot.csv `
  --format minipilot `
  --ignore-route true `
  --limit 100000 `
  --out monolith\output_minipilot
```

Mapeo usado por el preset `minipilot`:

- columna 3: identificador del bus
- columna 5: latitud escalada por `10,000,000`
- columna 6: longitud escalada por `10,000,000`
- columna 8: código operacional usado como ruta/línea aproximada
- columna 11: timestamp

Nota: para obtener velocidad por arco real se necesita un archivo de arcos del grafo del SITM-MIO. Si el código de ruta del MiniPilot no coincide con el `route_id` del grafo, use `--ignore-route true` para asociar por cercanía GPS.

Use `--limit` para pruebas rápidas y elimínelo cuando vaya a medir el archivo completo.

Para procesar todo el archivo MiniPilot, quite esta parte del comando:

```powershell
--limit 100000
```

Importante: actualmente `monolith\sample\arcs.csv` es solo un grafo de ejemplo. Para obtener resultados reales del SITM-MIO se debe reemplazar por un archivo de arcos reales con este formato:

```csv
arc_id,route_id,from_lat,from_lon,to_lat,to_lon
```

## Compilar

Desde la raíz del repositorio:

```powershell
javac -encoding UTF-8 -d monolith\out (Get-ChildItem -Recurse monolith\src\main\java\*.java)
```

## Ejecutar con datos de prueba

```powershell
java -cp monolith\out com.proyectomio.monolith.MonolithApp `
  --arcs monolith\sample\arcs.csv `
  --datagrams monolith\sample\datagrams.csv `
  --out monolith\output
```

Después de ejecutar, revise las salidas:

```text
monolith\output\arc_speeds.csv
monolith\output\metrics.txt
```

## Salidas

- `arc_speeds.csv`: velocidad promedio por arco.
- `metrics.txt`: métricas de ejecución para el informe y comparación con versiones distribuidas.

### Cómo interpretar `arc_speeds.csv`

Este archivo contiene el resultado principal del requerimiento del proyecto: velocidad promedio por arco.

Columnas importantes:

- `arc_id`: identificador del arco.
- `route_id`: ruta asociada al arco.
- `samples`: cantidad de segmentos válidos usados para calcular el promedio.
- `avg_speed_kmh`: velocidad promedio del arco en km/h.
- `min_speed_kmh`: velocidad mínima válida registrada para el arco.
- `max_speed_kmh`: velocidad máxima válida registrada para el arco.
- `status`: indica si el arco tiene datos suficientes (`OK`) o no (`SIN_DATOS_SUFICIENTES`).

### Cómo interpretar `metrics.txt`

Este archivo sirve para el informe de experimentos y para comparar el monolítico contra implementaciones distribuidas.

Métricas importantes:

- `total_datagrams`: cantidad total de datagramas leídos.
- `valid_datagrams`: datagramas con estructura y coordenadas válidas.
- `invalid_datagrams`: datagramas descartados por errores de formato.
- `unmatched_gps`: puntos GPS que no pudieron asociarse a ningún arco cercano.
- `out_of_order_datagrams`: registros que llegaron desordenados para un mismo bus.
- `anomalous_speeds`: velocidades descartadas por superar el umbral permitido.
- `processed_segments`: segmentos válidos usados para calcular velocidades.
- `elapsed_seconds`: tiempo total de procesamiento.
- `throughput_datagrams_per_second`: cantidad de datagramas procesados por segundo.

Estas métricas permiten responder preguntas de la rúbrica como: cuánto tarda el procesamiento, cuántos datos procesa por segundo y qué tan confiables fueron los datos usados.

## Generar datagramas sintéticos para experimentos

```powershell
java -cp monolith\out com.proyectomio.monolith.SyntheticDatagramGenerator `
  monolith\sample\arcs.csv `
  monolith\sample\generated_1000000.csv `
  1000000 `
  1000
```

Luego se procesa así:

```powershell
java -cp monolith\out com.proyectomio.monolith.MonolithApp `
  --arcs monolith\sample\arcs.csv `
  --datagrams monolith\sample\generated_1000000.csv `
  --out monolith\output_1000000
```

## Diseño eficiente

- Procesa datagramas por streaming para evitar cargar archivos gigantes en memoria.
- Mantiene solo la última posición válida de cada bus.
- Usa un índice espacial por celdas para buscar arcos candidatos cercanos.
- Parsea timestamps con un parser numérico directo para reducir costo por fila.
- Descarta registros inválidos, GPS no asignables y velocidades anómalas.
- Registra tiempo total, throughput y contadores de calidad de datos.
