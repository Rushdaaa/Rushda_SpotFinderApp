package com.example.rushda_spotfinderapp

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * The main activity for the Spot Finder application.
 *
 * This activity displays a Google Map and provides UI controls to interact with a
 * local database of saved locations. Users can perform the following actions:
 * - **Search**: Look up saved locations by name. The map will pan and zoom to the location if found.
 * - **Add**: Save a new location with a name, latitude, and longitude.
 * - **Update**: Modify the details of an existing location.
 * - **Delete**: Remove a location from the database.
 * - **Show All**: Display markers for all saved locations on the map.
 *
 * The activity manages the map's state, handles user input through various buttons and text fields,
 * and orchestrates CRUD (Create, Read, Update, Delete) operations on the location database
 * via the [LocationDBHelper].
 *
 * It implements [OnMapReadyCallback] to receive the [GoogleMap] instance once it's ready for use.
 */
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var dbHelper: LocationDBHelper

    private lateinit var etSearch: TextInputEditText
    private lateinit var searchLayout: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var etLatitude: TextInputEditText
    private lateinit var etLongitude: TextInputEditText
    private lateinit var btnAdd: MaterialButton
    private lateinit var btnUpdate: MaterialButton
    private lateinit var btnDelete: MaterialButton
    private lateinit var btnShowAll: MaterialButton
    private lateinit var btnSubmit: MaterialButton
    private lateinit var formLayout: LinearLayout
    private lateinit var tvCoordinates: TextView
    private lateinit var toggleGroup: MaterialButtonToggleGroup

    private var currentAction: String? = null
    // Stores the name of the last successfully searched location, used for updates.
    private var lastQueriedAddress: String? = null
    private var selectedMarker: Marker? = null
    // A flag to prevent the marker click listener from firing immediately after a programmatic moveCamera.
    private var ignoreNextMarkerClick = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the database helper.
        dbHelper = LocationDBHelper(this)
        // Asynchronously get the map fragment and prepare it for use.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        etSearch = findViewById(R.id.etSearch)
        searchLayout = findViewById(R.id.searchLayout)
        etName = findViewById(R.id.etAddress)
        etLatitude = findViewById(R.id.etLatitude)
        etLongitude = findViewById(R.id.etLongitude)
        btnAdd = findViewById(R.id.btnAdd)
        btnUpdate = findViewById(R.id.btnUpdate)
        btnDelete = findViewById(R.id.btnDelete)
        btnShowAll = findViewById(R.id.btnShowAll)
        btnSubmit = findViewById(R.id.btnSubmit)
        formLayout = findViewById(R.id.formLayout)
        toggleGroup = findViewById(R.id.actionToggleGroup)
        formLayout.visibility = View.GONE

        // A TextView created programmatically to show hints or coordinates under the search bar.
        tvCoordinates = TextView(this).apply {
            textSize = 14f
            visibility = View.GONE
            setPadding(0, 6, 0, 10)
        }
        // Insert the hint TextView into the layout dynamically.
        (searchLayout.parent as LinearLayout).addView(tvCoordinates, 1)

        // Handle search when the user presses the 'Enter' key on the keyboard.
        etSearch.setOnEditorActionListener { _, _, _ ->
            handleSearch(etSearch.text.toString().trim())
            true
        }
        // Handle search when the user clicks the search icon.
        searchLayout.setEndIconOnClickListener {
            handleSearch(etSearch.text.toString().trim())
        }

        btnAdd.setOnClickListener { toggleForm("add") }
        btnUpdate.setOnClickListener { toggleForm("update") }

        // Set up the delete button with a confirmation dialog.
        btnDelete.setOnClickListener {
            val address = etSearch.text.toString().trim()
            if (address.isEmpty()) {
                showHint("Enter a location name to delete")
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete \"$address\"?")
                .setPositiveButton("Yes") { _, _ ->
                    // If confirmed, delete from DB, clear map, and refresh all markers.
                    dbHelper.deleteLocation(address)
                    map.clear()
                    showAllMarkers()
                    // Reset UI state.
                    showHint("Deleted $address")
                    toggleGroup.clearChecked()
                    currentAction = null
                    formLayout.visibility = View.GONE
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnShowAll.setOnClickListener { showAllMarkers() }

        // The 'Submit' button handles both 'add' and 'update' actions based on the current state.
        btnSubmit.setOnClickListener {
            val name = etName.text.toString().trim()
            val latText = etLatitude.text.toString().trim()
            val lonText = etLongitude.text.toString().trim()
            val lat = latText.toDoubleOrNull()
            val lon = lonText.toDoubleOrNull()

            when (currentAction) {
                // Handle adding a new location.
                "add" -> {
                    if (name.isEmpty() || lat == null || lon == null) {
                        showHint("Fill in name, latitude, and longitude to add")
                        return@setOnClickListener
                    }
                    dbHelper.addLocation(name, lat, lon)
                    moveCamera(lat, lon, name, "Lat %.4f, Lon %.4f".format(lat, lon))
                    Toast.makeText(this, "Added successfully", Toast.LENGTH_SHORT).show()
                }

                // Handle updating an existing location.
                "update" -> {
                    val original = lastQueriedAddress
                    // An update requires a location to have been searched first.
                    if (original == null) {
                        showHint("Search a location first before updating")
                        return@setOnClickListener
                    }

                    // Partially update the location in the DB with any new values provided.
                    dbHelper.updatePartial(original, name, lat, lon)
                    // Re-query the location to get the fully updated details.
                    val updated = dbHelper.queryLocationCaseInsensitive(
                        if (name.isNotBlank()) name else original
                    )

                    if (updated != null) {
                        // Destructure the updated location data.
                        val (ulat, ulon, uname) = updated
                        lastQueriedAddress = uname
                        map.clear()
                        val coords = "Lat %.4f, Lon %.4f".format(ulat, ulon)
                        // Add a new marker for the updated location and display its info window.
                        val newMarker = map.addMarker(
                            MarkerOptions()
                                .position(LatLng(ulat, ulon))
                                .title(uname)
                                .snippet(coords)
                        )
                        selectedMarker = newMarker
                        moveCamera(ulat, ulon, uname, coords)
                        newMarker?.showInfoWindow()
                        Toast.makeText(this, "Updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // After submit, clear the form fields and reset the UI state.
            etName.text?.clear()
            etLatitude.text?.clear()
            etLongitude.text?.clear()
            formLayout.visibility = View.GONE
            toggleGroup.clearChecked()
            currentAction = null
        }
    }

    // Searches the database for a location by name (case-insensitive) and moves the camera to it if found.
    private fun handleSearch(query: String) {
        if (query.isEmpty()) {
            showHint("Enter a location to search")
            return
        }

        val result = dbHelper.queryLocationCaseInsensitive(query)
        if (result != null) {
            val (lat, lon, matchedName) = result
            // Store the name of the found location for potential updates.
            lastQueriedAddress = matchedName
            val coords = "Lat %.4f, Lon %.4f".format(lat, lon)
            showHint("Coordinates: $coords")
            moveCamera(lat, lon, matchedName, coords)
        } else {
            showHint("No record found for $query")
        }
    }

    // Displays a message in the dynamically added TextView under the search bar.
    private fun showHint(text: String) {
        tvCoordinates.text = text
        tvCoordinates.visibility = View.VISIBLE
    }

    // Toggles the visibility of the add/update form.
    private fun toggleForm(action: String) {
        // If the same action button is clicked again, hide the form.
        if (currentAction == action && formLayout.visibility == View.VISIBLE) {
            formLayout.visibility = View.GONE
            toggleGroup.clearChecked()
            currentAction = null // Reset the current action.
            return
        }

        currentAction = action
        formLayout.visibility = View.VISIBLE

        if (action == "update")
            showHint("Enter a location in the search bar first, then edit fields below")
        else
            showHint("")
    }

    // Callback function triggered when the Google Map is ready to be used.
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        // Set the initial camera position to a central point (e.g., GTA).
        val gta = LatLng(43.7, -79.4)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(gta, 8.5f))
        showAllMarkers()

        map.setOnMarkerClickListener { marker ->
            // This logic is to prevent the click listener from re-showing an info window right after it's programmatically shown.
            if (ignoreNextMarkerClick) {
                ignoreNextMarkerClick = false
                return@setOnMarkerClickListener true
            }
            selectedMarker = marker
            marker.showInfoWindow()
            true
        }
    }

    // A helper function to clear the map, add a single marker, and move the camera to it.
    private fun moveCamera(lat: Double, lon: Double, name: String, coords: String) {
        val loc = LatLng(lat, lon)
        // Clear previous markers before adding a new one for a focused view.
        map.clear()
        val marker = map.addMarker(
            MarkerOptions()
                .position(loc)
                .title(name)
                .snippet(coords)
        )
        selectedMarker = marker
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 11f))
        marker?.showInfoWindow()
    }

    // Fetches all locations from the database and displays them as markers on the map.
    private fun showAllMarkers() {
        map.clear()
        // Get all saved locations from the database helper.
        val all = dbHelper.getAllLocations()
        if (all.isEmpty()) {
            showHint("No locations available")
            return
        }
        for ((name, lat, lon) in all) {
            val loc = LatLng(lat, lon)
            map.addMarker(
                MarkerOptions()
                    .position(loc)
                    .title(name)
                    .snippet("Lat %.4f, Lon %.4f".format(lat, lon))
            )
        }
        // Reset camera to a default overview position after showing all markers.
        val center = LatLng(43.7, -79.4)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 8.5f))
    }
}
