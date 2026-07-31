# Testing

## Propósito

Documentar la estrategia de testing del proyecto, herramientas y cobertura.

## Estado actual

Existe un único test: `MultivaultApplicationTests.java` que verifica que el contexto de Spring Boot carga correctamente. No hay tests unitarios, de integración ni funcionales.

## Información encontrada

### Dependencias de test en pom.xml

| Dependencia | Propósito |
|---|---|
| `spring-boot-starter-data-jpa-test` | Test slices para JPA (DataJpaTest) |
| `spring-boot-starter-security-test` | Test utilities para seguridad (MockMvc + Security) |
| `spring-boot-starter-validation-test` | Test slices para validación |
| `spring-boot-starter-webmvc-test` | Test slices para controladores (WebMvcTest) |

### Test existente

```java
@SpringBootTest
class MultivaultApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

### Framework de testing

- **JUnit 5** (incluido por defecto en Spring Boot starters)
- **Mockito** (incluido por defecto)
- **AssertJ** (incluido por defecto)
- No hay Testcontainers ni H2 configurados

### Tipo de tests previstos (por implementar)

| Tipo | Anotación | Propósito |
|---|---|---|
| Unitarios | `@ExtendWith(MockitoExtension.class)` | Servicios, validación de reglas |
| JPA Slice | `@DataJpaTest` | Repositorios, queries |
| MVC Slice | `@WebMvcTest` | Controladores, serialización |
| Integración | `@SpringBootTest` | Flujos completos, multi-tenancy |
| Seguridad | `@WebMvcTest` + `@WithMockUser` | Autenticación, autorización |

## Pendientes

- [ ] Configurar test database (H2 en memoria o Testcontainers con PostgreSQL)
- [ ] Crear tests unitarios para servicios
- [ ] Crear tests de integración para repositorios
- [ ] Crear tests de controladores con MockMvc
- [ ] Configurar cobertura con JaCoCo
- [ ] Crear archivo de configuración `application-test.yaml`
- [ ] Implementar tests de seguridad (autenticación, autorización)

## Preguntas abiertas

- ¿Se usará Testcontainers para tener PostgreSQL real en tests o H2 con modo PostgreSQL?
- ¿Límite de cobertura deseado?
- ¿Se requiere integración continua con tests automatizados?
