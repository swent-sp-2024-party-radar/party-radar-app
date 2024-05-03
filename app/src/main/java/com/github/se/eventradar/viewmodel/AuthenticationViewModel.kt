package com.github.se.eventradar.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.eventradar.model.Resource
import com.github.se.eventradar.model.repository.user.IUserRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.storage.FirebaseStorage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@HiltViewModel
class LoginViewModel @Inject constructor(private val userRepository: IUserRepository) :
    ViewModel() {
  private val _uiState = MutableStateFlow(LoginUiState())
  val uiState: StateFlow<LoginUiState> = _uiState

    fun addUser(
        state: MutableStateFlow<LoginUiState> = _uiState,
        user: FirebaseUser? = Firebase.auth.currentUser
    ): Boolean {
        if (user == null) {
            Log.d("LoginScreenViewModel", "User not logged in")
            return false
        }

        // If no image is selected, use a placeholder image
        val imageURI =
            state.value.selectedImageUri
                ?: Uri.parse("android.resource://com.github.se.eventradar/drawable/placeholder")

        var qrCodeUrl: String = ""
        viewModelScope.launch(Dispatchers.IO) {
            qrCodeUrl = generateQRCode(user.uid)
            // Use the result on the main thread, if needed
            withContext(Dispatchers.Main) {
                // Update UI with qrCodeUrl
            }
        }

        val userValues =
            hashMapOf(
                "private/firstName" to state.value.firstName,
                "private/lastName" to state.value.lastName,
                "private/phoneNumber" to state.value.phoneNumber,
                "private/birthDate" to state.value.birthDate,
                "private/email" to user.email,
                "profilePicUrl" to imageURI.toString(),
                "qrCodeUrl" to qrCodeUrl,
                "username" to state.value.username,
                "accountStatus" to "active",
                "eventsAttendeeList" to emptyList<String>(),
                "eventsHostList" to emptyList<String>(),
            )

        // Add a new document with a generated ID into collection "users"
        val success: Boolean
        runBlocking { success = addUserAsync(userValues, user.uid) }

        return success
    }

  private suspend fun addUserAsync(userValues: Map<String, Any?>, userId: String): Boolean {
    return when (val result = userRepository.addUser(userValues, userId)) {
      is Resource.Success -> {
        true
      }
      is Resource.Failure -> {
        Log.d("LoginScreenViewModel", "Error adding user: ${result.throwable.message}")
        false
      }
    }
  }

  private suspend fun uploadImageAsync(
      selectedImageUri: Uri?,
      uid: String,
      folderName: String
  ): Boolean {
    if (selectedImageUri == null) {
      return false
    }
    return when (val result = userRepository.uploadImage(selectedImageUri, uid, folderName)) {
      is Resource.Success -> {
        true
      }
      is Resource.Failure -> {
        Log.d("LoginScreenViewModel", "Error uploading image: ${result.throwable.message}")
        false
      }
    }
  }

  private suspend fun getImageAsync(uid: String, folderName: String): String {
    return when (val result = userRepository.getImage(uid, folderName)) {
      is Resource.Success -> {
        Log.d("LoginScreenViewModel", "Image URL: ${result.data}")
        result.data
      }
      is Resource.Failure -> {
        Log.d("LoginScreenViewModel", "Error getting image: ${result.throwable.message}")
        ""
      }
    }
  }
    private suspend fun generateQRCode(userId: String): String {
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(userId, BarcodeFormat.QR_CODE, 200, 200)
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.RGB_565)
        for (x in 0 until 200) {
            for (y in 0 until 200) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color(0xFF000000).hashCode() else Color(0xFFFFFFFF).hashCode())
            }
        }

        // Convert the bitmap to a byte array
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val data = baos.toByteArray()

        // Create a reference to the file in Firebase Storage
        val storageRef = FirebaseStorage.getInstance().reference
        val qrCodesRef = storageRef.child("QR_Codes/$userId.png")

        // Upload the file to Firebase Storage
        val uploadTask = qrCodesRef.putBytes(data)

        // Get the download URL of the image
        val urlTask = uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let {
                    throw it
                }
            }
            qrCodesRef.downloadUrl
        }.await()

        return urlTask.toString()
    }

  fun doesUserExist(userId: String): Boolean {
    var userExists: Boolean

    runBlocking { userExists = doesUserExistAsync(userId) }

    return userExists
  }

  private suspend fun doesUserExistAsync(userId: String): Boolean {
    return when (val result = userRepository.doesUserExist(userId)) {
      is Resource.Success -> {
        true
      }
      is Resource.Failure -> {
        Log.d("LoginScreenViewModel", "User not logged in: ${result.throwable.message}")
        false
      }
    }
  }

  fun onSelectedImageUriChanged(uri: Uri?, state: MutableStateFlow<LoginUiState> = _uiState) {
    state.value = state.value.copy(selectedImageUri = uri)
  }

  fun onUsernameChanged(username: String, state: MutableStateFlow<LoginUiState> = _uiState) {
    state.value = state.value.copy(username = username)
  }

  fun onFirstNameChanged(firstName: String, state: MutableStateFlow<LoginUiState> = _uiState) {
    state.value = state.value.copy(firstName = firstName)
  }

  fun onLastNameChanged(lastName: String, state: MutableStateFlow<LoginUiState> = _uiState) {
    state.value = state.value.copy(lastName = lastName)
  }

  fun onPhoneNumberChanged(phoneNumber: String, state: MutableStateFlow<LoginUiState> = _uiState) {
    state.value = state.value.copy(phoneNumber = phoneNumber)
  }

  fun onBirthDateChanged(birthDate: String, state: MutableStateFlow<LoginUiState> = _uiState) {
    state.value = state.value.copy(birthDate = birthDate)
  }

  fun onCountryCodeChanged(
      countryCode: CountryCode,
      state: MutableStateFlow<LoginUiState> = _uiState
  ) {
    state.value = state.value.copy(selectedCountryCode = countryCode)
  }

  fun validateFields(state: MutableStateFlow<LoginUiState> = _uiState): Boolean {
    state.value =
        state.value.copy(
            userNameIsError = state.value.username.isEmpty(),
            firstNameIsError = state.value.firstName.isEmpty(),
            lastNameIsError = state.value.lastName.isEmpty(),
            phoneNumberIsError =
                !isValidPhoneNumber(state.value.phoneNumber, state.value.selectedCountryCode),
            birthDateIsError = !isValidDate(state.value.birthDate))

    return !state.value.userNameIsError &&
        !state.value.firstNameIsError &&
        !state.value.lastNameIsError &&
        !state.value.phoneNumberIsError &&
        !state.value.birthDateIsError
  }

  private fun isValidPhoneNumber(phoneNumber: String, countryCode: CountryCode): Boolean {
    return phoneNumber.length == countryCode.numberLength
  }

  private fun isValidDate(date: String): Boolean {
    val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    format.isLenient = false
    return try {
      format.parse(date)
      true
    } catch (e: Exception) {
      false
    }
  }
}

data class LoginUiState(
    val selectedImageUri: Uri? = null,
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumber: String = "",
    val birthDate: String = "",
    val selectedCountryCode: CountryCode = CountryCode.CH,
    val userNameIsError: Boolean = false,
    val firstNameIsError: Boolean = false,
    val lastNameIsError: Boolean = false,
    val phoneNumberIsError: Boolean = false,
    val birthDateIsError: Boolean = false,
)

enum class CountryCode(val ext: String, val country: String, val numberLength: Int) {
  US("+1", "United States", 10),
  FR("+33", "France", 9),
  CH("+41", "Switzerland", 9),
  UK("+44", "United Kingdom", 10),
  AU("+61", "Australia", 9),
  JP("+81", "Japan", 10),
  IN("+91", "India", 10),
}
