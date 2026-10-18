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
