# Endpoint Template

Usar este template al documentar un nuevo endpoint en `docs/02-Backend/API.md`:

```markdown
### `METHOD /path/{param}`

**Descripción:** [breve descripción]

**Controlador:** `dev.achiri.multivault.controller.[...]`

**Servicio:** `dev.achiri.multivault.service.[...]`

#### Request

- **Headers:**
  - `Authorization: Bearer {token}` (requerido)
  - `X-Tenant-ID: {tenantId}` (requerido para endpoints de tenant)

- **Path parameters:**
  - `{param}` — descripción

- **Query parameters:**
  - `?page=0&size=20` — paginación (default: page=0, size=20)

- **Body:**
```json
{
    "campo": "valor"
}
```

#### Response

- **200 OK** — éxito
```json
{
    "id": "uuid",
    "campo": "valor"
}
```

- **400 Bad Request** — error de validación
- **401 Unauthorized** — autenticación requerida
- **403 Forbidden** — sin permisos
- **404 Not Found** — recurso no encontrado

#### Permisos requeridos

- `OWNER` o `EDITOR` para modificar
- `VIEWER` para lectura

#### Notas

[información adicional relevante]
```
