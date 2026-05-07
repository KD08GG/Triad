package com.example.triad

data class Task(
    val id: String = "",
    val title: String = "",
    val category: String = "",
    val points: Int = 0,
    val icono: String = "",
    val completed: Boolean = false,
    val recurrent: Boolean = false,
    val createdDate: String = "",
    val penalizacion: Int = 0
)
