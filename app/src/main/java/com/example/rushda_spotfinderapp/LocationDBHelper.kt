package com.example.rushda_spotfinderapp

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.widget.Toast

/**
 * A SQLiteOpenHelper subclass for managing a local database of locations.
 * This class handles database creation, version management, and provides
 * CRUD (Create, Read, Update, Delete) operations for location data.
 *
 * The database schema consists of a single table named "locations" with columns
 * for an auto-incrementing ID, a unique address name (TEXT), latitude (REAL),
 * and longitude (REAL).
 *
 * Upon creation, the database is pre-populated with a list of locations in the
 * Greater Toronto Area (GTA) for initial use.
 *
 * @param context The context used to open or create the database.
 */
class LocationDBHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // Store context for showing Toast messages
    private val ctx = context

    companion object {
        private const val DATABASE_NAME = "locations.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "locations"
        private const val COL_ID = "id"
        private const val COL_ADDRESS = "address"
        private const val COL_LAT = "latitude"
        private const val COL_LON = "longitude"
    }

    /**
     * Called when the database is created for the first time. This is where the
     * creation of tables and the initial population of the tables should happen.
     * @param db The database.
     */
    override fun onCreate(db: SQLiteDatabase) {
        // SQL statement to create the locations table with a unique constraint on the address
        val createTable = """CREATE TABLE $TABLE_NAME (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_ADDRESS TEXT UNIQUE,
            $COL_LAT REAL,
            $COL_LON REAL
        )"""
        // Execute the SQL statement
        db.execSQL(createTable)
        // Populate the newly created table with initial data
        preloadLocations(db)
    }

    /**
     * Called when the database needs to be upgraded. This method will drop the
     * existing table and re-create it.
     * @param db The database.
     * @param oldVersion The old database version.
     * @param newVersion The new database version.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Simple upgrade strategy: drop the old table and create a new one
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /** ====================== CRUD OPERATIONS ====================== **/

    /**
     * Adds a new location to the database.
     * @param address The name of the location.
     * @param lat The latitude of the location.
     * @param lon The longitude of the location.
     */
    fun addLocation(address: String, lat: Double, lon: Double) {
        // Get a writable database instance
        val db = writableDatabase
        // ContentValues is used to store a set of key-value pairs for insertion
        val values = ContentValues().apply {
            put(COL_ADDRESS, address)
            put(COL_LAT, lat)
            put(COL_LON, lon)
        }

        // Insert the new row, returning the row ID or -1 if an error occurred (e.g., duplicate address)
        val result = db.insert(TABLE_NAME, null, values)
        if (result == -1L)
            Toast.makeText(ctx, "Failed to add (already exists?)", Toast.LENGTH_SHORT).show()
        else
            Toast.makeText(ctx, "Added successfully", Toast.LENGTH_SHORT).show()
    }

    /**
     * Partially updates an existing location. Finds the location by its original name
     * (case-insensitive) and updates any non-null fields provided.
     *
     * @param original The original address name to find the record to update.
     * @param newName The new name for the location, or null to keep the original name.
     * @param lat The new latitude, or null to keep the original latitude.
     * @param lon The new longitude, or null to keep the original longitude.
     */
    fun updatePartial(original: String, newName: String?, lat: Double?, lon: Double?) {
        val db = writableDatabase
        // First, query for the existing record to get its current values. This also checks if it exists.
        val current = queryLocationCaseInsensitive(original) ?: run {
            // If query returns null, the location doesn't exist. Show a message and exit the function.
            Toast.makeText(ctx, "Not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Destructure the Triple returned from the query into individual variables
        val (currLat, currLon, currName) = current
        // Determine the final values: use the new value if provided, otherwise keep the current value
        val finalName = if (!newName.isNullOrBlank()) newName else currName
        val finalLat = lat ?: currLat
        val finalLon = lon ?: currLon

        // Prepare the new values for the database update
        val values = ContentValues().apply {
            put(COL_ADDRESS, finalName)
            put(COL_LAT, finalLat)
            put(COL_LON, finalLon)
        }

        // Perform the update using a case-insensitive WHERE clause to find the original record
        db.update(TABLE_NAME, values, "LOWER($COL_ADDRESS)=LOWER(?)", arrayOf(original))
        db.close() // It's good practice to close the database when done
    }

    /**
     * Deletes a location from the database using its address. The comparison is
     * case-sensitive.
     * @param address The address of the location to delete.
     */
    fun deleteLocation(address: String) {
        val db = writableDatabase
        // The delete method returns the number of rows affected.
        val result = db.delete(TABLE_NAME, "$COL_ADDRESS=?", arrayOf(address))
        if (result > 0)
            Toast.makeText(ctx, "Deleted successfully", Toast.LENGTH_SHORT).show()
        else
            Toast.makeText(ctx, "Address not found", Toast.LENGTH_SHORT).show()
    }

    /**
     * Queries for a location's full details by its address (case-insensitive).
     *
     * @param address The address to search for, ignoring case.
     * @return A `Triple` of (latitude, longitude, matchedAddressName) if found,
     *         otherwise `null`.
     */
    fun queryLocationCaseInsensitive(address: String): Triple<Double, Double, String>? {
        val db = readableDatabase
        var result: Triple<Double, Double, String>? = null
        // Use rawQuery for a custom SQL statement with a case-insensitive WHERE clause
        val cursor = db.rawQuery(
            // LOWER() function makes the comparison case-insensitive
            "SELECT $COL_ADDRESS, $COL_LAT, $COL_LON FROM $TABLE_NAME WHERE LOWER($COL_ADDRESS) = LOWER(?)",
            arrayOf(address)
        )
        // Move cursor to the first result. If it returns true, a record was found.
        if (cursor.moveToFirst()) {
            // Retrieve data from the cursor by column index
            val matchedName = cursor.getString(0)
            val lat = cursor.getDouble(1)
            val lon = cursor.getDouble(2)
            // Package the data into a Triple to be returned
            result = Triple(lat, lon, matchedName)
        }
        cursor.close() // Always close the cursor to release its resources
        return result
    }

    /**
     * Retrieves all locations from the database.
     *
     * @return A `List` of `Triple` objects, each containing (address, latitude, longitude).
     */
    fun getAllLocations(): List<Triple<String, Double, Double>> {
        val db = readableDatabase
        val list = mutableListOf<Triple<String, Double, Double>>()
        // Simple query to select all columns from all rows
        val cursor = db.rawQuery("SELECT address, latitude, longitude FROM locations", null)
        // Loop through all the rows in the cursor
        while (cursor.moveToNext()) {
            // Extract data for the current row
            val address = cursor.getString(0)
            val lat = cursor.getDouble(1)
            val lon = cursor.getDouble(2)
            list.add(Triple(address, lat, lon))
        }
        cursor.close() // Clean up the cursor
        return list
    }

    /** ====================== PRELOAD 100 GTA LOCATIONS ====================== **/

    /**
     * Preloads the database with a list of locations in the Greater Toronto Area (GTA)
     * and a few test entries. This operation only runs if the database is currently empty.
     * @param db The SQLiteDatabase instance to insert data into.
     */
    private fun preloadLocations(db: SQLiteDatabase) {
        // Check if the table already has data before preloading
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        // If count is greater than 0, the table is not empty, so we skip preloading.
        if (count > 0) {
            return // Exit the function
        }

        val locations = listOf(
            "Toronto" to Pair(43.6510, -79.3470),
            "Scarborough" to Pair(43.7731, -79.2578),
            "Oshawa" to Pair(43.8971, -78.8658),
            "Whitby" to Pair(43.8975, -78.9429),
            "Ajax" to Pair(43.8509, -79.0204),
            "Pickering" to Pair(43.8384, -79.0868),
            "Mississauga" to Pair(43.5890, -79.6441),
            "Brampton" to Pair(43.7315, -79.7624),
            "Vaughan" to Pair(43.8363, -79.4985),
            "Markham" to Pair(43.8561, -79.3370),
            "Richmond Hill" to Pair(43.8828, -79.4403),
            "North York" to Pair(43.7615, -79.4111),
            "Etobicoke" to Pair(43.6435, -79.5650),
            "York" to Pair(43.6896, -79.4875),
            "Thornhill" to Pair(43.8130, -79.4205),
            "Maple" to Pair(43.8534, -79.5071),
            "Woodbridge" to Pair(43.7875, -79.6077),
            "Concord" to Pair(43.8012, -79.4982),
            "King City" to Pair(43.9248, -79.5287),
            "Aurora" to Pair(44.0065, -79.4504),
            "Newmarket" to Pair(44.0592, -79.4613),
            "Bradford" to Pair(44.1146, -79.5590),
            "Stouffville" to Pair(43.9707, -79.2442),
            "Uxbridge" to Pair(44.1092, -79.1205),
            "Brooklin" to Pair(43.9635, -78.9576),
            "Port Perry" to Pair(44.1001, -78.9442),
            "Clarington" to Pair(43.9356, -78.6074),
            "Bowmanville" to Pair(43.9126, -78.6870),
            "Courtice" to Pair(43.9112, -78.7975),
            "Newcastle" to Pair(43.9237, -78.5944),
            "Georgina" to Pair(44.2964, -79.4274),
            "Keswick" to Pair(44.2230, -79.4596),
            "Sutton" to Pair(44.3045, -79.3633),
            "Pefferlaw" to Pair(44.3153, -79.2029),
            "Mount Albert" to Pair(44.1363, -79.3148),
            "Ballantrae" to Pair(43.9768, -79.3184),
            "Unionville" to Pair(43.8622, -79.3104),
            "Cornell" to Pair(43.8665, -79.2277),
            "Box Grove" to Pair(43.8537, -79.2191),
            "Milliken" to Pair(43.8253, -79.3009),
            "Buttonville" to Pair(43.8592, -79.3720),
            "Cathedraltown" to Pair(43.8693, -79.3676),
            "Bayview Glen" to Pair(43.8333, -79.3765),
            "Cachet" to Pair(43.8772, -79.3496),
            "Victoria Square" to Pair(43.8903, -79.3677),
            "Berczy Village" to Pair(43.8961, -79.3064),
            "Greensborough" to Pair(43.9112, -79.2566),
            "Rouge Park" to Pair(43.8103, -79.1329),
            "Guildwood" to Pair(43.7553, -79.1968),
            "West Hill" to Pair(43.7678, -79.1771),
            "Port Union" to Pair(43.7852, -79.1320),
            "Highland Creek" to Pair(43.7873, -79.1845),
            "Morningside" to Pair(43.7993, -79.2156),
            "Woburn" to Pair(43.7701, -79.2318),
            "Malvern" to Pair(43.8067, -79.2297),
            "Agincourt" to Pair(43.7879, -79.2676),
            "Milliken Mills" to Pair(43.8315, -79.3168),
            "Middlefield" to Pair(43.8361, -79.2701),
            "Cedarwood" to Pair(43.8383, -79.2586),
            "Armour Heights" to Pair(43.7371, -79.4267),
            "Bathurst Manor" to Pair(43.7544, -79.4564),
            "Bayview Village" to Pair(43.7697, -79.3750),
            "Clanton Park" to Pair(43.7490, -79.4395),
            "Don Valley Village" to Pair(43.7807, -79.3494),
            "Downsview" to Pair(43.7436, -79.4905),
            "Glen Park" to Pair(43.7066, -79.4537),
            "Humber Summit" to Pair(43.7667, -79.5622),
            "Jane and Finch" to Pair(43.7610, -79.4961),
            "Kingsview Village" to Pair(43.7030, -79.5546),
            "Rexdale" to Pair(43.7277, -79.5563),
            "Smithfield" to Pair(43.7481, -79.5937),
            "The Elms" to Pair(43.7168, -79.5351),
            "West Humber" to Pair(43.7261, -79.5924),
            "Woodbine Gardens" to Pair(43.7045, -79.3095),
            "The Beaches" to Pair(43.6764, -79.2933),
            "Riverdale" to Pair(43.6667, -79.3477),
            "East York" to Pair(43.7045, -79.3275),
            "Leaside" to Pair(43.7095, -79.3631),
            "Rosedale" to Pair(43.6780, -79.3802),
            "Yorkville" to Pair(43.6708, -79.3948),
            "Annex" to Pair(43.6697, -79.4075),
            "Forest Hill" to Pair(43.6972, -79.4145),
            "Deer Park" to Pair(43.6900, -79.3960),
            "Casa Loma" to Pair(43.6785, -79.4095),
            "Summerhill" to Pair(43.6826, -79.3912),
            "Kensington Market" to Pair(43.6548, -79.4023),
            "Little Italy" to Pair(43.6541, -79.4195),
            "Chinatown" to Pair(43.6528, -79.3984),
            "Harbourfront" to Pair(43.6387, -79.3825),
            "Liberty Village" to Pair(43.6396, -79.4225),
            "Parkdale" to Pair(43.6415, -79.4308),
            "Swansea" to Pair(43.6505, -79.4755),
            "Nobleton" to Pair(43.9337, -79.6528),
            "Pelmo Park" to Pair(43.7050, -79.5156),
            "O'Connor-Parkview" to Pair(43.7051, -79.3151),
            "Davisville" to Pair(43.7047, -79.3834),
            "Chaplin Estates" to Pair(43.7024, -79.4095),
            "Moore Park" to Pair(43.6908, -79.3772),
            "Caledon" to Pair(43.86, -79.86),
            "Halton Hills" to Pair(43.64, -79.94)
        )

        // Shuffle the list to insert in a random order, then iterate and insert each location
        locations.shuffled().forEach { (name, coords) ->
            val values = ContentValues().apply {
                put(COL_ADDRESS, name)
                put(COL_LAT, coords.first)
                put(COL_LON, coords.second)
            }
            db.insert(TABLE_NAME, null, values)
        }

        // Add a specific set of test data for predictable testing of CRUD operations
        val testSet = listOf(
            "TestTown1" to Pair(43.999, -79.111),
            "DeleteMeSpot" to Pair(43.666, -79.444),
            "UpdateMeCity" to Pair(43.555, -79.555)
        )
        testSet.forEach { (name, coords) ->
            val v = ContentValues().apply {
                put(COL_ADDRESS, name)
                put(COL_LAT, coords.first)
                put(COL_LON, coords.second)
            }
            db.insert(TABLE_NAME, null, v)
        }

        Toast.makeText(ctx, "Preloaded 100 GTA + test locations", Toast.LENGTH_SHORT).show()
    }
}
