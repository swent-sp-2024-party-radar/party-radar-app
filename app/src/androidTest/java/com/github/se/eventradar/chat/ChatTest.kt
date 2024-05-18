package com.github.se.eventradar.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.eventradar.model.Resource
import com.github.se.eventradar.model.User
import com.github.se.eventradar.model.message.Message
import com.github.se.eventradar.model.repository.message.IMessageRepository
import com.github.se.eventradar.model.repository.message.MockMessageRepository
import com.github.se.eventradar.model.repository.user.MockUserRepository
import com.github.se.eventradar.screens.ChatScreen
import com.github.se.eventradar.ui.chat.ChatScreen
import com.github.se.eventradar.ui.navigation.NavigationActions
import com.github.se.eventradar.ui.navigation.Route
import com.github.se.eventradar.viewmodel.ChatViewModel
import com.kaspersky.components.composesupport.config.withComposeSupport
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import io.github.kakaocup.compose.node.element.ComposeScreen.Companion.onComposeScreen
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.unmockkAll
import io.mockk.verify
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatTest : TestCase(kaspressoBuilder = Kaspresso.Builder.withComposeSupport()) {

  @get:Rule val composeTestRule = createComposeRule()

  // This rule automatic initializes lateinit properties with @MockK, @RelaxedMockK, etc.
  @get:Rule val mockkRule = MockKRule(this)

  // Relaxed mocks methods have a default implementation returning values
  @RelaxedMockK lateinit var mockNavActions: NavigationActions

  private lateinit var mockMessageRepository: IMessageRepository
  private lateinit var mockUserRepository: MockUserRepository
  private lateinit var chatViewModel: ChatViewModel

  private val opponentUser =
      User(
          userId = "2",
          birthDate = "01/01/2000",
          email = "",
          firstName = "Test",
          lastName = "2",
          phoneNumber = "",
          accountStatus = "active",
          eventsAttendeeList = mutableListOf(),
          eventsHostList = mutableListOf(),
          friendsList = mutableListOf(),
          profilePicUrl =
              "https://firebasestorage.googleapis.com/v0/b/event-radar-e6a76.appspot.com/o/Profile_Pictures%2Fplaceholder.png?alt=media&token=ba4b4efb-ff45-4617-b60f-3789e8fb75b6",
          qrCodeUrl = "",
          bio = "",
          username = "Test2")

  @Before
  fun testSetup() = runTest {
    mockMessageRepository = MockMessageRepository()
    mockUserRepository = MockUserRepository()

    mockUserRepository.updateCurrentUserId("1")

    mockUserRepository.addUser(opponentUser)

    var messageHistory = mockMessageRepository.createNewMessageHistory("1", "2")

    messageHistory = messageHistory as Resource.Success

    // TODO: fix logic with add tests
    messageHistory.data.messages.add(
        Message(
            sender = "1", content = "Test Message 1", dateTimeSent = LocalDateTime.now(), id = "1"),
    )

    mockMessageRepository.addMessage(
        Message(
            sender = "2", content = "Test Message 2", dateTimeSent = LocalDateTime.now(), id = "2"),
        messageHistory.data)

    chatViewModel = ChatViewModel(mockMessageRepository, mockUserRepository, "2")

    composeTestRule.setContent { ChatScreen(chatViewModel, mockNavActions) }
  }

  @After fun testTeardown() = runTest { unmockkAll() }

  @Test
  fun screenDisplaysAllElementsCorrectlyEmptyMessages() = run {
    onComposeScreen<ChatScreen>(composeTestRule) {
      step("Check if all elements are displayed") {
        chatAppBar { assertIsDisplayed() }
        chatAppBarTitle { assertIsDisplayed() }
        chatAppBarTitleImage { assertIsDisplayed() }
        chatAppBarTitleColumn {
          assertIsDisplayed()
          assertHasClickAction()
        }
        chatAppBarBackArrow {
          assertIsDisplayed()
          assertHasClickAction()
        }

        chatScreenMessagesList { assertIsDisplayed() }
        bottomNav { assertIsDisplayed() }

        chatInput { assertIsDisplayed() }
        chatInputField { assertIsDisplayed() }
        chatInputPlaceholder { assertIsDisplayed() }
        chatInputSendButton { assertIsDisplayed() }
        chatInputSendButtonIcon { assertIsDisplayed() }

        // Check chat bubbles
        receivedChatBubble { assertIsDisplayed() }
        receivedChatBubbleText { assertIsDisplayed() }
        sentChatBubble { assertIsDisplayed() }
        sentChatBubbleText { assertIsDisplayed() }
      }
    }
  }

  @Test
  fun insertNewMessageWorks() = run {
    onComposeScreen<ChatScreen>(composeTestRule) {
      step("Type message") {
        chatInputField {
          assertIsDisplayed()
          performTextInput("Test Message 3")
        }
        chatInputSendButton { performClick() }
      }
      step("Check if message is displayed") {
        chatScreenMessagesList { assertIsDisplayed() }
        onNode { hasText("Test Message 3") }.assertIsDisplayed()
      }
    }
  }

  @Test
  fun goBackButtonWorks() = run {
    onComposeScreen<ChatScreen>(composeTestRule) {
      step("Click back button") { chatAppBarBackArrow { performClick() } }
      step("Check if navigation action is called") {
        verify { mockNavActions.goBack() }
        verify { mockNavActions.navController }
        confirmVerified(mockNavActions)
      }
    }
  }

  @Test
  fun clickOpponentUserNameWorks() = run {
    onComposeScreen<ChatScreen>(composeTestRule) {
      step("Click opponent profile picture") { chatAppBarTitleColumn { performClick() } }
      step("Check if navigation action is called") {
        verify { mockNavActions.navController.navigate("${Route.PROFILE}/${opponentUser.userId}") }
        confirmVerified(mockNavActions)
      }
    }
  }
}
