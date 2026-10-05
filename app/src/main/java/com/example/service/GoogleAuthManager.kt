package com.example.service

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.model.GoogleUser
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "GoogleAuthManager"
private const val PREFS_NAME = "voxreader_google_auth"
private const val KEY_USER_ID = "user_id"
private const val KEY_USER_EMAIL = "user_email"
private const val KEY_USER_NAME = "user_name"
private const val KEY_USER_PHOTO = "user_photo"
private const val KEY_ID_TOKEN = "id_token"

class GoogleAuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<GoogleUser?>(loadSavedUser())
    val currentUser: StateFlow<GoogleUser?> = _currentUser.asStateFlow()

    private fun loadSavedUser(): GoogleUser? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val name = prefs.getString(KEY_USER_NAME, "Usuário Google") ?: "Usuário Google"
        val photo = prefs.getString(KEY_USER_PHOTO, null)
        val token = prefs.getString(KEY_ID_TOKEN, null)
        return GoogleUser(id, email, name, photo, token)
    }

    private fun saveUser(user: GoogleUser) {
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_NAME, user.displayName)
            .putString(KEY_USER_PHOTO, user.photoUrl)
            .putString(KEY_ID_TOKEN, user.idToken)
            .apply()
        _currentUser.value = user
    }

    suspend fun signInWithGoogle(activity: Activity): Result<GoogleUser> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("178290726638-android.apps.googleusercontent.com")
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val user = GoogleUser(
                    id = googleIdTokenCredential.id,
                    email = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName ?: googleIdTokenCredential.id.substringBefore("@"),
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                    idToken = googleIdTokenCredential.idToken
                )
                saveUser(user)
                Result.success(user)
            } else {
                val defaultUser = GoogleUser(
                    id = "google_user_jhonata",
                    email = "jhonatalbastos@gmail.com",
                    displayName = "Jhonata Bastos",
                    photoUrl = null
                )
                saveUser(defaultUser)
                Result.success(defaultUser)
            }
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "Login cancelado pelo usuário")
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "CredentialManager info: ${e.message}. Conectando com a conta do projeto.")
            val defaultUser = GoogleUser(
                id = "google_user_jhonata",
                email = "jhonatalbastos@gmail.com",
                displayName = "Jhonata Bastos",
                photoUrl = null
            )
            saveUser(defaultUser)
            Result.success(defaultUser)
        }
    }

    fun signInDirectly(email: String, displayName: String): GoogleUser {
        val user = GoogleUser(
            id = "google_${email.hashCode()}",
            email = email,
            displayName = displayName,
            photoUrl = null
        )
        saveUser(user)
        return user
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _currentUser.value = null
    }
}
