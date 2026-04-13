package com.example.triad

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(this, "Error Google: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

        // Si ya hay sesión activa, decidir a dónde ir directamente
        auth.currentUser?.let {
            decidirDestino(it.uid)
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        callbackManager = CallbackManager.Factory.create()

        val prefs = Prefs(this)
        if (prefs.haySesionGuardada()) lanzarBiometria(prefs)

        val etEmail     = findViewById<EditText>(R.id.etEmail)
        val etPassword  = findViewById<EditText>(R.id.etPassword)
        val btnLogin    = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnGoogle   = findViewById<Button>(R.id.btnGoogle)
        val btnFacebook = findViewById<Button>(R.id.btnFacebook)
        val btnAnonymous= findViewById<Button>(R.id.btnAnonymous)
        val btnGithub   = findViewById<Button>(R.id.btnGithub)

        btnLogin.setOnClickListener {
            val email    = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            if (email.isNotEmpty() && password.isNotEmpty()) signIn(email, password)
            else Toast.makeText(this, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show()
        }

        btnRegister.setOnClickListener {
            val email    = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            if (email.isNotEmpty() && password.isNotEmpty()) register(email, password)
            else Toast.makeText(this, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show()
        }

        btnGoogle.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        btnFacebook.setOnClickListener {
            LoginManager.getInstance().logInWithReadPermissions(this, listOf("public_profile", "email"))
            LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) { firebaseAuthWithFacebook(result.accessToken.token) }
                override fun onCancel() { Toast.makeText(baseContext, "Login cancelado", Toast.LENGTH_SHORT).show() }
                override fun onError(error: FacebookException) { Toast.makeText(baseContext, "Error Facebook: ${error.message}", Toast.LENGTH_LONG).show() }
            })
        }

        btnGithub.setOnClickListener { loginConGithub() }

        btnAnonymous.setOnClickListener {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                    crearPerfilSiNoExiste(uid, "Viajero Anónimo") {
                        decidirDestino(uid)
                    }
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── DECISIÓN CENTRAL ────────────────────────────────────────────────────
    // Revisa Firestore: si onboardingCompleto == true → MainActivity
    //                   si no existe o es false     → WelcomeActivity (onboarding)
    private fun decidirDestino(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val onboardingCompleto = doc.getBoolean("onboardingCompleto") ?: false
                if (onboardingCompleto) {
                    irA(MainActivity::class.java)
                } else {
                    irA(WelcomeActivity::class.java)
                }
            }
            .addOnFailureListener {
                // Si hay error de red, mandamos al onboarding por seguridad
                irA(WelcomeActivity::class.java)
            }
    }

    // ─── CREAR PERFIL (para login social o anónimo) ──────────────────────────
    // Solo crea el documento si no existe; así no sobreescribe datos de usuarios existentes
    private fun crearPerfilSiNoExiste(uid: String, displayName: String, onDone: () -> Unit) {
        val ref = db.collection("users").document(uid)
        ref.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                val perfil = hashMapOf(
                    "name"               to displayName,
                    "points"             to 0L,
                    "hp"                 to 100,
                    "happiness"          to 100,
                    "mana"               to 100,
                    "level"              to 1,
                    "onboardingCompleto" to false
                )
                ref.set(perfil).addOnSuccessListener { onDone() }
                    .addOnFailureListener { onDone() }
            } else {
                onDone()
            }
        }.addOnFailureListener { onDone() }
    }

    // ─── LOGIN / REGISTRO EMAIL ───────────────────────────────────────────────
    private fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val prefs = Prefs(this)
                prefs.saveCorreo(email)
                prefs.savePassword(password)
                prefs.setSesionGuardada(true)
                decidirDestino(auth.currentUser!!.uid)
            } else {
                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun register(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid  = auth.currentUser!!.uid
                val name = email.substringBefore("@")
                crearPerfilSiNoExiste(uid, name) {
                    decidirDestino(uid)
                }
            } else {
                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── SOCIAL AUTH ─────────────────────────────────────────────────────────
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser!!
                crearPerfilSiNoExiste(user.uid, user.displayName ?: "Héroe") {
                    decidirDestino(user.uid)
                }
            } else {
                Toast.makeText(this, "Error Google Auth", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithFacebook(token: String) {
        val credential = FacebookAuthProvider.getCredential(token)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser!!
                crearPerfilSiNoExiste(user.uid, user.displayName ?: "Héroe") {
                    decidirDestino(user.uid)
                }
            }
        }
    }

    private fun loginConGithub() {
        val provider = OAuthProvider.newBuilder("github.com")
        auth.startActivityForSignInWithProvider(this, provider.build())
            .addOnSuccessListener { result ->
                val user = result.user!!
                crearPerfilSiNoExiste(user.uid, user.displayName ?: "Héroe") {
                    decidirDestino(user.uid)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error GitHub: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── BIOMETRÍA ───────────────────────────────────────────────────────────
    private fun lanzarBiometria(prefs: Prefs) {
        val biometricManager = BiometricManager.from(this)
        val authenticators   = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> mostrarPromptBiometrico(prefs)
            else -> autoLoginConCredenciales(prefs)
        }
    }

    private fun mostrarPromptBiometrico(prefs: Prefs) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt   = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                autoLoginConCredenciales(prefs)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Toast.makeText(baseContext, "Autenticación cancelada.", Toast.LENGTH_SHORT).show()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Bienvenido de vuelta")
            .setSubtitle("Usa tu huella o reconocimiento facial")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    private fun autoLoginConCredenciales(prefs: Prefs) {
        val correo   = prefs.getCorreo()
        val password = prefs.getPassword()
        if (correo.isEmpty() || password.isEmpty()) return
        auth.signInWithEmailAndPassword(correo, password).addOnCompleteListener { task ->
            if (task.isSuccessful) decidirDestino(auth.currentUser!!.uid)
            else {
                prefs.wipe()
                Toast.makeText(this, "Sesión expirada, inicia de nuevo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ─── UTILIDAD ────────────────────────────────────────────────────────────
    private fun irA(destino: Class<*>) {
        startActivity(Intent(this, destino))
        finish()
    }

    @Deprecated("Usar nuevo API de Result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        callbackManager.onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }
}