# Ejecucion distribuida con ICE y ZeroTier

Esta version mantiene el `Main` concurrente local como baseline y agrega un flujo distribuido:

1. `DistributedMasterMain` lee el CSV, particiona cada datagrama por `busId + tripId` y distribuye lotes a workers ICE.
2. Cada `WorkerServer` recibe tareas, reduce cada lote incrementalmente y conserva solo el ultimo evento de cada trayectoria para calcular correctamente entre lotes.
3. El master solicita `finishJob`, consolida los parciales y expone/imprime el mismo reporte por `lineId_mes_anio`.

La particion por `busId + tripId` evita que una trayectoria quede repartida entre workers distintos. Dentro de cada worker se reciben multiples lotes, y el worker guarda el ultimo evento de cada trayectoria para no perder velocidades que cruzan de un lote al siguiente.

Antes de probar, reconstruya el jar y reinicie todos los workers:

```powershell
.\gradlew.bat :data-center:jar --offline
```

## Red con ZeroTier

Todas las maquinas deben estar en la misma red ZeroTier y poder verse por su IP privada de ZeroTier.

Ejemplo:

```text
Master:  10.147.17.10
Worker1: 10.147.17.21
Worker2: 10.147.17.22
```

## Levantar workers

En cada maquina worker:

```powershell
.\gradlew.bat :data-center:runWorker --args="10.147.17.21 10000 WorkerService"
```

Para ejecutar directamente el worker desde el jar:

```powershell
java -cp "data-center/build/libs/data-center.jar" co.edu.icesi.sitmmio.datacenter.distributed.WorkerServer 10.147.17.21 10000 WorkerService
```

El endpoint que debe usar el master queda asi:

```text
WorkerService:default -h 10.147.17.21 -p 10000
```

## Ejecutar master

```powershell
.\gradlew.bat :data-center:runDistributedMaster --args="datagrams-MiniPilot.csv lines-241-ActiveGT.csv `"WorkerService:default -h 10.147.17.21 -p 10000`" `"WorkerService:default -h 10.147.17.22 -p 10000`""
```

O directamente desde el jar:

```powershell
java -cp "data-center/build/libs/data-center.jar" co.edu.icesi.sitmmio.datacenter.distributed.DistributedMasterMain `
  datagrams-MiniPilot.csv `
  lines-241-ActiveGT.csv `
  "WorkerService:default -h 10.147.17.21 -p 10000" `
  "WorkerService:default -h 10.147.17.22 -p 10000"
```

## Comparacion de tiempos

Para saber si mejora el tiempo de respuesta, compare:

```powershell
java -cp "data-center/build/libs/data-center.jar" co.edu.icesi.sitmmio.datacenter.Main datagrams-MiniPilot.csv lines-241-ActiveGT.csv 8
```

contra:

```powershell
java -cp "data-center/build/libs/data-center.jar" co.edu.icesi.sitmmio.datacenter.distributed.DistributedMasterMain ...
```

La version distribuida mejora cuando el costo de computo supera el costo de red y serializacion. Con pocos datos, pocos workers o red lenta, puede ser mas lenta que la concurrente local.

## Problemas comunes

Si el master falla con `Connection reset by peer`, normalmente el proceso worker se cerro. En versiones previas esto podia pasar porque el worker acumulaba todos los eventos en memoria hasta `finishJob`. La version actual procesa incrementalmente, pero si aparece de nuevo revise:

- Que el worker siga vivo en su terminal.
- Que el jar haya sido reconstruido despues de los cambios.
- Que el worker haya sido reiniciado despues de reconstruir el jar.
- Que la IP y el puerto del proxy correspondan al worker activo.
