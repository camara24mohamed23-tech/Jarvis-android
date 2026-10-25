# Jarvis pour Android

Assistant personnel en français : discussion par la voix ou par écrit, réponses à voix haute, rappels avec notification, alarmes, minuteurs, ouverture d'applications, préparation d'appels et de SMS.

## Obtenir l'application (sans Android Studio)

1. Crée un compte gratuit sur github.com, puis un dépôt vide nommé `jarvis-android`.
2. Envoie-y tout le contenu de ce dossier, y compris le dossier caché `.github`.
3. Ouvre l'onglet **Actions** du dépôt. La construction « Construire Jarvis » démarre seule et dure environ 4 minutes.
4. Quand elle est verte, ouvre-la et télécharge l'élément **jarvis-apk** (un fichier zip contenant `app-debug.apk`).
5. Sur ton téléphone, ouvre l'APK et autorise l'installation depuis cette source quand Android le demande.

## Première utilisation

Au premier lancement, Jarvis demande une clé API Claude : crée-la sur console.anthropic.com (API keys), ajoute un peu de crédit, puis colle-la dans Réglages. Elle reste uniquement sur le téléphone. Autorise le micro et les notifications quand Android le demande.

Le modèle par défaut est `claude-haiku-5-5` (rapide et économique). Tu peux le changer dans Réglages.

## Ce que Jarvis fait sur le téléphone

| Demande | Résultat |
| --- | --- |
| « Rappelle-moi demain à 9 h d'appeler Mamadou » | Notification sonore à l'heure exacte, même écran éteint |
| « Réveille-moi à 6 h 30 » | Alarme dans l'application Horloge |
| « Minuteur de 10 minutes » | Minuteur dans l'application Horloge |
| « Ouvre WhatsApp » | Lance l'application |
| « Appelle le 622 00 00 00 » | Ouvre le clavier d'appel avec le numéro, tu appuies sur appeler |
| « Écris un SMS à 622 00 00 00 : j'arrive » | Ouvre les messages avec le texte prêt, tu appuies sur envoyer |
| « Emmène-moi au marché Madina » | Ouvre Google Maps |

Les appels et les SMS restent validés par toi : Jarvis ne peut pas les lancer seul.

## Appel vocal, internet et actualité

- **Appel vocal :** touche « Appel vocal ». Parle, Jarvis répond à voix haute puis réécoute, sans toucher l'écran. Dis « raccroche » ou touche « Raccrocher » pour finir. L'appel s'arrête seul après quelques silences.
- **Recherche internet et actualité mondiale :** Jarvis cherche sur le web pour les infos récentes (actualité, politique, prix, météo, sport). Il cite ses sources et présente les positions de façon équilibrée sur les sujets disputés.
- **Commandes en ligne :** Jarvis cherche le produit, compare des offres, ouvre la page ou prépare un message pour le vendeur. Tu finalises toi-même le paiement.

La recherche internet doit être activée sur ton compte Anthropic (console.anthropic.com, Settings) et chaque recherche est facturée en plus des messages.

## Réponses automatiques WhatsApp

Dans Réglages, active « Jarvis répond à ma place ». Jarvis lit les notifications WhatsApp et répond aux messages texte avec le bouton « Répondre » de la notification. Il ignore les vocaux, photos, appels et, par défaut, les groupes.

- Chaque réponse commence par « [Jarvis, assistant de ... ] » : le contact sait qu'il parle à un assistant automatique.
- Jarvis ne donne aucune information personnelle et ne s'engage sur rien. Pour un message urgent, un sujet d'argent ou un code, il dit qu'il transmet et tu reçois une notification « À voir sur WhatsApp ».
- « Voir les réponses envoyées » affiche le journal de tout ce qui a été envoyé.
- Autorise l'accès aux notifications (bouton dans Réglages). Sur Android 13 ou plus récent, si le réglage est grisé : Paramètres, Applications, Jarvis, menu à trois points, « Autoriser les paramètres restreints ».
- Désactive l'économiseur de batterie pour Jarvis, sinon Android peut arrêter le service.
- Le texte des messages reçus est envoyé à Claude pour préparer la réponse. Ne l'active pas si tes contacts échangent des informations très confidentielles avec toi.
