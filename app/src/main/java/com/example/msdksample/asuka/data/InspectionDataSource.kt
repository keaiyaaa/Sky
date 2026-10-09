package com.example.msdksample.asuka.data

interface InspectionDataSource {
    fun getAllDateGroups(): List<InspectionDateGroup>
    fun getDateGroup(date: String): InspectionDateGroup?
    fun getLatestDateGroup(): InspectionDateGroup?
    fun getAllDates(): List<String>
    fun sortDateGroups(filterType: String): List<InspectionDateGroup>
    fun getTask(date: String, taskIndex: Int): InspectionTask?
}
