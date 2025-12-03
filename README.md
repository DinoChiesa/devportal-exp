# Demonstration Developer Portal

This is a custom Apigee Developer Portal that is designed to run in Google Cloud Run.

* The backend is built on Java21, and [javalin](https://javalin.io/).  It uses
  maven for the build. You can use Java24 as well.

* The front-end uses [Angular v19](https://github.com/angular/angular) for UI,
  and [Firebase v11](https://www.npmjs.com/package/firebase) for authentication
  and signin.  It uses npm for package management and for the build.

## Disclaimer

This example is not an official Google product, nor is it part of an official Google product.

## Current Status

This is for demonstration, exploration, and illustration. It is not expected to
be practically usable in production, without modification and extension.

### Features

- Firebase Authn / Google Identity Sign-in for developers

- Presents the list of Apigee apps registered for a developer, and credentials for same.

- Allows user to add an app and generate new client credentials, choosing among
  the available API products.

- Allows user to remove a registered app from their account.

- Allows user to generate and register RSA keypairs to their accounts,
  potentially supporting JWT bearer token grants.

- Views/peruses the list of API products available.

- Runs in Google Cloud Run


### Limitations

- No support for sign-in mechanisms other than Firebase.  Eg, it does not
  support bringing your own OIDC provider.
- No support for viewing API analytics.
- No support for rendering OpenAPI Specs for API products
- No support for Teams-owned developer apps
- No support for "skinning" or styling the portal without re-compiling
- I'm sure there are many others


## Building and Running

Build in two phases:
- the webapp, via "npm run build" which in turn runs "ng build"
- the service, via maven.

### Preparation

0. First, set your environment.  Open the [env.sh](./env.sh) file in an editor,
   and apply the settings as appropriate for yoru environment. This includes
   settings for Google Cloud and Firebase Auth.  Save the file.

1. Open a terminal session. Source your environment file:
   ```bash
   source ./env.sh
   ```

2. Create the service account necessary for the service:
   ```bash
   ./create-sa.sh
   ```

3. Create the keypair and certificate used for demonstration purposes.
   ```bash
   ./create-keypair.sh
   ```


### Webapp build

The webapp build runs in [the frontend directory](./frontend). It uses `npm` and `ng` to build.

To run that build, use the same terminal from which you sourced your env.sh settings.

```
cd frontend
npm install
npm run build
```

Alternatively, there is a helper script which avoids the need to `cd`:
```
./1-bfront.sh
```

If the build is successfuly, a post build step will copy the produced files into the backend src directory.

### Service build

After building the frontend, build the service.
You must use Java 21 or later.

Start from the main directory.

1. verify your Java version:
   ```sh
   java --version
   ```
   Make sure it's v21 or later.  I also used v24 successfully.

1. run the build:
   ```sh
   cd backend
   MAVEN_OPTS="--enable-native-access=ALL-UNNAMED" mvn clean package
   ```

   Alternatively, there is a helper script which avoids the need to `cd`:
   ```sh
   ./2-bback.sh
   ```

2. run locally
   ```sh
   java --enable-native-access=ALL-UNNAMED -jar backend/target/devportal-exp-backend-20250411.jar
   ```

   Alternatively, there is a helper script:
   ```sh
   ./brun.sh
   ```


After the service starts, access it at: http://localhost:7070/ .


## Hosting in Google Cloud Run

### Building locally and deploying separately

1. Build the container image locally, and publish it to Artifact Registry:
   ```sh
   cd backend
   MAVEN_OPTS="--enable-native-access=ALL-UNNAMED" mvn clean package jib:build
   ```

   You can do the same with the helper script:
   ```sh
   ./3-bimage.sh
   ```

   In the output from the build, observe the output URL for the image.  It will look like:
   ```
   gcr.io/YOUR-GCP-PROJECT-HERE/cloud-builds-submit/devportal-exp-backend-container:20250411
   ```

   Optionally, you could now run the image locally, or in any container platform.

3. Deploy that image to Cloud Run:

   The service account should have role "roles/apigee.developerAdmin".

   ```sh
   gcloud run deploy devportal-exp \
     --image gcr.io/${REPSITORY_PROJECT}/cloud-builds-submit/devportal-exp-backend-container:20250411 \
     --cpu 1 \
     --set-env-vars "APIGEE_PROJECT=${APIGEE_PROJECT}" \
     --memory '512Mi' \
     --min-instances 0 \
     --max-instances 1 \
     --allow-unauthenticated \
     --service-account ${SERVICE_ACCOUNT} \
     --project ${CLOUDRUN_PROJECT}\
     --region ${CLOUDRUN_REGION} \
     --timeout 300
   ```

   Access it via the URL emitted by that command.

   Again, there is a shortcut script:
   ```sh
   ./4-deploy-prebuilt-image-to-cloudrun.sh
   ```

## License

This material is Copyright 2019-2025 Google LLC and is licensed under the
[Apache 2.0 License](LICENSE). This includes the Java code, the TypeScript and
JavaScript, the CSS and HTML, and all other configuration.

## Bugs

??
