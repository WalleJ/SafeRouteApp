package com.example.saferouteapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    private EditText nameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private Button registerButton;
    private TextView loginTextView;

    // Lista hardcodeada de usuarios registrados (simulación)
    private static final String[] REGISTERED_EMAILS = {
            "usuario@saferoute.com",
            "admin@saferoute.com"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Inicializar vistas
        nameEditText = findViewById(R.id.name_edit_text);
        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        confirmPasswordEditText = findViewById(R.id.confirm_password_edit_text);
        registerButton = findViewById(R.id.register_button);
        loginTextView = findViewById(R.id.login_text_view);

        // Configurar botón de registro
        registerButton.setOnClickListener(v -> attemptRegister());

        // Configurar link de login
        loginTextView.setOnClickListener(v -> {
            finish(); // Volver a LoginActivity
        });
    }

    private void attemptRegister() {
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        // Validaciones
        if (name.isEmpty()) {
            nameEditText.setError("Ingresa tu nombre");
            nameEditText.requestFocus();
            return;
        }

        if (email.isEmpty()) {
            emailEditText.setError("Ingresa tu email");
            emailEditText.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Ingresa un email válido");
            emailEditText.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordEditText.setError("Ingresa tu contraseña");
            passwordEditText.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("La contraseña debe tener al menos 6 caracteres");
            passwordEditText.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Las contraseñas no coinciden");
            confirmPasswordEditText.requestFocus();
            return;
        }

        // Verificar si el email ya está registrado
        for (String registeredEmail : REGISTERED_EMAILS) {
            if (email.equalsIgnoreCase(registeredEmail)) {
                emailEditText.setError("Este email ya está registrado");
                emailEditText.requestFocus();
                Toast.makeText(this, "El email ya existe. Intenta iniciar sesión.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // Registro exitoso (simulado)
        Toast.makeText(this, "¡Registro exitoso! Ahora puedes iniciar sesión.", Toast.LENGTH_LONG).show();

        // Volver a LoginActivity
        finish();
    }
}

