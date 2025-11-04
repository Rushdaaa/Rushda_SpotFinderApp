# 🌍 SpotFinder App

An Android application developed for **SOFE 4640U – Mobile Application Development (Fall 2025)**.
The app allows users to **query, add, update, and delete** locations across the **Greater Toronto Area (GTA)** using a local **SQLite database** integrated with the **Google Maps API**.

---

## 📱 Overview

SpotFinder demonstrates the connection between **databases** and **maps** in Android development.
Users can search for any GTA location stored in the database to instantly view its coordinates on a map.
CRUD operations dynamically update both the local database and map markers in real time.

---

## 🧩 Features

* **SQLite Database Integration** – Stores 100 preloaded GTA locations (name, latitude, longitude).
* **Query/Search Bar** – Enter a location to view its coordinates and marker on the map.
* **Add & Update Operations** – Add new places or update existing ones (supports partial field updates).
* **Delete Operation** – Remove a location with a confirmation dialog to prevent mistakes.
* **Material Design UI** – Clean, responsive interface with toggle buttons and inline hints.
* **Google Maps Integration** – Displays all saved markers and dynamically refreshes them after every change.

---

## 🏗️ Main Files

| File                    | Purpose                                                                 |
| ----------------------- | ----------------------------------------------------------------------- |
| **MainActivity.kt**     | Handles all CRUD actions, search logic, and Google Map updates.         |
| **LocationDBHelper.kt** | Manages SQLite database creation, preloading, and helper queries.       |
| **activity_main.xml**   | Defines UI components (search bar, toggle buttons, form, map fragment). |

---

## 🗺️ How It Works

1. **Search a location** → its coordinates appear below the search bar and a map marker appears.
2. **Add a new location** → enter name, latitude, and longitude; marker appears immediately.
3. **Update a location** → edit any of the three fields (name or coordinates) to modify an entry.
4. **Delete a location** → enter its name, confirm, and it disappears from both DB and map.
5. **Show All Spots** → reloads and displays all 100 GTA locations at once.

---

## 📸 Screenshots

| Function | Screenshot |
|-----------|-------------|
| **Query/Search** | <img width="500" alt="Query Screenshot" src="https://github.com/user-attachments/assets/58ef5646-6a18-4f64-9341-c08678378b15" /> |
| **Add Location** | <img width="500" alt="Add Screenshot" src="https://github.com/user-attachments/assets/17cb9696-8149-47d7-8422-9c86e876fc4c" /> |
| **Update Location** | <img width="500" alt="Update Screenshot" src="https://github.com/user-attachments/assets/b70c806a-c490-4e05-8feb-15b6d2fb68a5" /> |
| **Delete Location** | <img width="500" alt="Delete Screenshot" src="https://github.com/user-attachments/assets/f0236110-1b2e-4de3-b47a-48beb7ac5943" /> |
| **Show All Spots** | <img width="500" alt="Show All Spots Screenshot" src="https://github.com/user-attachments/assets/888aad06-cf39-4431-ba9a-aeb47b6dbe66" /> |




## 💾 Database Details

* **Database name:** `locations.db`
* **Table name:** `locations`
* **Columns:** `id (INT)`, `address (TEXT)`, `latitude (REAL)`, `longitude (REAL)`
* Preloaded ~100 GTA entries + test entries (`UpdateMeCity`, `DeleteMeSpot`).

---

## 🧠 Technologies Used

* **Language:** Kotlin
* **Database:** SQLite (via SQLiteOpenHelper)
* **Map API:** Google Maps SDK for Android
* **UI Framework:** Material Design Components

---

## 👩‍💻 Author

**Rushda Khan**
Ontario Tech University | SOFE 4640U Mobile App Development
GitHub Repository → https://github.com/Rushdaaa/Rushda_SpotFinderApp 
