# Demonstration Developer Portal

This repo has a demonstration custom Apigee Developer Portal that is designed to run in Google Cloud Run.

* The backend is built on Java21, and [javalin](https://javalin.io/).  It uses
  maven for the build. You can use Java24 as well.

* The front-end uses [Angular v19](https://github.com/angular/angular) for UI,
  and [Firebase v11](https://www.npmjs.com/package/firebase) for authentication
  and signin.  It uses npm for package management and for the build.

## Disclaimer

This example is not an official Google product, nor is it part of an official Google product.

## Building and Running

Build in two phases:
- the webapp, via "ng build"
- the service, via maven.

### Webapp build
The webapp build runs in [the frontend directory](./frontend). It uses `ng` to build.

To run that build,
```
cd frontend
npm i
npm run build
```


### Service build

After building the frontend, build the service.
You must use Java 21 or later.

Start from the main directory.

1. copy the frontend dist files to the Java project.
   ```
    ./slurp.sh
   ```

1. verify Java
   ```
    java --version
   ```
   Make sure it's v21 or later.  I also used v24 successfully.

1. build
   ```
    cd backend
    mvn clean package
   ```

2. run locally
   ```
   java --enable-native-access=ALL-UNNAMED -jar backend/target/devportal-exp-backend-20250411.jar
   ```

Access it at: http://localhost:7070/ .


## Hosting in Google Cloud Run

### Building locally and deploying separately

0. First, set your environment.  Open the [env.sh](./env.sh) file in an editor, and apply the
   settings as appropriate for yoru environment. Save the file.

1. Open a terminal session. Source your environment file:
   ```bash
   source ./env.sh
   ```

2. Build the container image locally, and publish it to Artifact Registry:
   ```
   cd backend
   MAVEN_OPTS="--enable-native-access=ALL-UNNAMED" mvn clean package jib:build
   ```

   You can do the same with the helper script:
   ```sh
   ./bimage.sh
   ```

   In the output from the build, observe the output URL for the image.  It will look like:
   ```
   gcr.io/YOUR-GCP-PROJECT-HERE/cloud-builds-submit/devportal-exp-backend-container:20250411
   ```

   Optionally, you could now run the image locally, or in any container platform.

3. Deploy that image to Cloud Run:

   The service account should have role "roles/apigee.developerAdmin".

   ```
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

### Building and Deploying in one step

I am not sure this works, but I think you might be able to
Use the gcloud command line tool to build and deploy in one step.


0. First, set your environment.  Open the [env.sh](./env.sh) file in an editor, and apply the
   settings as appropriate for yoru environment. Save the file.

1. Open a terminal session. Source your environment file:
   ```bash
   source ./env.sh
   ```

2. Use gcloud to build an image, publish it to Artifact Registry, and dpeloy it:

   ```
   gcloud run deploy devportal-exp \
     --source . \
     --cpu 1 \
     --set-env-vars "APIGEE_PROJECT=${APIGEE_PROJECT}" \
     --memory '512Mi' \
     --min-instances 0 \
     --max-instances 1 \
     --allow-unauthenticated \
     --service-account ${SERVICE_ACCOUNT} \
     --project ${CLOUDRUN_PROJECT} \
     --region ${CLOUDRUN_REGION} \
     --timeout 300
   ```

And again, access it via the URL emitted by that command.

## License

This material is Copyright 2019-2025
Google LLC and is licensed under the [Apache 2.0
License](LICENSE). This includes the Java code, the TypeScript and JavaScript, the CSS and HTML, and
all other configuration.

## Bugs

??
