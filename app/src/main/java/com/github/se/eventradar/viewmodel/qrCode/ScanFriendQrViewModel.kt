package com.github.se.eventradar.viewmodel.qrCode

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.eventradar.model.Resource
import com.github.se.eventradar.model.User
import com.github.se.eventradar.model.repository.user.IUserRepository
import com.github.se.eventradar.ui.navigation.NavigationActions
import com.github.se.eventradar.ui.navigation.Route
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// TODO ViewModel & UI can be improved on by having a state where the UI reflects a loading icon if
// firebase operations take a long time
// TODO ViewModel & UI can be improved to error message for each different type of Error ?

@HiltViewModel(assistedFactory = ScanFriendQrViewModel.Factory::class)
class ScanFriendQrViewModel
@AssistedInject
constructor(
    private val userRepository: IUserRepository, // Dependency injection
    val qrCodeAnalyser: QrCodeAnalyser, // Dependency injection
    @Assisted private val navigationActions: NavigationActions
) : ViewModel() {

  @AssistedFactory
  interface Factory {
    fun create(navigationActions: NavigationActions): ScanFriendQrViewModel
  }

  companion object {
    @Composable
    fun create(navigationActions: NavigationActions): ScanFriendQrViewModel {
      return hiltViewModel<ScanFriendQrViewModel, Factory>(
          creationCallback = { factory -> factory.create(navigationActions = navigationActions) })
    }
  }

  enum class Action {
    None,
    NavigateToNextScreen,
    FirebaseFetchError,
    FirebaseUpdateError,
    AnalyserError,
    CantGetMyUID
  }

  enum class Tab {
    MyQR,
    ScanQR
  }

  data class QrCodeScanFriendState(
      val decodedResult: String? = null,
      val action: Action = Action.None,
      val tabState: Tab = Tab.MyQR,
      val username: String = "",
      val qrCodeLink: String = "",
      val isLoading: Boolean = true, // Indicates loading state
  )

  private var myUID: String = ""

  private val _uiState = MutableStateFlow(QrCodeScanFriendState())
  val uiState: StateFlow<QrCodeScanFriendState> = _uiState

  private val initialUiState: StateFlow<QrCodeScanFriendState> =
      flow {
            emit(QrCodeScanFriendState(isLoading = true))

            when (val userIdResult = userRepository.getCurrentUserId()) {
              is Resource.Success -> {
                val getMyUID = userIdResult.data
                myUID = getMyUID
              }
              is Resource.Failure -> {
                emit(QrCodeScanFriendState(isLoading = false, action = Action.CantGetMyUID))
              }
            }
            val decodedResult =
                callbackFlow {
                      qrCodeAnalyser.onDecoded = { decodedString ->
                        trySend(decodedString)
                        close()
                      }
                      awaitClose { qrCodeAnalyser.onDecoded = null }
                    }
                    .first()

            if (decodedResult == null) {
              emit(QrCodeScanFriendState(isLoading = false, action = Action.AnalyserError))
            } else {
              emit(QrCodeScanFriendState(isLoading = false, decodedResult = decodedResult))
              updateFriendList(decodedResult)
            }
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(5000),
              QrCodeScanFriendState(isLoading = true))

  init {
    viewModelScope.launch { initialUiState.collect { newState -> _uiState.value = newState } }
  }

  private fun updateFriendList(friendID: String) { // private
    Log.d("QrCodeFriendViewModel", "Friend ID: $friendID")

    viewModelScope.launch {
      val friendUserDeferred = async { userRepository.getUser(friendID) }
      val currentUserDeferred = async { userRepository.getUser(myUID) }

      val friendUser = friendUserDeferred.await()
      val currentUser = currentUserDeferred.await()

      if (friendUser is Resource.Success && currentUser is Resource.Success) {
        val friendUpdatesDeferred = async { retryUpdate(friendUser.data!!, myUID) }
        val userUpdatesDeferred = async { retryUpdate(currentUser.data!!, friendID) }

        // Await both updates to complete successfully
        val friendUpdateResult = friendUpdatesDeferred.await()
        val userUpdateResult = userUpdatesDeferred.await()

        // After successful updates, navigate to the next screen
        if (friendUpdateResult && userUpdateResult) {
          changeAction(Action.NavigateToNextScreen)
        } else {
          changeAction(Action.FirebaseUpdateError)
        }
      } else {
        changeAction(Action.FirebaseFetchError)
        return@launch
      }
    }
  }

  private suspend fun retryUpdate(user: User, friendIDToAdd: String): Boolean {
    var maxNumberOfRetries = 3
    var updateResult: Resource<Any>?
    do {
      updateResult =
          if (user.friendsList.contains(friendIDToAdd)) {
            Resource.Success(Unit)
          } else {
            user.friendsList.add(friendIDToAdd)
            when (userRepository.updateUser(user)) {
              is Resource.Success -> Resource.Success(Unit)
              is Resource.Failure -> Resource.Failure(Exception("Failed to update user"))
            }
          }
    } while ((updateResult !is Resource.Success) && (maxNumberOfRetries-- > 0))

    return updateResult is Resource.Success
  }

  private fun changeAction(action: Action) {

    _uiState.update { uiState -> uiState.copy(action = action) }
    if (action == Action.NavigateToNextScreen) {
      navigationActions.navController.navigate(
          Route.PRIVATE_CHAT + "/${uiState.value.decodedResult}")
      _uiState.update { uiState -> uiState.copy(action = Action.None) }
      changeTabState(Tab.MyQR)
    }
  }

  fun changeTabState(tab: Tab) {
    _uiState.update { uiState -> uiState.copy(tabState = tab) }
  }

  fun getUserDetails() {
    viewModelScope.launch {
      when (val userIdResource = userRepository.getCurrentUserId()) {
        is Resource.Success -> {
          val userId = userIdResource.data
          // Now fetch the user data using the fetched user ID
          when (val userResult = userRepository.getUser(userId)) {
            is Resource.Success -> {
              _uiState.update { uiState ->
                uiState.copy(
                    username = userResult.data!!.username, qrCodeLink = userResult.data.qrCodeUrl)
              }
            }
            is Resource.Failure -> {
              Log.d(
                  "ScanFriendQrViewModel",
                  "Error fetching user details: ${userResult.throwable.message}")
            }
          }
        }
        is Resource.Failure -> {
          Log.d(
              "ScanFriendQrViewModel",
              "Error fetching user ID: ${userIdResource.throwable.message}")
        }
      }
    }
  }
}
