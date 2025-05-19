# environment variables needed

export REPOSITORY_PROJECT=my-artifact-registry-proj
export CLOUDRUN_PROJECT=my-crun-proj-2092
export CLOUDRUN_REGION=us-west1
export APIGEE_PROJECT=my-project-178939

# SHORT form of the service account; it must be available in the CLOUDRUN_PROJECT
# Create it with ./create-sa.sh
export SERVICE_ACCOUNT=short-form-sa-name

# Firebase things

# Get this from the Firebase console.
#
# • Go to your Firebase project online (https://console.firebase.google.com/).
#
# • Click the Gear icon (Project settings) next to "Project Overview".
#
# • In the "General" tab, scroll down to the "Your apps" section.
#
# • If you haven't added your web app yet, click "Add app" and select the Web
#   platform (</>). Follow the steps to register your app (you'll give it a
#   nickname).
#
# • If you have already added your web app, click on it in the list.
#
# • Look for the "Firebase SDK snippet" section and select the "Config" option.
#
# • The configuration object shown there will contain your apiKey, authDomain,
#   projectId, storageBucket, messagingSenderId, and the appId. Copy the appId
#   value from there into your environment.ts and environment.prod.ts files.

export FB_APP_ID="paste-in-value-from-firebase-here"
export FB_MSGING_SENDER_ID=1000000001010101
export FB_STORAGE_BUCKET=project-name-00000.firebasestorage.app
export FB_PROJECT_ID=project-name-00000
export FB_AUTH_DOMAIN=project-name-00000.firebaseapp.com
export FB_API_KEY=INSERT_ACTUAL_VALUE_HERE
