package com.luxboy.mysecretary.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luxboy.mysecretary.ui.calendar.CalendarScreen
import com.luxboy.mysecretary.ui.contacts.ContactsScreen
import com.luxboy.mysecretary.ui.event.EventEditScreen
import com.luxboy.mysecretary.ui.search.SearchScreen
import com.luxboy.mysecretary.ui.settings.SettingsScreen
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime

object Routes {
    const val CALENDAR = "calendar"
    const val EVENT_EDIT = "event/edit"
    const val SETTINGS = "settings"
    const val CONTACTS = "contacts"
    const val SEARCH = "search"
    const val ARG_EVENT_ID = "eventId"
    const val ARG_DATE = "date"
    const val ARG_TITLE = "title"
    const val ARG_TIME = "time"

    fun eventEdit(
        eventId: Long? = null,
        date: LocalDate? = null,
        title: String? = null,
        time: LocalTime? = null,
    ): String {
        val id = eventId ?: 0L
        val epochDay = date?.toEpochDay() ?: 0L
        val encodedTitle = title?.takeIf { it.isNotBlank() }
            ?.let { URLEncoder.encode(it, "UTF-8") }
            ?: ""
        val timeSec = time?.toSecondOfDay()?.toLong() ?: -1L
        return "$EVENT_EDIT?$ARG_EVENT_ID=$id&$ARG_DATE=$epochDay&$ARG_TITLE=$encodedTitle&$ARG_TIME=$timeSec"
    }
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CALENDAR) {
        composable(Routes.CALENDAR) {
            CalendarScreen(
                onAddEvent = { date -> navController.navigate(Routes.eventEdit(date = date)) },
                onEditEvent = { id -> navController.navigate(Routes.eventEdit(eventId = id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
            )
        }
        composable(
            route = "${Routes.EVENT_EDIT}?${Routes.ARG_EVENT_ID}={${Routes.ARG_EVENT_ID}}" +
                "&${Routes.ARG_DATE}={${Routes.ARG_DATE}}" +
                "&${Routes.ARG_TITLE}={${Routes.ARG_TITLE}}" +
                "&${Routes.ARG_TIME}={${Routes.ARG_TIME}}",
            arguments = listOf(
                navArgument(Routes.ARG_EVENT_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument(Routes.ARG_DATE) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument(Routes.ARG_TITLE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Routes.ARG_TIME) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            EventEditScreen(
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenContacts = { navController.navigate(Routes.CONTACTS) },
            )
        }
        composable(Routes.CONTACTS) {
            ContactsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onEventClick = { id -> navController.navigate(Routes.eventEdit(eventId = id)) },
            )
        }
    }
}
