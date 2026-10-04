package tech.kloos.kompound.i18n

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.intl.Locale

/**
 * Every text Kompound shows or announces on its own (button labels, content descriptions, empty states, sort announcements). Components
 * take their defaults from here, so one object translates the whole library; a parameter you pass to a component still wins.
 *
 * [KompoundTheme][tech.kloos.kompound.theme.KompoundTheme] picks the bundle for the device language ([forLocale]: English, German, French,
 * Spanish and Italian are built in) unless you pass `strings`. Make your own with `KompoundStrings.English.copy(close = "Dismiss")` or
 * fill every field for another language.
 *
 * The strings are code, not resources, so they work the same on every target (also in the browser, where resources load asynchronously)
 * and can be swapped at runtime.
 */
@Immutable
public data class KompoundStrings(
    val ok: String = "OK",
    val cancel: String = "Cancel",
    val close: String = "Close",
    val save: String = "Save",
    val edit: String = "Edit",
    val add: String = "Add",
    val remove: String = "Remove",
    val search: String = "Search",
    val clearSearch: String = "Clear search",
    val showPassword: String = "Show password",
    val hidePassword: String = "Hide password",
    val tryAgain: String = "Try again",
    val stepWaiting: String = "Waiting",
    val stepInProgress: String = "In progress",
    val stepDone: String = "Done",
    val statusOnline: String = "online",
    val statusAway: String = "away",
    val statusBusy: String = "busy",
    val statusOffline: String = "offline",
    val expand: String = "Expand",
    val collapse: String = "Collapse",
    val expanded: String = "Expanded",
    val collapsed: String = "Collapsed",
    val loading: String = "Loading",
    val noResults: String = "No results",
    val noData: String = "No data",
    val previous: String = "Previous",
    val next: String = "Next",
    val today: String = "Today",
    val openNavigation: String = "Open navigation menu",
    val closeNavigation: String = "Close navigation menu",
    val resizePanes: String = "Resize panes",
    val sortAscending: String = "Sort ascending",
    val sortDescending: String = "Sort descending",
    val sortedAscending: String = "Sorted ascending",
    val sortedDescending: String = "Sorted descending",
    val selectAll: String = "Select all",
    val selectRow: String = "Select row",
    val searchCommands: String = "Search commands",
    val noCommands: String = "No matching commands",
    val select: String = "Select",
    val hour: String = "Hour",
    val minute: String = "Minute",
    val am: String = "AM",
    val pm: String = "PM",
    val hue: String = "Hue",
    val saturation: String = "Saturation",
    val brightness: String = "Brightness",
    val opacity: String = "Opacity",
    val hexColor: String = "Hex color",
    val required: String = "Required",
    val submit: String = "Submit",
    val submitting: String = "Submitting",
    val invalidValue: String = "Invalid value",
    val addTag: String = "Add a tag",
    /** Announcement of the button that removes the tag or item called [name]. */
    val removeNamed: (name: String) -> String = { "Remove $it" },
    /** Announcement of a sparkline or chart: `"Chart, 12 values from 3 to 9"`. */
    val chartSummary: (count: Int, min: String, max: String) -> String = { count, min, max -> "Chart, $count values from $min to $max" },
) {
    /** Every plain-text field by name, for tests that check a bundle is complete. */
    internal fun texts(): Map<String, String> = linkedMapOf(
        "ok" to ok, "cancel" to cancel, "close" to close, "save" to save, "edit" to edit, "add" to add, "remove" to remove, "search" to search,
        "clearSearch" to clearSearch, "showPassword" to showPassword, "hidePassword" to hidePassword, "tryAgain" to tryAgain,
        "stepWaiting" to stepWaiting, "stepInProgress" to stepInProgress, "stepDone" to stepDone,
        "statusOnline" to statusOnline, "statusAway" to statusAway, "statusBusy" to statusBusy, "statusOffline" to statusOffline,
        "expand" to expand, "collapse" to collapse, "expanded" to expanded, "collapsed" to collapsed, "loading" to loading, "noResults" to noResults, "noData" to noData,
        "previous" to previous, "next" to next, "today" to today, "openNavigation" to openNavigation, "closeNavigation" to closeNavigation,
        "resizePanes" to resizePanes, "sortAscending" to sortAscending, "sortDescending" to sortDescending,
        "sortedAscending" to sortedAscending, "sortedDescending" to sortedDescending, "selectAll" to selectAll, "selectRow" to selectRow,
        "searchCommands" to searchCommands, "noCommands" to noCommands, "select" to select, "hour" to hour, "minute" to minute, "am" to am, "pm" to pm,
        "hue" to hue, "saturation" to saturation, "brightness" to brightness, "opacity" to opacity, "hexColor" to hexColor,
        "required" to required, "submit" to submit, "submitting" to submitting, "invalidValue" to invalidValue, "addTag" to addTag,
        "removeNamed" to removeNamed("X"), "chartSummary" to chartSummary(3, "1", "2"),
    )

    public companion object {
        /** The built-in English texts (the defaults). */
        public val English: KompoundStrings = KompoundStrings()

        /** German. */
        public val German: KompoundStrings = KompoundStrings(
            ok = "OK", cancel = "Abbrechen", close = "Schließen", save = "Speichern", edit = "Bearbeiten", add = "Hinzufügen", remove = "Entfernen",
            search = "Suchen", clearSearch = "Suche löschen", showPassword = "Passwort anzeigen", hidePassword = "Passwort verbergen", tryAgain = "Erneut versuchen",
            stepWaiting = "Wartet", stepInProgress = "In Arbeit", stepDone = "Fertig",
            statusOnline = "online", statusAway = "abwesend", statusBusy = "beschäftigt", statusOffline = "offline",
            expand = "Aufklappen", collapse = "Zuklappen", expanded = "Aufgeklappt", collapsed = "Zugeklappt", loading = "Wird geladen", noResults = "Keine Ergebnisse", noData = "Keine Daten",
            previous = "Zurück", next = "Weiter", today = "Heute", openNavigation = "Navigationsmenü öffnen", closeNavigation = "Navigationsmenü schließen",
            resizePanes = "Bereiche in der Größe ändern", sortAscending = "Aufsteigend sortieren", sortDescending = "Absteigend sortieren",
            sortedAscending = "Aufsteigend sortiert", sortedDescending = "Absteigend sortiert", selectAll = "Alle auswählen", selectRow = "Zeile auswählen",
            searchCommands = "Befehle suchen", noCommands = "Keine passenden Befehle", select = "Auswählen", hour = "Stunde", minute = "Minute", am = "AM", pm = "PM",
            hue = "Farbton", saturation = "Sättigung", brightness = "Helligkeit", opacity = "Deckkraft", hexColor = "Hex-Farbe",
            required = "Pflichtfeld", submit = "Absenden", submitting = "Wird gesendet", invalidValue = "Ungültiger Wert", addTag = "Tag hinzufügen",
            removeNamed = { "$it entfernen" },
            chartSummary = { count, min, max -> "Diagramm, $count Werte von $min bis $max" },
        )

        /** French. */
        public val French: KompoundStrings = KompoundStrings(
            ok = "OK", cancel = "Annuler", close = "Fermer", save = "Enregistrer", edit = "Modifier", add = "Ajouter", remove = "Supprimer",
            search = "Rechercher", clearSearch = "Effacer la recherche", showPassword = "Afficher le mot de passe", hidePassword = "Masquer le mot de passe", tryAgain = "Réessayer",
            stepWaiting = "En attente", stepInProgress = "En cours", stepDone = "Terminé",
            statusOnline = "en ligne", statusAway = "absent", statusBusy = "occupé", statusOffline = "hors ligne",
            expand = "Développer", collapse = "Réduire", expanded = "Développé", collapsed = "Réduit", loading = "Chargement", noResults = "Aucun résultat", noData = "Aucune donnée",
            previous = "Précédent", next = "Suivant", today = "Aujourd'hui", openNavigation = "Ouvrir le menu de navigation", closeNavigation = "Fermer le menu de navigation",
            resizePanes = "Redimensionner les volets", sortAscending = "Trier par ordre croissant", sortDescending = "Trier par ordre décroissant",
            sortedAscending = "Trié par ordre croissant", sortedDescending = "Trié par ordre décroissant", selectAll = "Tout sélectionner", selectRow = "Sélectionner la ligne",
            searchCommands = "Rechercher une commande", noCommands = "Aucune commande correspondante", select = "Sélectionner", hour = "Heure", minute = "Minute", am = "AM", pm = "PM",
            hue = "Teinte", saturation = "Saturation", brightness = "Luminosité", opacity = "Opacité", hexColor = "Couleur hexadécimale",
            required = "Obligatoire", submit = "Envoyer", submitting = "Envoi en cours", invalidValue = "Valeur invalide", addTag = "Ajouter une étiquette",
            removeNamed = { "Supprimer $it" },
            chartSummary = { count, min, max -> "Graphique, $count valeurs de $min à $max" },
        )

        /** Spanish. */
        public val Spanish: KompoundStrings = KompoundStrings(
            ok = "Aceptar", cancel = "Cancelar", close = "Cerrar", save = "Guardar", edit = "Editar", add = "Añadir", remove = "Quitar",
            search = "Buscar", clearSearch = "Borrar búsqueda", showPassword = "Mostrar contraseña", hidePassword = "Ocultar contraseña", tryAgain = "Reintentar",
            stepWaiting = "En espera", stepInProgress = "En curso", stepDone = "Hecho",
            statusOnline = "en línea", statusAway = "ausente", statusBusy = "ocupado", statusOffline = "desconectado",
            expand = "Expandir", collapse = "Contraer", expanded = "Expandido", collapsed = "Contraído", loading = "Cargando", noResults = "Sin resultados", noData = "Sin datos",
            previous = "Anterior", next = "Siguiente", today = "Hoy", openNavigation = "Abrir menú de navegación", closeNavigation = "Cerrar menú de navegación",
            resizePanes = "Cambiar el tamaño de los paneles", sortAscending = "Ordenar de forma ascendente", sortDescending = "Ordenar de forma descendente",
            sortedAscending = "Ordenado de forma ascendente", sortedDescending = "Ordenado de forma descendente", selectAll = "Seleccionar todo", selectRow = "Seleccionar fila",
            searchCommands = "Buscar comandos", noCommands = "No hay comandos coincidentes", select = "Seleccionar", hour = "Hora", minute = "Minuto", am = "a. m.", pm = "p. m.",
            hue = "Tono", saturation = "Saturación", brightness = "Brillo", opacity = "Opacidad", hexColor = "Color hexadecimal",
            required = "Obligatorio", submit = "Enviar", submitting = "Enviando", invalidValue = "Valor no válido", addTag = "Añadir etiqueta",
            removeNamed = { "Quitar $it" },
            chartSummary = { count, min, max -> "Gráfico, $count valores de $min a $max" },
        )

        /** Italian. */
        public val Italian: KompoundStrings = KompoundStrings(
            ok = "OK", cancel = "Annulla", close = "Chiudi", save = "Salva", edit = "Modifica", add = "Aggiungi", remove = "Rimuovi",
            search = "Cerca", clearSearch = "Cancella ricerca", showPassword = "Mostra password", hidePassword = "Nascondi password", tryAgain = "Riprova",
            stepWaiting = "In attesa", stepInProgress = "In corso", stepDone = "Fatto",
            statusOnline = "online", statusAway = "assente", statusBusy = "occupato", statusOffline = "offline",
            expand = "Espandi", collapse = "Comprimi", expanded = "Espanso", collapsed = "Compresso", loading = "Caricamento", noResults = "Nessun risultato", noData = "Nessun dato",
            previous = "Precedente", next = "Successivo", today = "Oggi", openNavigation = "Apri il menu di navigazione", closeNavigation = "Chiudi il menu di navigazione",
            resizePanes = "Ridimensiona i riquadri", sortAscending = "Ordina in modo crescente", sortDescending = "Ordina in modo decrescente",
            sortedAscending = "Ordinato in modo crescente", sortedDescending = "Ordinato in modo decrescente", selectAll = "Seleziona tutto", selectRow = "Seleziona riga",
            searchCommands = "Cerca comandi", noCommands = "Nessun comando corrispondente", select = "Seleziona", hour = "Ora", minute = "Minuto", am = "AM", pm = "PM",
            hue = "Tonalità", saturation = "Saturazione", brightness = "Luminosità", opacity = "Opacità", hexColor = "Colore esadecimale",
            required = "Obbligatorio", submit = "Invia", submitting = "Invio in corso", invalidValue = "Valore non valido", addTag = "Aggiungi tag",
            removeNamed = { "Rimuovi $it" },
            chartSummary = { count, min, max -> "Grafico, $count valori da $min a $max" },
        )

        /** The bundle for a BCP 47 [languageTag] (`"de"`, `"de-AT"`, `"fr_CA"`); [English] for languages without one. */
        public fun forLanguageTag(languageTag: String): KompoundStrings = when (languageTag.substringBefore('-').substringBefore('_').lowercase()) {
            "de" -> German
            "fr" -> French
            "es" -> Spanish
            "it" -> Italian
            else -> English
        }

        /** The bundle for [locale] (see [forLanguageTag]). */
        public fun forLocale(locale: Locale): KompoundStrings = forLanguageTag(locale.language)
    }
}

internal val LocalKompoundStrings = compositionLocalOf { KompoundStrings.English }
