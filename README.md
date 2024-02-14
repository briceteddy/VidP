# vidP : Pipeline de traitement video en utilisant Amazon Kinesis Video Streams et AWS Fargate

## Table des matières
- vidP : Pipeline de traitement video
  - Table des matières
  - Introduction
  - Badges
  - Conditions préalables et limitations
   - Prérequis
   - Limites
  - Architecture
    - Pile technologique cible
    - Architecture cible
  - Outils 
  - Déployer l'infrastructure
  - Executez un test
  - Informations supplémentaires
    -  Choisir un IDE
    -  Nettoyage
  - Contribuer
  - Difficultes rencontrees


## Introduction

vidP est un projet qui permet de créer un pipeline de traitement automatique de vidéo. Il utilise des conteneurs Docker pour effectuer différentes opérations sur les vidéos, telles que la compression, l'identification de la langue et la génération des sous-titres. Il utilise ensuite des machines virtuelles AWS pour agréger les métadonnées extraites et les vidéos compressées, et les afficher sur une page web publique.


## Badges

![License: MIT](https://stackoverflow.com/questions/5922882/what-file-uses-md-extension-and-how-should-i-edit-them) ![Docker](https://stackoverflow.com/questions/75964216/github-how-to-change-font-size-in-markdown-file) ![AWS](https://www.markdownguide.org/basic-syntax/)

# Conditions préalables et limitations

### Prérequis

* Un compte AWS actif

* Kit de développement Java SE (JDK) 11, installé

* Apache Maven, installé

* Kit de développement cloud AWS (AWS CDK), installé

* Interface de ligne de commande AWS (AWS CLI) version 2, installée

* Docker (nécessaire pour créer des images Docker à utiliser dans les définitions de tâches AWS Fargate), installé

### Limites 

Il ne peut pas être utilisé sous sa forme actuelle dans les déploiements de production.

## Architecture

### Pile technologique cible

* Amazon Kinesis Video Streams

* Tâche AWS Fargate

* File d'attente Amazon Simple Queue Service (Amazon SQS)

* Compartiment Amazon S3

### Architecture cible

![architecture cible](image/save.png.png)

L'utilisateur crée un flux vidéo Kinesis, télécharge une vidéo et envoie un message JSON contenant des détails sur le flux vidéo Kinesis d'entrée et le bucket S3 de sortie vers une file d'attente SQS. AWS Fargate, qui exécute l'application principale dans un conteneur, extrait le message de la file d'attente SQS et commence à extraire les cadres. Chaque image est enregistrée dans un fichier image et stockée dans le compartiment S3 cible.

## Outils 
* **AWS CDK** est un framework de développement logiciel permettant de définir votre infrastructure et vos ressources cloud à l'aide de langages de programmation tels que Python TypeScript JavaScript, Java et C#/Net.

* **Amazon Kinesis Video Streams** est un service AWS entièrement géré que vous pouvez utiliser pour diffuser des vidéos en direct depuis des appareils vers le cloud AWS, ou créer des applications pour le traitement vidéo en temps réel ou l'analyse vidéo par lots.

* **AWS Fargate** est un moteur de calcul sans serveur pour les conteneurs. Fargate élimine le besoin de provisionner et de gérer des serveurs, et vous permet de vous concentrer sur le développement de vos applications.

* **Amazon S3** est un service de stockage d'objets qui offre évolutivité, disponibilité des données, sécurité et performances.

* **Amazon SQS** est un service de mise en file d'attente de messages entièrement géré qui vous permet de découpler et de dimensionner les microservices, les systèmes distribués et les applications sans serveur.

## Déployer l'infrastructure

| Tâche                                  | Description                                                                                                                                                                                                                                                                                                                                                                                                                       | Compétences requises |
|----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| Lancez le démon Docker.               | Démarrez le daemon Docker sur votre système local. L'AWS CDK utilise Docker pour créer l'image utilisée dans la tâche AWS Fargate. Vous devez exécuter Docker avant de passer à l'étape suivante.                                                                                                                                                                                                                               | Développeur  |
| Générez le projet.                    | Téléchargez l'frame-splitter-code et le coller en local sur votre machine. Avant de déployer l'infrastructure, vous devez créer le projet Java Maven. À l'invite de commande, accédez au répertoire racine du projet et créez le projet en exécutant la commande suivante : `mvn clean install`                                                                                                                       | Développeur|
| Démarrez le kit AWS CDK.             |  Démarrer l'environnement en exécutant la commande de la CLI AWS : `cdk bootstrap --profile "$AWS_PROFILE_NAME"` où $AWS_PROFILE_NAME contient le nom du profil AWS issu de vos informations d'identification AWS. Pour plus d'informations,                                       | Développeur |
| Déployez la pile AWS CDK.            | Au cours de cette étape, vous créez les ressources d'infrastructure requises (file d'attente SQS, compartiment S3, définition de tâche AWS Fargate) dans votre compte AWS, vous créez l'image Docker requise pour la tâche AWS Fargate et vous déployez l'application. À l'invite de commande, accédez au répertoire racine du projet et exécutez la commande suivante : `cdk deploy --profile "$AWS_PROFILE_NAME" --all` | Développeur |
| Créez un flux vidéo.                  | Au cours de cette étape, vous allez créer un flux vidéo Kinesis qui servira de flux d'entrée pour le traitement vidéo. Assurez-vous que l'interface de ligne de commande AWS est installée et configurée. Dans l'AWS CLI, exécutez : `aws kinesisvideo --profile "$AWS_PROFILE_NAME" create-stream --stream-name "$STREAM_NAME" --data-retention-in-hours "24"` où $AWS_PROFILE_NAME contient le nom du profil AWS issu de vos informations d'identification AWS et $STREAM_NAME est un nom de flux valide. | Développeur |


## Executez un test 

| Tâche                               | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Compétences requises            |
|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| Téléchargez la vidéo sur le stream. | Dans le dossier de projet de l'exemple d'application frame-splitter-code, ouvrez le fichier ProcessingTaskTest.java qui se trouve à src/test/java/amazon/awscdk/examples/splitter. Remplacez les variables streamName et profileName par les valeurs que vous avez utilisées dans les étapes précédentes. Pour télécharger l'exemple de vidéo dans le flux vidéo Kinesis que vous avez créé à l'étape précédente, exécutez : `amazon.awscdk.examples.splitter.ProcessingTaskTest#testExample test`. | Développeur |
| Lancez le traitement vidéo.         | Maintenant que vous avez chargé une vidéo dans le flux vidéo Kinesis, vous pouvez commencer à la traiter. Pour lancer la logique de traitement, vous devez envoyer un message contenant des informations détaillées à la file d'attente SQS créée par le CDK AWS lors du déploiement. Pour envoyer un message à l'aide de l'AWS CLI, exécutez : `aws sqs --profile "$AWS_PROFILE_NAME" send-message --queue-url QUEUE_URL --message-body MESSAGE`, où $AWS_PROFILE_NAME contient le nom du profil AWS issu de vos informations d'identification AWS (supprimez ce paramètre pour utiliser le profil par défaut), QUEUE_URL est la QueueUrlvaleur de la sortie du CDK AWS et MESSAGE est une chaîne JSON au format suivant : `{ "streamARN": "STREAM_ARN", "bucket": "BUCKET_NAME", "s3Directory": "test-output" }`, où STREAM_ARN est l'ARN du flux vidéo que vous avez créé lors d'une étape précédente et BUCKET_NAME la valeur du bucket issue de la sortie AWS CDK. L'envoi de ce message lance le traitement vidéo. Vous pouvez également envoyer un message à l'aide de la console Amazon SQS, comme décrit dans la documentation Amazon SQS. | Développeur |
| Visionnez des images des images vidéo. | Vous pouvez voir les images obtenues dans le compartiment de sortie S3, `s3://BUCKET_NAME/test-output` où `BUCKET_NAME` trouve la valeur du compartiment provenant de la sortie AWS CDK. | Développeur|

## Informations supplémentaires

### Choisir un IDE

Nous vous recommandons d'utiliser votre IDE Java préféré pour créer et explorer ce projet.

### Nettoyage

Une fois que vous avez terminé d'exécuter cet exemple, supprimez toutes les ressources déployées pour éviter d'encourir des coûts supplémentaires liés à l'infrastructure AWS.

Pour supprimer l'infrastructure et le flux vidéo, utilisez ces deux commandes dans l'AWS CLI :

`cdk destroy --profile "$AWS_PROFILE_NAME" --all`

`aws kinesisvideo --profile "$AWS_PROFILE_NAME" delete-stream --stream-arn "$STREAM_ARN"`

## Contribuer

* Si vous souhaitez contribuer à ce projet, vous pouvez créer une branche, faire vos modifications et soumettre une pull request.
* Vous pouvez également signaler les bugs ou proposer des améliorations en créant une issue.
* Merci de respecter le code de conduite et les conventions de codage du projet.
* Ce projet est sous licence MIT


# Difficultes rencontrees
 
 * la complexité du système à concevoir.


Auteur : SIMO CHENDJOU BRICE TEDDY
Date : 14 Fevrier 2024 