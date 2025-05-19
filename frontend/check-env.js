const requiredEnvVars = [
  "FB_APP_ID",
  "FB_MSGING_SENDER_ID",
  "FB_STORAGE_BUCKET",
  "FB_PROJECT_ID",
  "FB_AUTH_DOMAIN",
  "FB_API_KEY",
];

console.log("Checking for required environment variables...");

let missingVars = requiredEnvVars.filter((varName) => {
  const val = process.env[varName];
  return !val || val.trim() === "";
});

if (missingVars.length) {
  console.error(
    "\nBuild cannot proceed due to missing or empty environment variables: " +
      missingVars.join(", "),
  );
  process.exit(1);
}

console.log(
  "\nAll required environment variables are set. Proceeding with the build.",
);
process.exit(0);
