# 🔐 Sistema de Login y Registro - SafeRouteApp

## ✅ Archivos Creados

### Java Activities:
1. **LoginActivity.java** - Pantalla de inicio de sesión
2. **RegisterActivity.java** - Pantalla de registro de nuevos usuarios

### Layouts XML:
1. **activity_login.xml** - Diseño de la pantalla de login
2. **activity_register.xml** - Diseño de la pantalla de registro

### AndroidManifest.xml:
- Actualizado para registrar las nuevas activities
- LoginActivity configurada como pantalla de inicio (LAUNCHER)

---

## 🔑 Credenciales Hardcodeadas

### Para Login:
```
Email: usuario@saferoute.com
Contraseña: 123456
```

### Emails ya "registrados" (no se pueden usar para registro):
```
• usuario@saferoute.com
• admin@saferoute.com
```

---

## 📱 Flujo de la Aplicación

```
1. App inicia → LoginActivity (Pantalla de Login)
   ↓
2a. Usuario hace clic en "Regístrate" → RegisterActivity
   ↓
   Completa formulario → Valida datos → Vuelve a Login
   
2b. Usuario ingresa credenciales correctas → MainActivity (Mapa)
```

---

## 🎨 Características Implementadas

### LoginActivity:
- ✅ Campo de email con validación
- ✅ Campo de contraseña con toggle para mostrar/ocultar
- ✅ Botón "Iniciar Sesión" con validación
- ✅ Link "¿Olvidaste tu contraseña?" (muestra toast)
- ✅ Link "Regístrate" para ir a RegisterActivity
- ✅ Información visual de credenciales de prueba
- ✅ Validación de credenciales hardcodeadas
- ✅ Navegación a MainActivity al login exitoso

### RegisterActivity:
- ✅ Campo de nombre completo
- ✅ Campo de email con validación de formato
- ✅ Campo de contraseña (mínimo 6 caracteres)
- ✅ Campo de confirmación de contraseña
- ✅ Validación de coincidencia de contraseñas
- ✅ Verificación de email duplicado
- ✅ Link "Inicia Sesión" para volver al login
- ✅ Información visual de emails ya registrados

---

## 🔄 Validaciones Implementadas

### En Login:
1. Email no vacío
2. Contraseña no vacía
3. Credenciales coinciden con las hardcodeadas

### En Registro:
1. Nombre no vacío
2. Email no vacío
3. Email con formato válido
4. Contraseña no vacía
5. Contraseña de al menos 6 caracteres
6. Contraseñas coinciden
7. Email no existe en la lista de registrados

---

## 🎯 Próximos Pasos Sugeridos

Para convertir esto en un sistema real, podrías:

1. **Integrar con Firebase Authentication**
   - Reemplazar validación hardcodeada
   - Almacenar usuarios reales

2. **Agregar SharedPreferences**
   - Mantener sesión iniciada
   - Recordar usuario

3. **Agregar recuperación de contraseña**
   - Implementar funcionalidad real en "Olvidé mi contraseña"

4. **Agregar login con redes sociales**
   - Google Sign-In
   - Facebook Login

---

## 🧪 Cómo Probar

1. Ejecuta la app en el emulador/dispositivo
2. Verás la pantalla de Login
3. **Opción 1 - Login directo:**
   - Ingresa: usuario@saferoute.com
   - Contraseña: 123456
   - Presiona "Iniciar Sesión"
   - Deberías ver la pantalla del mapa

4. **Opción 2 - Registro:**
   - Presiona "Regístrate"
   - Completa el formulario con datos válidos
   - Usa un email diferente a los registrados
   - Presiona "Registrarse"
   - Vuelve al login (pero las credenciales hardcodeadas siguen siendo las únicas válidas)

---

## 📝 Notas Técnicas

- Las contraseñas se muestran/ocultan con el ícono del ojo
- Los campos tienen validación en tiempo real al enviar
- Los errores se muestran directamente en el campo correspondiente
- Se usan Material Design Components para un diseño moderno
- El código está listo para integrar con un backend real

---

## 🎨 Diseño

- **Color principal:** Azul (#2196F3) para elementos de login
- **Color secundario:** Verde (#4CAF50) para registro
- Material Design Text Input Layouts
- Iconos integrados en los campos
- Diseño responsivo con ScrollView
- Información visual de ayuda para testing

