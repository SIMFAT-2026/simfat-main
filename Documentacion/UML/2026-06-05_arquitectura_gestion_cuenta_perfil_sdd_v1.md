# Arquitectura — Gestion de Cuenta y Perfil de Usuario

Fecha: 2026-06-05
Sprint: CU12 / CU13 / CU14
Estado: implementado en produccion

---

## 1. Diagrama de componentes

```mermaid
flowchart TD
    subgraph Frontend ["Frontend React/Vite (Vercel)"]
        NB[Navbar\nLink username → /account]
        AP[AccountPage\n/account]
        PF[ProfileForm\nfullName / phone\nselect region / select comuna]
        PW[PasswordForm\ncurrentPwd / newPwd / confirmPwd]
        TD[territorioChile.js\n86 comunas GADM estaticas]
        AS[accountService.js\ngetProfile / updateProfile / changePassword]
        EP[endpoints.js\n/api/account/me\n/api/account/change-password]
    end

    subgraph Backend ["Backend Spring Boot (Railway)"]
        AC[AccountController\nGET /api/account/me\nPATCH /api/account/me\nPOST /api/account/change-password]
        ACS[AccountServiceImpl]
        AUTH[AuthService\nrevokeAllTokens]
        AUR[AppUserRepository]
        UVR[UserVerificationRepository]
        VER[VerificationEventRepository]
        UCP[UserCommunityProfileRepository]
        PE[PasswordEncoder\nBCrypt]
        SEC[SecurityIntegrationConfig\nJWT filter\nOPTIONS permitAll]
        COR[CorsConfig\nGET POST PUT PATCH DELETE OPTIONS]
    end

    subgraph DB ["PostgreSQL (Supabase)"]
        UT[(app_users\n+phone +region_code +comuna_code)]
        UV[(user_verification)]
        VE[(verification_events)]
        UPR[(user_community_profiles\nprimary_region_id)]
        RT[(refresh_tokens\nrevoked_at)]
    end

    NB --> AP
    AP --> PF
    AP --> PW
    PF --> TD
    PF --> AS
    PW --> AS
    AS --> EP

    EP -->|JWT Bearer| SEC
    SEC --> AC
    AC --> ACS
    ACS --> AUR --> UT
    ACS --> UVR --> UV
    ACS --> VER --> VE
    ACS --> UCP --> UPR
    ACS --> PE
    ACS --> AUTH --> RT
```

---

## 2. Diagrama de clases

```mermaid
classDiagram
    class AccountController {
        +getProfile(principal) AccountProfileDTO
        +updateProfile(principal, UpdateProfileRequestDTO) AccountProfileDTO
        +changePassword(principal, ChangePasswordRequestDTO) void
    }

    class AccountService {
        <<interface>>
        +getProfile(userId) AccountProfileDTO
        +updateProfile(userId, UpdateProfileRequestDTO) AccountProfileDTO
        +changePassword(userId, ChangePasswordRequestDTO) void
    }

    class AccountServiceImpl {
        -appUserRepository AppUserRepository
        -userVerificationRepository UserVerificationRepository
        -verificationEventRepository VerificationEventRepository
        -userCommunityProfileRepository UserCommunityProfileRepository
        -passwordEncoder PasswordEncoder
        -authService AuthService
        +getProfile(userId) AccountProfileDTO
        +updateProfile(userId, request) AccountProfileDTO
        +changePassword(userId, request) void
        -requireUser(userId) AppUser
        -toProfileDTO(user, verification) AccountProfileDTO
    }

    class AuthService {
        <<interface>>
        +revokeAllTokens(userId) void
    }

    class AppUser {
        +String id
        +String email
        +String fullName
        +String passwordHash
        +Boolean enabled
        +Set~AppRole~ rolesLegacy
        +String phone
        +String regionCode
        +String comunaCode
        +Instant createdAt
        +Instant updatedAt
    }

    class UserVerification {
        +String userId
        +VerificationStatus status
        +String organizationName
        +Instant emailVerifiedAt
        +Instant identityVerifiedAt
    }

    class VerificationEvent {
        +String id
        +String userId
        +String eventType
        +VerificationStatus oldStatus
        +VerificationStatus newStatus
        +String reviewedBy
        +String notes
        +Instant createdAt
    }

    class UserCommunityProfile {
        +String userId
        +String primaryRegionId
        +Instant updatedAt
    }

    class AccountProfileDTO {
        +String id
        +String email
        +String fullName
        +String phone
        +String regionCode
        +String comunaCode
        +String organizationName
        +String verificationStatus
        +Set~String~ roles
        +Instant createdAt
    }

    class UpdateProfileRequestDTO {
        +String fullName
        +String phone
        +String regionCode
        +String comunaCode
    }

    class ChangePasswordRequestDTO {
        +String currentPassword
        +String newPassword
        +String confirmPassword
    }

    AccountController --> AccountService
    AccountServiceImpl ..|> AccountService
    AccountServiceImpl --> AppUser
    AccountServiceImpl --> UserVerification
    AccountServiceImpl --> VerificationEvent
    AccountServiceImpl --> UserCommunityProfile
    AccountServiceImpl --> AuthService
    AccountController ..> AccountProfileDTO
    AccountController ..> UpdateProfileRequestDTO
    AccountController ..> ChangePasswordRequestDTO
```

---

## 3. Diagrama de secuencia — PATCH /api/account/me

```mermaid
sequenceDiagram
    actor Usuario
    participant Frontend
    participant AccountController
    participant AccountServiceImpl
    participant AppUserRepository
    participant UserVerificationRepository
    participant VerificationEventRepository
    participant UserCommunityProfileRepository

    Usuario->>Frontend: Guarda cambios de perfil
    Frontend->>AccountController: PATCH /api/account/me\n{fullName, phone, regionCode, comunaCode}
    AccountController->>AccountServiceImpl: updateProfile(userId, request)
    AccountServiceImpl->>AppUserRepository: findById(userId)
    AppUserRepository-->>AccountServiceImpl: AppUser

    alt fullName cambio Y verificacion alta
        AccountServiceImpl->>UserVerificationRepository: findById(userId)
        UserVerificationRepository-->>AccountServiceImpl: UserVerification{FULLY_VERIFIED}
        AccountServiceImpl->>UserVerificationRepository: save(status=EMAIL_VERIFIED)
        AccountServiceImpl->>VerificationEventRepository: save(IDENTITY_RESET)
    end

    AccountServiceImpl->>AppUserRepository: save(user con campos actualizados)

    alt regionCode cambio
        AccountServiceImpl->>UserCommunityProfileRepository: findById o crear nuevo
        AccountServiceImpl->>UserCommunityProfileRepository: save(primaryRegionId=regionCode)
        Note over AccountServiceImpl,UserCommunityProfileRepository: Sync automatico → acceso al chat regional
    end

    AccountServiceImpl-->>AccountController: AccountProfileDTO
    AccountController-->>Frontend: 200 OK {data: perfil actualizado}
    Frontend-->>Usuario: Muestra perfil guardado\n(con aviso de degradacion si aplica)
```

---

## 4. Diagrama de secuencia — POST /api/account/change-password

```mermaid
sequenceDiagram
    actor Usuario
    participant Frontend
    participant AccountController
    participant AccountServiceImpl
    participant AppUserRepository
    participant AuthService
    participant RefreshTokenRepository

    Usuario->>Frontend: Envia currentPassword + newPassword + confirmPassword
    Frontend->>AccountController: POST /api/account/change-password
    AccountController->>AccountServiceImpl: changePassword(userId, request)
    AccountServiceImpl->>AppUserRepository: findById(userId)
    AppUserRepository-->>AccountServiceImpl: AppUser

    AccountServiceImpl->>AccountServiceImpl: BCrypt.matches(currentPassword, hash)
    alt currentPassword incorrecta
        AccountServiceImpl-->>AccountController: BadRequestException
        AccountController-->>Frontend: 400 "La contrasena actual no es correcta"
    end

    AccountServiceImpl->>AccountServiceImpl: BCrypt.matches(newPassword, hash)
    alt newPassword == currentPassword
        AccountServiceImpl-->>AccountController: BadRequestException
        AccountController-->>Frontend: 400 "La nueva contrasena debe ser distinta"
    end

    AccountServiceImpl->>AccountServiceImpl: newPassword.equals(confirmPassword)
    alt no coinciden
        AccountServiceImpl-->>AccountController: BadRequestException
        AccountController-->>Frontend: 400 "Confirmacion no coincide"
    end

    AccountServiceImpl->>AppUserRepository: save(passwordHash=BCrypt.encode(newPassword))
    AccountServiceImpl->>AuthService: revokeAllTokens(userId)
    AuthService->>RefreshTokenRepository: setRevokedAt=NOW() WHERE userId AND revokedAt IS NULL

    AccountServiceImpl-->>AccountController: void
    AccountController-->>Frontend: 200 OK "Contrasena actualizada. Sesiones cerradas."
    Frontend-->>Usuario: Redirige a /login en 2.5s
```

---

## 5. Flujo de datos frontend — selects de region/comuna

```mermaid
flowchart LR
    subgraph territorioChile.js
        R[REGIONES\nbiobio / nuble / araucania]
        C[getComunasByRegion\nregionId → ComunaInfo[]]
    end

    subgraph AccountPage.jsx
        SR[select region\nonChange resetComuna]
        SC[select comuna\nfiltrado por regionSeleccionada]
        BT[Guardar perfil]
    end

    subgraph accountService.js
        UP[updateAccountProfile\nPATCH /api/account/me]
    end

    R --> SR
    SR -->|regionId| C
    C --> SC
    SC --> BT
    BT --> UP
```

**Valores en el select de region:**

| value | label |
|---|---|
| `"biobio"` | Biobío |
| `"nuble"` | Ñuble |
| `"araucania"` | Araucanía |

Los valores coinciden exactamente con `regions._id` en MongoDB y con `community_chat_room_access.region_id`, habilitando el sync automatico sin transformacion.

---

## 6. Integracion con modulo de chat — sync de region

```mermaid
flowchart TD
    A[Usuario guarda regionCode="biobio"] --> B[AccountServiceImpl.updateProfile]
    B --> C{regionCode != null?}
    C -->|si| D[upsert UserCommunityProfile\nprimaryRegionId = regionCode]
    D --> E[ChatService.canAccessRoom]
    E --> F{primaryRegionId\n== roomRegionId?}
    F -->|si| G[Acceso al chat regional habilitado]
    F -->|no| H[Sin acceso al chat]
    C -->|no| I[No se modifica primaryRegionId]
```

El admin puede sobrescribir `primary_region_id` desde el panel de administracion para revocar o cambiar el acceso, independientemente de lo que el usuario tenga en su perfil.

---

## 7. Seguridad — cambios en configuracion

### CorsConfig.java
```java
registry.addMapping("/api/**")
    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    // PATCH agregado en este sprint para soportar PATCH /api/account/me
```

### SecurityIntegrationConfig.java
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()  // primera regla — preflight CORS
    // ... otras reglas ...
    .requestMatchers("/api/account/**").authenticated()      // cualquier usuario autenticado
)
```

La regla OPTIONS debe ser la primera para que el filtro CORS pueda procesar el preflight antes de que Spring Security evalúe autenticacion.
