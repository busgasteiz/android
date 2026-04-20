package com.jaureguialzo.busgasteiz.data

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

// MARK: - Estado de autenticación

sealed class AuthState {
    object Loading : AuthState()
    object Anonymous : AuthState()
    data class SignedIn(val email: String) : AuthState()
}

// MARK: - Gestión de identidad del usuario via Google Sign-In
//
// Flujo:
//  1. Al arrancar se intenta un sign-in silencioso (solo cuentas ya autorizadas, sin UI).
//  2. Si falla, la app sigue funcionando con auth anónima de Firebase.
//  3. El usuario puede iniciar sesión explícitamente desde la pantalla de Favoritos.
//  4. Cuando la cuenta anónima se vincula a Google, el UID de Firestore no cambia
//     y los favoritos existentes se preservan. En un segundo dispositivo, Firebase
//     devuelve el mismo UID → los favoritos aparecen automáticamente.

class AuthRepository(private val context: Context) {

    private val auth = Firebase.auth
    private val credentialManager = CredentialManager.create(context)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState

    init {
        updateAuthState()
        auth.addAuthStateListener { updateAuthState() }
    }

    private fun updateAuthState() {
        val user = auth.currentUser
        _authState.value = when {
            user == null || user.isAnonymous -> AuthState.Anonymous
            else -> AuthState.SignedIn(user.email ?: "")
        }
    }

    // Intento silencioso al arrancar: sin UI en ningún caso.
    // Paso 1: cuentas ya autorizadas previamente (otro dispositivo).
    // Paso 2: si hay exactamente una cuenta Google en el dispositivo, se selecciona sola.
    // En cualquier otro caso (sin cuentas, múltiples cuentas) se queda con auth anónima.
    suspend fun trySilentSignIn(activity: ComponentActivity) {
        if (auth.currentUser?.isAnonymous == false) return

        val serverClientId = serverClientId() ?: return

        // Paso 1: cuentas ya autorizadas
        val authorizedRequest = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setAutoSelectEnabled(true)
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(serverClientId)
                    .build()
            )
            .build()

        try {
            val result = credentialManager.getCredential(activity, authorizedRequest)
            processCredential(GoogleIdTokenCredential.createFrom(result.credential.data).idToken)
            return
        } catch (e: GetCredentialException) {
            // Continúa al paso 2
        }

        // Paso 2: única cuenta en el dispositivo (autoSelect solo funciona con una)
        val singleAccountRequest = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setAutoSelectEnabled(true)
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(serverClientId)
                    .build()
            )
            .build()

        try {
            val result = credentialManager.getCredential(activity, singleAccountRequest)
            processCredential(GoogleIdTokenCredential.createFrom(result.credential.data).idToken)
        } catch (e: GetCredentialException) {
            // Sin cuentas o múltiples cuentas: seguimos anónimos, sin mensaje.
        }
    }

    private suspend fun processCredential(idToken: String) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser

        if (currentUser != null && currentUser.isAnonymous) {
            try {
                // Vincula la cuenta anónima a Google preservando el UID y los datos.
                currentUser.linkWithCredential(firebaseCredential).await()
            } catch (e: FirebaseAuthUserCollisionException) {
                // El usuario ya vinculó esta cuenta Google desde otro dispositivo:
                // iniciamos sesión directamente para obtener el mismo UID.
                auth.signInWithCredential(firebaseCredential).await()
            }
        } else {
            auth.signInWithCredential(firebaseCredential).await()
        }
    }

    // El Web Client ID lo genera el plugin google-services a partir de google-services.json.
    // Requiere que Google Sign-In esté habilitado en Firebase Console.
    // Si el recurso no existe todavía, devuelve null sin error de compilación.
    private fun serverClientId(): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) {
            println("[AuthRepository] default_web_client_id no disponible. Habilita Google Sign-In en Firebase Console.")
            return null
        }
        return context.getString(resId).takeIf { it.isNotEmpty() }
    }
}
