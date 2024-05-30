package com.github.se.eventradar.endtoend

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.eventradar.model.Location
import com.github.se.eventradar.model.Resource
import com.github.se.eventradar.model.User
import com.github.se.eventradar.model.event.Event
import com.github.se.eventradar.model.event.EventCategory
import com.github.se.eventradar.model.event.EventTicket
import com.github.se.eventradar.model.message.Message
import com.github.se.eventradar.model.repository.event.IEventRepository
import com.github.se.eventradar.model.repository.event.MockEventRepository
import com.github.se.eventradar.model.repository.message.IMessageRepository
import com.github.se.eventradar.model.repository.message.MockMessageRepository
import com.github.se.eventradar.model.repository.user.IUserRepository
import com.github.se.eventradar.model.repository.user.MockUserRepository
import com.github.se.eventradar.screens.ChatScreen
import com.github.se.eventradar.screens.EventDetailsScreen
import com.github.se.eventradar.screens.HomeScreen
import com.github.se.eventradar.screens.MessagesScreen
import com.github.se.eventradar.screens.ViewFriendsProfileScreen
import com.github.se.eventradar.ui.chat.ChatScreen
import com.github.se.eventradar.ui.event.EventDetails
import com.github.se.eventradar.ui.home.HomeScreen
import com.github.se.eventradar.ui.messages.MessagesScreen
import com.github.se.eventradar.ui.navigation.NavigationActions
import com.github.se.eventradar.ui.navigation.Route
import com.github.se.eventradar.ui.theme.MyApplicationTheme
import com.github.se.eventradar.ui.viewProfile.ViewFriendsProfileUi
import com.github.se.eventradar.viewmodel.ChatViewModel
import com.github.se.eventradar.viewmodel.EventDetailsViewModel
import com.github.se.eventradar.viewmodel.EventsOverviewViewModel
import com.github.se.eventradar.viewmodel.MessagesViewModel
import com.github.se.eventradar.viewmodel.ViewFriendsProfileViewModel
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.mockk.junit4.MockKRule
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UserFlowTests : TestCase() {
  @get:Rule var hiltRule = HiltAndroidRule(this)

  @get:Rule val composeTestRule = createComposeRule()

  // This rule automatically initializes lateinit properties with @MockK, @RelaxedMockK, etc.
  @get:Rule val mockkRule = MockKRule(this)

  private lateinit var navActions: NavigationActions

  private var userRepository: IUserRepository = MockUserRepository()
  private var eventRepository: IEventRepository = MockEventRepository()
  private var messageRepository: IMessageRepository = MockMessageRepository()

  private val mockEvent =
      Event(
          "Test 1",
          "",
          LocalDateTime.parse("2021-12-31T09:00:00"),
          LocalDateTime.parse("2022-01-01T00:00:00"),
          Location(0.0, 0.0, "EPFL"),
          "Test Description",
          EventTicket("Test Ticket", 0.0, 100, 0),
          "",
          mutableListOf(),
          mutableListOf(),
          EventCategory.SOCIAL,
          "1")

  private val user1 =
      User(
          userId = "user1",
          birthDate = "01.01.2000",
          email = "test@example.com",
          firstName = "John",
          lastName = "Doe",
          phoneNumber = "1234567890",
          accountStatus = "active",
          eventsAttendeeList = mutableListOf("event1", "event2"),
          eventsHostList = mutableListOf("event3"),
          friendsList = mutableListOf("user2"),
          profilePicUrl = "http://example.com/Profile_Pictures/1",
          qrCodeUrl = "http://example.com/QR_Codes/1",
          bio = "",
          username = "johndoe")

  private val user2 =
      User(
          userId = "user2",
          birthDate = "01.01.2000",
          email = "test@example.com",
          firstName = "Test",
          lastName = "",
          phoneNumber = "TestPhone",
          accountStatus = "active",
          eventsAttendeeList = mutableListOf("event1", "event2"),
          eventsHostList = mutableListOf("event3"),
          friendsList = mutableListOf("user1"),
          profilePicUrl = "http://example.com/Profile_Pictures/1",
          qrCodeUrl = "http://example.com/QR_Codes/1",
          bio = "Available",
          username = "pauldoe")

  @Before
  fun setUp() = runBlocking {
    hiltRule.inject()
    for (i in 0..2) {
      eventRepository.addEvent(mockEvent.copy(eventName = "Test $i", fireBaseID = "$i"))
    }

    (userRepository as MockUserRepository).addUser(user1)
    (userRepository as MockUserRepository).addUser(user2)
    (userRepository as MockUserRepository).updateCurrentUserId("user1")

    val mh = messageRepository.createNewMessageHistory("user1", "user2")
    messageRepository.addMessage(
        Message(
            sender = "user1",
            content = "Test Message 1",
            dateTimeSent = LocalDateTime.parse("2021-01-01T00:00:00"),
            id = "1"),
        (mh as Resource.Success).data)

    messageRepository.addMessage(
        Message(
            sender = "user2",
            content = "Test Message 2",
            dateTimeSent = LocalDateTime.parse("2021-02-01T00:00:00"),
            id = "2"),
        (mh).data)

    // Launch the Home screen
    composeTestRule.setContent {
      val navController = rememberNavController()
      navActions = NavigationActions(navController)
      MyApplicationTheme {
        NavHost(navController = navController, startDestination = Route.HOME) {
          composable(Route.HOME) {
            HomeScreen(EventsOverviewViewModel(eventRepository, userRepository), navActions)
          }
          composable(
              "${Route.EVENT_DETAILS}/{eventId}",
              arguments = listOf(navArgument("eventId") { type = NavType.StringType })) {
                val eventId = it.arguments!!.getString("eventId")!!
                EventDetails(
                    EventDetailsViewModel(eventRepository, userRepository, eventId), navActions)
              }
          composable(Route.MESSAGE) {
            MessagesScreen(
                MessagesViewModel(messageRepository, userRepository),
                navigationActions = navActions)
          }
          composable(
              "${Route.PRIVATE_CHAT}/{opponentId}",
              arguments = listOf(navArgument("opponentId") { type = NavType.StringType })) {
                val opponentId = it.arguments!!.getString("opponentId")!!
                val viewModel = ChatViewModel(messageRepository, userRepository, opponentId)
                ChatScreen(viewModel = viewModel, navigationActions = navActions)
              }
          composable(
              "${Route.PROFILE}/{friendUserId}",
              arguments = listOf(navArgument("friendUserId") { type = NavType.StringType })) {
                val friendUserId = it.arguments!!.getString("friendUserId")!!
                val viewModel = ViewFriendsProfileViewModel(userRepository, friendUserId)
                ViewFriendsProfileUi(viewModel = viewModel, navigationActions = navActions)
              }
        }
      }
    }
  }

  @Test
  fun homeScreenToEventDetailsScreen() = run {
    ComposeScreen.onComposeScreen<HomeScreen>(composeTestRule) {
      step("Check if all events are present at the start") {
        // Test the UI elements
        eventList { assertIsDisplayed() }
        eventCard { assertIsDisplayed() }
      }

      step("Filter to only show one event") {
        // Test the UI elements
        searchBarAndFilter { assertIsDisplayed() }
        searchBar { performTextInput("Test 1") }

        eventList { assertIsDisplayed() }
        eventCard { assertIsDisplayed() }
      }

      step("Click on the event card") { eventCard { performClick() } }
    }

    ComposeScreen.onComposeScreen<EventDetailsScreen>(composeTestRule) {
      step("Check if the event details are displayed") {
        // Test the UI elements
        eventTitle { assertIsDisplayed() }
        eventImage { assertIsDisplayed() }
        descriptionTitle { assertIsDisplayed() }
        descriptionContent {
          assertIsDisplayed()
          assertTextContains("Test Description")
        }
        distanceTitle { assertIsDisplayed() }
        distanceContent { assertIsDisplayed() }
        categoryTitle { assertIsDisplayed() }
        categoryContent {
          assertIsDisplayed()
          assertTextContains("Social")
        }
        dateTitle { assertIsDisplayed() }
        dateContent { assertIsDisplayed() }
        timeTitle { assertIsDisplayed() }
        timeContent { assertIsDisplayed() }
      }
    }
  }

  // User flow: homeScreen => messageScreen => friendList tab => view a friend's profile => open
  // conversation => send a message
  @Test
  fun homeScreenToOpenConversationAndSendMessage() = run {
    ComposeScreen.onComposeScreen<HomeScreen>(composeTestRule) {
      step("Check if elements are present") {
        logo { assertIsDisplayed() }
        tabs { assertIsDisplayed() }
        upcomingTab { assertIsDisplayed() }
        browseTab { assertIsDisplayed() }
        eventCard { assertIsDisplayed() }
        bottomNav { assertIsDisplayed() }
        viewToggleFab { assertIsDisplayed() }
        searchBarAndFilter { assertIsDisplayed() }
        eventList { assertIsDisplayed() }
        filterPopUp { assertIsNotDisplayed() }
        bottomNav { assertIsDisplayed() }
        homeIcon { assertIsDisplayed() }
        messageIcon { assertIsDisplayed() }
        qrIcon { assertIsDisplayed() }
        profileIcon { assertIsDisplayed() }
        hostingIcon { assertIsDisplayed() }
      }
      step("Click on the message icon") { messageIcon.performClick() }
    }

    ComposeScreen.onComposeScreen<MessagesScreen>(composeTestRule) {
      step("Check if tabs are present") {
        messagesTab { assertIsDisplayed() }
        friendsTab { assertIsDisplayed() }
      }
      step("Click on friends list") { friendsTab.performClick() }

      step("Check if friendsList is displayed") {
        friendsList { assertIsDisplayed() }
        friendPreviewItem {
          assertIsDisplayed()
          assertHasClickAction()
        }
        friendProfilePic { assertIsDisplayed() }
        friendName { assertIsDisplayed() }
        friendPhoneNumber { assertIsDisplayed() }
      }
      step("View friend profile") { friendPreviewItem { performClick() } }
    }
    ComposeScreen.onComposeScreen<ViewFriendsProfileScreen>(composeTestRule) {
      chatButton { assertIsDisplayed() }
      goBackButton { assertIsDisplayed() }
      bottomNav { assertIsDisplayed() }
      centeredViewProfileColumn { assertIsDisplayed() }
      friendProfilePic { assertIsDisplayed() }
      friendName { assertIsDisplayed() }
      friendUserName { assertIsDisplayed() }
      leftAlignedViewProfileColumn { assertIsDisplayed() }
      bioLabelText { assertIsDisplayed() }
      bioInfoText { assertIsDisplayed() }

      step("Start a conversation") { chatButton { performClick() } }
    }

    ComposeScreen.onComposeScreen<ChatScreen>(composeTestRule) {
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

        chatInput { assertIsDisplayed() }
        chatInputField { assertIsDisplayed() }
        chatInputPlaceholder { assertIsDisplayed() }
        chatInputSendButton { assertIsDisplayed() }
        chatInputSendButtonIcon { assertIsDisplayed() }

        receivedChatBubble { assertIsDisplayed() }
        receivedChatBubbleText { assertIsDisplayed() }
        sentChatBubble { assertIsDisplayed() }
        sentChatBubbleText { assertIsDisplayed() }
      }

      step("Send a message") {
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
  // User flow: homeScreen => messageScreen => messagesList => click on conversation => send a
  // message
  @Test
  fun homeScreenToMessagesListAndSendMessage() = run {
    ComposeScreen.onComposeScreen<HomeScreen>(composeTestRule) {
      step("Check if nav bar and tabs are present") {
        logo { assertIsDisplayed() }
        tabs { assertIsDisplayed() }
        upcomingTab { assertIsDisplayed() }
        browseTab { assertIsDisplayed() }
        eventCard { assertIsDisplayed() }
        bottomNav { assertIsDisplayed() }
        viewToggleFab { assertIsDisplayed() }
        searchBarAndFilter { assertIsDisplayed() }
        eventList { assertIsDisplayed() }
        filterPopUp { assertIsNotDisplayed() }
        bottomNav { assertIsDisplayed() }
        homeIcon { assertIsDisplayed() }
        messageIcon { assertIsDisplayed() }
        qrIcon { assertIsDisplayed() }
        profileIcon { assertIsDisplayed() }
        hostingIcon { assertIsDisplayed() }
      }
      step("Click on the message icon") { messageIcon.performClick() }
    }

    ComposeScreen.onComposeScreen<MessagesScreen>(composeTestRule) {
      step("Check if elements are displayed") {
        logo { assertIsDisplayed() }
        tabs { assertIsDisplayed() }
        messagesTab { assertIsDisplayed() }
        friendsTab { assertIsDisplayed() }
        messagesList { assertIsDisplayed() }
        messagePreviewItem {
          assertIsDisplayed()
          assertHasClickAction()
        }
        bottomNav { assertIsDisplayed() }
        profilePic { assertIsDisplayed() }
      }
      step("Click on conversation") { messagePreviewItem.performClick() }
    }
    ComposeScreen.onComposeScreen<ChatScreen>(composeTestRule) {
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

        chatInput { assertIsDisplayed() }
        chatInputField { assertIsDisplayed() }
        chatInputPlaceholder { assertIsDisplayed() }
        chatInputSendButton { assertIsDisplayed() }
        chatInputSendButtonIcon { assertIsDisplayed() }

        receivedChatBubble { assertIsDisplayed() }
        receivedChatBubbleText { assertIsDisplayed() }
        sentChatBubble { assertIsDisplayed() }
        sentChatBubbleText { assertIsDisplayed() }
      }

      step("Send a message") {
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
}
