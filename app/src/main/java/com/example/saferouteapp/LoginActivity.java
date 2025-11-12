package com.example.saferouteapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton;
    private TextView registerTextView, forgotPasswordTextView;

    // Credenciales hardcodeadas
    private static final String HARDCODED_EMAIL = "usuario@saferoute.com";
    private static final String HARDCODED_PASSWORD = "123456";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Inicializar vistas
        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        loginButton = findViewById(R.id.login_button);
        registerTextView = findViewById(R.id.register_text_view);
        forgotPasswordTextView = findViewById(R.id.forgot_password_text_view);

        // Configurar botón de login
        loginButton.setOnClickListener(v -> attemptLogin());

        // Configurar link de registro
        registerTextView.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // Configurar link de olvidé contraseña
        forgotPasswordTextView.setOnClickListener(v -> {
            Toast.makeText(this, "Funcionalidad en desarrollo", Toast.LENGTH_SHORT).show();
        });
    }

    private void attemptLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Validaciones básicas
        if (email.isEmpty()) {
            emailEditText.setError("Ingresa tu email");
            emailEditText.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordEditText.setError("Ingresa tu contraseña");
            passwordEditText.requestFocus();
            return;
        }

        // Validar con credenciales hardcodeadas
        if (email.equals(HARDCODED_EMAIL) && password.equals(HARDCODED_PASSWORD)) {
            Toast.makeText(this, "¡Bienvenido!", Toast.LENGTH_SHORT).show();

            // Ir a MainActivity
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // Cerrar LoginActivity
        } else {
            Toast.makeText(this, "Email o contraseña incorrectos", Toast.LENGTH_LONG).show();
            passwordEditText.setText("");
        }
    }
}

