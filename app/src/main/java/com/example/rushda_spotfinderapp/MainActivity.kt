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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * The main screen of the application, responsible for displaying a Google Map
 * and providing UI for managing saved locations.
 *
 * This activity handles:
 * - Displaying a map centered on the Greater Toronto Area.
 * - CRUD (Create, Read, Update, Delete) operations for locations stored in a local SQLite database
 *   via [LocationDBHelper].
 * - Searching for saved locations by name and displaying them on the map.
 * - Adding new locations with a name, latitude, and longitude.
 * - Updating existing locations.
 * - Deleting locations with user confirmation.
 * - Displaying all saved locations as markers on the map.
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

    private var currentAction: String? = null
    private var lastQueriedAddress: String? = null
    private var selectedMarker: Marker? = null
    private var ignoreNextMarkerClick = false  // ✅ prevents infinite update loops

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = LocationDBHelper(this)
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
        formLayout.visibility = View.GONE

        tvCoordinates = TextView(this).apply {
            textSize = 14f
            visibility = View.GONE
            setPadding(0, 6, 0, 10)
        }
        (searchLayout.parent as LinearLayout).addView(tvCoordinates, 1)

        etSearch.setOnEditorActionListener { _, _, _ ->
            handleSearch(etSearch.text.toString().trim())
            true
        }
        searchLayout.setEndIconOnClickListener {
            handleSearch(etSearch.text.toString().trim())
        }

        btnAdd.setOnClickListener { toggleForm("add") }
        btnUpdate.setOnClickListener { toggleForm("update") }

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
                    dbHelper.deleteLocation(address)
                    showHint("Deleted $address")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnShowAll.setOnClickListener { showAllMarkers() }

        btnSubmit.setOnClickListener {
            val name = etName.text.toString().trim()
            val latText = etLatitude.text.toString().trim()
            val lonText = etLongitude.text.toString().trim()
            val lat = latText.toDoubleOrNull()
            val lon = lonText.toDoubleOrNull()

            when (currentAction) {
                "add" -> {
                    if (name.isEmpty() || lat == null || lon == null) {
                        showHint("Fill in name, latitude, and longitude to add")
                        return@setOnClickListener
                    }
                    dbHelper.addLocation(name, lat, lon)
                    moveCamera(lat, lon, name, "Lat %.4f, Lon %.4f".format(lat, lon))
                    Toast.makeText(this, "✅ Added successfully", Toast.LENGTH_SHORT).show()
                }

                "update" -> {
                    val original = lastQueriedAddress
                    if (original == null) {
                        showHint("Search a location first before updating")
                        return@setOnClickListener
                    }

                    dbHelper.updatePartial(original, name, lat, lon)
                    // ✅ now re-fetch and refresh map marker in a single reliable block
                    val updated = dbHelper.queryLocationCaseInsensitive(
                        if (name.isNotBlank()) name else original
                    )

                    if (updated != null) {
                        val (ulat, ulon, uname) = updated
                        lastQueriedAddress = uname
                        map.clear()
                        val coords = "Lat %.4f, Lon %.4f".format(ulat, ulon)
                        val newMarker = map.addMarker(
                            MarkerOptions()
                                .position(LatLng(ulat, ulon))
                                .title(uname)
                                .snippet(coords)
                        )
                        selectedMarker = newMarker
                        moveCamera(ulat, ulon, uname, coords)
                        newMarker?.showInfoWindow()  // ✅ show updated popup immediately
                        Toast.makeText(this, "✅ Updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "⚠️ Update failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            etName.text?.clear()
            etLatitude.text?.clear()
            etLongitude.text?.clear()
            formLayout.visibility = View.GONE
        }
    }

    private fun handleSearch(query: String) {
        if (query.isEmpty()) {
            showHint("Enter a location to search")
            return
        }

        val result = dbHelper.queryLocationCaseInsensitive(query)
        if (result != null) {
            val (lat, lon, matchedName) = result
            lastQueriedAddress = matchedName
            val coords = "Lat %.4f, Lon %.4f".format(lat, lon)
            showHint("Coordinates: $coords")
            moveCamera(lat, lon, matchedName, coords)
        } else {
            showHint("No record found for $query")
        }
    }

    private fun showHint(text: String) {
        tvCoordinates.text = text
        tvCoordinates.visibility = View.VISIBLE
    }

    private fun toggleForm(action: String) {
        currentAction = action
        formLayout.visibility =
            if (formLayout.visibility == View.VISIBLE && currentAction == action) View.GONE
            else View.VISIBLE

        if (action == "update")
            showHint("Enter a location in the search bar first, then edit fields below")
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val gta = LatLng(43.7, -79.4)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(gta, 8.5f))
        showAllMarkers()

        // ✅ marker click no longer triggers redundant updates
        map.setOnMarkerClickListener { marker ->
            if (ignoreNextMarkerClick) {
                ignoreNextMarkerClick = false
                return@setOnMarkerClickListener true
            }
            selectedMarker = marker
            marker.showInfoWindow()
            true
        }
    }

    private fun moveCamera(lat: Double, lon: Double, name: String, coords: String) {
        val loc = LatLng(lat, lon)
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

    private fun showAllMarkers() {
        map.clear()
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
        val center = LatLng(43.7, -79.4)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 8.5f))
    }
}
