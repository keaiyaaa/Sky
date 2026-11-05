package com.example.msdksample.data.remote

data class CloudFlightLog(val id: String,val clientId: String,val category: String,val action: String,
    val result: String,val source: String,val detail: String,val taskId: String,val occurredAt: String,
    val version: Long,val origin: String)
data class FlightLogPage(val items: List<CloudFlightLog>,val page: Int,val size: Int,val total: Long,val missionEventsAvailable: Boolean)
data class FlightLogCreate(val clientId: String,val category: String,val action: String,val result: String,
    val source: String,val detail: String,val taskId: String,val occurredAt: String,val expectedVersion: Long=0,val idempotencyKey: String)
data class FlightLogFilter(val keyword: String="",val source: String?=null,val category: String?=null,
    val from: String?=null,val to: String?=null)
