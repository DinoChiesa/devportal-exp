# Key & Certificate

You must create a public/private keypair, and an X509 certificate, in order to
allow the example service to run.

You can do this from a command-line terminal session.


1. First, get a timestamp:
   ```sh
   TIMESTAMP=$(date +%Y%m%d-%H%M)
   ```

2. Then, create the keypair:

   ```sh
   openssl genpkey -algorithm rsa -pkeyopt rsa_keygen_bits:2048 -out "issuer-rsa-private-key-${TIMESTAMP}.pem"
   ```

2. Then, create the X509 certificate from that keypair:
   ```sh
   openssl req -new -x509 -sha256 -days 3650 \
     -key "issuer-rsa-private-key-${TIMESTAMP}.pem" \
     -out "issuer-certificate-${TIMESTAMP}.pem" \
     -subj "/C=US/ST=Washington/L=Kirkland/O=Google LLC/OU=Apigee/CN=Apigee Demonstration Portal ${TIMESTAMP} Test Root CA" \
     -addext "basicConstraints=critical,CA:TRUE" \
     -addext "keyUsage=critical,keyCertSign,cRLSign" \
     -addext "extendedKeyUsage=serverAuth,clientAuth" \
     -addext "subjectKeyIdentifier=hash"
   ```

