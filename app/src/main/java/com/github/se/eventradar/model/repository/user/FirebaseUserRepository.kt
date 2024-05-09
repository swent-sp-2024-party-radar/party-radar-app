package com.github.se.eventradar.model.repository.user

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.github.se.eventradar.model.Resource
import com.github.se.eventradar.model.User
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.ktx.storage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.tasks.await

class FirebaseUserRepository(db: FirebaseFirestore = Firebase.firestore) : IUserRepository {
  private val userRef: CollectionReference = db.collection("users")

  private val birthdateString = "private/birthDate"
  private val emailString = "private/email"
  private val firstNameString = "private/firstName"
  private val lastNameString = "private/lastName"
  private val phoneNumberString = "private/phoneNumber"

  override suspend fun getUsers(): Resource<List<User>> {
    return try {
      val resultDocument = userRef.get().await()
      val users =
          resultDocument.documents.map { document ->
            val userMap = document.data!!
            val privateResult =
                userRef
                    .document(document.id)
                    .collection("private")
                    .document("private")
                    .get()
                    .await()
            userMap[birthdateString] = privateResult["birthDate"] as String
            userMap[emailString] = privateResult["email"] as String
            userMap[firstNameString] = privateResult["firstName"] as String
            userMap[lastNameString] = privateResult["lastName"] as String
            userMap[phoneNumberString] = privateResult["phoneNumber"] as String
            User(userMap, document.id)
          }
      Resource.Success(users)
    } catch (e: Exception) {
      Resource.Failure(e)
    }
  }

  override suspend fun getUser(uid: String): Resource<User?> {
    return try {
      val resultDocument = userRef.document(uid).get().await()
      val userMap = resultDocument.data!!
      val privateResult =
          userRef
              .document(resultDocument.id)
              .collection("private")
              .document("private")
              .get()
              .await()
      userMap[birthdateString] = privateResult["birthDate"] as String
      userMap[emailString] = privateResult["email"] as String
      userMap[firstNameString] = privateResult["firstName"] as String
      userMap[lastNameString] = privateResult["lastName"] as String
      userMap[phoneNumberString] = privateResult["phoneNumber"] as String

      val user = User(userMap, resultDocument.id)
      Resource.Success(user)
    } catch (e: Exception) {
      Resource.Failure(e)
    }
  }

  override suspend fun addUser(user: User): Resource<Unit> {
    val maps: Pair<Map<String, Any?>, Map<String, Any?>> = getMaps(user)

    return try {
      val docId = userRef.document().id
      userRef.document(docId).set(maps.first).await()
      userRef.document(docId).collection("private").document("private").set(maps.second).await()
      Resource.Success(Unit)
    } catch (e: Exception) {
      Resource.Failure(e)
    }
  }

  override suspend fun addUser(map: Map<String, Any?>, documentId: String): Resource<Unit> {
    val user = User(map, documentId)
    val maps: Pair<Map<String, Any?>, Map<String, Any?>> = getMaps(user)

    return try {
      userRef.document(documentId).set(maps.first).await()
      userRef
          .document(documentId)
          .collection("private")
          .document("private")
          .set(maps.second)
          .await()
      Resource.Success(Unit)
    } catch (e: Exception) {
      Resource.Failure(e)
    }
  }

  override suspend fun updateUser(user: User): Resource<Unit> {
    val maps: Pair<Map<String, Any?>, Map<String, Any?>> = getMaps(user)

    return try {
      userRef.document(user.userId).update(maps.first).await()
      userRef
          .document(user.userId)
          .collection("private")
          .document("private")
          .update(maps.second)
          .await()
      Resource.Success(Unit)
    } catch (e: Exception) {
      Resource.Failure(e)
    }
  }

  override suspend fun deleteUser(user: User): Resource<Unit> {
    return try {
      userRef.document(user.userId).delete().await()
      Resource.Success(Unit)
    } catch (e: Exception) {
      Resource.Failure(e)
    }
  }

  override suspend fun doesUserExist(userId: String): Resource<Unit> {
    return try {
      val user = userRef.document(userId).get().await()
      if (user.exists()) {
        Resource.Success(Unit)
      } else {
        Resource.Failure(Exception("User not found"))
      }
    } catch (e: Exception) {
      Resource.Failure(e)
    }
  }

  override suspend fun uploadImage(
      selectedImageUri: Uri,
      uid: String,
      folderName: String
  ): Resource<Unit> {
    val storageRef = Firebase.storage.reference.child("$folderName/$uid")
    try {
      val result = storageRef.putFile(selectedImageUri).await()
      return if (result.task.isSuccessful) {
        Resource.Success(Unit)
      } else {
        val error = result.task.exception
        Resource.Failure(error ?: Exception("Upload failed without a specific error"))
      }
    } catch (e: FirebaseNetworkException) {
        return Resource.Failure(Exception("Network error while trying to upload image", e))
    } catch (e: StorageException) {
       return if (e.message == StorageException.ERROR_OBJECT_NOT_FOUND.toString()) {
           Resource.Failure(Exception("File not found during upload", e))
        } else {
            Resource.Failure(Exception("Storage error during upload: ${e.message}", e))
        }
    } catch (e: Exception) {
        return Resource.Failure(Exception("Unknown error occurred during upload: ${e.message}", e))
    }
  }

  override suspend fun getImage(uid: String, folderName: String): Resource<String> {

    val storageRef =Firebase.storage.reference.child("$folderName/$uid")
    return try {
      val result = storageRef.downloadUrl.await()
      val url = result.toString()
      Resource.Success(url)
    } catch (e: FirebaseNetworkException) {
          Resource.Failure(Exception("Network error while trying to get image", e))
      } catch (e: StorageException) {
          if (e.message == StorageException.ERROR_OBJECT_NOT_FOUND.toString()) {
              Resource.Failure(Exception("Image file not found", e))
          } else {
              Resource.Failure(Exception("Storage error: ${e.message}", e))
          }
      } catch (e: Exception) {
          Resource.Failure(Exception("Unknown error occurred", e))
      }
  }

  override suspend fun getCurrentUserId(): Resource<String> {
    val userId = Firebase.auth.currentUser?.uid
    return if (userId != null) {
      Resource.Success(userId)
    } else {
      Resource.Failure(Exception("No user currently signed in"))
    }
  }

  override suspend fun generateQRCode(userId: String): Resource<String> { // upload this in firebase
    return try {
      val qrCodeWriter = QRCodeWriter()
      val bitMatrix = qrCodeWriter.encode(userId, BarcodeFormat.QR_CODE, 200, 200)
      val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.RGB_565)
      for (x in 0 until 200) {
        for (y in 0 until 200) {
          bitmap.setPixel(
              x,
              y,
              if (bitMatrix[x, y]) Color(0xFF000000).hashCode() else Color(0xFFFFFFFF).hashCode())
        }
      }
      // Convert the bitmap to a byte array
      val baos = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
      val data = baos.toByteArray()

      // Create a reference to the file in Firebase Storage
      val storageRef = FirebaseStorage.getInstance().reference
      val qrCodesRef = storageRef.child("QR_Codes/$userId")

      // Upload the file to Firebase Storage
      val uploadTask = qrCodesRef.putBytes(data).await()
      // Get the download URL of the image
      if (uploadTask.task.isSuccessful) {
        Resource.Success(qrCodesRef.path) // Return the reference to the uploaded QR Code's path
      } else {
        val error = uploadTask.task.exception
        Resource.Failure(error ?: Exception("Upload failed without a specific error"))
      }
    } catch (e: com.google.zxing.WriterException) {
      Resource.Failure(Exception("QR Code generation failed", e))
    } catch (e: java.io.IOException) {
      Resource.Failure(Exception("IO error during QR code generation", e))
    } catch (e: FirebaseNetworkException) {
      Resource.Failure(Exception("Network error while trying to get image", e))
    } catch (e: StorageException) {
      Resource.Failure(Exception("Firebase Upload Storage operation failed", e))
    } catch (e: Exception) {
      Resource.Failure(e)
    }
  }

  private fun getMaps(user: User): Pair<Map<String, Any?>, Map<String, Any?>> {
    val privateMap =
        mutableMapOf(
            "firstName" to user.firstName,
            "lastName" to user.lastName,
            "phoneNumber" to user.phoneNumber,
            "birthDate" to user.birthDate,
            "email" to user.email,
        )

    val publicMap =
        mutableMapOf(
            "profilePicUrl" to user.profilePicUrl,
            "qrCodeUrl" to user.qrCodeUrl,
            "username" to user.username,
            "accountStatus" to user.accountStatus,
            "eventsAttendeeList" to user.eventsAttendeeList,
            "eventsHostList" to user.eventsHostList,
            "friendsList" to user.friendsList,
        )

    return Pair(publicMap, privateMap)
  }
}
