# Objetivos

## Propósito

Definir los objetivos del sistema MultiVault a nivel funcional y técnico.

## Estado actual

El posicionamiento del producto está definido en [ADR-0018](../06-Decisiones/ADR-0018.md). Los objetivos funcionales y técnicos aquí descritos se infieren del diseño de los esquemas de base de datos y las tecnologías seleccionadas.

## Información encontrada

### Posicionamiento (ADR-0018)

MultiVault es **el backend del producto de otro, no un producto con cuentas propias**.

- No hay identidades de usuario final, ni sesiones, ni contraseñas gestionadas por MultiVault.
- La raíz de confianza de cada tenant es su llave maestra SERVICE. El admin de un tenant es quien posee la llave; su identidad nominal es asunto del producto del cliente, que ya tiene su propio IdP.
- Los miembros del tenant se autentican siempre contra el Identity Provider del propio tenant (ADR-0006 lo hace obligatorio en el alta).
- No hay entidad `customer`/`account`: `subscription` factura por tenant.
- `platform_user` es staff interno de plataforma, con alcance restringido a operación de plataforma y **sin acceso a datos de tenant** (garantía estructural del enrutamiento por `search_path`).

El criterio que fija esta decisión: **se puede vender sin escribir una línea de front-end.**

### Objetivos funcionales (inferidos del schema)

- **Gestión documental:** Permitir la creación, versionado y organización de documentos en carpetas jerárquicas
- **Multi-tenancy:** Aislar completamente los datos de cada cliente mediante schemas separados en PostgreSQL
- **Control de acceso por recurso:** Implementar ACLs con niveles OWNER, EDITOR, VIEWER por documento
- **Autenticación federada:** Soportar OIDC/JWT por tenant para que cada cliente use su propio Identity Provider
- **Autenticación M2M:** Proveer API keys para integraciones machine-to-machine (SERVICE y STANDARD)
- **Auditoría WORM:** Registrar todas las operaciones en un log de auditoría inmutable (write-once-read-many)
- **Facturación por plan:** Soportar planes FREE, PRO, BUSINESS, ENTERPRISE con límites de almacenamiento, usuarios y requests

### Objetivos técnicos (inferidos del stack)

- **Stack:** Java 21 + Spring Boot 4.1.0 + PostgreSQL + JPA/Hibernate
- **API:** RESTful con Spring Web MVC
- **Seguridad:** Spring Security con validación JWT multifuente (un issuer distinto por tenant)
- **Migraciones:** Flyway para evolución del schema (aunque la dependencia no está en pom.xml aún)

## Pendientes

- [ ] Formalizar y priorizar los objetivos con el equipo
- [ ] Definir OKRs medibles
- [ ] Establecer criterios de aceptación para el MVP

## Preguntas abiertas

- ~~¿Cuál es el objetivo principal de negocio?~~ → **Resuelto:** MultiVault es el backend del producto de otro ([ADR-0018](../06-Decisiones/ADR-0018.md)).
- ¿Hay objetivos de rendimiento o escalabilidad definidos?
- ¿Se busca cumplir con alguna certificación (SOC2, ISO 27001, etc.)?
- ¿En qué deployments opera: self-hosted dentro de la red del cliente, o multi-tenant compartido operado por MultiVault? El segundo caso es el trigger que activa el login de `platform_user` y una posible identidad de primer usuario.
