# Plan de Implementación: Sistema SITM-MIO

Este documento describe las historias de usuario, criterios de aceptación y actividades necesarias para construir el sistema distribuido SITM-MIO utilizando ZeroC Ice, procesamiento distribuido y análisis de datos históricos de transporte.

---

# Fase 1: Infraestructura y Contratos Distribuidos

## US01: Definición de Interfaces Distribuidas

**Como** arquitecto del sistema,
**quiero** definir los contratos de comunicación entre los componentes distribuidos,
**para que** todos los módulos intercambien información de forma consistente y tipada.

### Criterios de Aceptación

* Todas las interfaces distribuidas deben definirse en archivos Slice.
* El código Java correspondiente debe generarse automáticamente.
* Los contratos deben permitir la comunicación entre simuladores, procesadores, workers y clientes.

### Tareas

* [ ] Crear proyecto `contracts`.
* [ ] Definir DTOs para eventos, posiciones GPS y reportes.
* [ ] Definir interfaz `DatagramReceiver`.
* [ ] Definir interfaz `ArchiveService`.
* [ ] Definir interfaz `MonitoringSubscriber`.
* [ ] Definir interfaz `ReportProvider`.
* [ ] Definir interfaz `TaskDispatcher`.
* [ ] Configurar generación automática de código Ice.

---

# Fase 2: Ingesta y Procesamiento de Eventos

## US02: Simulación de Buses

**Como** operador de pruebas,
**quiero** simular la generación de eventos de los buses a partir de archivos históricos,
**para que** el sistema pueda ser evaluado sin depender de buses reales.

### Criterios de Aceptación

* El simulador debe leer archivos CSV.
* Debe generar datagramas equivalentes a los enviados por buses reales.
* Debe ser posible configurar la velocidad de reproducción.

### Tareas

* [ ] Crear módulo `bus-simulator`.
* [ ] Implementar lector de CSV.
* [ ] Implementar conversión de registros a datagramas.
* [ ] Implementar cliente Ice para enviar eventos.

---

## US03: Procesamiento de Eventos

**Como** analista operacional,
**quiero** validar y normalizar los eventos recibidos,
**para que** los datos almacenados tengan un formato uniforme.

### Criterios de Aceptación

* Las coordenadas deben transformarse a formato decimal.
* Los eventos inválidos deben descartarse.
* Los eventos válidos deben reenviarse al Data Center.

### Tareas

* [ ] Crear módulo `event-processor`.
* [ ] Implementar `DatagramReceiver`.
* [ ] Implementar validación de eventos.
* [ ] Implementar normalización de coordenadas.
* [ ] Implementar reenvío hacia el Data Center.

---

# Fase 3: Persistencia de Información

## US04: Almacenamiento de Eventos Históricos

**Como** administrador del sistema,
**quiero** almacenar todos los eventos recibidos,
**para que** puedan utilizarse posteriormente en análisis históricos.

### Criterios de Aceptación

* Ningún evento válido debe perderse.
* Los eventos deben almacenarse de forma persistente.
* Debe ser posible recuperar eventos históricos para análisis.

### Tareas

* [ ] Crear módulo `data-center`.
* [ ] Implementar `ArchiveService`.
* [ ] Implementar repositorio de eventos.
* [ ] Implementar persistencia de eventos históricos.

---

# Fase 4: Procesamiento Distribuido

## US05: Distribución de Cálculos Analíticos

**Como** administrador del sistema,
**quiero** distribuir el procesamiento de grandes volúmenes de eventos,
**para que** el tiempo de cálculo sea reducido.

### Criterios de Aceptación

* El procesamiento debe ejecutarse mediante el patrón Master-Worker.
* El sistema debe soportar múltiples Workers.
* El Master debe consolidar los resultados parciales.

### Tareas

* [ ] Implementar componente Master.
* [ ] Implementar componente Worker.
* [ ] Implementar distribución de tareas.
* [ ] Implementar recepción de resultados parciales.
* [ ] Implementar consolidación de resultados.

---

## US06: Procesamiento Concurrente

**Como** administrador del sistema,
**quiero** que cada Worker procese múltiples tareas simultáneamente,
**para que** los recursos computacionales se aprovechen eficientemente.

### Criterios de Aceptación

* Cada Worker debe utilizar un Thread Pool.
* Las tareas deben ejecutarse concurrentemente.
* Debe evitarse la creación excesiva de hilos.

### Tareas

* [ ] Implementar Thread Pool.
* [ ] Configurar tamaño del pool.
* [ ] Implementar ejecución concurrente de tareas.
* [ ] Implementar monitoreo básico de ejecución.

---

# Fase 5: Cálculo de Velocidades

## US07: Cálculo de Velocidad Promedio por Arco

**Como** gestor de operación,
**quiero** calcular la velocidad promedio de cada arco del grafo de rutas,
**para que** sea posible identificar zonas con congestión o baja velocidad operacional.

### Criterios de Aceptación

* Las velocidades deben calcularse usando posiciones GPS.
* El cálculo debe utilizar distancia geográfica y diferencia temporal.
* Los resultados deben almacenarse para consultas posteriores.

### Tareas

* [ ] Cargar el grafo de rutas.
* [ ] Implementar map matching básico.
* [ ] Asociar eventos a arcos.
* [ ] Calcular distancia entre posiciones GPS consecutivas.
* [ ] Calcular velocidad promedio por arco.
* [ ] Almacenar resultados consolidados.

---

# Fase 6: Consultas Analíticas

## US08: Consulta de Reportes Históricos

**Como** operador del CCO,
**quiero** consultar velocidades promedio históricas,
**para que** pueda analizar el comportamiento operacional del sistema.

### Criterios de Aceptación

* Debe ser posible consultar por arco.
* Debe ser posible consultar por período.
* Las consultas deben responderse utilizando resultados previamente calculados.

### Tareas

* [ ] Implementar Query Engine.
* [ ] Implementar `ReportProvider`.
* [ ] Implementar filtros de consulta.
* [ ] Implementar recuperación de reportes.

---

# Fase 7: Monitoreo en Tiempo Real (Bono)

## US09: Actualización en Tiempo Real

**Como** operador del CCO,
**quiero** visualizar la posición de los buses en tiempo real,
**para que** pueda monitorear la operación del sistema.

### Criterios de Aceptación

* Las actualizaciones deben utilizar Observer / Pub-Sub.
* El Dashboard debe recibir eventos automáticamente.
* La latencia de actualización debe ser mínima.

### Tareas

* [ ] Implementar componente RealTimeStreaming.
* [ ] Implementar patrón Observer.
* [ ] Implementar suscripción de clientes.
* [ ] Implementar envío de actualizaciones.

---

## US10: Dashboard de Monitoreo

**Como** operador del CCO,
**quiero** visualizar la información operacional del sistema,
**para que** pueda interpretar fácilmente el estado de la red de transporte.

### Criterios de Aceptación

* El Dashboard debe seguir el patrón MVC.
* Debe mostrar información histórica y en tiempo real.
* Debe permitir consultas analíticas.

### Tareas

* [ ] Implementar DashboardModel.
* [ ] Implementar DashboardView.
* [ ] Implementar DashboardController.
* [ ] Integrar consultas históricas.
* [ ] Integrar eventos en tiempo real.

```
```
