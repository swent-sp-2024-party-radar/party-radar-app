package com.github.se.eventradar

import android.util.Log
import com.github.se.eventradar.model.Location
import com.github.se.eventradar.model.event.Event
import com.github.se.eventradar.model.event.EventCategory
import com.github.se.eventradar.model.event.EventTicket
import com.github.se.eventradar.model.repository.event.IEventRepository
import com.github.se.eventradar.model.repository.event.MockEventRepository
import com.github.se.eventradar.viewmodel.EventDetailsViewModel
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@ExperimentalCoroutinesApi
class EventDetailsViewmodelUnitTest {
  private lateinit var viewModel: EventDetailsViewModel
  private lateinit var eventRepository: IEventRepository

  class MainDispatcherRule(
      private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
  ) : TestWatcher() {
    override fun starting(description: Description) {
      Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
      Dispatchers.resetMain()
    }
  }

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  val test: MutableSet<String> = mutableSetOf("Test Organiser", "Organiser2")

  private val mockEvent =
      Event(
          eventName = "Event 1",
          eventPhoto = "",
          start = LocalDateTime.now(),
          end = LocalDateTime.now(),
          location = Location(0.0, 0.0, "Test Location"),
          description = "Test Description",
          ticket = EventTicket("Test Ticket", 0.0, 1, 0),
          mainOrganiser = "1",
          organiserList = mutableListOf("Test Organiser"),
          attendeeList = mutableListOf("Test Attendee"),
          category = EventCategory.COMMUNITY,
          fireBaseID = "1")

  private val factory =
      object : EventDetailsViewModel.Factory {
        override fun create(eventId: String): EventDetailsViewModel {
          return EventDetailsViewModel(eventRepository, eventId)
        }
      }

  @Before
  fun setUp() {
    eventRepository = MockEventRepository()
    viewModel = factory.create(eventId = mockEvent.fireBaseID)
  }

  @Test
  fun testGetEventWithNoDataInDataBase() = runTest {
    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0

    viewModel.getEventData()
    assert(viewModel.uiState.value.eventName.isEmpty())
    assert(viewModel.uiState.value.description.isEmpty())
    assert(viewModel.uiState.value.eventPhoto.isEmpty())
    assert(viewModel.uiState.value.mainOrganiser.isEmpty())

    unmockkAll()
  }

  @Test
  fun testGetEventSuccess() = runTest {
    eventRepository.addEvent(mockEvent)
    viewModel.getEventData()

    assert(viewModel.uiState.value.eventName == mockEvent.eventName)
    assert(viewModel.uiState.value.description == mockEvent.description)
    assert(viewModel.uiState.value.mainOrganiser == mockEvent.mainOrganiser)
    assert(viewModel.uiState.value.start == mockEvent.start)
    assert(viewModel.uiState.value.end == mockEvent.end)
    assert(viewModel.uiState.value.location == mockEvent.location)
    assert(viewModel.uiState.value.ticket == mockEvent.ticket)
    assert(viewModel.uiState.value.category == mockEvent.category)
  }

  @Test
  fun testGetEventWithUpdateAndFetchAgain() = runTest {
    eventRepository.addEvent(mockEvent)
    viewModel.getEventData()
    assert(viewModel.uiState.value.eventName == mockEvent.eventName)

    mockEvent.eventName = "New Name"
    assert(viewModel.uiState.value.eventName != mockEvent.eventName)

    viewModel.getEventData()
    assert(viewModel.uiState.value.eventName == mockEvent.eventName)
  }

  @Test
  fun testTicketIsFree() = runTest {
    eventRepository.addEvent(mockEvent)
    mockEvent.ticket = EventTicket("Paid", 0.0, 10, 0)
    viewModel.getEventData()
    assert(viewModel.isTicketFree())
  }

  @Test
  fun testTicketIsNotFree() = runTest {
    eventRepository.addEvent(mockEvent)
    val randomPrice: Double = kotlin.random.Random.nextDouble(0.001, Double.MAX_VALUE)
    mockEvent.ticket = EventTicket("Paid", randomPrice, 10, 0)
    viewModel.getEventData()
    assert(!viewModel.isTicketFree())
  }
}
