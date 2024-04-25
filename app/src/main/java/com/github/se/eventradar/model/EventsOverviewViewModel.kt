package com.github.se.eventradar.model

import android.util.Log
import androidx.lifecycle.ViewModel
import com.github.se.eventradar.model.event.Event
import com.github.se.eventradar.model.event.EventCategory
import com.github.se.eventradar.model.event.EventList
import com.github.se.eventradar.model.event.getEventCategory
import com.github.se.eventradar.model.event.getEventTicket
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class EventsOverviewViewModel(db: FirebaseFirestore = Firebase.firestore) : ViewModel() {
  private val _uiState = MutableStateFlow(EventsOverviewUiState())

  val uiState: StateFlow<EventsOverviewUiState> = _uiState

  private val eventRef = db.collection("events")

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        filterEvents()
    }

    fun onFilterApplyClicked(applyFilter: Boolean) {
        _uiState.value = _uiState.value.copy(applyFilter = applyFilter)
        filterEvents()
    }

    private fun filterEvents() {
        val query = _uiState.value.searchQuery
        val filteredEvents =
            _uiState.value.eventList.allEvents.filter { it.eventName.contains(query, ignoreCase = true) }

        if (_uiState.value.applyFilter) {
            filteredEvents.filter { it.category == _uiState.value.eventList.selectedEvent?.category }
        } else {
            filteredEvents
        }

        _uiState.value = _uiState.value.copy(
            eventList = _uiState.value.eventList.copy(filteredEvent = filteredEvents)
        )
    }

  fun getEvents() {
    eventRef
        .get()
        .addOnSuccessListener { result ->
          val events =
              result.documents.map { document ->
                Event(
                    eventName = document.data?.get("name") as String,
                    eventPhoto = document.data?.get("photo_url") as String,
                    start = getLocalDateTime(document.data?.get("start") as String),
                    end = getLocalDateTime(document.data?.get("end") as String),
                    location =
                        getLocation(
                            document.data?.get("location_name") as String,
                            document.data?.get("location_lat") as Double,
                            document.data?.get("location_lng") as Double,
                        ),
                    description = document.data?.get("description") as String,
                    ticket =
                        getEventTicket(
                            document.data?.get("ticket_name") as String,
                            document.data?.get("ticket_price") as Double,
                            document.data?.get("ticket_quantity") as Int,
                        ),
                    contact = getEventContact(document.data?.get("main_organiser") as String),
                    organiserList = getSetOfStrings(document.data?.get("organisers_list")),
                    attendeeList = getSetOfStrings(document.data?.get("attendees_list")),
                    category = getEventCategory(document.data?.get("category") as String),
                    fireBaseID = document.id)
              }
          _uiState.value =
              _uiState.value.copy(
                  eventList = EventList(events, events, _uiState.value.eventList.selectedEvent))
        }
        .addOnFailureListener { exception ->
          Log.d("EventsOverviewViewModel", "Error getting documents: ", exception)
        }
  }

  private val userRef = db.collection("users")

  private fun getEventContact(contactId: String): String {
    var contactEmail = ""

    userRef
        .document(contactId)
        .collection("private")
        .limit(1) // Limit the query to retrieve only one document
        .get()
        .addOnSuccessListener { querySnapshot ->
          for (documentSnapshot in querySnapshot.documents) {
            contactEmail = documentSnapshot.data?.get("email") as String
            break
          }
        }
        .addOnFailureListener { exception ->
          Log.d("EventsOverviewViewModel", "Error getting event contact: ", exception)
        }

    return contactEmail
  }

  companion object {
    fun getLocalDateTime(dateTime: String): LocalDateTime {
      val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
      return LocalDateTime.parse(dateTime, formatter)
    }

    fun getSetOfStrings(data: Any?): Set<String> {
      return when (data) {
        is List<*> -> data.filterIsInstance<String>().toSet()
        is String -> setOf(data)
        else -> emptySet()
      }
    }
  }
}

data class EventsOverviewUiState(
    val eventList: EventList = EventList(emptyList(), emptyList(), null),
    val searchQuery: String = "",
    val applyFilter: Boolean = false,
)