# Arquitectura de Monny

Documento corto para orientarse en el código. La app es Android nativa, 100%
local (sin backend) y usa una sola Activity con Jetpack Compose.

## Capas

```
ui/            Compose: pantallas, componentes y ViewModel
 ├─ screens/   una pantalla por archivo
 ├─ sheets/    hoja inferior de nuevo/editar gasto
 ├─ components/ piezas reutilizables (tarjetas, filas, campos)
 └─ theme/     colores, tipografía y formas
domain/        reglas puras (ciclos, dinero, importador CSV)
data/          Room: entidades, DAOs, repositorio, respaldos
notifications/ recordatorios con WorkManager
```

La UI nunca toca la base de datos directamente: todo pasa por
`MonnyRepository` y `MonnyViewModel`.

## Flujo de datos

1. `MonnyDb` (Room) expone consultas como `Flow`.
2. `MonnyRepository` combina y transforma esos flows (por ejemplo, los datos de
   un ciclo suman gastos + pagos + apartados).
3. `MonnyViewModel` deriva el estado de cada pantalla (`HomeState`,
   `CycleRow`, `CycleDetailState`, etc.) con `stateIn`.
4. Las pantallas observan ese estado con `collectAsStateWithLifecycle`, por lo
   que cualquier cambio se refleja en todas al instante.

## Conceptos clave

### Ciclo

Un ciclo se identifica por su **fecha de corte** (`cycleKey`, un epochDay).
`CycleMath` contiene todas las reglas:

- `cycleKeyFor(fecha, corte)`: a qué ciclo pertenece un movimiento.
- `cycleStart(cutKey, corte)`: primer día del ciclo (se usa para el nombre visible).
- `purchaseCycleKey(...)`: variante para compras; puede mandar el día de corte
  al ciclo siguiente (opción por tarjeta).
- `paymentDue(...)`: vencimiento (corte + días configurados).

El nombre visible del ciclo es el mes en que arranca, no el del corte.

### "En sobre"

Es la suma de los `Cover` de un ciclo. Un `Cover` con monto negativo es un
retiro. `Disponible = suma(apartados) + pagos − gastos`.

### Tasa 0

`InstallmentPlan` guarda la compra original. Al crearla se materializan N
`Expense` (una por ciclo) con `planId` y `installmentIndex`. Editar el plan
regenera sus cuotas; eliminarlo borra las futuras.

### Gastos fijos

`RecurringExpense` es la regla mensual. `materializeRecurring()` crea los
`Expense` que faltan hasta el ciclo siguiente. `RecurringCharge` registra qué
ciclos ya se generaron: evita duplicados y que un cobro borrado reaparezca.

### Respaldos

`Backup` serializa todas las tablas a JSON (incluye IDs). `AutoBackup` guarda
una copia diaria en `Descargas/Monny`. Restaurar reemplaza todo el contenido.

### Importador

`CsvImport` detecta el formato (bloques por categoría o tabla plana), los
pagos negativos, el "En sobre" del resumen y las filas sin fecha. El
`ImportExportScreen` muestra una vista previa y aplica la importación.

## Decisiones

- **Offline-first**: no hay cuentas ni servidores. El único uso de internet es
  descargar una hoja pública al importar por enlace.
- **Montos en céntimos (Long)**: evita errores de punto flotante.
- **Fechas como epochDay**: comparables y ordenables en SQLite.
- **Migraciones versionadas**: actualizar la app nunca borra datos.
- **Firma estable**: las actualizaciones se instalan encima.
