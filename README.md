# Romurbex — carnet de lieux abandonnés (Android)

Application Android personnelle pour répertorier des lieux abandonnés (urbex) : carte
interactive, fiche par lieu avec photos, import depuis Google Maps ou Pinterest, recherche
intelligente locale, et une aide à la découverte de nouveaux lieux via l'IA (Gemini).

Conçue pour un usage strictement privé (pas de compte, pas de backend, tout reste en local
sur le téléphone) — les coordonnées GPS enregistrées sont donc précises, sans floutage.

## Fonctionnalités

- **Carte interactive** (osmdroid / tuiles OpenStreetMap) : vue satellite avec noms de villes,
  regroupement des pins proches, bouton « ma position », cadrage automatique sur les résultats
  d'une recherche, téléchargement de tuiles hors-ligne, pins agrandis pour les lieux proches de
  toi, mémorisation du dernier mode de vue utilisé. L'app s'ouvre toujours centrée sur ta
  position actuelle.
- **14 catégories de lieux** (château, usine, hôpital, maison, moulin, église, école, militaire,
  gare, théâtre, piscine, carrière, ferme, loisir, autre), chacune avec sa propre icône et sa
  propre couleur de pin — change l'icône d'un lieu directement en tapant sur son pin.
- **Import** : lien Google Maps (épingle unique ou liste partagée entière), fichier KML/GeoJSON
  (Google My Maps / Takeout), CSV, ou description d'épingle Pinterest collée à la main (extrait
  les coordonnées GPS au format degrés/minutes/secondes si elles y figurent).
- **Écran Découvrir** : recherche IA (Gemini, avec recherche web) de nouveaux lieux abandonnés
  près de toi ou par mot-clé — te propose des candidats à vérifier toi-même, jamais ajoutés
  automatiquement. Quotas Gemini gérés avec rotation entre 3 clés API.
- **Recherche vocale** : dis ta recherche, l'IA peut résumer les résultats à voix haute.
- **Recherche locale par mots-clés** (nom, description, notes, dossier d'origine) — tolérante
  aux fautes de frappe et aux accents, 100 % hors-ligne, sans modèle embarqué.
- **Photos** : attache des photos à un lieu via le sélecteur système (SAF, référence seulement —
  aucun fichier copié), avec visionneuse plein écran zoomable/swipeable.
- **Listes** : les lieux importés gardent leur liste d'origine, que tu peux afficher, masquer ou
  supprimer indépendamment.

## 1. Prérequis

- **Android Studio** (Ladybug ou plus récent).
- Aucun NDK/CMake requis : Romurbex est 100 % Kotlin/Compose.
- Optionnel : une ou plusieurs clés [API Gemini](https://aistudio.google.com/) pour la
  recherche vocale et l'écran Découvrir (Réglages > clés API). L'app fonctionne entièrement
  sans, ces deux fonctionnalités sont simplement désactivées.

## 2. Ouvrir le projet

`File > Open` puis sélectionne ce dossier. Android Studio détecte le wrapper Gradle et propose
de synchroniser — accepte.

## 3. Importer des lieux

Écran **Importer** (icône en haut de la carte) :

1. Exporte ta liste depuis Google Maps :
   - **Google My Maps** : ouvre ta carte > menu ⋮ > *Télécharger un fichier KML*.
   - **Google Takeout** : takeout.google.com > sélectionne *Maps (vos lieux)* > *Enregistrés*
     → un ou plusieurs fichiers `.json` (GeoJSON), un par liste.
   - Ou un CSV avec des colonnes `nom,latitude,longitude[,description]`.
2. Dans Romurbex, choisis le fichier — les lieux détectés s'affichent en aperçu, donne un nom
   à la liste, confirme.

**Ou colle un lien Google Maps** (épingle unique, ou lien de liste partagée via le bouton
« Partager la liste ») dans le champ de liens de l'écran Importer.

**Ou colle une description d'épingle Pinterest** (écran Découvrir) si elle contient des
coordonnées GPS au format `45°20'51.3"N 0°11'41.3"W` — le titre et la description collés sont
analysés localement, aucune IA ni réseau impliqué dans l'extraction elle-même.

## 4. Attacher des photos

Depuis la fiche d'un lieu, bouton **Ajouter** dans la galerie : sélectionne une ou plusieurs
photos via le sélecteur système. Romurbex ne copie pas les fichiers, il garde une référence
(URI persistée) vers leur emplacement d'origine.

## 5. Recherche

La barre de recherche (carte ou liste) tape sur le nom du lieu, la description, tes notes et
le nom de la liste/dossier d'origine (jamais l'adresse — trop de faux positifs via des noms de
rue sans rapport) — mot par mot, insensible aux accents et tolérante aux fautes de frappe. La
carte se cadre automatiquement sur les résultats trouvés.

## 6. Carte

La carte utilise les tuiles OpenStreetMap via [osmdroid](https://github.com/osmdroid/osmdroid)
— aucune clé API ni compte Google Cloud nécessaire pour la vue standard. La vue satellite
utilise les tuiles Esri World Imagery (également sans clé). Connexion internet nécessaire pour
charger les tuiles (mises en cache localement après un premier affichage, ou en bloc via le
téléchargement hors-ligne en vue satellite).

## 7. Structure du projet

```
app/src/main/java/com/romurbex/app/
  ai/               client Gemini REST (recherche vocale + Découvrir)
  data/             entités Room (lieux, photos, listes), DAO, repository, préférences chiffrées
  importer/         parsing KML / GeoJSON (Takeout) / CSV / liens Google Maps / épingles Pinterest
  search/           recherche locale par mots-clés, tolérante aux fautes/accents
  voice/            reconnaissance et synthèse vocale (SpeechRecognizer / TextToSpeech)
  navigation/       graphe de navigation (carte, liste, fiche, édition, import, découvrir, réglages)
  ui/map/           écran carte (osmdroid), regroupement des pins, icônes de marqueur
  ui/list/          écran liste
  ui/lists/         gestion des listes importées (afficher/masquer/supprimer)
  ui/detail/        fiche lieu détaillée + galerie photos + visionneuse plein écran
  ui/edit/          formulaire d'ajout / modification
  ui/importscreen/  écran d'import (fichier ou lien Google Maps)
  ui/discover/      écran Découvrir (recherche IA + collage d'épingle Pinterest)
  ui/settings/      réglages (clés API Gemini, réponses vocales)
  ui/components/    éléments partagés (catégories, filtres, recherche vocale)
  ui/theme/         thème visuel (béton sombre, rouille, mousse)
```

## 8. Limites connues

- Pas de sauvegarde/synchronisation cloud : les données vivent uniquement dans la base locale
  du téléphone (Room). Pense à exporter/sauvegarder si tu changes de téléphone.
- L'import Google Takeout ne récupère que ce que Google inclut dans l'export GeoJSON (nom,
  coordonnées, adresse) — pas les photos ni les avis associés à un lieu.
- La recherche web de l'écran Découvrir (grounding Gemini) a son propre quota Google, séparé et
  plus strict que les appels Gemini classiques — souvent inutilisable sans facturation activée
  sur le projet Google Cloud de la clé.
