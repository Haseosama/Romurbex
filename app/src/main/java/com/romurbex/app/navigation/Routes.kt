package com.romurbex.app.navigation

import java.net.URLEncoder

sealed class Routes(val route: String) {
    data object Map : Routes("map")
    data object List : Routes("list")
    data object Import : Routes("import")
    data object Lists : Routes("lists")
    data object Settings : Routes("settings")
    data object Discover : Routes("discover")

    /** name/description/lat/lng/list en query params optionnels : préremplit le formulaire quand
     *  on arrive depuis Découvrir (nom+description), un import d'épingle Pinterest (+ coordonnées),
     *  ou en rattachant le lieu à une liste existante (+ nom de liste), vide sinon (comportement
     *  inchangé du bouton "Ajouter"). */
    data object Add : Routes("edit?name={name}&description={description}&lat={lat}&lng={lng}&list={list}") {
        fun of(name: String = "", description: String = "", lat: Double? = null, lng: Double? = null, list: String = ""): String {
            val n = URLEncoder.encode(name, "UTF-8")
            val d = URLEncoder.encode(description, "UTF-8")
            val l = URLEncoder.encode(list, "UTF-8")
            return "edit?name=$n&description=$d&lat=${lat ?: ""}&lng=${lng ?: ""}&list=$l"
        }
    }
    data object Detail : Routes("detail/{locationId}") {
        fun of(id: Long) = "detail/$id"
    }
    data object Edit : Routes("edit/{locationId}") {
        fun of(id: Long) = "edit/$id"
    }
}
