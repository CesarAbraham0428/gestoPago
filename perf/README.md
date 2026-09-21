# Prueba de carga del catálogo

El plan `catalogo-load.jmx` consulta `GET /productos` con Bearer Token y comprueba que las respuestas HTTP sean 200 y provengan de Redis. El plan comienza con 50 usuarios, rampa de 30 segundos y 100 iteraciones por usuario (hasta 5,000 solicitudes). Ajusta `users`, `rampSeconds` y `loops` según la capacidad del equipo y documenta esos valores junto con los resultados.

## Preparación y ejecución

1. Inicia PostgreSQL, Redis y la API.
2. Inicia sesión con `POST /auth/login` y conserva el JWT solo en la sesión local de PowerShell.
3. Haz una consulta inicial autenticada a `/productos` y confirma en los logs que se resolvió desde Redis. Esto calienta la caché antes de lanzar carga concurrente.
4. Define el token y ejecuta JMeter desde la raíz del repositorio:

   ```powershell
   $env:GESTOAPI_TEST_TOKEN = "<JWT de prueba>"
   jmeter -n -t perf/catalogo-load.jmx -Jusers=50 -JrampSeconds=30 -Jloops=100 -l perf/results/catalogo.jtl -e -o perf/results/html
   Remove-Item Env:GESTOAPI_TEST_TOKEN
   ```

El directorio `perf/results/html` debe no existir antes de cada ejecución. Los resultados generados se excluyen de Git. El token se lee desde una variable de entorno y no queda escrito en el plan ni en los resultados.

## Métricas que se deben reportar

Conserva el HTML generado por JMeter y reporta solicitudes totales, throughput, tiempo promedio, mediana, p95, p99, porcentaje de errores, mínimo y máximo. Comprueba que la mayoría de respuestas pasen la aserción de Redis y que no haya una ráfaga de llamadas directas a GestoPago. Registra concurrencia, ramp-up e iteraciones usadas.

No configures el plan para un millón de usuarios simultáneos. Una prueba grande debe aumentar usuarios e iteraciones gradualmente, con Redis caliente, y observar la capacidad del entorno sin saturar el servicio externo.
