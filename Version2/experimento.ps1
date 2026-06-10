 $datasets = @("datagrams-MiniPilot.csv", "datagrams4Pilot.csv")
 $threadCounts = @(1, 2, 4, 8)
$results = @()

foreach ($ds in $datasets) {
  foreach ($th in $threadCounts) {
        Write-Host "`n>>> EXPERIMENTO: $ds con $th hilos <<<" -ForegroundColor Yellow
          # Ejecutamos y capturamos solo el tiempo final
           $output = java -jar "data-center/build/libs/data-center.jar" $ds lines-241-ActiveGT.csv $th
            # Verás los tiempos en la consola. Anótalos.
        }
   }