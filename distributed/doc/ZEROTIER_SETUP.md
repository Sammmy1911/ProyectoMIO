# Guía de Distribución con ZeroTier - SITM-MIO

Esta guía explica cómo distribuir los componentes del sistema SITM-MIO en diferentes computadoras utilizando una red virtual de **ZeroTier**.

## 1. Configuración de la Red ZeroTier

1.  **Crear una cuenta**: Regístrate en [zerotier.com](https://www.zerotier.com/).
2.  **Crear una Red**: Crea una nueva red y anota el **Network ID**.
3.  **Instalar en cada PC**: Descarga e instala el cliente de ZeroTier en cada computadora que formará parte del sistema.
4.  **Unirse a la Red**: En cada PC, ejecuta:
    ```bash
    zerotier-cli join <Network_ID>
    ```
5.  **Autorizar**: En el panel de control de ZeroTier, autoriza cada PC para que reciba una dirección IP virtual (ej. `10.147.17.x`).
6.  **Identificar IPs**: Anota la IP de cada máquina. Para propósitos de este ejemplo:
    - **PC_SERVIDOR**: `10.147.17.1` (Ejecutará el Data Center y el Event Processor)
    - **PC_WORKER_1**: `10.147.17.2`
    - **PC_CLIENTE**: `10.147.17.3` (Ejecutará el Simulador y el Visualizador)

---

## 2. Configuración de los Componentes

Deberás modificar los archivos `config.properties` de cada módulo según la IP de la máquina donde se ejecuten.

### A. PC_SERVIDOR (`10.147.17.1`)

#### `distributed/event-processor/config.properties`
```properties
# Dónde escucha a los buses (debe ser accesible por el Simulador)
EventProcessorAdapter.Endpoints=tcp -h 10.147.17.1 -p 10000

# Dónde envía los datos para archivo (local en el servidor)
ArchiveService.Proxy=ArchiveService:tcp -h 10.147.17.1 -p 10001
```

#### `distributed/data-center/config.properties`
```properties
# Dónde escucha a los Workers y al Visualizer
DataCenterAdapter.Endpoints=tcp -h 10.147.17.1 -p 10001
```

### B. PC_WORKER_1 (`10.147.17.2`)

#### `distributed/worker-node/config.properties`
```properties
# Dónde escucha tareas del Master (debe ser la IP de esta máquina)
WorkerAdapter.Endpoints=tcp -h 10.147.17.2 -p 10002

# Dónde reporta los resultados al Master (IP del servidor)
Master.Proxy=Master:tcp -h 10.147.17.1 -p 10001
```

### C. PC_CLIENTE (`10.147.17.3`)

#### `distributed/bus-simulator/config.properties`
```properties
# IP del servidor donde corre el Event Processor
DatagramReceiver.Proxy=DatagramReceiver:tcp -h 10.147.17.1 -p 10000
```

#### `distributed/visualizer-client/config.properties`
```properties
# IP de esta máquina para recibir actualizaciones en tiempo real
VisualizerCallbackAdapter.Endpoints=tcp -h 10.147.17.3

# IP del servidor para streaming
RealTimeStreaming.Proxy=RealTimeStreaming:tcp -h 10.147.17.1 -p 10000

# IP del servidor para consultas
QueryProvider.Proxy=QueryProvider:tcp -h 10.147.17.1 -p 10001
```

---

## 3. Ejecución del Sistema

Sigue este orden para asegurar que los servicios estén disponibles:

1.  **PC_SERVIDOR**: Iniciar `DataCenterMaster` y `EventProcessor`.
2.  **PC_WORKER_n**: Iniciar los `WorkerNode`.
3.  **PC_CLIENTE**: Iniciar `VisualizerClient` y luego el `BusSimulator`.

### Comandos de ejecución (ejemplo):
En la raíz del proyecto `distributed`:

```bash
# Servidor
./gradlew :data-center:run
./gradlew :event-processor:run

# Workers
./gradlew :worker-node:run

# Cliente
./gradlew :visualizer-client:run
./gradlew :bus-simulator:run
```

---

## 4. Notas Importantes

- **Firewall**: Asegúrate de que los puertos `10000`, `10001` y `10002` estén abiertos en el firewall de Windows o que la red de ZeroTier esté marcada como "Privada" o "Doméstica".
- **Ice.Default.Host**: Si prefieres no editar cada archivo, puedes añadir `--Ice.Default.Host=10.147.17.x` al final de los comandos de ejecución.
- **Detección Automática**: Para una configuración más avanzada, se podría usar **IceGrid**, pero para esta distribución manual, el uso directo de IPs es suficiente.
