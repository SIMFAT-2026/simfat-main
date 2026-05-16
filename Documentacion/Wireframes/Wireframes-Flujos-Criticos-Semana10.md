# Wireframes Funcionales - Semana 10

Fecha: 2026-05-15

## Flujo 1: Login + acceso por rol

```text
[Login]
  email + password
      |
      v
[JWT emitido]
      |
      v
[Router protegido]
  - PUBLIC -> vistas publicas
  - COMMUNITY/VERIFIED -> comunidad/reportes
  - MODERATOR -> moderacion
  - ADMIN/SUPER_ADMIN -> admin + control accesos
```

## Flujo 2: Panel Control de Accesos (rediseño compacto)

```text
[Tabla usuarios]
  email | nombre | roles efectivos | verificacion
      |
      v
[Card usuario]
  Perfil predefinido (select)
  Switch: Usuario verificado
  [Ajustes avanzados] (colapsable)
  Boton: Guardar roles
```

## Criterios UX aplicados

- Menor carga cognitiva (perfil principal en 1 seleccion).
- Acciones frecuentes visibles, acciones avanzadas ocultables.
- Feedback inmediato de guardado y errores.
- Diseño responsive para escritorio y notebook.
