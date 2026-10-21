# Pruebas de endpoints (curl)

Salidas reales contra la aplicación en `localhost:8080`. No hay simulador de pasarela levantado, así que el pago en línea muestra el rechazo 402 por pasarela no disponible.

## Proveedor pagosudes (por defecto)
```

$ curl localhost:8080/api/multas
[]
[HTTP 200]

$ curl -X POST localhost:8080/api/multas -H Content-Type: application/json -d {"estudianteId":"1152001","concepto":"Calculo I","diasAtraso":4}
{"id":1,"estudianteId":"1152001","concepto":"Calculo I","diasAtraso":4,"monto":2000,"estado":"PENDIENTE","fechaGeneracion":"2026-10-21","fechaPago":null,"metodoPago":null}
[HTTP 201]

$ curl -X POST localhost:8080/api/multas -H Content-Type: application/json -d {"concepto":"Sin estudiante","diasAtraso":4}
{"estudianteId":"El código de estudiante es obligatorio"}
[HTTP 400]

$ curl -X POST localhost:8080/api/multas -H Content-Type: application/json -d {"estudianteId":"1152002","concepto":"Tope","diasAtraso":100}
{"id":2,"estudianteId":"1152002","concepto":"Tope","diasAtraso":100,"monto":15000,"estado":"PENDIENTE","fechaGeneracion":"2026-10-21","fechaPago":null,"metodoPago":null}
[HTTP 201]

$ curl -X POST localhost:8080/api/multas -H Content-Type: application/json -d {"estudianteId":"1152001","concepto":"Cuarta","diasAtraso":1}
{"error":"El estudiante 1152001 ya tiene 3 multas pendientes (límite: 3)"}
[HTTP 409]

$ curl localhost:8080/api/multas/999
{"error":"Multa 999 no encontrada"}
[HTTP 404]

$ curl -X PATCH localhost:8080/api/multas/1/pagar
{"id":1,"estudianteId":"1152001","concepto":"Calculo I","diasAtraso":4,"monto":2000.00,"estado":"PAGADA","fechaGeneracion":"2026-10-21","fechaPago":"2026-10-21","metodoPago":"VENTANILLA"}
[HTTP 200]

$ curl -X PATCH localhost:8080/api/multas/1/pagar
{"error":"La multa 1 ya fue pagada el 2026-10-21"}
[HTTP 409]

$ curl -X POST localhost:8080/api/multas/2/pagar-en-linea
{"error":"PagosUDES no disponible: I/O error on POST request for \"http://localhost:9001/pagosudes/transacciones\": Connection refused"}
[HTTP 402]

$ curl -X POST localhost:8080/api/multas/1/pagar-en-linea
{"error":"La multa 1 ya fue pagada el 2026-10-21"}
[HTTP 409]
```

## Proveedor wompi (solo cambia `app.pagos.proveedor`, sin recompilar)
```

$ curl -X POST localhost:8080/api/multas/1/pagar-en-linea
{"error":"Wompi no disponible: I/O error on POST request for \"http://localhost:9002/wompi/transactions\": Connection refused"}
[HTTP 402]
```
