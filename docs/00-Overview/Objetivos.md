# Objetivos

## Propósito

Definir los objetivos del sistema MultiVault a nivel funcional y técnico.

## Estado actual

No existen objetivos formalmente documentados. Los objetivos aquí descritos se infieren del diseño de los esquemas de base de datos y las tecnologías seleccionadas.

## Información encontrada

### Objetivos funcionales (inferidos del schema)

- **Gestión documental:** Permitir la creación, versionado y organización de documentos en carpetas jerárquicas
- **Multi-tenancy:** Aislar completamente los datos de cada cliente mediante schemas separados en PostgreSQL
- **Control de acceso por recurso:** Implementar ACLs con niveles OWNER, EDITOR, VIEWER por documento
- **Autenticación federada:** Soportar OIDC/JWT por tenant para que cada cliente use su propio Identity Provider
- **Autenticación M2M:** Proveer API keys para integraciones machine-to-machine (SERVICE y STANDARD)
- **Auditoría WORM:** Registrar todas las operaciones en un log de auditoría inmutable (write-once-read-many)
- **Facturación por plan:** Soportar planes FREE, PRO, ENTERPRISE con límites de almacenamiento, usuarios y requests

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

- ¿Cuál es el objetivo principal de negocio?
- ¿Hay objetivos de rendimiento o escalabilidad definidos?
- ¿Se busca cumplir con alguna certificación (SOC2, ISO 27001, etc.)?
