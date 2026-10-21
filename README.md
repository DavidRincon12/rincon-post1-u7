# Post-contenido — Unidad 7: Patrones Arquitectónicos I

## Descripción
Repositorio del post-contenido de la Unidad 7 de Patrones de Diseño de Software. Un único proyecto Spring Boot (`multas-biblioteca-api`) para la gestión de multas de una biblioteca universitaria, en dos partes:

1. Una API REST en capas (Model, Repository, Service, Controller) sobre H2.
2. Pago en línea de multas con dos pasarelas intercambiables por configuración (PagosUDES y Wompi).

## Parte 1 — Arquitectura en capas
- `controller/`: `MultaController` expone `/api/multas` y `GlobalExceptionHandler` traduce las excepciones a HTTP. Nunca toca el repositorio: siempre pasa por `MultaService`.
- `service/`: `MultaService` orquesta los casos de uso y aplica el tope de multas pendientes por estudiante (máximo 3).
- `model/`: entidad `Multa`, `EstadoMulta` y las excepciones de dominio. `Multa.calcularMonto` y `Multa.marcarComoPagada` son comportamiento de la propia entidad.
- `repository/`: `MultaRepository` (Spring Data JPA) con `countByEstudianteIdAndEstado`.

### Estructura de paquetes final
```
multas-biblioteca-api/src/main/java/com/example/multas/
├── controller/          MultaController, GlobalExceptionHandler, GenerarMultaRequest
├── service/             MultaService
├── model/               Multa, EstadoMulta, excepciones de negocio
├── repository/          MultaRepository
├── domain/              (Parte 2) Java puro, sin Spring
│   ├── port/            PasarelaPagoPort
│   ├── ResultadoPago
│   └── PagoRechazadoException
├── infrastructure/      (Parte 2) conoce Spring y HTTP
│   ├── pago/            PagosUdesAdapter, WompiAdapter
│   └── config/          RestTemplateConfig
└── MultasApplication
```

## Parte 2 — Pago en línea con dos pasarelas
**Opción elegida: C, puerto de dominio con dos adaptadores, solo en la porción de pago.** `PasarelaPagoPort` y `ResultadoPago` están en `domain/` y solo dependen de `java.*` (y de la entidad `Multa`). `PagosUdesAdapter` y `WompiAdapter` viven en `infrastructure/pago/`, traducen cada contrato HTTP (`idTransaccion/estadoTransaccion` vs. `reference/status` en centavos) al mismo `ResultadoPago` y se activan con `app.pagos.proveedor`. `Multa`, `MultaRepository` y el resto de la Parte 1 no se migraron a hexagonal: eso sería sobreingeniería para este alcance.

Endpoint nuevo: `POST /api/multas/{id}/pagar-en-linea` (402 si la pasarela rechaza, 409 si la multa ya estaba pagada).

## Cómo ejecutar
```
$ cd multas-biblioteca-api
$ mvn clean package && mvn spring-boot:run
```
Para usar Wompi en lugar de PagosUDES (sin recompilar):
```
$ mvn spring-boot:run -Dspring-boot.run.arguments=--app.pagos.proveedor=wompi
```
Consola H2 en `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:multas_biblioteca_db`, usuario `sa`).

Las pasarelas son externas y no se incluyen simuladores; sin ellas levantadas el pago en línea responde 402 con "no disponible". Las pruebas automáticas simulan las pasarelas (`MockRestServiceServer` y un `@MockBean` del puerto) y se corren con `mvn test` (16 pruebas).

## Pruebas de endpoints
Las salidas reales de `curl` (200, 201, 400, 402, 404 y 409) para ambos proveedores están en [`docs/pruebas-endpoints.md`](docs/pruebas-endpoints.md).

## Herramientas utilizadas
- Java 17 (compilado y probado con JDK 21), Spring Boot 3.3.5, Spring Data JPA, H2, RestTemplate
- JUnit 5, AssertJ, Mockito, MockMvc
- Apache Maven, curl, Git, GitHub

## Decisiones de diseño

### Punto de decisión 1 — Cálculo del monto: ¿entidad o Service?
`Multa.calcularMonto(diasAtraso)` es un método estático de la entidad porque la regla (días × $500, tope de $15.000) solo depende del parámetro que recibe: no necesita el repositorio ni ningún otro colaborador. El criterio que usé: si la regla no necesita ningún colaborador externo, es candidata a vivir en el objeto de dominio; si necesita datos o servicios, va en el Service.

Con la alternativa (método privado en `MultaService`) `Multa` quedaría como un contenedor de datos (modelo anémico) y cualquier otro punto que necesitara recalcular un monto tendría que duplicar la fórmula o pasar por el Service sin necesidad. Lo mismo vale para `marcarComoPagada`, que protege la invariante "una multa pagada no se paga dos veces" dentro de la entidad.

### Punto de decisión 2 — Conteo de multas pendientes: ¿consulta o filtrado en memoria?
`MultaRepository.countByEstudianteIdAndEstado` se traduce a un `COUNT` en SQL. La decisión de negocio ("¿se le permite una multa más?") la toma `MultaService`, pero el dato que necesita se resuelve donde es eficiente: en el motor de base de datos.

La alternativa era `findByEstudianteId` y filtrar con streams. Con pocas filas no se nota, pero si un estudiante acumulara miles de multas, cada creación traería todo su historial a memoria y el tiempo de respuesta crecería proporcionalmente a ese historial; el `COUNT` devuelve un solo número.

### Punto de decisión 3 — Selección del adaptador activo
Usé `@ConditionalOnProperty` sobre `app.pagos.proveedor`: solo un adaptador existe como bean en el contexto, así que `MultaService` pide un único `PasarelaPagoPort` por constructor, sin `@Qualifier` ni lógica condicional propia. `PagosUdesAdapter` queda como valor por defecto (`matchIfMissing = true`).

La alternativa era inyectar un `Map<String, PasarelaPagoPort>` y elegir la clave en tiempo de ejecución. Es más flexible (cambiar de proveedor sin reiniciar), pero el requisito real es una pasarela fija por sede durante el piloto, y el `Map` habría obligado a `MultaService` a conocer las claves de configuración de cada proveedor. Lo que se sacrifica con mi decisión es justamente el cambio en caliente: cambiar de pasarela exige reiniciar la aplicación.

### Punto de decisión 4 — Diseño del puerto y el tipo de resultado
`PasarelaPagoPort.procesar` devuelve `ResultadoPago(proveedor, exitoso, referenciaExterna, mensaje)`, un tipo neutral. Cada adaptador traduce su respuesta a él antes de devolverla, así que `MultaService` nunca ve `idTransaccion`, `reference`, `status` ni centavos.

Si el puerto devolviera el DTO de cada pasarela, o tuviera un método por proveedor, `MultaService` tendría que conocer y distinguir ambos formatos, y agregar una tercera pasarela obligaría a modificarlo además de escribir un adaptador. Y si `ResultadoPago` tuviera un campo `idTransaccion` en vez de `referenciaExterna`, `WompiAdapter` tendría que inventar un valor o reutilizar un nombre que no describe lo que Wompi devuelve (su `reference`): señal de que el tipo de dominio se habría acoplado al vocabulario de PagosUDES.

### Trade-off considerado — Parte 2
Descarté la opción A (rama `if/switch` en `MultaService`) porque haría que el Service conociera los detalles HTTP de ambas pasarelas y que cada pasarela nueva o retirada lo modificara, algo que el requisito pide explícitamente evitar. También descarté la B (Strategy en `service/`): resuelve la intercambiabilidad, pero deja a los adaptadores, que hablan HTTP con sistemas externos, dentro de la capa de servicio, y el contrato de cada pasarela terminaría mezclado con la lógica de aplicación.

Con la opción C se gana que agregar o quitar una pasarela es agregar o borrar una clase en `infrastructure/pago/` más un valor de configuración, sin tocar `MultaService` ni `MultaController`, y que el dominio se prueba sin red. Costó dos paquetes nuevos (`domain/`, `infrastructure/`), un tipo de resultado propio y una traducción en cada adaptador: más archivos y más indirección para un caso que hoy son dos proveedores. Además, el puerto importa la entidad `Multa`, así que el dominio no está completamente aislado de la Parte 1; lo acepté para no duplicar la entidad.

Si el piloto terminara y solo quedara una pasarela, consideraría simplificar: dejar un único cliente HTTP en `service/` e incluso borrar el puerto, porque la indirección ya no pagaría su costo. Mientras haya dos proveedores, o la posibilidad real de una tercera, el puerto se justifica.

## Conclusiones
En la Parte 1 el criterio más útil fue preguntar si una regla necesita un colaborador externo: eso separó el cálculo del monto (entidad) del tope de multas pendientes (Service con un `COUNT` en el repositorio). En la Parte 2 lo difícil fue decidir entre extender las capas o introducir el puerto, porque con solo dos pasarelas la opción B también funcionaba; lo que inclinó la balanza fue que los contratos HTTP son distintos y que el número de proveedores va a cambiar. Aprendí que un puerto bien diseñado depende de que su tipo de resultado sea neutral, y que aplicar hexagonal solo a la porción que lo necesita evita sobreingeniería en el resto del proyecto.
