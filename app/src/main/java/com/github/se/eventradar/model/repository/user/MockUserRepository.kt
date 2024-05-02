package com.github.se.eventradar.model.repository.user

import android.net.Uri
import com.github.se.eventradar.model.Resource
import com.github.se.eventradar.model.User
import kotlinx.coroutines.runBlocking

class MockUserRepository : IUserRepository {
  private val mockUsers = mutableListOf<User>()

  override suspend fun getUsers(): Resource<List<User>> {
    return Resource.Success(mockUsers)
  }

  override suspend fun getUser(uid: String): Resource<User?> {
    val user = mockUsers.find { it.userId == uid }

    return if (user != null) {
      Resource.Success(user)
    } else {
      Resource.Failure(Exception("User with id $uid not found"))
    }
  }

  override suspend fun addUser(user: User): Resource<Unit> {
    mockUsers.add(user)
    return Resource.Success(Unit)
  }

  override suspend fun addUser(map: Map<String, Any?>, documentId: String): Resource<Unit> {
    val user = User(map, documentId)
    return addUser(user)
  }

  override suspend fun updateUser(user: User): Resource<Unit> {
    val index = mockUsers.indexOfFirst { it.userId == user.userId }

    return if (index != -1) {
      mockUsers[index] = user
      Resource.Success(Unit)
    } else {
      Resource.Failure(Exception("User with id ${user.userId} not found"))
    }
  }

  override suspend fun deleteUser(user: User): Resource<Unit> {
    return if (mockUsers.remove(user)) {
      Resource.Success(Unit)
    } else {
      Resource.Failure(Exception("User with id ${user.userId} not found"))
    }
  }

  override suspend fun doesUserExist(userId: String): Resource<Unit> {
    return if (mockUsers.none { userId == it.userId })
        Resource.Failure(Exception("User not logged in"))
    else Resource.Success(Unit)
  }

  override suspend fun uploadImage(
    selectedImageUri: Uri,
    uid: String,
    folderName: String
  ): Resource<String> {
    val index = mockUsers.indexOfFirst { it.userId == uid }
    val profilePicUrl = "http://example.com/$folderName/${selectedImageUri}.jpg"

    return if (index != -1) {
      val user = User(
        userId = uid,
        username = mockUsers[index].username,
        qrCodeUrl = mockUsers[index].qrCodeUrl,
        email = mockUsers[index].email,
        birthDate = mockUsers[index].birthDate,
        phoneNumber = mockUsers[index].phoneNumber,
        firstName = mockUsers[index].firstName,
        lastName = mockUsers[index].lastName,
        accountStatus = mockUsers[index].accountStatus,
        eventsAttendeeSet = mockUsers[index].eventsAttendeeSet,
        eventsHostSet = mockUsers[index].eventsHostSet,
        friendsSet = mockUsers[index].friendsSet,
        profilePicUrl = profilePicUrl
        )
      val updateUser = runBlocking {updateUser(user) }
      if (updateUser is Resource.Failure) {
        return Resource.Failure(Exception("Failed to update user"))
      }
      Resource.Success(profilePicUrl)
    } else {
      Resource.Failure(Exception("User with id $uid not found"))
    }
  }

  override suspend fun getImage(uid: String, folderName: String): Resource<String> {
    return try {

      if (mockUsers.none { uid == it.userId }) {
        throw Exception("Invalid user ID")
      }
      Resource.Success("http://example.com/$folderName/pic.jpg")
    } catch (e: Exception) {
      Resource.Failure(e)
    }
  }
}
