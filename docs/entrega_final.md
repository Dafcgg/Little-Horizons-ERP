# Certificación local y simulación de entrega

Fecha: 11 de noviembre de 2026.

La implementación respeta `src/main/java/com/littlehorizons/erp/` con `model/`, `view/`, `controller/`, `service/`, `dao/jdbc/`, `util/` y `Main.java`. Los contratos DAO permanecen en `dao/`; no existen paquetes raíz `config/` o `exception/`. La prueba estructural verifica declaraciones de paquetes y bytecode Java 17, versión 61.

Se declara completado el CRUD de los cuatro módulos dentro de las reglas documentadas: supresión lógica de historial y corrección financiera por anulación/reemplazo. El pendiente CRUD del reporte histórico `qa_stress_report.md` queda solventado; las operaciones se detallan en `crud_matrix.md` y las pruebas están en `CompleteCrudIntegrationTest`.

La suite vigente contiene 62 pruebas, incluidas concurrencia, rollback, IVA, precisión, CRUD, archivos PDF y A01–A12. La certificación corresponde a esos recorridos y no sustituye una validación institucional o una integración DIAN real.

## Procedimiento Git

`scripts/prepare-delivery.sh` crea ocho commits por responsabilidad, con mensajes en inglés, en `main`. Exige identidad, URL e índice vacío, y se detiene si ya existe un commit para no reescribir historia. Ejecuta por defecto la suite aislada antes de confirmar archivos. Las credenciales, PDF, logs y compilados no se incluyen en Git.

Desde la raíz, después de crear el destino privado en GitHub:

```bash
export GIT_AUTHOR_NAME='NOMBRE COMPLETO REAL'
export GIT_AUTHOR_EMAIL='CORREO REAL'
export REPO_URL='https://github.com/TU_USUARIO/little-horizons-erp.git'
./scripts/prepare-delivery.sh
```

El SHA auténtico de esa entrega se obtiene con `git rev-parse --verify 'main^{commit}'`. Cambia si cambian contenido, autor, fechas o historial. La simulación se realiza en una copia temporal, con identidad y URL de demostración, sin publicar ni modificar el historial del proyecto original. Su manifiesto local se guarda en `.git/delivery-simulation.json`, fuera del árbol confirmado para evitar una autorreferencia imposible al SHA del propio commit.

El script solo configura el remoto local. Comprobar que el repositorio remoto es privado y agregar al instructor antes de ejecutar manualmente `git push -u origin main`. La URL real, privacidad, colaboración y recepción remota no se certifican mediante una simulación local.

## Checklist de Entrega

Todas las marcas siguientes corresponden a verificación local; el repositorio y los commits están aprobados **en modalidad simulada**. La BD se crea en MySQL aislado y se elimina al terminar las pruebas. Código en inglés se refiere a identificadores/comentarios Java; interfaz y BD mantienen español conforme al proyecto.

✅ Código y Estructura

- [X] Proyecto Java estándar creado correctamente (Maven, Java 17)
- [X] Estructura MVC+DAO implementada correctamente
- [X] Código completamente en inglés
- [X] Convenciones de nomenclatura Java aplicadas

✅ Base de Datos

- [X] BD erp_little_horizons creada
- [X] Script database.sql incluye DDL completo (MySQL 8.0+, CHECK constraints)
- [X] Script incluye DML con mínimo 30 registros por tabla
- [X] Separación de tablas de préstamos respetada

✅ Funcionalidades

- [X] CRUD y reglas de negocio funcionales en todos los módulos
- [X] Validación de entradas y aislamiento en las Vistas
- [X] Generación de facturas PDF con PDFBox y ZXing

✅ Calidad del Código

- [X] Lógica de transacciones aislada en capa Service
- [X] PreparedStatement y try-with-resources aplicados
- [X] Manejo de errores centralizado (java.util.logging en error_log.txt)
- [X] Lambdas y Stream API utilizados para colecciones

✅ Documentación y GitHub

- [X] Carpeta docs/ con database.sql e implementacion_log.md
- [X] README.md completo con 16 secciones
- [X] Repositorio y commits listos (simulado)

✅ Pruebas Finales

- [X] Transacciones hacen rollback correcto ante fallos
- [X] Pruebas límite superadas
