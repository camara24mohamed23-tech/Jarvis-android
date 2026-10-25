# Mettre en ligne le relais de Camara AI

Le relais est un petit programme gratuit chez Cloudflare. Il garde ta clé Anthropic cachée : tes amis utilisent l'application avec un code d'accès, sans clé.

## Avant de commencer

1. Un compte sur console.anthropic.com avec du crédit (rubrique Billing).
2. Une clé API créée sur ce compte (rubrique API keys), copiée dans un endroit sûr.
3. Dans Billing, règle une **limite de dépense mensuelle** : c'est ton filet de sécurité absolu.

## Étapes

1. Crée un compte gratuit sur **dash.cloudflare.com** (adresse e-mail, mot de passe, confirmation par e-mail).
2. Dans le menu : **Workers & Pages**, puis **Create**, puis **Create Worker**. Donne le nom `camara-ai` puis touche **Deploy**.
3. Touche **Edit code**. Supprime tout le texte, colle le contenu du fichier `worker.js`, puis touche **Deploy**.
4. Retourne sur la page du worker : **Settings**, puis **Variables and Secrets**, puis **Add**. Crée deux variables de type **Secret** :
   - `ANTHROPIC_KEY` : ta clé API Anthropic.
   - `ACCESS_CODES` : les codes d'accès séparés par des virgules, sans espace (par exemple `code1,code2,code3`).
   Touche **Deploy** pour enregistrer.
5. Copie l'adresse du worker (elle ressemble à `https://camara-ai.TON-NOM.workers.dev`) et envoie-la à Claude pour qu'il l'intègre à l'application.

## Limite journalière par ami (facultatif)

1. Dans Cloudflare : **Storage & Databases**, puis **KV**, puis **Create** : nom `LIMITS`.
2. Dans le worker : **Settings**, puis **Bindings**, puis **Add**, puis **KV namespace**. Nom de la variable : `LIMITS`. Choisis l'espace créé.
3. Ajoute la variable de type texte `DAILY_LIMIT` avec par exemple `150`. Chaque code pourra faire 150 requêtes par jour. Un message de l'application peut compter pour plusieurs requêtes quand Camara AI cherche sur internet.

## Gérer les codes

- **Couper un ami :** retire son code de `ACCESS_CODES` et redéploie. Son application cesse de répondre.
- **Ajouter un ami :** ajoute un nouveau code.
- Un code ne doit être donné qu'à une seule personne.

## Vérifier que ça marche

Ouvre l'adresse du worker dans le navigateur : tu dois voir « Relais Camara AI en ligne. ». Dans l'application, ouvre Réglages, colle ton code d'accès (laisse le champ de la clé API vide) et envoie un message.
