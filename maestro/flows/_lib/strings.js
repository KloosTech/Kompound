// Kompound's own labels per language: the harness forces the language with the lang= parameter, so flows look for the words of that language.
// Use: - runScript: { file: ../../../_lib/strings.js, env: { LANG: ${LANG} } }   then  ${output.s.showPassword}
// Keep in sync with tech.kloos.kompound.i18n.KompoundStrings (add the words a flow needs, in all five languages).
var S = {
  en: { showPassword: 'Show password', hidePassword: 'Hide password', clearSearch: 'Clear search', close: 'Close', cancel: 'Cancel', ok: 'OK' },
  de: { showPassword: 'Passwort anzeigen', hidePassword: 'Passwort verbergen', clearSearch: 'Suche löschen', close: 'Schließen', cancel: 'Abbrechen', ok: 'OK' },
  fr: { showPassword: 'Afficher le mot de passe', hidePassword: 'Masquer le mot de passe', clearSearch: 'Effacer la recherche', close: 'Fermer', cancel: 'Annuler', ok: 'OK' },
  es: { showPassword: 'Mostrar contraseña', hidePassword: 'Ocultar contraseña', clearSearch: 'Borrar búsqueda', close: 'Cerrar', cancel: 'Cancelar', ok: 'Aceptar' },
  it: { showPassword: 'Mostra password', hidePassword: 'Nascondi password', clearSearch: 'Cancella ricerca', close: 'Chiudi', cancel: 'Annulla', ok: 'OK' }
};
var lang = (typeof LANG !== 'undefined' && LANG) ? LANG : 'en';
output.s = S[lang] || S.en;
